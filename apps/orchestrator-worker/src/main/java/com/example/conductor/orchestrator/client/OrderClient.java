package com.example.conductor.orchestrator.client;

import com.example.conductor.orchestrator.config.ServiceEndpoints;
import com.example.conductor.orchestrator.dto.OrderCreateRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class OrderClient {

    private final RestTemplate restTemplate;
    private final ServiceEndpoints endpoints;

    public OrderClient(RestTemplate restTemplate, ServiceEndpoints endpoints) {
        this.restTemplate = restTemplate;
        this.endpoints = endpoints;
    }

    public void createOrder(OrderCreateRequest request) {
        restTemplate.postForEntity(endpoints.getOrderBaseUrl() + "/orders", request, Object.class);
    }

    public void confirmOrder(String orderNo) {
        restTemplate.postForEntity(
            endpoints.getOrderBaseUrl() + "/orders/" + orderNo + "/confirm",
            null,
            Object.class
        );
    }

    public void cancelOrder(String orderNo) {
        restTemplate.postForEntity(
            endpoints.getOrderBaseUrl() + "/orders/" + orderNo + "/cancel",
            null,
            Object.class
        );
    }
}
