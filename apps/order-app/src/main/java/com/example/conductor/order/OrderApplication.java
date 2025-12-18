package com.example.conductor.order;

import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class OrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderApplication.class, args);
    }

    @RestController
    static class OrderHealthController {

        @GetMapping("/health")
        Map<String, String> health() {
            return Map.of("app", "order-app", "status", "ok");
        }
    }
}
