package com.example.conductor.order.dto;

import java.math.BigDecimal;
import java.util.List;

public record CreateOrderRequest(
    String orderNo,
    BigDecimal totalAmount,
    String currency,
    String customerId,
    String paymentMethod,
    List<OrderItemRequest> items
) {
}
