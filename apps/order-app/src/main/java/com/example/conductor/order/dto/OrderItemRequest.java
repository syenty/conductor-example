package com.example.conductor.order.dto;

import java.math.BigDecimal;

public record OrderItemRequest(
    String productId,
    int quantity,
    BigDecimal unitPrice
) {
}
