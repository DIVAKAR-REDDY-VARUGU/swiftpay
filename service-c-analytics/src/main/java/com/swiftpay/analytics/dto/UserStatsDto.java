package com.swiftpay.analytics.dto;

import java.math.BigDecimal;

// GET /v1/analytics/users/{id} — what one user has sent and received (completed only).
public record UserStatsDto(
        long userId,
        long sentCount,
        BigDecimal sentVolume,
        long receivedCount,
        BigDecimal receivedVolume
) {}
