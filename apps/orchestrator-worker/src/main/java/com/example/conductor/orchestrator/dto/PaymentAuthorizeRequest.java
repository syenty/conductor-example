package com.example.conductor.orchestrator.dto;

import java.math.BigDecimal;

public record PaymentAuthorizeRequest(
    String orderNo,
    BigDecimal amount,
    String currency,
    String method,
    BigDecimal failRate,
    Integer delayMs
) {
}
