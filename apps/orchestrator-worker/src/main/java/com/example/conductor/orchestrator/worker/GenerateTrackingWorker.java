package com.example.conductor.orchestrator.worker;

import com.netflix.conductor.client.worker.Worker;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class GenerateTrackingWorker implements Worker {

    private static final Logger log = LoggerFactory.getLogger(GenerateTrackingWorker.class);

    @Override
    public String getTaskDefName() {
        return "generate_tracking";
    }

    @Override
    public TaskResult execute(Task task) {
        log.info("Generating tracking number for order: {}", task.getInputData().get("orderNo"));

        TaskResult result = new TaskResult(task);

        try {
            String orderNo = (String) task.getInputData().get("orderNo");
            String courierId = (String) task.getInputData().get("courierId");
            String warehouseId = (String) task.getInputData().get("warehouseId");

            // Generate tracking number
            String trackingNumber = "TRK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            String trackingUrl = "https://tracking.example.com/track/" + trackingNumber;

            log.info("Generated tracking number {} for order {} (courier: {}, warehouse: {})",
                trackingNumber, orderNo, courierId, warehouseId);

            result.setStatus(TaskResult.Status.COMPLETED);
            result.setOutputData(Map.of(
                "trackingNumber", trackingNumber,
                "trackingUrl", trackingUrl,
                "generatedAt", System.currentTimeMillis(),
                "trackingStatus", "READY_TO_SHIP"
            ));

        } catch (Exception e) {
            log.error("Failed to generate tracking number", e);
            result.setStatus(TaskResult.Status.FAILED);
            result.setReasonForIncompletion("Tracking generation failed: " + e.getMessage());
        }

        return result;
    }
}
