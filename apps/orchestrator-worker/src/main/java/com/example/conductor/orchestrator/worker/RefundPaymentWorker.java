package com.example.conductor.orchestrator.worker;

import com.example.conductor.orchestrator.client.PaymentClient;
import com.example.conductor.orchestrator.dto.RefundRequest;
import com.netflix.conductor.client.worker.Worker;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class RefundPaymentWorker implements Worker {

    private final PaymentClient paymentClient;

    public RefundPaymentWorker(PaymentClient paymentClient) {
        this.paymentClient = paymentClient;
    }

    @Override
    public String getTaskDefName() {
        return "refund_payment";
    }

    @Override
    public TaskResult execute(Task task) {
        TaskResult result = new TaskResult(task);
        try {
            String orderNo = String.valueOf(task.getInputData().get("orderNo"));
            String reason = (String) task.getInputData().getOrDefault("reason", "workflow_failure");
            paymentClient.refund(orderNo, new RefundRequest(reason));
            result.setStatus(TaskResult.Status.COMPLETED);
            result.setOutputData(Map.of("orderNo", orderNo, "status", "REFUNDED"));
        } catch (Exception ex) {
            result.setStatus(TaskResult.Status.FAILED);
            result.setReasonForIncompletion(ex.getMessage());
        }
        return result;
    }
}
