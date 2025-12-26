#!/usr/bin/env bash
set -euo pipefail

CONDUCTOR_BASE_URL="${CONDUCTOR_BASE_URL:-http://localhost:8080/api}"

if [ $# -lt 1 ]; then
  echo "Usage: $0 <workflow_id>"
  echo "Example: $0 abc123-def456-..."
  exit 1
fi

WORKFLOW_ID="$1"

echo "=== Completing WAIT task for bank deposit ==="
echo "Workflow ID: ${WORKFLOW_ID}"
echo ""

# Get task ID for wait_for_bank_deposit
TASK_ID=$(curl -sS "${CONDUCTOR_BASE_URL}/workflow/${WORKFLOW_ID}" | jq -r '.tasks[] | select(.referenceTaskName == "wait_for_bank_deposit") | .taskId')

if [ -z "$TASK_ID" ] || [ "$TASK_ID" == "null" ]; then
  echo "Error: Could not find wait_for_bank_deposit task in workflow"
  exit 1
fi

echo "Task ID: ${TASK_ID}"

# Complete the WAIT task
curl -sS -X POST "${CONDUCTOR_BASE_URL}/tasks" \
  -H "Content-Type: application/json" \
  -d "{
    \"workflowInstanceId\": \"${WORKFLOW_ID}\",
    \"taskId\": \"${TASK_ID}\",
    \"status\": \"COMPLETED\",
    \"outputData\": {
      \"deposited\": true,
      \"depositedAt\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\"
    }
  }"

echo ""
echo "Bank deposit confirmed. Workflow will proceed to confirm_bank_transfer."
