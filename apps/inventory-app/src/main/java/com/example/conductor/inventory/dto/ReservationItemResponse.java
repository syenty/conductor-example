package com.example.conductor.inventory.dto;

public record ReservationItemResponse(
    String productId,
    int quantity,
    String status
) {
}
