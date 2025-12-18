package com.example.conductor.payment;

import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class PaymentApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentApplication.class, args);
    }

    @RestController
    static class PaymentHealthController {

        @GetMapping("/health")
        Map<String, String> health() {
            return Map.of("app", "payment-app", "status", "ok");
        }
    }
}
