package com.swiftpay.analytics.event;

import java.math.BigDecimal;

// Consumed from payments.completed (produced by the ledger). Field names must match the JSON.
// status is "COMPLETED" or "FAILED"; reason is only set on failures.
public record PaymentOutcomeEvent(
        String transactionId,
        Long senderId,
        Long receiverId,
        BigDecimal amount,
        String status,
        String reason
) {}
