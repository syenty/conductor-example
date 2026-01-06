#!/usr/bin/env bash
set -euo pipefail

CONDUCTOR_BASE_URL="${CONDUCTOR_BASE_URL:-http://localhost:8080/api}"
TIMESTAMP=$(date +%s)

echo "=== W3 Timeout Handling Test ==="
echo ""

echo "Test 1: Normal payment (delayMs=2000, timeout=10s)"
ORDER_NO_1="ORD-W3-${TIMESTAMP}-1"
echo "Order No: ${ORDER_NO_1}"
WORKFLOW_ID_1=$(curl -sS -X POST "${CONDUCTOR_BASE_URL}/workflow/timeout_handling" \
  -H "Content-Type: application/json" \
  -d "{
    \"orderNo\": \"${ORDER_NO_1}\",
    \"totalAmount\": 100000,
    \"currency\": \"KRW\",
    \"customerId\": \"CUST-001\",
    \"paymentMethod\": \"CREDIT_CARD\",
    \"paymentFailRate\": 0,
    \"paymentDelayMs\": 2000,
    \"items\": [
      {\"productId\": \"PROD-001\", \"quantity\": 2, \"unitPrice\": 50000}
    ],
    \"forceOutOfStock\": false,
    \"partialFailIndex\": -1
  }" | tr -d '"')
echo "Workflow ID: ${WORKFLOW_ID_1}"
echo "Expected: Payment completes within timeout"

echo ""
echo "Waiting for workflow to complete..."
sleep 5

echo "Checking workflow status..."
curl -sS "${CONDUCTOR_BASE_URL}/workflow/${WORKFLOW_ID_1}" | \
  jq '{status: .status, tasks: [.tasks[] | {name: .referenceTaskName, status: .status}]}'

echo ""
echo ""

echo "Test 2: Timeout payment (delayMs=12000, timeout=10s)"
ORDER_NO_2="ORD-W3-${TIMESTAMP}-2"
echo "Order No: ${ORDER_NO_2}"
WORKFLOW_ID_2=$(curl -sS -X POST "${CONDUCTOR_BASE_URL}/workflow/timeout_handling" \
  -H "Content-Type: application/json" \
  -d "{
    \"orderNo\": \"${ORDER_NO_2}\",
    \"totalAmount\": 100000,
    \"currency\": \"KRW\",
    \"customerId\": \"CUST-002\",
    \"paymentMethod\": \"CREDIT_CARD\",
    \"paymentFailRate\": 0,
    \"paymentDelayMs\": 12000,
    \"items\": [
      {\"productId\": \"PROD-002\", \"quantity\": 2, \"unitPrice\": 50000}
    ],
    \"forceOutOfStock\": false,
    \"partialFailIndex\": -1
  }" | tr -d '"')
echo "Workflow ID: ${WORKFLOW_ID_2}"
echo "Expected: Payment times out (TIMEOUT_FAILED)"

echo ""
echo "Waiting for timeout..."
sleep 15

echo "Checking workflow status..."
curl -sS "${CONDUCTOR_BASE_URL}/workflow/${WORKFLOW_ID_2}" | \
  jq '{status: .status, tasks: [.tasks[] | {name: .referenceTaskName, status: .status}]}'

echo ""
echo ""
echo "=== Database Check Commands ==="
echo "Order 1: docker exec order-db psql -U order_user -d order_db -c \"SELECT order_no, status FROM orders WHERE order_no = '${ORDER_NO_1}';\""
echo "Order 2: docker exec order-db psql -U order_user -d order_db -c \"SELECT order_no, status FROM orders WHERE order_no = '${ORDER_NO_2}';\""
