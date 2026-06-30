package com.swiftpay.ledger.event;

import java.math.BigDecimal;
import java.util.UUID;

// Consumed from topic payments.initiated (produced by the gateway). Field names must match the JSON.
public record PaymentInitiatedEvent(
        UUID transactionId,
        Long senderId,
        Long receiverId,
        BigDecimal amount,
        String currency
) {}
