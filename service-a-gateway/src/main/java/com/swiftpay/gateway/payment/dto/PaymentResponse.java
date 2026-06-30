package com.swiftpay.gateway.payment.dto;

import com.swiftpay.gateway.entity.TransactionStatus;

// What the client gets back from the gateway (immediately, before the ledger settles).
public record PaymentResponse(String transactionId, TransactionStatus status, String message) {}
