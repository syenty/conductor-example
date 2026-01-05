package com.example.conductor.orchestrator.config;

import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.client.automator.TaskRunnerConfigurer;
import com.netflix.conductor.client.worker.Worker;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
  public WorkflowClient workflowClient(ConductorWorkerProperties properties) {
    WorkflowClient client = new WorkflowClient();
    client.setRootURI(properties.getConductorBaseUrl());
    return client;
  }

  @Bean
  public TaskRunnerConfigurer taskRunnerConfigurer(
      TaskClient taskClient,
      List<Worker> workers,
      ConductorWorkerProperties properties) {
    System.out.println("Worker count: " + (workers != null ? workers.size() : 0));
    System.out.println("Thread count: " + properties.getThreadCount());
    System.out.println("Worker ID: " + properties.getWorkerId());

    int threadCount = properties.getThreadCount() > 0 ? properties.getThreadCount() : 1;
    String workerPrefix = properties.getWorkerId() != null ? properties.getWorkerId() : "worker";

    // Build taskThreadCount map: assign thread count per task type
    Map<String, Integer> taskThreadCount = new HashMap<>();
    if (workers != null) {
      for (Worker worker : workers) {
        taskThreadCount.put(worker.getTaskDefName(), threadCount);
      }
    }

    TaskRunnerConfigurer configurer = new TaskRunnerConfigurer.Builder(taskClient, workers)
        .withWorkerNamePrefix(workerPrefix)
        .withTaskThreadCount(taskThreadCount)
        .build();
    configurer.init();
    return configurer;
  }
}
