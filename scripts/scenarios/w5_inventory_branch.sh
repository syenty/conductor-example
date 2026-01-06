#!/usr/bin/env bash
set -euo pipefail

CONDUCTOR_BASE_URL="${CONDUCTOR_BASE_URL:-http://localhost:8080/api}"
TIMESTAMP=$(date +%s)

echo "=== W5 Inventory Shortage Branch Test ==="
echo ""

# Test 1: Normal flow - inventory available
echo "Test 1: Normal flow (inventory available)"
ORDER_NO_1="ORD-W5-${TIMESTAMP}-1"
echo "Order No: ${ORDER_NO_1}"
WORKFLOW_ID_1=$(curl -sS -X POST "${CONDUCTOR_BASE_URL}/workflow/inventory_shortage_branch" \
  -H "Content-Type: application/json" \
  -d "{
    \"orderNo\": \"${ORDER_NO_1}\",
    \"totalAmount\": 10000,
    \"currency\": \"KRW\",
    \"customerId\": \"CUST-001\",
    \"paymentMethod\": \"CARD\",
    \"paymentFailRate\": 0.0,
    \"paymentDelayMs\": 0,
    \"items\": [
      {\"productId\": \"PROD-001\", \"quantity\": 2, \"unitPrice\": 5000}
    ],
    \"forceOutOfStock\": false,
    \"partialFailIndex\": -1
  }" | tr -d '"')
echo "Workflow ID: ${WORKFLOW_ID_1}"
echo "Expected: Order confirmed (inventory reserved successfully)"

echo ""
echo ""

# Test 2: Out of stock - refund and cancel
echo "Test 2: Out of stock (refund payment and cancel order)"
ORDER_NO_2="ORD-W5-${TIMESTAMP}-2"
echo "Order No: ${ORDER_NO_2}"
WORKFLOW_ID_2=$(curl -sS -X POST "${CONDUCTOR_BASE_URL}/workflow/inventory_shortage_branch" \
  -H "Content-Type: application/json" \
  -d "{
    \"orderNo\": \"${ORDER_NO_2}\",
    \"totalAmount\": 20000,
    \"currency\": \"KRW\",
    \"customerId\": \"CUST-002\",
    \"paymentMethod\": \"CARD\",
    \"paymentFailRate\": 0.0,
    \"paymentDelayMs\": 0,
    \"items\": [
      {\"productId\": \"PROD-OUT-OF-STOCK\", \"quantity\": 1, \"unitPrice\": 20000}
    ],
    \"forceOutOfStock\": true,
    \"partialFailIndex\": -1
  }" | tr -d '"')
echo "Workflow ID: ${WORKFLOW_ID_2}"
echo "Expected: Payment refunded, Order cancelled (out of stock)"

echo ""
echo "Waiting for workflows to complete..."
sleep 5

echo "Checking workflow status..."
echo "Test 1 (Normal - should be COMPLETED):"
curl -sS "${CONDUCTOR_BASE_URL}/workflow/${WORKFLOW_ID_1}" | \
  jq '{status: .status, tasks: [.tasks[] | {name: .referenceTaskName, status: .status}]}'

echo ""
echo "Test 2 (Out of stock - should be COMPLETED with refund):"
curl -sS "${CONDUCTOR_BASE_URL}/workflow/${WORKFLOW_ID_2}" | \
  jq '{status: .status, tasks: [.tasks[] | {name: .referenceTaskName, status: .status}]}'

echo ""
echo ""
echo "=== Database Check Commands ==="
echo "Order 1: docker exec order-db psql -U order_user -d order_db -c \"SELECT order_no, status FROM orders WHERE order_no = '${ORDER_NO_1}';\""
echo "Order 2: docker exec order-db psql -U order_user -d order_db -c \"SELECT order_no, status FROM orders WHERE order_no = '${ORDER_NO_2}';\""
echo "Payment 2: docker exec payment-db psql -U payment_user -d payment_db -c \"SELECT order_no, status FROM payments WHERE order_no = '${ORDER_NO_2}';\""
