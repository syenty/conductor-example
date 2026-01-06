package com.example.conductor.orchestrator.worker;

import com.netflix.conductor.client.worker.Worker;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Component
public class AssignCourierWorker implements Worker {

    private static final Logger log = LoggerFactory.getLogger(AssignCourierWorker.class);

    @Override
    public String getTaskDefName() {
        return "assign_courier";
    }

    @Override
    public TaskResult execute(Task task) {
        log.info("Assigning courier for order: {}", task.getInputData().get("orderNo"));

        TaskResult result = new TaskResult(task);

        try {
            String orderNo = (String) task.getInputData().get("orderNo");
            String deliveryType = (String) task.getInputData().getOrDefault("deliveryType", "STANDARD");
            String destination = (String) task.getInputData().getOrDefault("destination", "Seoul");

            // Simulate courier assignment
            String courierId;
            String courierName;
            int deliveryDays;

            if ("EXPRESS".equals(deliveryType)) {
                courierId = "COURIER-EXPRESS-" + (System.currentTimeMillis() % 100);
                courierName = "Express Delivery Service";
                deliveryDays = 1; // Next day delivery
            } else {
                courierId = "COURIER-STANDARD-" + (System.currentTimeMillis() % 100);
                courierName = "Standard Delivery Service";
                deliveryDays = 3; // 3 days delivery
            }

            String estimatedDeliveryDate = LocalDate.now()
                .plusDays(deliveryDays)
                .format(DateTimeFormatter.ISO_DATE);

            log.info("Assigned courier {} ({}) for order {} to {} (ETA: {})",
                courierId, courierName, orderNo, destination, estimatedDeliveryDate);

            result.setStatus(TaskResult.Status.COMPLETED);
            result.setOutputData(Map.of(
                "courierId", courierId,
                "courierName", courierName,
                "estimatedDeliveryDate", estimatedDeliveryDate,
                "deliveryDays", deliveryDays,
                "assignedAt", System.currentTimeMillis()
            ));

        } catch (Exception e) {
            log.error("Failed to assign courier", e);
            result.setStatus(TaskResult.Status.FAILED);
            result.setReasonForIncompletion("Courier assignment failed: " + e.getMessage());
        }

        return result;
    }
}
