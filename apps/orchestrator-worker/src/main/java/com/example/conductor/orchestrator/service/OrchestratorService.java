package com.example.conductor.orchestrator.service;

import com.example.conductor.orchestrator.config.ServiceEndpoints;
import com.example.conductor.orchestrator.dto.InventoryReserveRequest;
import com.example.conductor.orchestrator.dto.InventoryReserveResponse;
import com.example.conductor.orchestrator.dto.OrderCreateRequest;
import com.example.conductor.orchestrator.dto.OrderStatusUpdateRequest;
import com.example.conductor.orchestrator.dto.PaymentAuthorizeRequest;
import com.example.conductor.orchestrator.dto.PaymentResponse;
import com.example.conductor.orchestrator.dto.RefundRequest;
import com.example.conductor.orchestrator.dto.OrderItemRequest;
import com.example.conductor.orchestrator.dto.OrderRequest;
import com.example.conductor.orchestrator.dto.OrderResponse;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class OrchestratorService {

    private final RestTemplate restTemplate;
    private final ServiceEndpoints endpoints;

    public OrchestratorService(RestTemplate restTemplate, ServiceEndpoints endpoints) {
        this.restTemplate = restTemplate;
        this.endpoints = endpoints;
    }

    public OrderResponse processOrder(OrderRequest request) {
        String orderBase = endpoints.getOrderBaseUrl();
        String paymentBase = endpoints.getPaymentBaseUrl();
        String inventoryBase = endpoints.getInventoryBaseUrl();

        createOrder(orderBase, request);
        PaymentResponse payment = authorizePayment(paymentBase, request);
        InventoryReserveResponse inventory = reserveInventory(inventoryBase, request);

        if (!"RESERVED".equals(inventory.status())) {
            refundPayment(paymentBase, request.orderNo(), "inventory_failed");
            updateOrderStatus(orderBase, request.orderNo(), "CANCELED");
            return new OrderResponse(request.orderNo(), "CANCELED", payment.status(), inventory.status());
        }

        updateOrderStatus(orderBase, request.orderNo(), "CONFIRMED");
        return new OrderResponse(request.orderNo(), "CONFIRMED", payment.status(), inventory.status());
    }

    private void createOrder(String baseUrl, OrderRequest request) {
        List<OrderCreateRequest.OrderItemRequest> items = toOrderItems(request.items());
        OrderCreateRequest payload = new OrderCreateRequest(
            request.orderNo(),
            request.totalAmount(),
            request.currency(),
            request.customerId(),
            request.paymentMethod(),
            items
        );
        restTemplate.postForEntity(baseUrl + "/orders", payload, Object.class);
    }

    private PaymentResponse authorizePayment(String baseUrl, OrderRequest request) {
        PaymentAuthorizeRequest payload = new PaymentAuthorizeRequest(
            request.orderNo(),
            request.totalAmount(),
            request.currency(),
            request.paymentMethod(),
            request.paymentFailRate(),
            request.paymentDelayMs()
        );
        ResponseEntity<PaymentResponse> response = restTemplate.postForEntity(
            baseUrl + "/payments/authorize",
            payload,
            PaymentResponse.class
        );
        PaymentResponse body = response.getBody();
        if (body == null || !"AUTHORIZED".equals(body.status())) {
            updateOrderStatus(endpoints.getOrderBaseUrl(), request.orderNo(), "FAILED");
            throw new IllegalStateException("payment authorization failed");
        }
        return body;
    }

    private InventoryReserveResponse reserveInventory(String baseUrl, OrderRequest request) {
        List<InventoryReserveRequest.ReservationItem> items = toReservationItems(request.items());
        InventoryReserveRequest payload = new InventoryReserveRequest(
            request.orderNo(),
            items,
            false,
            null
        );
        ResponseEntity<InventoryReserveResponse> response = restTemplate.postForEntity(
            baseUrl + "/inventory/reservations",
            payload,
            InventoryReserveResponse.class
        );
        InventoryReserveResponse body = response.getBody();
        if (body == null) {
            throw new IllegalStateException("inventory response missing");
        }
        return body;
    }

    private void updateOrderStatus(String baseUrl, String orderNo, String status) {
        OrderStatusUpdateRequest payload = new OrderStatusUpdateRequest(status);
        restTemplate.postForEntity(baseUrl + "/orders/" + orderNo + "/status", payload, Object.class);
    }

    private void refundPayment(String baseUrl, String orderNo, String reason) {
        RefundRequest payload = new RefundRequest(reason);
        restTemplate.postForEntity(baseUrl + "/payments/" + orderNo + "/refund", payload, Object.class);
    }

    private List<OrderCreateRequest.OrderItemRequest> toOrderItems(List<OrderItemRequest> items) {
        if (items == null) {
            return List.of();
        }
        return items.stream()
            .map(item -> new OrderCreateRequest.OrderItemRequest(
                item.productId(),
                item.quantity(),
                item.unitPrice()
            ))
            .toList();
    }

    private List<InventoryReserveRequest.ReservationItem> toReservationItems(List<OrderItemRequest> items) {
        if (items == null) {
            return List.of();
        }
        return items.stream()
            .map(item -> new InventoryReserveRequest.ReservationItem(
                item.productId(),
                item.quantity(),
                null
            ))
            .toList();
    }
}
