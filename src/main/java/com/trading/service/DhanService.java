package com.trading.service;

import com.trading.dto.ClosePositionRequest;
import com.trading.dto.CreateOrderRequest;
import com.trading.model.DhanAccount;
import com.trading.model.Order;
import com.trading.model.Position;
import com.trading.repository.DhanAccountRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResponseErrorHandler;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
public class DhanService {

    @Autowired
    private DhanAccountRepository accountRepository;

    private final RestTemplate restTemplate;
    private static final String DHAN_BASE_URL = "https://api.dhan.co";

    public DhanService(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder
                .errorHandler(new RestTemplateErrorHandler())
                .build();
    }

    public DhanAccount linkAccount(String clientId, String accessToken) {
        Optional<DhanAccount> existing = accountRepository.findByClientId(clientId);

        DhanAccount account;
        if (existing.isPresent()) {
            account = existing.get();
        } else {
            account = new DhanAccount();
            account.setClientId(clientId);
        }

        account.setAccessToken(accessToken);
        account.setActive(true);
        account.setLinkedAt(LocalDateTime.now());
        account.setLastSyncedAt(LocalDateTime.now());

        return accountRepository.save(account);
    }

    public Optional<DhanAccount> getActiveAccount() {
        return accountRepository.findByIsActiveTrue();
    }

    public List<Position> getPositions() {
        Optional<DhanAccount> account = getActiveAccount();
        if (account.isEmpty()) {
            throw new RuntimeException("No active Dhan account linked");
        }

        try {
            HttpHeaders headers = getDhanHeaders(account.get().getAccessToken());
            HttpEntity<String> entity = new HttpEntity<>(headers);

            String url = DHAN_BASE_URL + "/v2/positions";

            log.info("Fetching positions from Dhan API: {}", url);

            ResponseEntity<List> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, List.class
            );

            log.debug("Dhan API Response Body: {}", response.getBody());

            List<Position> positions = parsePositions(response.getBody());

            log.info("Parsed {} open positions", positions.size());

            account.get().setLastSyncedAt(LocalDateTime.now());
            accountRepository.save(account.get());

            return positions;

        } catch (HttpStatusCodeException e) {
            log.error("Dhan Positions API returned status {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Failed to fetch positions: " + e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Error fetching positions from Dhan", e);
            throw new RuntimeException("Failed to fetch positions: " + e.getMessage());
        }
    }

