package com.example.conductor.orchestrator.controller;

import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.WorkflowSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/deposit")
public class DepositController {

    private static final Logger log = LoggerFactory.getLogger(DepositController.class);

    private final WorkflowClient workflowClient;
    private final TaskClient taskClient;

    public DepositController(WorkflowClient workflowClient, TaskClient taskClient) {
        this.workflowClient = workflowClient;
        this.taskClient = taskClient;
    }

    @PostMapping("/confirm")
    public ResponseEntity<Map<String, Object>> confirmDeposit(@RequestBody Map<String, String> request) {
        String workflowId = request.get("workflowId");
        String orderNo = request.get("orderNo");

        log.info("Deposit confirmation request for workflowId: {}, order: {}", workflowId, orderNo);

        try {
            // 1. Get workflow directly by ID (with tasks included)
            Workflow targetWorkflow = workflowClient.getWorkflow(workflowId, true);

            if (targetWorkflow == null) {
                log.error("Workflow not found for workflowId: {}", workflowId);
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Workflow not found for workflowId: " + workflowId
                ));
            }

            // Verify orderNo matches
            if (!targetWorkflow.getInput().get("orderNo").equals(orderNo)) {
                log.error("OrderNo mismatch. Expected: {}, Got: {}",
                    targetWorkflow.getInput().get("orderNo"), orderNo);
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Order number does not match workflow"
                ));
            }

            // 3. Find WAIT task
            Task waitTask = null;
            log.info("Searching for WAIT task. Total tasks: {}", targetWorkflow.getTasks().size());
            for (Task task : targetWorkflow.getTasks()) {
                log.info("Task: ref={}, status={}", task.getReferenceTaskName(), task.getStatus().name());
                if ("wait_for_deposit".equals(task.getReferenceTaskName()) &&
                    "IN_PROGRESS".equals(task.getStatus().name())) {
                    waitTask = task;
                    log.info("Found WAIT task: {}", task.getTaskId());
                    break;
                }
            }

            if (waitTask == null) {
                log.error("WAIT task not found or not in progress for orderNo: {}", orderNo);
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Deposit wait task not found or already completed"
                ));
            }

            // 4. Complete the WAIT task
            TaskResult result = new TaskResult(waitTask);
            result.setStatus(TaskResult.Status.COMPLETED);

            Map<String, Object> outputData = new HashMap<>();
            outputData.put("depositConfirmed", true);
            outputData.put("confirmedAt", System.currentTimeMillis());
            result.setOutputData(outputData);

            taskClient.updateTask(result);

            log.info("Deposit confirmed for order: {}, taskId: {}", orderNo, waitTask.getTaskId());

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Deposit confirmed successfully",
                "orderNo", orderNo,
                "workflowId", targetWorkflow.getWorkflowId(),
                "taskId", waitTask.getTaskId()
            ));

        } catch (Exception e) {
            log.error("Error confirming deposit for order: {}", orderNo, e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Error: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/status/{orderNo}")
    public ResponseEntity<Map<String, Object>> getDepositStatus(@PathVariable String orderNo) {
        try {
            SearchResult<WorkflowSummary> searchResult = workflowClient.search(
                0,
                100,
                "ASC",
                "*",
                "workflowType=event_wait_deposit"
            );

            for (WorkflowSummary summary : searchResult.getResults()) {
                Workflow wf = workflowClient.getWorkflow(summary.getWorkflowId(), true);
                if (wf.getInput().get("orderNo").equals(orderNo)) {
                    Task waitTask = wf.getTasks().stream()
                        .filter(t -> "wait_for_deposit".equals(t.getReferenceTaskName()))
                        .findFirst()
                        .orElse(null);

                    return ResponseEntity.ok(Map.of(
                        "orderNo", orderNo,
                        "workflowId", wf.getWorkflowId(),
                        "workflowStatus", wf.getStatus().name(),
                        "waitTaskStatus", waitTask != null ? waitTask.getStatus().name() : "NOT_FOUND"
                    ));
                }
            }

            return ResponseEntity.notFound().build();

        } catch (Exception e) {
            log.error("Error getting deposit status for order: {}", orderNo, e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Error: " + e.getMessage()
            ));
        }
    }
}
