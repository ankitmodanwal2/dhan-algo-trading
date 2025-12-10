package com.trading.controller;

import com.trading.dto.*;
import com.trading.model.DhanAccount;
import com.trading.model.Order;
import com.trading.model.Position;
import com.trading.model.SecurityMaster;
import com.trading.service.DhanService;
import com.trading.service.SecurityMasterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dhan")
@Slf4j
public class DhanController {

    @Autowired
    private DhanService dhanService;

    @Autowired
    private SecurityMasterService securityMasterService;

    @PostMapping("/link-account")
    public ResponseEntity<ApiResponse<DhanAccount>> linkAccount(
            @RequestBody LinkAccountRequest request) {
        try {
            DhanAccount account = dhanService.linkAccount(
                    request.getClientId(),
                    request.getAccessToken()
            );
            return ResponseEntity.ok(
                    ApiResponse.success("Dhan account linked successfully", account)
            );
        } catch (Exception e) {
            log.error("Error linking account: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("Failed to link account: " + e.getMessage())
            );
        }
    }

    @GetMapping("/positions")
    public ResponseEntity<ApiResponse<List<Position>>> getPositions() {
        try {
            List<Position> positions = dhanService.getPositions();
            return ResponseEntity.ok(
                    ApiResponse.success("Positions fetched successfully", positions)
            );
        } catch (Exception e) {
            log.error("Error fetching positions: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("Failed to fetch positions: " + e.getMessage())
            );
        }
    }

    @PostMapping("/orders")
    public ResponseEntity<ApiResponse<Order>> createOrder(
            @RequestBody CreateOrderRequest request) {
        try {
            Order order = dhanService.createOrder(request);
            return ResponseEntity.ok(
                    ApiResponse.success("Order created successfully", order)
            );
        } catch (Exception e) {
            log.error("Error creating order: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("Failed to create order: " + e.getMessage())
            );
        }
    }

    @DeleteMapping("/orders/{orderId}")
    public ResponseEntity<ApiResponse<Order>> closeOrder(
            @PathVariable String orderId) {
        try {
            Order order = dhanService.closeOrder(orderId);
            return ResponseEntity.ok(
                    ApiResponse.success("Order closed successfully", order)
            );
        } catch (Exception e) {
            log.error("Error closing order: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("Failed to close order: " + e.getMessage())
            );
        }
    }

    @GetMapping("/account")
    public ResponseEntity<ApiResponse<DhanAccount>> getActiveAccount() {
        try {
            DhanAccount account = dhanService.getActiveAccount()
                    .orElseThrow(() -> new RuntimeException("No active account found"));
            return ResponseEntity.ok(
                    ApiResponse.success("Active account retrieved", account)
            );
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error(e.getMessage())
            );
        }
    }
    @PostMapping("/symbols/search")
    public ResponseEntity<ApiResponse<List<SecurityMaster>>> searchSymbols(
            @RequestBody SymbolSearchRequest request) {
        try {
            List<SecurityMaster> results = securityMasterService.searchSymbols(
                    request.getQuery(),
                    request.getExchange(),
                    request.getLimit()
            );
            return ResponseEntity.ok(
                    ApiResponse.success("Symbols found", results)
            );
        } catch (Exception e) {
            log.error("Error searching symbols: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("Failed to search symbols: " + e.getMessage())
            );
        }
    }

    @PostMapping("/positions/close")
    public ResponseEntity<ApiResponse<Order>> closePosition(
            @RequestBody ClosePositionRequest request) {
        try {
            Order order = dhanService.closePosition(request);
            return ResponseEntity.ok(
                    ApiResponse.success("Position closed successfully", order)
            );
        } catch (Exception e) {
            log.error("Error closing position: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("Failed to close position: " + e.getMessage())
            );
        }
    }
}