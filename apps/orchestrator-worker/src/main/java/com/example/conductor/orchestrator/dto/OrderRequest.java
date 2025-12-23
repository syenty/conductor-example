package com.example.conductor.orchestrator.dto;

import java.math.BigDecimal;
import java.util.List;

public record OrderRequest(
    String orderNo,
    BigDecimal totalAmount,
    String currency,
    String customerId,
    String paymentMethod,
    BigDecimal paymentFailRate,
    Integer paymentDelayMs,
    List<OrderItemRequest> items
) {
}
