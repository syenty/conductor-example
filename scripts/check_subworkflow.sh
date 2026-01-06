#!/usr/bin/env bash
set -euo pipefail

CONDUCTOR_BASE_URL="${CONDUCTOR_BASE_URL:-http://localhost:8080/api}"

if [ $# -lt 1 ]; then
  echo "Usage: $0 <main-workflow-id>"
  echo ""
  echo "Example: $0 8df0a653-ed98-40b1-9b46-edc8b2a03ce4"
  exit 1
fi

MAIN_WORKFLOW_ID=$1

echo "=== Main Workflow Information ==="
echo "Main Workflow ID: ${MAIN_WORKFLOW_ID}"
echo ""

# Get main workflow details
curl -sS "${CONDUCTOR_BASE_URL}/workflow/${MAIN_WORKFLOW_ID}" | \
  jq '{
    workflowId: .workflowId,
    workflowType: .workflowName,
    status: .status,
    tasks: [.tasks[] | {
      name: .referenceTaskName,
      status: .status,
      taskType: .taskType
    }]
  }'

echo ""
echo "=== Sub-Workflow Details ==="

# Extract sub-workflow ID
SUB_WORKFLOW_ID=$(curl -sS "${CONDUCTOR_BASE_URL}/workflow/${MAIN_WORKFLOW_ID}" | \
  jq -r '.tasks[] | select(.taskType == "SUB_WORKFLOW") | .subWorkflowId')

if [ -z "$SUB_WORKFLOW_ID" ] || [ "$SUB_WORKFLOW_ID" == "null" ]; then
  echo "No sub-workflow found in this workflow"
  exit 0
fi

echo "Sub-Workflow ID: ${SUB_WORKFLOW_ID}"
echo ""

# Get sub-workflow full details
curl -sS "${CONDUCTOR_BASE_URL}/workflow/${SUB_WORKFLOW_ID}" | \
  jq '{
    workflowId: .workflowId,
    workflowType: .workflowName,
    status: .status,
    input: .input,
    tasks: [.tasks[] | {
      seq: .seq,
      name: .referenceTaskName,
      status: .status,
      workerId: .workerId,
      startTime: .startTime,
      endTime: .endTime,
      input: .inputData,
      output: .outputData
    }],
    output: .output
  }'

echo ""
echo "=== Sub-Workflow Task Summary ==="
curl -sS "${CONDUCTOR_BASE_URL}/workflow/${SUB_WORKFLOW_ID}" | \
  jq -r '.tasks[] | "\(.seq). \(.referenceTaskName): \(.status) (workerId: \(.workerId // "N/A"))"'
