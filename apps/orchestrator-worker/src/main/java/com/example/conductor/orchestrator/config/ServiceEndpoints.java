package com.example.conductor.orchestrator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "orchestrator")
public class ServiceEndpoints {

    private String orderBaseUrl;
    private String paymentBaseUrl;
    private String inventoryBaseUrl;

    public String getOrderBaseUrl() {
        return orderBaseUrl;
    }

    public void setOrderBaseUrl(String orderBaseUrl) {
        this.orderBaseUrl = orderBaseUrl;
    }

    public String getPaymentBaseUrl() {
        return paymentBaseUrl;
    }

    public void setPaymentBaseUrl(String paymentBaseUrl) {
        this.paymentBaseUrl = paymentBaseUrl;
    }

    public String getInventoryBaseUrl() {
        return inventoryBaseUrl;
    }

    public void setInventoryBaseUrl(String inventoryBaseUrl) {
        this.inventoryBaseUrl = inventoryBaseUrl;
    }
}
