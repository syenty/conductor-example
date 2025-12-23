package com.example.conductor.inventory.dto;

import java.util.List;

public record InventoryReserveRequest(
    String orderNo,
    List<ReservationItemRequest> items,
    Boolean forceOutOfStock,
    Integer partialFailIndex
) {
}
