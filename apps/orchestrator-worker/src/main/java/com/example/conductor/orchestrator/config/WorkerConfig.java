package com.example.conductor.orchestrator.config;

import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.worker.TaskRunnerConfigurer;
import com.netflix.conductor.client.worker.Worker;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WorkerConfig {

    @Bean
    public TaskClient taskClient(ConductorWorkerProperties properties) {
        TaskClient client = new TaskClient();
        client.setRootURI(properties.getConductorBaseUrl());
        return client;
    }

    @Bean
    public TaskRunnerConfigurer taskRunnerConfigurer(
        TaskClient taskClient,
        List<Worker> workers,
        ConductorWorkerProperties properties
    ) {
        TaskRunnerConfigurer configurer = new TaskRunnerConfigurer.Builder(taskClient, workers)
            .withWorkerId(properties.getWorkerId())
            .withThreadCount(properties.getThreadCount())
            .build();
        configurer.init();
        return configurer;
    }
}
