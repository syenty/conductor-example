package com.example.conductor.inventory.service;

import com.example.conductor.inventory.domain.InventoryEvent;
import com.example.conductor.inventory.domain.InventoryItem;
import com.example.conductor.inventory.domain.InventoryReservation;
import com.example.conductor.inventory.dto.InventoryItemRequest;
import com.example.conductor.inventory.dto.InventoryItemResponse;
import com.example.conductor.inventory.dto.InventoryReserveRequest;
import com.example.conductor.inventory.dto.InventoryReserveResponse;
import com.example.conductor.inventory.dto.ReleaseResponse;
import com.example.conductor.inventory.dto.ReservationItemRequest;
import com.example.conductor.inventory.dto.ReservationItemResponse;
import com.example.conductor.inventory.repository.InventoryEventRepository;
import com.example.conductor.inventory.repository.InventoryRepository;
import com.example.conductor.inventory.repository.InventoryReservationRepository;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryReservationRepository reservationRepository;
    private final InventoryEventRepository eventRepository;

    public InventoryService(
        InventoryRepository inventoryRepository,
        InventoryReservationRepository reservationRepository,
        InventoryEventRepository eventRepository
    ) {
        this.inventoryRepository = inventoryRepository;
        this.reservationRepository = reservationRepository;
        this.eventRepository = eventRepository;
    }

    @Transactional
    public InventoryItemResponse upsertItem(InventoryItemRequest request) {
        InventoryItem item = inventoryRepository.findByProductId(request.productId())
            .orElseGet(InventoryItem::new);
        item.setProductId(request.productId());
        item.setStock(request.stock());
        if (item.getReserved() < 0) {
            item.setReserved(0);
        }
        InventoryItem saved = inventoryRepository.save(item);
        return toItemResponse(saved);
    }

    public Optional<InventoryItemResponse> getItem(String productId) {
        return inventoryRepository.findByProductId(productId).map(this::toItemResponse);
    }

    @Transactional
    public InventoryReserveResponse reserve(InventoryReserveRequest request) {
        List<ReservationItemResponse> responses = new ArrayList<>();
        boolean anyFailed = false;
        List<ReservationItemRequest> items = request.items() == null ? List.of() : request.items();
        boolean forceOutOfStock = request.forceOutOfStock() != null && request.forceOutOfStock();
        Integer partialFailIndex = request.partialFailIndex();

        for (int i = 0; i < items.size(); i++) {
            ReservationItemRequest itemRequest = items.get(i);
            boolean shouldFail = forceOutOfStock || (partialFailIndex != null && partialFailIndex == i);
            ReservationResult result = reserveItem(request.orderNo(), itemRequest, shouldFail, partialFailIndex);
            responses.add(new ReservationItemResponse(
                itemRequest.productId(),
                itemRequest.quantity(),
                result.status
            ));
            if (!"RESERVED".equals(result.status)) {
                anyFailed = true;
            }
        }

        String status = anyFailed ? "FAILED" : "RESERVED";
        return new InventoryReserveResponse(request.orderNo(), status, responses);
    }

    @Transactional
    public ReleaseResponse release(String orderNo) {
        List<InventoryReservation> reservations = reservationRepository.findByOrderNo(orderNo);
        int released = 0;
        for (InventoryReservation reservation : reservations) {
            if (!"RESERVED".equals(reservation.getStatus())) {
                continue;
            }
            InventoryItem item = inventoryRepository.findByProductId(reservation.getProductId())
                .orElse(null);
            if (item != null) {
                item.setReserved(Math.max(0, item.getReserved() - reservation.getQuantity()));
                inventoryRepository.save(item);
            }
            reservation.setStatus("RELEASED");
            reservationRepository.save(reservation);
            recordEvent(reservation, "RELEASED", null);
            released++;
        }
        return new ReleaseResponse(orderNo, released);
    }

    private ReservationResult reserveItem(
        String orderNo,
        ReservationItemRequest itemRequest,
        boolean shouldFail,
        Integer partialFailIndex
    ) {
        InventoryReservation reservation = new InventoryReservation();
        reservation.setOrderNo(orderNo);
        reservation.setOrderItemId(itemRequest.orderItemId());
        reservation.setProductId(itemRequest.productId());
        reservation.setQuantity(itemRequest.quantity());
        reservation.setForceOutOfStock(shouldFail);
        reservation.setPartialFailIndex(partialFailIndex);

        if (shouldFail) {
            reservation.setStatus("FAILED");
            InventoryReservation saved = reservationRepository.save(reservation);
            recordEvent(saved, "FAILED", "forced");
            return new ReservationResult("FAILED");
        }

        InventoryItem item = inventoryRepository.findByProductId(itemRequest.productId())
            .orElseGet(() -> {
                InventoryItem created = new InventoryItem();
                created.setProductId(itemRequest.productId());
                created.setStock(0);
                created.setReserved(0);
                return created;
            });

        int available = item.getStock() - item.getReserved();
        if (available < itemRequest.quantity()) {
            reservation.setStatus("FAILED");
            InventoryReservation saved = reservationRepository.save(reservation);
            recordEvent(saved, "FAILED", "out_of_stock");
            return new ReservationResult("FAILED");
        }

        item.setReserved(item.getReserved() + itemRequest.quantity());
        inventoryRepository.save(item);

        reservation.setStatus("RESERVED");
        InventoryReservation saved = reservationRepository.save(reservation);
        recordEvent(saved, "RESERVED", null);
        return new ReservationResult("RESERVED");
    }

    private InventoryItemResponse toItemResponse(InventoryItem item) {
        return new InventoryItemResponse(
            item.getProductId(),
            item.getStock(),
            item.getReserved(),
            item.getCreatedAt(),
            item.getUpdatedAt()
        );
    }

    private void recordEvent(InventoryReservation reservation, String type, String detail) {
        InventoryEvent event = new InventoryEvent();
        event.setReservation(reservation);
        event.setEventType(type);
        event.setDetail(toJsonDetail(detail));
        eventRepository.save(event);
    }

    private String toJsonDetail(String detail) {
        if (detail == null) {
            return null;
        }
        return "{\"message\":\"" + escapeJson(detail) + "\"}";
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static class ReservationResult {
        private final String status;

        private ReservationResult(String status) {
            this.status = status;
        }
    }
}
