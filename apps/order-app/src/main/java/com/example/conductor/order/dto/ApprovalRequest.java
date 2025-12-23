package com.example.conductor.order.dto;

public record ApprovalRequest(
    String orderNo,
    String requestedBy,
    String comment
) {
}
