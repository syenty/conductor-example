package com.example.conductor.orchestrator.dto;

import java.util.List;

public record InventoryReserveRequest(
    String orderNo,
    List<ReservationItem> items,
    Boolean forceOutOfStock,
    Integer partialFailIndex
) {
    public record ReservationItem(String productId, int quantity, Long orderItemId) {}
}
