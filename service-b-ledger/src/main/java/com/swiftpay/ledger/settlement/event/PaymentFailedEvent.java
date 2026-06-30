package com.swiftpay.ledger.settlement.event;

import java.util.UUID;

// Emitted to payments.completed when settlement fails (e.g., insufficient funds).
public record PaymentFailedEvent(
        UUID transactionId,
        String reason
) {}
