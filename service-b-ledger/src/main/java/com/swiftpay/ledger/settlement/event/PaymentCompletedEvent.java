package com.swiftpay.ledger.settlement.event;

import java.math.BigDecimal;

// Emitted to payments.completed after a successful settlement.
public record PaymentCompletedEvent(
        String transactionId,
        Long senderId,
        Long receiverId,
        BigDecimal amount
) {}
