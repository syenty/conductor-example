package com.example.conductor.orchestrator.dto;

import java.math.BigDecimal;

public record OrderItemRequest(
    String productId,
    int quantity,
    BigDecimal unitPrice
) {
}
