package com.trading.dto;

import lombok.Data;

@Data
public class SymbolSearchRequest {
    private String query;
    private String exchange;
    private Integer limit = 10;
}