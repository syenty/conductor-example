package com.example.conductor.orchestrator;

import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class OrchestratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrchestratorApplication.class, args);
    }

    @RestController
    static class OrchestratorHealthController {

        @GetMapping("/health")
        Map<String, String> health() {
            return Map.of("app", "orchestrator-worker", "status", "ok");
        }
    }
}
