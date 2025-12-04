package com.trading.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
public class DhanAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String clientId;

    // 🌟 FIX: Add the @Column annotation to specify a larger length 🌟
    @Column(length = 1024)
    private String accessToken;

    private boolean isActive;
    private LocalDateTime linkedAt;
    private LocalDateTime lastSyncedAt;
}