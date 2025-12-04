package com.trading.dto;

import lombok.Data;

@Data
public class LinkAccountRequest {
    private String clientId;
    private String accessToken;
}