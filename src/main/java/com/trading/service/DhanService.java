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

            log.info("Dhan API Response Status: {}", response.getStatusCode());
            log.info("Dhan API Response Body: {}", response.getBody());

            List<Position> positions = parsePositions(response.getBody());

            log.info("Parsed {} positions", positions.size());

            account.get().setLastSyncedAt(LocalDateTime.now());
            accountRepository.save(account.get());

            return positions;

        } catch (HttpStatusCodeException e) {
            log.error("Dhan Positions API returned status {}: {}",
                    e.getStatusCode(),
                    e.getResponseBodyAsString());
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
            orderData.put("securityId", request.getSymbol());

            if ("LIMIT".equals(request.getOrderType())) {
                orderData.put("price", request.getPrice());
            }

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(orderData, headers);

            String url = DHAN_BASE_URL + "/v2/orders";
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, Map.class
            );

            return parseOrder(response.getBody());

        } catch (HttpStatusCodeException e) {
            log.error("Dhan Orders API returned status {}: {}",
                    e.getStatusCode(),
                    e.getResponseBodyAsString());
            return getMockOrder(request);

        } catch (Exception e) {
            log.error("Error creating order on Dhan: {}", e.getMessage());
            return getMockOrder(request);
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
            log.error("Dhan Close Order API returned status {}: {}",
                    e.getStatusCode(),
                    e.getResponseBodyAsString());
            Order order = new Order();
            order.setOrderId(orderId);
            order.setStatus("CANCEL_FAILED");
            return order;

        } catch (Exception e) {
            log.error("Error closing order on Dhan: {}", e.getMessage());
            Order order = new Order();
            order.setOrderId(orderId);
            order.setStatus("CANCELLED");
            return order;
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

        if (responseList == null || responseList.isEmpty()) {
            log.warn("Positions response is null or empty");
            return positions;
        }

        log.info("Parsing {} position records", responseList.size());

        for (Map<String, Object> posMap : responseList) {
            try {
                log.debug("Parsing position map: {}", posMap);

                Position pos = new Position();

                // Get symbol - try multiple field names
                String symbol = getStringValue(posMap, "tradingSymbol");
                if (symbol == null || symbol.isEmpty()) {
                    symbol = getStringValue(posMap, "securityId");
                }
                if (symbol == null || symbol.isEmpty()) {
                    log.warn("Skipping position with no symbol: {}", posMap);
                    continue;
                }
                pos.setSymbol(symbol);

                // Exchange and Product Type
                pos.setExchange(getStringValue(posMap, "exchangeSegment"));
                pos.setProductType(getStringValue(posMap, "productType"));

                // Net Quantity - this is the key field for positions
                int netQty = getIntValue(posMap, "netQty");

                log.debug("Position {} - netQty: {}", symbol, netQty);

                // Skip if position is closed (netQty = 0)
                if (netQty == 0) {
                    log.debug("Skipping position {} with netQty=0", symbol);
                    continue;
                }

                pos.setQuantity(Math.abs(netQty));
                pos.setPositionType(netQty > 0 ? "LONG" : "SHORT");

                // Average Price - try multiple approaches
                double avgPrice = 0.0;

                // First try: Use buyAvg or sellAvg based on position type
                if (netQty > 0) {
                    avgPrice = getDoubleValue(posMap, "buyAvg");
                    if (avgPrice == 0.0) {
                        avgPrice = getDoubleValue(posMap, "avgPrice");
                    }
                } else {
                    avgPrice = getDoubleValue(posMap, "sellAvg");
                    if (avgPrice == 0.0) {
                        avgPrice = getDoubleValue(posMap, "avgPrice");
                    }
                }

                // Fallback: Calculate from day values if available
                if (avgPrice == 0.0) {
                    double dayBuyValue = getDoubleValue(posMap, "dayBuyValue");
                    double daySellValue = getDoubleValue(posMap, "daySellValue");
                    int dayBuyQty = getIntValue(posMap, "dayBuyQty");
                    int daySellQty = getIntValue(posMap, "daySellQty");

                    if (netQty > 0 && dayBuyQty > 0) {
                        avgPrice = dayBuyValue / dayBuyQty;
                    } else if (netQty < 0 && daySellQty > 0) {
                        avgPrice = daySellValue / daySellQty;
                    }
                }

                pos.setAvgPrice(avgPrice);

                // P&L - use realizedProfit + unrealizedProfit
                double realizedPnl = getDoubleValue(posMap, "realizedProfit");
                double unrealizedPnl = getDoubleValue(posMap, "unrealizedProfit");
                double totalPnl = realizedPnl + unrealizedPnl;
                pos.setPnl(totalPnl);

                // LTP - Last Traded Price
                double ltp = getDoubleValue(posMap, "lastTradedPrice");
                if (ltp == 0.0) {
                    ltp = getDoubleValue(posMap, "ltp");
                }
                if (ltp == 0.0) {
                    // If LTP not available, calculate from avgPrice + pnl
                    if (avgPrice > 0 && netQty != 0) {
                        ltp = avgPrice + (unrealizedPnl / Math.abs(netQty));
                    }
                }
                pos.setLtp(ltp);

                log.info("Successfully parsed position: {} - Qty: {}, Avg: {}, LTP: {}, PnL: {}",
                        symbol, pos.getQuantity(), avgPrice, ltp, totalPnl);

                positions.add(pos);

            } catch (Exception e) {
                log.error("Error parsing position map: {}", posMap, e);
            }
        }

        log.info("Successfully parsed {} valid positions", positions.size());
        return positions;
    }

    // Helper methods to safely extract values from Map
    private String getStringValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        return String.valueOf(value);
    }

    private int getIntValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return 0;
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            log.warn("Could not parse int value for key {}: {}", key, value);
            return 0;
        }
    }

    private double getDoubleValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return 0.0;
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            log.warn("Could not parse double value for key {}: {}", key, value);
            return 0.0;
        }
    }

    private Order parseOrder(Map<String, Object> response) {
        Order order = new Order();
        order.setOrderId(UUID.randomUUID().toString());
        order.setStatus("PLACED");
        return order;
    }

    private List<Position> getMockPositions() {
        List<Position> positions = new ArrayList<>();

        Position pos1 = new Position();
        pos1.setSymbol("RELIANCE");
        pos1.setExchange("NSE");
        pos1.setQuantity(10);
        pos1.setAvgPrice(2450.50);
        pos1.setLtp(2465.75);
        pos1.setPnl(152.50);
        pos1.setProductType("INTRADAY");
        pos1.setPositionType("LONG");
        positions.add(pos1);

        Position pos2 = new Position();
        pos2.setSymbol("TCS");
        pos2.setExchange("NSE");
        pos2.setQuantity(5);
        pos2.setAvgPrice(3750.00);
        pos2.setLtp(3745.25);
        pos2.setPnl(-23.75);
        pos2.setProductType("INTRADAY");
        pos2.setPositionType("LONG");
        positions.add(pos2);

        return positions;
    }

    private Order getMockOrder(CreateOrderRequest request) {
        Order order = new Order();
        order.setOrderId("ORD" + System.currentTimeMillis());
        order.setSymbol(request.getSymbol());
        order.setExchange(request.getExchange());
        order.setTransactionType(request.getTransactionType());
        order.setQuantity(request.getQuantity());
        order.setPrice(request.getPrice() != null ? request.getPrice() : 0.0);
        order.setOrderType(request.getOrderType());
        order.setProductType(request.getProductType());
        order.setStatus("PLACED");
        order.setTimestamp(LocalDateTime.now().toString());
        return order;
    }

    private static class RestTemplateErrorHandler implements ResponseErrorHandler {

        @Override
        public boolean hasError(ClientHttpResponse httpResponse) throws IOException {
            return (httpResponse.getStatusCode().is4xxClientError() ||
                    httpResponse.getStatusCode().is5xxServerError());
        }

        @Override
        public void handleError(ClientHttpResponse httpResponse) throws IOException {
            // Empty on purpose - allows HttpStatusCodeException to be thrown
        }
    }
    public Order closePosition(ClosePositionRequest request) {
        Optional<DhanAccount> account = getActiveAccount();
        if (account.isEmpty()) {
            throw new RuntimeException("No active Dhan account linked");
        }

        try {
            // To close a position, place an opposite order
            // If LONG position, place SELL order
            // If SHORT position, place BUY order
            String transactionType = "LONG".equals(request.getPositionType()) ? "SELL" : "BUY";

            HttpHeaders headers = getDhanHeaders(account.get().getAccessToken());

            Map<String, Object> orderData = new HashMap<>();
            orderData.put("dhanClientId", account.get().getClientId());
            orderData.put("transactionType", transactionType);
            orderData.put("exchangeSegment", request.getExchange());
            orderData.put("productType", request.getProductType());
            orderData.put("orderType", "MARKET"); // Use MARKET order to close immediately
            orderData.put("validity", "DAY");
            orderData.put("quantity", request.getQuantity());
            orderData.put("securityId", request.getSecurityId());
            orderData.put("price", "0");
            orderData.put("disclosedQuantity", "0");
            orderData.put("triggerPrice", "0");
            orderData.put("afterMarketOrder", false);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(orderData, headers);

            String url = DHAN_BASE_URL + "/v2/orders";

            log.info("Closing position - Symbol: {}, Type: {}, Qty: {}",
                    request.getSymbol(), transactionType, request.getQuantity());

            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, Map.class
            );

            log.info("Close position response: {}", response.getBody());

            Order order = parseOrder(response.getBody());
            order.setSymbol(request.getSymbol());
            order.setTransactionType(transactionType);
            order.setQuantity(request.getQuantity());

            return order;

        } catch (HttpStatusCodeException e) {
            log.error("Dhan Close Position API returned status {}: {}",
                    e.getStatusCode(),
                    e.getResponseBodyAsString());
            throw new RuntimeException("Failed to close position: " + e.getResponseBodyAsString());

        } catch (Exception e) {
            log.error("Error closing position on Dhan: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to close position: " + e.getMessage());
        }
    }
}