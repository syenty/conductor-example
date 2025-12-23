package com.example.conductor.orchestrator.dto;

import java.math.BigDecimal;
import java.util.List;

public record OrderCreateRequest(
    String orderNo,
    BigDecimal totalAmount,
    String currency,
    String customerId,
    String paymentMethod,
    List<OrderItemRequest> items
) {
    public record OrderItemRequest(String productId, int quantity, BigDecimal unitPrice) {}
}
