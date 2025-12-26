package com.example.conductor.orchestrator.worker;

import com.example.conductor.orchestrator.client.PaymentClient;
import com.example.conductor.orchestrator.dto.PaymentAuthorizeRequest;
import com.example.conductor.orchestrator.dto.PaymentResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.client.worker.Worker;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AuthorizePaymentWorker implements Worker {

  private final PaymentClient paymentClient;
  private final ObjectMapper objectMapper;

  public AuthorizePaymentWorker(PaymentClient paymentClient, ObjectMapper objectMapper) {
    this.paymentClient = paymentClient;
    this.objectMapper = objectMapper;
  }

  @Override
  public String getTaskDefName() {
    return "authorize_payment";
  }

  @Override
  public TaskResult execute(Task task) {
    TaskResult result = new TaskResult(task);
    try {
      PaymentAuthorizeRequest request = objectMapper.convertValue(task.getInputData(), PaymentAuthorizeRequest.class);
      PaymentResponse response = paymentClient.authorize(request);
      String status = response == null ? "UNKNOWN" : response.status();

      if ("FAILED".equals(status)) {
        result.setStatus(TaskResult.Status.FAILED);
        result.setReasonForIncompletion("Payment authorization failed - will retry");
      } else {
        result.setStatus(TaskResult.Status.COMPLETED);
        result.setOutputData(Map.of("status", status));
      }
    } catch (Exception ex) {
      result.setStatus(TaskResult.Status.FAILED);
      result.setReasonForIncompletion(ex.getMessage());
    }
    return result;
  }
}
