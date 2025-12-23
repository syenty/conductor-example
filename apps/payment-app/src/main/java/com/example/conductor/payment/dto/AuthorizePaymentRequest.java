package com.example.conductor.payment.dto;

import java.math.BigDecimal;

public record AuthorizePaymentRequest(
    String orderNo,
    BigDecimal amount,
    String currency,
    String method,
    BigDecimal failRate,
    Integer delayMs
) {
}
