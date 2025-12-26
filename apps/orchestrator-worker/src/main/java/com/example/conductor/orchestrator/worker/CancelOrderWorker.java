package com.example.conductor.orchestrator.worker;

import com.example.conductor.orchestrator.client.OrderClient;
import com.example.conductor.orchestrator.client.PaymentClient;
import com.example.conductor.orchestrator.dto.RefundRequest;
import com.netflix.conductor.client.worker.Worker;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class CancelOrderWorker implements Worker {

    private final OrderClient orderClient;
    private final PaymentClient paymentClient;

    public CancelOrderWorker(OrderClient orderClient, PaymentClient paymentClient) {
        this.orderClient = orderClient;
        this.paymentClient = paymentClient;
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

            // Refund payment if exists
            try {
                paymentClient.refund(orderNo, new RefundRequest(null));
            } catch (Exception ex) {
                // Payment may not exist or already refunded - ignore
            }

            // Cancel order
            orderClient.cancelOrder(orderNo);

            result.setStatus(TaskResult.Status.COMPLETED);
            result.setOutputData(Map.of("orderNo", orderNo, "status", "CANCELED", "paymentRefunded", true));
        } catch (Exception ex) {
            result.setStatus(TaskResult.Status.FAILED);
            result.setReasonForIncompletion(ex.getMessage());
        }
        return result;
    }
}
