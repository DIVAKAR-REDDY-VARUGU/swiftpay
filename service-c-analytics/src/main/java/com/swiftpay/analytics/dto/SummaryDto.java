package com.swiftpay.analytics.dto;

import java.math.BigDecimal;

// GET /v1/analytics/summary — overall counts + total settled volume.
public record SummaryDto(long completedCount, long failedCount, BigDecimal totalVolume) {}
