package com.example.conductor.inventory.controller;

import com.example.conductor.inventory.dto.InventoryItemRequest;
import com.example.conductor.inventory.dto.InventoryItemResponse;
import com.example.conductor.inventory.dto.InventoryReserveRequest;
import com.example.conductor.inventory.dto.InventoryReserveResponse;
import com.example.conductor.inventory.dto.ReleaseResponse;
import com.example.conductor.inventory.service.InventoryService;
import java.util.Optional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PostMapping("/items")
    public InventoryItemResponse upsertItem(@RequestBody InventoryItemRequest request) {
        return inventoryService.upsertItem(request);
    }

    @GetMapping("/items/{productId}")
    public Optional<InventoryItemResponse> getItem(@PathVariable String productId) {
        return inventoryService.getItem(productId);
    }

    @PostMapping("/check-availability")
    public InventoryReserveResponse checkAvailability(@RequestBody InventoryReserveRequest request) {
        return inventoryService.checkAvailability(request);
    }

    @PostMapping("/reservations")
    public InventoryReserveResponse reserve(@RequestBody InventoryReserveRequest request) {
        return inventoryService.reserve(request);
    }

    @PostMapping("/reservations/{orderNo}/release")
    public ReleaseResponse release(@PathVariable String orderNo) {
        return inventoryService.release(orderNo);
    }
}
