package com.example.conductor.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
    String orderNo,
    String status,
    BigDecimal amount,
    String currency,
    String method,
    BigDecimal failRate,
    Integer delayMs,
    int attempts,
    Instant createdAt,
    Instant updatedAt
) {
}
