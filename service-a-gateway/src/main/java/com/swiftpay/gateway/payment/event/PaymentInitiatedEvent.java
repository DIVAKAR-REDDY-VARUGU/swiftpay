package com.swiftpay.gateway.payment.event;

import java.math.BigDecimal;
import java.util.UUID;

// Event emitted to Kafka (topic: payments.initiated) for the Ledger service to consume and settle.
public record PaymentInitiatedEvent(
        UUID transactionId,
        Long senderId,
        Long receiverId,
        BigDecimal amount,
        String currency
) {}
