package com.example.conductor.orchestrator.worker;

import com.example.conductor.orchestrator.client.PaymentClient;
import com.example.conductor.orchestrator.dto.PaymentResponse;
import com.netflix.conductor.client.worker.Worker;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ConfirmBankTransferWorker implements Worker {

    private final PaymentClient paymentClient;

    public ConfirmBankTransferWorker(PaymentClient paymentClient) {
        this.paymentClient = paymentClient;
    }

    @Override
    public String getTaskDefName() {
        return "confirm_bank_transfer";
    }

    @Override
    public TaskResult execute(Task task) {
        TaskResult result = new TaskResult(task);
        try {
            String orderNo = String.valueOf(task.getInputData().get("orderNo"));
            PaymentResponse response = paymentClient.confirmBankTransfer(orderNo);

            String status = response == null ? "UNKNOWN" : response.status();
            result.setStatus(TaskResult.Status.COMPLETED);
            result.setOutputData(Map.of("orderNo", orderNo, "status", status));
        } catch (Exception ex) {
            result.setStatus(TaskResult.Status.FAILED);
            result.setReasonForIncompletion(ex.getMessage());
        }
        return result;
    }
}
