package com.example.conductor.orchestrator.worker;

import com.netflix.conductor.client.worker.Worker;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class PackItemsWorker implements Worker {

    private static final Logger log = LoggerFactory.getLogger(PackItemsWorker.class);

    @Override
    public String getTaskDefName() {
        return "pack_items";
    }

    @Override
    public TaskResult execute(Task task) {
        log.info("Packing items for order: {}", task.getInputData().get("orderNo"));

        TaskResult result = new TaskResult(task);

        try {
            String orderNo = (String) task.getInputData().get("orderNo");
            String warehouseId = (String) task.getInputData().get("warehouse");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) task.getInputData().get("items");

            // Simulate packing process
            int totalItems = items.size();
            int packagesNeeded = (totalItems + 2) / 3; // Up to 3 items per package

            log.info("Packed {} items into {} packages at warehouse {} for order {}",
                totalItems, packagesNeeded, warehouseId, orderNo);

            result.setStatus(TaskResult.Status.COMPLETED);
            result.setOutputData(Map.of(
                "packagesCount", packagesNeeded,
                "packedAt", warehouseId,
                "packingTime", System.currentTimeMillis(),
                "packingStatus", "COMPLETED"
            ));

        } catch (Exception e) {
            log.error("Failed to pack items", e);
            result.setStatus(TaskResult.Status.FAILED);
            result.setReasonForIncompletion("Packing failed: " + e.getMessage());
        }

        return result;
    }
}
