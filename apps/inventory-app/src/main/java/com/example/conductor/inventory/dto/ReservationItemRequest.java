package com.example.conductor.inventory.dto;

public record ReservationItemRequest(
    String productId,
    int quantity,
    Long orderItemId
) {
}
