package com.swiftpay.ledger.settlement.event;

import java.math.BigDecimal;

// The settlement result, published to payments.completed. status is "COMPLETED" or "FAILED";
// reason is only filled when it failed. one event type instead of two keeps the analytics consumer simple.
public record PaymentOutcomeEvent(
        String transactionId,
        Long senderId,
        Long receiverId,
        BigDecimal amount,
        String status,
        String reason
) {}
