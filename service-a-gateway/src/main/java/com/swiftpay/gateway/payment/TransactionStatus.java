package com.swiftpay.gateway.payment;

// Lifecycle of a payment: created PENDING by the gateway, settled to COMPLETED/FAILED by the ledger.
public enum TransactionStatus { PENDING, COMPLETED, FAILED }
