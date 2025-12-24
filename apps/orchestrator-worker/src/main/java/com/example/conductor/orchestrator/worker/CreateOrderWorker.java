package com.example.conductor.orchestrator.worker;

import com.example.conductor.orchestrator.client.OrderClient;
import com.example.conductor.orchestrator.dto.OrderCreateRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.client.worker.Worker;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class CreateOrderWorker implements Worker {

    private final OrderClient orderClient;
    private final ObjectMapper objectMapper;

    public CreateOrderWorker(OrderClient orderClient, ObjectMapper objectMapper) {
        this.orderClient = orderClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getTaskDefName() {
        return "create_order";
    }

    @Override
    public TaskResult execute(Task task) {
        TaskResult result = new TaskResult(task);
        try {
            OrderCreateRequest request = objectMapper.convertValue(task.getInputData(), OrderCreateRequest.class);
            orderClient.createOrder(request);
            result.setStatus(TaskResult.Status.COMPLETED);
            result.setOutputData(Map.of("orderNo", request.orderNo()));
        } catch (Exception ex) {
            result.setStatus(TaskResult.Status.FAILED);
            result.setReasonForIncompletion(ex.getMessage());
        }
        return result;
    }
}
