package com.swiftpay.analytics.dto;

import java.math.BigDecimal;

// GET /v1/analytics/top-senders — a sender ranked by how much they've successfully sent.
public record TopSenderDto(long senderId, BigDecimal totalSent, long transactionCount) {}
