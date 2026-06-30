package com.swiftpay.ledger.settlement.event;

import java.math.BigDecimal;
import java.util.UUID;

// Emitted to payments.completed after a successful settlement.
public record PaymentCompletedEvent(
        UUID transactionId,
        Long senderId,
        Long receiverId,
        BigDecimal amount
) {}
