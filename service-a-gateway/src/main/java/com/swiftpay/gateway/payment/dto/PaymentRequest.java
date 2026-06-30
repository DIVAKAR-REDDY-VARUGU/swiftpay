package com.swiftpay.gateway.payment.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

// Incoming POST /v1/payments body. transactionId is optional: provide it to make retries idempotent.
public record PaymentRequest(
        UUID transactionId,
        @NotNull Long senderId,
        @NotNull Long receiverId,
        @NotNull @DecimalMin(value = "0.01", message = "amount must be positive") BigDecimal amount,
        @NotBlank @Size(min = 3, max = 3, message = "currency must be a 3-letter code") String currency
) {}
