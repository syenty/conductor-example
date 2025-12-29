package com.example.conductor.orchestrator.worker;

import com.example.conductor.orchestrator.client.InventoryClient;
import com.example.conductor.orchestrator.dto.InventoryReserveRequest;
import com.example.conductor.orchestrator.dto.InventoryReserveResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.client.worker.Worker;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class CheckInventoryWorker implements Worker {

    private final InventoryClient inventoryClient;
    private final ObjectMapper objectMapper;

    public CheckInventoryWorker(InventoryClient inventoryClient, ObjectMapper objectMapper) {
        this.inventoryClient = inventoryClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getTaskDefName() {
        return "check_inventory";
    }

    @Override
    public TaskResult execute(Task task) {
        TaskResult result = new TaskResult(task);
        try {
            InventoryReserveRequest request = objectMapper.convertValue(
                task.getInputData(),
                InventoryReserveRequest.class
            );
            InventoryReserveResponse response = inventoryClient.checkAvailability(request);
            String status = response == null ? "UNKNOWN" : response.status();
            result.setStatus(TaskResult.Status.COMPLETED);
            result.setOutputData(Map.of("status", status));
        } catch (Exception ex) {
            result.setStatus(TaskResult.Status.FAILED);
            result.setReasonForIncompletion(ex.getMessage());
        }
        return result;
    }
}
