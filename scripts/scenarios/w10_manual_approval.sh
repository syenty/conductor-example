#!/usr/bin/env bash
set -euo pipefail

CONDUCTOR_BASE_URL="${CONDUCTOR_BASE_URL:-http://localhost:8080/api}"
TIMESTAMP=$(date +%s)

echo "=== W10 Manual Approval Test ==="
echo ""

# Test 1: High value order (>= 100,000 KRW) - Approval required, then APPROVED
echo "Test 1: High value order (>= 100,000 KRW) - Manual approval required"
ORDER_NO_1="ORD-W10-${TIMESTAMP}-1"
echo "Order No: ${ORDER_NO_1}"
WORKFLOW_ID_1=$(curl -sS -X POST "${CONDUCTOR_BASE_URL}/workflow/manual_approval" \
  -H "Content-Type: application/json" \
  -d "{
    \"orderNo\": \"${ORDER_NO_1}\",
    \"totalAmount\": 150000,
    \"currency\": \"KRW\",
    \"customerId\": \"CUST-001\",
    \"paymentMethod\": \"CARD\",
    \"paymentFailRate\": 0.0,
    \"paymentDelayMs\": 0,
    \"items\": [
      {\"productId\": \"PROD-001\", \"quantity\": 3, \"unitPrice\": 50000}
    ],
    \"forceOutOfStock\": false,
    \"partialFailIndex\": -1
  }" | tr -d '"')
echo "Workflow ID: ${WORKFLOW_ID_1}"
echo "Expected: Wait for approval (HUMAN task IN_PROGRESS)"

echo ""
echo "Waiting for HUMAN task to be created..."
sleep 3

# Wait for HUMAN task to be created (retry logic)
MAX_RETRIES=10
RETRY_COUNT=0
TASK_ID_1=""

while [ $RETRY_COUNT -lt $MAX_RETRIES ]; do
  TASK_ID_1=$(curl -sS "${CONDUCTOR_BASE_URL}/workflow/${WORKFLOW_ID_1}" | \
    jq -r '.tasks[] | select(.referenceTaskName == "approve_high_value_order") | .taskId')

  if [ -n "$TASK_ID_1" ] && [ "$TASK_ID_1" != "null" ]; then
    break
  fi

  RETRY_COUNT=$((RETRY_COUNT + 1))
  echo "Waiting for HUMAN task... (attempt $RETRY_COUNT/$MAX_RETRIES)"
  sleep 1
done

if [ -z "$TASK_ID_1" ] || [ "$TASK_ID_1" == "null" ]; then
  echo "ERROR: HUMAN task not found after $MAX_RETRIES attempts!"
  exit 1
fi

echo "HUMAN Task ID: ${TASK_ID_1}"
echo ""
echo "Approving order via API..."

# Approve the order
curl -sS -X POST "${CONDUCTOR_BASE_URL}/tasks" \
  -H "Content-Type: application/json" \
  -d "{
    \"taskId\": \"${TASK_ID_1}\",
    \"workflowInstanceId\": \"${WORKFLOW_ID_1}\",
    \"status\": \"COMPLETED\",
    \"outputData\": {
      \"approved\": true,
      \"approver\": \"admin\",
      \"comment\": \"High value order approved\"
    }
  }"

echo "Approval sent. Waiting for workflow to complete..."
sleep 3

echo ""
echo ""

# Test 2: High value order - REJECTED
echo "Test 2: High value order (>= 100,000 KRW) - Approval REJECTED"
ORDER_NO_2="ORD-W10-${TIMESTAMP}-2"
echo "Order No: ${ORDER_NO_2}"
WORKFLOW_ID_2=$(curl -sS -X POST "${CONDUCTOR_BASE_URL}/workflow/manual_approval" \
  -H "Content-Type: application/json" \
  -d "{
    \"orderNo\": \"${ORDER_NO_2}\",
    \"totalAmount\": 200000,
    \"currency\": \"KRW\",
    \"customerId\": \"CUST-002\",
    \"paymentMethod\": \"CARD\",
    \"paymentFailRate\": 0.0,
    \"paymentDelayMs\": 0,
    \"items\": [
      {\"productId\": \"PROD-001\", \"quantity\": 4, \"unitPrice\": 50000}
    ],
    \"forceOutOfStock\": false,
    \"partialFailIndex\": -1
  }" | tr -d '"')
echo "Workflow ID: ${WORKFLOW_ID_2}"

echo ""
echo "Waiting for HUMAN task to be created..."
sleep 3

# Wait for HUMAN task to be created (retry logic)
MAX_RETRIES=10
RETRY_COUNT=0
TASK_ID_2=""

while [ $RETRY_COUNT -lt $MAX_RETRIES ]; do
  TASK_ID_2=$(curl -sS "${CONDUCTOR_BASE_URL}/workflow/${WORKFLOW_ID_2}" | \
    jq -r '.tasks[] | select(.referenceTaskName == "approve_high_value_order") | .taskId')

  if [ -n "$TASK_ID_2" ] && [ "$TASK_ID_2" != "null" ]; then
    break
  fi

  RETRY_COUNT=$((RETRY_COUNT + 1))
  echo "Waiting for HUMAN task... (attempt $RETRY_COUNT/$MAX_RETRIES)"
  sleep 1
done

if [ -z "$TASK_ID_2" ] || [ "$TASK_ID_2" == "null" ]; then
  echo "ERROR: HUMAN task not found after $MAX_RETRIES attempts!"
  exit 1
fi

echo "HUMAN Task ID: ${TASK_ID_2}"
echo ""
echo "Rejecting order via API..."

# Reject the order
curl -sS -X POST "${CONDUCTOR_BASE_URL}/tasks" \
  -H "Content-Type: application/json" \
  -d "{
    \"taskId\": \"${TASK_ID_2}\",
    \"workflowInstanceId\": \"${WORKFLOW_ID_2}\",
    \"status\": \"COMPLETED\",
    \"outputData\": {
      \"approved\": false,
      \"approver\": \"admin\",
      \"comment\": \"Rejected due to fraud suspicion\"
    }
  }"

echo "Rejection sent. Waiting for workflow to complete..."
sleep 3

echo ""
echo ""
echo "=== Check Results ==="
echo "Test 1 (approved): curl -sS \"${CONDUCTOR_BASE_URL}/workflow/${WORKFLOW_ID_1}\" | jq '{status: .status, tasks: [.tasks[] | .referenceTaskName]}'"
echo "Test 2 (rejected): curl -sS \"${CONDUCTOR_BASE_URL}/workflow/${WORKFLOW_ID_2}\" | jq '{status: .status, tasks: [.tasks[] | .referenceTaskName]}'"
echo ""
echo "=== Database Check Commands ==="
echo "Order 1: docker exec order-db psql -U order_user -d order_db -c \"SELECT order_no, status FROM orders WHERE order_no = '${ORDER_NO_1}';\""
echo "Order 2: docker exec order-db psql -U order_user -d order_db -c \"SELECT order_no, status FROM orders WHERE order_no = '${ORDER_NO_2}';\""
echo "Payment 2: docker exec payment-db psql -U payment_user -d payment_db -c \"SELECT order_no, status FROM payments WHERE order_no = '${ORDER_NO_2}';\""
