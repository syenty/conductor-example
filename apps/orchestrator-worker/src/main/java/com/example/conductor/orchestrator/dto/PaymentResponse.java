package com.example.conductor.orchestrator.dto;

public record PaymentResponse(
    String orderNo,
    String status
) {
}
