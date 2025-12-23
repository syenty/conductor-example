package com.example.conductor.order.dto;

import java.time.Instant;

public record ApprovalResponse(
    String orderNo,
    String status,
    String requestedBy,
    String comment,
    Instant createdAt,
    Instant updatedAt
) {
}
