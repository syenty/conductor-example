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
echo ""
echo "=== Check Results ==="
echo "Test 1 (normal): curl -sS \"${CONDUCTOR_BASE_URL}/workflow/${WORKFLOW_ID_1}\" | jq '.status'"
echo "Test 2 (out of stock): curl -sS \"${CONDUCTOR_BASE_URL}/workflow/${WORKFLOW_ID_2}\" | jq '.status'"
