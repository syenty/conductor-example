package com.example.conductor.orchestrator.worker;

import com.example.conductor.orchestrator.client.OrderClient;
import com.netflix.conductor.client.worker.Worker;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class CancelOrderWorker implements Worker {

    private final OrderClient orderClient;

    public CancelOrderWorker(OrderClient orderClient) {
        this.orderClient = orderClient;
    }

    @Override
    public String getTaskDefName() {
        return "cancel_order";
    }

    @Override
    public TaskResult execute(Task task) {
        TaskResult result = new TaskResult(task);
        try {
            String orderNo = String.valueOf(task.getInputData().get("orderNo"));
            orderClient.cancelOrder(orderNo);
            result.setStatus(TaskResult.Status.COMPLETED);
            result.setOutputData(Map.of("orderNo", orderNo, "status", "CANCELED"));
        } catch (Exception ex) {
            result.setStatus(TaskResult.Status.FAILED);
            result.setReasonForIncompletion(ex.getMessage());
        }
        return result;
    }
}
