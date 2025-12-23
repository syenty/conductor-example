package com.example.conductor.inventory.dto;

public record InventoryItemRequest(
    String productId,
    int stock
) {
}
