package com.example.conductor.inventory.dto;

import java.util.List;

public record InventoryReserveResponse(
    String orderNo,
    String status,
    List<ReservationItemResponse> items
) {
}
