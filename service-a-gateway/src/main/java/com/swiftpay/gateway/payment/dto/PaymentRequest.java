package com.swiftpay.gateway.payment.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

// Incoming POST /v1/payments body. transactionId is the optional idempotency key:
// supply your own (e.g. "Trans-3-1_1_100") to make retries safe; omit it and the server generates one.
public record PaymentRequest(
        @Size(max = 80, message = "transactionId too long (max 80 chars)") String transactionId,
        @NotNull Long senderId,
        @NotNull Long receiverId,
        @NotNull @DecimalMin(value = "0.01", message = "amount must be positive") BigDecimal amount,
        @NotBlank @Size(min = 3, max = 3, message = "currency must be a 3-letter code") String currency
) {}
