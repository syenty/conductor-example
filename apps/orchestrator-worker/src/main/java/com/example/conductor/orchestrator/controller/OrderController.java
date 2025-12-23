package com.example.conductor.orchestrator.controller;

import com.example.conductor.orchestrator.dto.OrderRequest;
import com.example.conductor.orchestrator.dto.OrderResponse;
import com.example.conductor.orchestrator.service.OrchestratorService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrchestratorService orchestratorService;

    public OrderController(OrchestratorService orchestratorService) {
        this.orchestratorService = orchestratorService;
    }

    @PostMapping
    public OrderResponse process(@RequestBody OrderRequest request) {
        return orchestratorService.processOrder(request);
    }
}
