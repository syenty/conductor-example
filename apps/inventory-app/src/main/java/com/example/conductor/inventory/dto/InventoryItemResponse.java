package com.example.conductor.inventory.dto;

import java.time.Instant;

public record InventoryItemResponse(
    String productId,
    int stock,
    int reserved,
    Instant createdAt,
    Instant updatedAt
) {
}
