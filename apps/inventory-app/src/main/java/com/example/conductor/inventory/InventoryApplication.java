package com.example.conductor.inventory;

import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class InventoryApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryApplication.class, args);
    }

    @RestController
    static class InventoryHealthController {

        @GetMapping("/health")
        Map<String, String> health() {
            return Map.of("app", "inventory-app", "status", "ok");
        }
    }
}
