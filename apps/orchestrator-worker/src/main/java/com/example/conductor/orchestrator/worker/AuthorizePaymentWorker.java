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
    long startTime = System.currentTimeMillis();

    try {
      // Check task timeout setting
      long timeoutSeconds = task.getTaskDefinition()
          .map(def -> def.getTimeoutSeconds())
          .filter(t -> t > 0)
          .orElse(10L); // default 10 seconds

      PaymentAuthorizeRequest request = objectMapper.convertValue(task.getInputData(), PaymentAuthorizeRequest.class);
      PaymentResponse response = paymentClient.authorize(request);

      // Check if execution time exceeded timeout
      long executionTime = System.currentTimeMillis() - startTime;
      if (executionTime > timeoutSeconds * 1000L) {
        // Return COMPLETED with TIMEOUT status so workflow can proceed to decide_payment
        result.setStatus(TaskResult.Status.COMPLETED);
        result.setOutputData(Map.of(
            "status", "TIMEOUT",
            "executionTimeMs", executionTime,
            "timeoutSeconds", timeoutSeconds
        ));
        return result;
      }

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
