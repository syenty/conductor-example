package com.example.conductor.orchestrator.dto;

public record OrderResponse(
    String orderNo,
    String orderStatus,
    String paymentStatus,
    String inventoryStatus
) {
}
