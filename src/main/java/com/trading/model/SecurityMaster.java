package com.trading.model;

import lombok.Data;

@Data
public class SecurityMaster {
    private String securityId;
    private String tradingSymbol;
    private String name;
    private String exchangeSegment;
    private String instrumentType;
    private Double tickSize;
    private Integer lotSize;
}