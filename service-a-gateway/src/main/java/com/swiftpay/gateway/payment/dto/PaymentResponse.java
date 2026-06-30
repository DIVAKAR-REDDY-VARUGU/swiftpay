package com.swiftpay.gateway.payment.dto;

import com.swiftpay.gateway.payment.TransactionStatus;
import java.util.UUID;

// What the client gets back from the gateway (immediately, before the ledger settles).
public record PaymentResponse(UUID transactionId, TransactionStatus status, String message) {}
