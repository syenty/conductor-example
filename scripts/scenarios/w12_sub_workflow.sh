#!/usr/bin/env bash
set -euo pipefail

CONDUCTOR_BASE_URL="${CONDUCTOR_BASE_URL:-http://localhost:8080/api}"
TIMESTAMP=$(date +%s)

echo "=== W12 Sub-Workflow (Shipping Process) Test ==="
echo ""

# Test 1: Standard delivery
echo "Test 1: Standard delivery order"
ORDER_NO_1="ORD-W12-${TIMESTAMP}-1"
echo "Order No: ${ORDER_NO_1}"
WORKFLOW_ID_1=$(curl -sS -X POST "${CONDUCTOR_BASE_URL}/workflow/order_with_shipping_subflow" \
  -H "Content-Type: application/json" \
  -d "{
    \"orderNo\": \"${ORDER_NO_1}\",
    \"totalAmount\": 150000,
    \"currency\": \"KRW\",
    \"customerId\": \"CUST-001\",
    \"paymentMethod\": \"CREDIT_CARD\",
    \"paymentFailRate\": 0,
    \"paymentDelayMs\": 0,
    \"items\": [
      {\"productId\": \"PROD-001\", \"quantity\": 3, \"unitPrice\": 50000}
    ],
    \"forceOutOfStock\": false,
    \"partialFailIndex\": -1,
    \"deliveryType\": \"STANDARD\",
    \"deliveryAddress\": \"Seoul, Gangnam\"
  }" | tr -d '"')
echo "Workflow ID: ${WORKFLOW_ID_1}"
echo "Expected: Standard delivery (3 days)"

echo ""
echo "Waiting for workflow to complete..."
sleep 8

echo "Checking workflow status..."
curl -sS "${CONDUCTOR_BASE_URL}/workflow/${WORKFLOW_ID_1}" | \
  jq '{status: .status, tasks: [.tasks[] | {name: .referenceTaskName, status: .status, taskType: .taskType}]}'

echo ""
echo ""

# Test 2: Express delivery
echo "Test 2: Express delivery order"
ORDER_NO_2="ORD-W12-${TIMESTAMP}-2"
echo "Order No: ${ORDER_NO_2}"
WORKFLOW_ID_2=$(curl -sS -X POST "${CONDUCTOR_BASE_URL}/workflow/order_with_shipping_subflow" \
  -H "Content-Type: application/json" \
  -d "{
    \"orderNo\": \"${ORDER_NO_2}\",
    \"totalAmount\": 200000,
    \"currency\": \"KRW\",
    \"customerId\": \"CUST-002\",
    \"paymentMethod\": \"CREDIT_CARD\",
    \"paymentFailRate\": 0,
    \"paymentDelayMs\": 0,
    \"items\": [
      {\"productId\": \"PROD-001\", \"quantity\": 2, \"unitPrice\": 50000},
      {\"productId\": \"PROD-002\", \"quantity\": 2, \"unitPrice\": 50000}
    ],
    \"forceOutOfStock\": false,
    \"partialFailIndex\": -1,
    \"deliveryType\": \"EXPRESS\",
    \"deliveryAddress\": \"Seoul, Gangnam\"
  }" | tr -d '"')
echo "Workflow ID: ${WORKFLOW_ID_2}"
echo "Expected: Express delivery (next day)"

echo ""
echo "Waiting for workflow to complete..."
sleep 8

echo "Checking workflow status..."
curl -sS "${CONDUCTOR_BASE_URL}/workflow/${WORKFLOW_ID_2}" | \
  jq '{status: .status, tasks: [.tasks[] | {name: .referenceTaskName, status: .status, taskType: .taskType}]}'

echo ""
echo ""
echo "=== Check Sub-Workflow Execution ==="
echo "Standard delivery sub-workflow:"
SUB_WORKFLOW_ID_1=$(curl -sS "${CONDUCTOR_BASE_URL}/workflow/${WORKFLOW_ID_1}" | \
  jq -r '.tasks[] | select(.taskType == "SUB_WORKFLOW") | .subWorkflowId')

if [ -n "$SUB_WORKFLOW_ID_1" ] && [ "$SUB_WORKFLOW_ID_1" != "null" ]; then
  echo "Sub-workflow ID: ${SUB_WORKFLOW_ID_1}"
  curl -sS "${CONDUCTOR_BASE_URL}/workflow/${SUB_WORKFLOW_ID_1}" | \
    jq '{workflowId, status, tasks: [.tasks[] | {name: .referenceTaskName, status: .status}], output}'
else
  echo "Sub-workflow not found or not yet created"
fi

echo ""
echo "Express delivery sub-workflow:"
SUB_WORKFLOW_ID_2=$(curl -sS "${CONDUCTOR_BASE_URL}/workflow/${WORKFLOW_ID_2}" | \
  jq -r '.tasks[] | select(.taskType == "SUB_WORKFLOW") | .subWorkflowId')

if [ -n "$SUB_WORKFLOW_ID_2" ] && [ "$SUB_WORKFLOW_ID_2" != "null" ]; then
  echo "Sub-workflow ID: ${SUB_WORKFLOW_ID_2}"
  curl -sS "${CONDUCTOR_BASE_URL}/workflow/${SUB_WORKFLOW_ID_2}" | \
    jq '{workflowId, status, tasks: [.tasks[] | {name: .referenceTaskName, status: .status}], output}'
else
  echo "Sub-workflow not found or not yet created"
fi

echo ""
echo "=== Database Check Commands ==="
echo "Standard order: docker exec order-db psql -U order_user -d order_db -c \"SELECT order_no, status FROM orders WHERE order_no = '${ORDER_NO_1}';\""
echo "Express order: docker exec order-db psql -U order_user -d order_db -c \"SELECT order_no, status FROM orders WHERE order_no = '${ORDER_NO_2}';\""
