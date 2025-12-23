package com.example.conductor.order.controller;

import com.example.conductor.order.dto.CreateOrderRequest;
import com.example.conductor.order.dto.OrderResponse;
import com.example.conductor.order.dto.StatusUpdateRequest;
import com.example.conductor.order.service.OrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public OrderResponse create(@RequestBody CreateOrderRequest request) {
        return orderService.createOrder(request);
    }

    @GetMapping("/{orderNo}")
    public OrderResponse get(@PathVariable String orderNo) {
        return orderService.getOrder(orderNo);
    }

    @PostMapping("/{orderNo}/status")
    public OrderResponse updateStatus(@PathVariable String orderNo, @RequestBody StatusUpdateRequest request) {
        return orderService.updateStatus(orderNo, request.status());
    }

    @PostMapping("/{orderNo}/confirm")
    public OrderResponse confirm(@PathVariable String orderNo) {
        return orderService.confirmOrder(orderNo);
    }

    @PostMapping("/{orderNo}/cancel")
    public OrderResponse cancel(@PathVariable String orderNo) {
        return orderService.cancelOrder(orderNo);
    }
}
