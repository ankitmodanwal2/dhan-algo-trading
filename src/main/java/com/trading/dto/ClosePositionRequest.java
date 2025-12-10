package com.trading.dto;

import lombok.Data;

@Data
public class ClosePositionRequest {
    private String symbol;
    private String securityId;
    private String exchange;
    private int quantity;
    private String productType;
    private String positionType; // LONG or SHORT
}