package com.example.conductor.order.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
    String orderNo,
    String status,
    BigDecimal totalAmount,
    String currency,
    String customerId,
    String paymentMethod,
    Instant createdAt,
    Instant updatedAt,
    List<OrderItemResponse> items
) {
}
