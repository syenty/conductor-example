package com.example.conductor.order.dto;

import java.math.BigDecimal;

public record OrderItemResponse(
    Long id,
    String productId,
    int quantity,
    BigDecimal unitPrice,
    String status
) {
}
