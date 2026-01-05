#!/usr/bin/env bash
set -euo pipefail

CONDUCTOR_BASE_URL="${CONDUCTOR_BASE_URL:-http://localhost:8080/api}"
WORKER_BASE_URL="${WORKER_BASE_URL:-http://localhost:8084}"
TIMESTAMP=$(date +%s)

echo "=== W11 Event Wait (Bank Transfer Deposit) Test ==="
echo ""

# Test 1: Wait for deposit confirmation
echo "Test 1: Bank transfer order - Wait for deposit confirmation"
ORDER_NO_1="ORD-W11-${TIMESTAMP}-1"
echo "Order No: ${ORDER_NO_1}"
WORKFLOW_ID_1=$(curl -sS -X POST "${CONDUCTOR_BASE_URL}/workflow/event_wait_deposit" \
  -H "Content-Type: application/json" \
  -d "{
    \"orderNo\": \"${ORDER_NO_1}\",
    \"totalAmount\": 100000,
    \"currency\": \"KRW\",
    \"customerId\": \"CUST-001\",
    \"items\": [
      {\"productId\": \"PROD-001\", \"quantity\": 2, \"unitPrice\": 50000}
    ],
    \"forceOutOfStock\": false,
    \"partialFailIndex\": -1
  }" | tr -d '"')
echo "Workflow ID: ${WORKFLOW_ID_1}"
echo "Expected: Wait for deposit (WAIT task IN_PROGRESS)"

echo ""
echo "Waiting for WAIT task to be created..."
sleep 3

# Wait for WAIT task to be created (retry logic)
MAX_RETRIES=10
RETRY_COUNT=0
WAIT_TASK_STATUS=""

while [ $RETRY_COUNT -lt $MAX_RETRIES ]; do
  WAIT_TASK_STATUS=$(curl -sS "${CONDUCTOR_BASE_URL}/workflow/${WORKFLOW_ID_1}" | \
    jq -r '.tasks[] | select(.referenceTaskName == "wait_for_deposit") | .status')

  if [ -n "$WAIT_TASK_STATUS" ] && [ "$WAIT_TASK_STATUS" != "null" ]; then
    break
  fi

  RETRY_COUNT=$((RETRY_COUNT + 1))
  echo "Waiting for WAIT task... (attempt $RETRY_COUNT/$MAX_RETRIES)"
  sleep 1
done

if [ -z "$WAIT_TASK_STATUS" ] || [ "$WAIT_TASK_STATUS" == "null" ]; then
  echo "ERROR: WAIT task not found after $MAX_RETRIES attempts!"
  exit 1
fi

echo "WAIT Task Status: ${WAIT_TASK_STATUS}"

# Check workflow status
echo ""
echo "Checking workflow status..."
curl -sS "${CONDUCTOR_BASE_URL}/workflow/${WORKFLOW_ID_1}" | \
  jq '{status: .status, tasks: [.tasks[] | {name: .referenceTaskName, status: .status}]}'

echo ""
echo ""
echo "Simulating deposit confirmation via API..."
echo "POST ${WORKER_BASE_URL}/deposit/confirm"

# Confirm deposit via custom API
DEPOSIT_RESPONSE=$(curl -sS -X POST "${WORKER_BASE_URL}/deposit/confirm" \
  -H "Content-Type: application/json" \
  -d "{\"workflowId\": \"${WORKFLOW_ID_1}\", \"orderNo\": \"${ORDER_NO_1}\"}")

echo "Deposit API Response:"
echo "$DEPOSIT_RESPONSE" | jq '.'

echo ""
echo "Waiting for workflow to complete..."
sleep 5

echo ""
echo ""
echo "=== Check Results ==="
echo "Workflow status:"
curl -sS "${CONDUCTOR_BASE_URL}/workflow/${WORKFLOW_ID_1}" | \
  jq '{status: .status, tasks: [.tasks[] | {name: .referenceTaskName, status: .status}]}'

echo ""
echo "=== Database Check Commands ==="
echo "Order: docker exec order-db psql -U order_user -d order_db -c \"SELECT order_no, status, payment_method FROM orders WHERE order_no = '${ORDER_NO_1}';\""
