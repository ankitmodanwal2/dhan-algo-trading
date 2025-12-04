package com.trading.model;

import lombok.Data;

@Data
public class Position {
    private String symbol;
    private String exchange;
    private int quantity;
    private double avgPrice;
    private double ltp;
    private double pnl;
    private String productType;
    private String positionType; // LONG/SHORT
}
