package com.swiftpay.gateway.payment.event;

import java.math.BigDecimal;

// Event emitted to Kafka (topic: payments.initiated) for the Ledger service to consume and settle.
public record PaymentInitiatedEvent(
        String transactionId,
        Long senderId,
        Long receiverId,
        BigDecimal amount,
        String currency
) {}
