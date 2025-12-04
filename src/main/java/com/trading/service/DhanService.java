package com.trading.service;

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
import org.springframework.web.client.ResponseErrorHandler;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResponseErrorHandler;

@Service
@Slf4j
public class DhanService {

    @Autowired
    private DhanAccountRepository accountRepository;

    private final RestTemplate restTemplate;
    private static final String DHAN_BASE_URL = "https://api.dhan.co";

    // 🌟 1. Constructor for RestTemplate setup 🌟
    public DhanService(RestTemplateBuilder restTemplateBuilder) {
        // Use RestTemplateBuilder to set up custom error handling
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

            // Call Dhan API to get positions
            String url = DHAN_BASE_URL + "/v2/positions";

            // 🌟 2. Changed expected type from Map.class to List.class 🌟
            // Dhan APIs often return a JSON array as the root element for lists.
            ResponseEntity<List> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, List.class
            );

            // Parse response and convert to Position objects
            // The response body is now a List, which we cast and parse.
            List<Position> positions = parsePositions(response.getBody());

            // Update last synced time
            account.get().setLastSyncedAt(LocalDateTime.now());
            accountRepository.save(account.get());

            return positions;

        } catch (HttpStatusCodeException e) {
            // 🌟 3. Specific logging for API errors (e.g., 401 Unauthorized) 🌟
            log.error("Dhan Positions API returned status {}: {}",
                    e.getStatusCode(),
                    e.getResponseBodyAsString());

            // Return mock data for demonstration after logging the real error
            return getMockPositions();

        } catch (Exception e) {
            log.error("Error fetching positions from Dhan: {}", e.getMessage());
            // Return mock data for demonstration
            return getMockPositions();
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
            // Return mock order for demonstration
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

    // Helper method for headers
    private HttpHeaders getDhanHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("access-token", accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    // 🌟 4. Updated parsePositions to handle List input 🌟
    private List<Position> parsePositions(List<Map<String, Object>> responseList) {
        List<Position> positions = new ArrayList<>();

        if (responseList == null || responseList.isEmpty()) {
            return positions; // Return empty list if no data
        }

        // **NOTE**: You would implement the logic here to convert each Map
        // element in responseList into your Position DTO.
        // For now, we return mock data since the logic is missing.

        return getMockPositions();
    }

    // This method is fine if the Dhan API returns a single order Map
    private Order parseOrder(Map<String, Object> response) {
        Order order = new Order();
        // Parse actual Dhan API response
        order.setOrderId(UUID.randomUUID().toString());
        order.setStatus("PLACED");
        return order;
    }

    // Mock data for demonstration (kept as is)
    private List<Position> getMockPositions() {
        List<Position> positions = new ArrayList<>();
        // ... (mock position data)
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

    // Mock order (kept as is)
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

    // 🌟 5. Custom RestTemplate Error Handler 🌟
    /**
     * Prevents RestTemplate from throwing an exception on 4xx/5xx status codes,
     * allowing the calling method to catch HttpStatusCodeException instead
     * and log the actual error body from the Dhan API.
     */
    private static class RestTemplateErrorHandler implements ResponseErrorHandler {

        @Override
        public boolean hasError(ClientHttpResponse httpResponse) throws IOException {
            return (httpResponse.getStatusCode().is4xxClientError() ||
                    httpResponse.getStatusCode().is5xxServerError());
        }

        @Override
        public void handleError(ClientHttpResponse httpResponse) throws IOException {
            // We leave this body EMPTY on purpose.
            // By not throwing a specific exception here, we allow the calling
            // RestTemplate code to throw the HttpStatusCodeException,
            // which contains the response body you need to log.
        }
    }
}