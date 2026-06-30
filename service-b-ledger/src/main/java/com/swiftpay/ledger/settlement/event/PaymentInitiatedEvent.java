package com.swiftpay.ledger.settlement.event;

import java.math.BigDecimal;

// Consumed from topic payments.initiated (produced by the gateway). Field names must match the JSON.
public record PaymentInitiatedEvent(
        String transactionId,
        Long senderId,
        Long receiverId,
        BigDecimal amount,
        String currency
) {}