    public Order createOrder(CreateOrderRequest request) {
        Optional<DhanAccount> account = getActiveAccount();
        if (account.isEmpty()) {
            throw new RuntimeException("No active Dhan account linked");
        }

        try {
            HttpHeaders headers = getDhanHeaders(account.get().getAccessToken());

            Map<String, Object> orderData = new HashMap<>();
            orderData.put("dhanClientId", account.get().getClientId());
            orderData.put("transactionType", request.getTransactionType());
            orderData.put("exchangeSegment", request.getExchange());
            orderData.put("productType", request.getProductType());
            orderData.put("orderType", request.getOrderType());
            orderData.put("quantity", request.getQuantity());
            orderData.put("securityId", request.getSymbol()); // In your App.jsx, symbol is passed as securityId
            orderData.put("validity", "DAY");

            if ("LIMIT".equals(request.getOrderType())) {
                orderData.put("price", request.getPrice());
            }

            log.info("Sending Order to Dhan: {}", orderData);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(orderData, headers);

            String url = DHAN_BASE_URL + "/v2/orders";
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, Map.class
            );

            log.info("Order Response: {}", response.getBody());

            // 🌟 FIX: Parse the REAL response from Dhan
            return parseOrder(response.getBody());

        } catch (HttpStatusCodeException e) {
            log.error("Dhan Orders API Error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            // 🌟 FIX: Throw exception so frontend sees the error instead of "Order Placed"
            throw new RuntimeException(e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Error creating order on Dhan: {}", e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }

    public Order closeOrder(String orderId) {
        Optional<DhanAccount> account = getActiveAccount();
        if (account.isEmpty()) {
            throw new RuntimeException("No active Dhan account linked");
        }

        try {
            HttpHeaders headers = getDhanHeaders(account.get().getAccessToken());
            HttpEntity<String> entity = new HttpEntity<>(headers);

            String url = DHAN_BASE_URL + "/v2/orders/" + orderId;
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.DELETE, entity, Map.class
            );

            return parseOrder(response.getBody());

        } catch (HttpStatusCodeException e) {
            log.error("Dhan Close Order API Error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException(e.getResponseBodyAsString());
        }
    }

    public Order closePosition(ClosePositionRequest request) {
        Optional<DhanAccount> account = getActiveAccount();
        if (account.isEmpty()) {
            throw new RuntimeException("No active Dhan account linked");
        }

        try {
            String transactionType = "LONG".equals(request.getPositionType()) ? "SELL" : "BUY";
            HttpHeaders headers = getDhanHeaders(account.get().getAccessToken());

            Map<String, Object> orderData = new HashMap<>();
            orderData.put("dhanClientId", account.get().getClientId());
            orderData.put("transactionType", transactionType);
            orderData.put("exchangeSegment", request.getExchange());
            orderData.put("productType", request.getProductType());
            orderData.put("orderType", "MARKET");
            orderData.put("validity", "DAY");
            orderData.put("quantity", request.getQuantity());
            orderData.put("securityId", request.getSecurityId());

            log.info("Closing Position: {}", orderData);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(orderData, headers);
            String url = DHAN_BASE_URL + "/v2/orders";

            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, Map.class
            );

            Order order = parseOrder(response.getBody());
            order.setSymbol(request.getSymbol());
            return order;

        } catch (HttpStatusCodeException e) {
            log.error("Dhan Close Position Error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Failed to close position: " + e.getResponseBodyAsString());
        }
    }

    private HttpHeaders getDhanHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("access-token", accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private List<Position> parsePositions(List<Map<String, Object>> responseList) {
        List<Position> positions = new ArrayList<>();
        if (responseList == null || responseList.isEmpty()) return positions;

        for (Map<String, Object> posMap : responseList) {
            try {
                int netQty = getIntValue(posMap, "netQty");

                // Only show OPEN positions (netQty != 0)
                if (netQty == 0) continue;

                Position pos = new Position();
                pos.setSymbol(getStringValue(posMap, "tradingSymbol"));
                pos.setSecurityId(getStringValue(posMap, "securityId"));
                pos.setExchange(getStringValue(posMap, "exchangeSegment"));
                pos.setProductType(getStringValue(posMap, "productType"));
                pos.setQuantity(Math.abs(netQty));
                pos.setPositionType(netQty > 0 ? "LONG" : "SHORT");

                // Calculate Price logic
                double avgPrice = 0.0;
                if (netQty > 0) {
                    avgPrice = getDoubleValue(posMap, "buyAvg");
                    if (avgPrice == 0.0) avgPrice = getDoubleValue(posMap, "avgPrice");
                } else {
                    avgPrice = getDoubleValue(posMap, "sellAvg");
                    if (avgPrice == 0.0) avgPrice = getDoubleValue(posMap, "avgPrice");
                }

                // Fallback for price
                if (avgPrice == 0.0) {
                    double dayBuyVal = getDoubleValue(posMap, "dayBuyValue");
                    int dayBuyQty = getIntValue(posMap, "dayBuyQty");
                    if (dayBuyQty > 0) avgPrice = dayBuyVal / dayBuyQty;
                }

                pos.setAvgPrice(avgPrice);

                double realizedPnl = getDoubleValue(posMap, "realizedProfit");
                double unrealizedPnl = getDoubleValue(posMap, "unrealizedProfit");
                pos.setPnl(realizedPnl + unrealizedPnl);

                double ltp = getDoubleValue(posMap, "lastTradedPrice");
                if (ltp == 0.0) ltp = getDoubleValue(posMap, "ltp");
                pos.setLtp(ltp);

                positions.add(pos);
            } catch (Exception e) {
                log.error("Error parsing position: {}", e.getMessage());
            }
        }
        return positions;
    }

    // 🌟 FIX: Actual parsing of Order Response
    private Order parseOrder(Map<String, Object> response) {
        Order order = new Order();
        if (response != null) {
            order.setOrderId(getStringValue(response, "orderId"));
            order.setStatus(getStringValue(response, "orderStatus"));
            // Add other fields if needed
        }
        return order;
    }

    private String getStringValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? String.valueOf(value) : null;
    }

    private int getIntValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) return ((Number) value).intValue();
        try { return Integer.parseInt(String.valueOf(value)); } catch (Exception e) { return 0; }
    }

    private double getDoubleValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) return ((Number) value).doubleValue();
        try { return Double.parseDouble(String.valueOf(value)); } catch (Exception e) { return 0.0; }
    }

    private static class RestTemplateErrorHandler implements ResponseErrorHandler {
        @Override
        public boolean hasError(ClientHttpResponse httpResponse) throws IOException {
            return (httpResponse.getStatusCode().is4xxClientError() ||
                    httpResponse.getStatusCode().is5xxServerError());
        }

        @Override
        public void handleError(ClientHttpResponse httpResponse) throws IOException {
            // Allow exception to propagate to be caught in try-catch blocks
        }
    }
}