package com.example.conductor.order.controller;

import com.example.conductor.order.dto.ApprovalDecisionRequest;
import com.example.conductor.order.dto.ApprovalRequest;
import com.example.conductor.order.dto.ApprovalResponse;
import com.example.conductor.order.service.OrderService;
import java.util.Optional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/approvals")
public class ApprovalController {

    private final OrderService orderService;

    public ApprovalController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ApprovalResponse request(@RequestBody ApprovalRequest request) {
        return orderService.requestApproval(request);
    }

    @GetMapping("/{orderNo}")
    public Optional<ApprovalResponse> get(@PathVariable String orderNo) {
        return orderService.getApproval(orderNo);
    }

    @PostMapping("/{orderNo}/approve")
    public ApprovalResponse approve(
        @PathVariable String orderNo,
        @RequestBody ApprovalDecisionRequest request
    ) {
        return orderService.approve(orderNo, request);
    }

    @PostMapping("/{orderNo}/reject")
    public ApprovalResponse reject(
        @PathVariable String orderNo,
        @RequestBody ApprovalDecisionRequest request
    ) {
        return orderService.reject(orderNo, request);
    }
}
