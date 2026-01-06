#!/usr/bin/env bash
set -euo pipefail

CONDUCTOR_BASE_URL="${CONDUCTOR_BASE_URL:-http://localhost:8080/api}"
TIMESTAMP=$(date +%s)

echo "=== W1 Basic Order Flow Test ==="
echo ""

ORDER_NO="ORD-W1-${TIMESTAMP}"
echo "Order No: ${ORDER_NO}"

WORKFLOW_ID=$(curl -sS -X POST "${CONDUCTOR_BASE_URL}/workflow/basic_order" \
  -H "Content-Type: application/json" \
  -d "{
    \"orderNo\": \"${ORDER_NO}\",
    \"totalAmount\": 150000,
    \"currency\": \"KRW\",
    \"customerId\": \"CUST-001\",
    \"paymentMethod\": \"CREDIT_CARD\",
    \"paymentFailRate\": 0,
    \"paymentDelayMs\": 0,
    \"items\": [
      {\"productId\": \"PROD-001\", \"quantity\": 2, \"unitPrice\": 50000},
      {\"productId\": \"PROD-002\", \"quantity\": 1, \"unitPrice\": 50000}
    ],
    \"forceOutOfStock\": false,
    \"partialFailIndex\": -1
  }" | tr -d '"')

echo "Workflow ID: ${WORKFLOW_ID}"
echo ""
echo "Waiting for workflow to complete..."
sleep 5

echo "Checking workflow status..."
curl -sS "${CONDUCTOR_BASE_URL}/workflow/${WORKFLOW_ID}" | \
  jq '{status: .status, tasks: [.tasks[] | {name: .referenceTaskName, status: .status}]}'

echo ""
echo "=== Database Check Commands ==="
echo "docker exec order-db psql -U order_user -d order_db -c \"SELECT order_no, status FROM orders WHERE order_no = '${ORDER_NO}';\""
