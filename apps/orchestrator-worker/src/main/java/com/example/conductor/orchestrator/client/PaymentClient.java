package com.example.conductor.orchestrator.client;

import com.example.conductor.orchestrator.config.ServiceEndpoints;
import com.example.conductor.orchestrator.dto.PaymentAuthorizeRequest;
import com.example.conductor.orchestrator.dto.PaymentResponse;
import com.example.conductor.orchestrator.dto.RefundRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class PaymentClient {

    private final RestTemplate restTemplate;
    private final ServiceEndpoints endpoints;

    public PaymentClient(RestTemplate restTemplate, ServiceEndpoints endpoints) {
        this.restTemplate = restTemplate;
        this.endpoints = endpoints;
    }

    public PaymentResponse authorize(PaymentAuthorizeRequest request) {
        ResponseEntity<PaymentResponse> response = restTemplate.postForEntity(
            endpoints.getPaymentBaseUrl() + "/payments/authorize",
            request,
            PaymentResponse.class
        );
        return response.getBody();
    }

    public void refund(String orderNo, RefundRequest request) {
        restTemplate.postForEntity(
            endpoints.getPaymentBaseUrl() + "/payments/" + orderNo + "/refund",
            request,
            Object.class
        );
    }

    public PaymentResponse confirmBankTransfer(String orderNo) {
        ResponseEntity<PaymentResponse> response = restTemplate.postForEntity(
            endpoints.getPaymentBaseUrl() + "/payments/" + orderNo + "/bank-transfer/confirm",
            null,
            PaymentResponse.class
        );
        return response.getBody();
    }
}
