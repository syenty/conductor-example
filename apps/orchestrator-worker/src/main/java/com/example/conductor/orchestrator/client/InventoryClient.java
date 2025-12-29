package com.example.conductor.orchestrator.client;

import com.example.conductor.orchestrator.config.ServiceEndpoints;
import com.example.conductor.orchestrator.dto.InventoryReserveRequest;
import com.example.conductor.orchestrator.dto.InventoryReserveResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class InventoryClient {

    private final RestTemplate restTemplate;
    private final ServiceEndpoints endpoints;

    public InventoryClient(RestTemplate restTemplate, ServiceEndpoints endpoints) {
        this.restTemplate = restTemplate;
        this.endpoints = endpoints;
    }

    public InventoryReserveResponse checkAvailability(InventoryReserveRequest request) {
        ResponseEntity<InventoryReserveResponse> response = restTemplate.postForEntity(
            endpoints.getInventoryBaseUrl() + "/inventory/check-availability",
            request,
            InventoryReserveResponse.class
        );
        return response.getBody();
    }

    public InventoryReserveResponse reserve(InventoryReserveRequest request) {
        ResponseEntity<InventoryReserveResponse> response = restTemplate.postForEntity(
            endpoints.getInventoryBaseUrl() + "/inventory/reservations",
            request,
            InventoryReserveResponse.class
        );
        return response.getBody();
    }
}
