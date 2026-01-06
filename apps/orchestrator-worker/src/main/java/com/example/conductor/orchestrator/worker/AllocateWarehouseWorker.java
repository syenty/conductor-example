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
public class AllocateWarehouseWorker implements Worker {

    private static final Logger log = LoggerFactory.getLogger(AllocateWarehouseWorker.class);

    @Override
    public String getTaskDefName() {
        return "allocate_warehouse";
    }

    @Override
    public TaskResult execute(Task task) {
        log.info("Allocating warehouse for order: {}", task.getInputData().get("orderNo"));

        TaskResult result = new TaskResult(task);

        try {
            String orderNo = (String) task.getInputData().get("orderNo");
            String deliveryType = (String) task.getInputData().getOrDefault("deliveryType", "STANDARD");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) task.getInputData().get("items");

            // Simulate warehouse allocation logic
            String warehouseId;
            if ("EXPRESS".equals(deliveryType)) {
                warehouseId = "WH-EXPRESS-01"; // Express warehouse near city center
            } else {
                warehouseId = "WH-STANDARD-01"; // Standard warehouse
            }

            int totalQuantity = items.stream()
                .mapToInt(item -> ((Number) item.get("quantity")).intValue())
                .sum();

            log.info("Allocated warehouse {} for order {} (deliveryType: {}, totalQty: {})",
                warehouseId, orderNo, deliveryType, totalQuantity);

            result.setStatus(TaskResult.Status.COMPLETED);
            result.setOutputData(Map.of(
                "warehouseId", warehouseId,
                "warehouseLocation", deliveryType.equals("EXPRESS") ? "Seoul Central" : "Gyeonggi Province",
                "allocationTime", System.currentTimeMillis()
            ));

        } catch (Exception e) {
            log.error("Failed to allocate warehouse", e);
            result.setStatus(TaskResult.Status.FAILED);
            result.setReasonForIncompletion("Warehouse allocation failed: " + e.getMessage());
        }

        return result;
    }
}
