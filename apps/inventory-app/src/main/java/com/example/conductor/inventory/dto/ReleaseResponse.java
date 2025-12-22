package com.example.conductor.inventory.dto;

public record ReleaseResponse(
    String orderNo,
    int releasedCount
) {
}
