package com.trading.model;

import lombok.Data;

@Data
public class Order {
    private String orderId;
    private String symbol;
    private String exchange;
    private String transactionType; // BUY/SELL
    private int quantity;
    private double price;
    private String orderType; // MARKET/LIMIT
    private String productType; // INTRADAY/DELIVERY
    private String status;
    private String timestamp;
}