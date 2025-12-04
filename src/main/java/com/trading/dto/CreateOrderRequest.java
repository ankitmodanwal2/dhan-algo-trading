package com.trading.dto;

import lombok.Data;

@Data
public class CreateOrderRequest {
    private String symbol;
    private String exchange;
    private String transactionType; // BUY/SELL
    private int quantity;
    private Double price; // Optional for MARKET orders
    private String orderType; // MARKET/LIMIT
    private String productType; // INTRADAY/DELIVERY
}
