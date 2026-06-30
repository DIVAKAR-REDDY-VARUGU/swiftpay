package com.swiftpay.ledger.settlement.event;

// Emitted to payments.completed when settlement fails (e.g., insufficient funds).
public record PaymentFailedEvent(
        String transactionId,
        String reason
) {}
