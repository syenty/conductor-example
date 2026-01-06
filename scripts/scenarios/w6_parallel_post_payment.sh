#!/usr/bin/env bash
set -euo pipefail

CONDUCTOR_BASE_URL="${CONDUCTOR_BASE_URL:-http://localhost:8080/api}"
TIMESTAMP=$(date +%s)

echo "=== W6 Parallel Post Payment Test ==="
echo ""

# Test 1: Both success - payment and inventory OK
echo "Test 1: Both success (payment OK + inventory OK)"
ORDER_NO_1="ORD-W6-${TIMESTAMP}-1"
echo "Order No: ${ORDER_NO_1}"
WORKFLOW_ID_1=$(curl -sS -X POST "${CONDUCTOR_BASE_URL}/workflow/parallel_post_payment?version=4" \
  -H "Content-Type: application/json" \
  -d "{
    \"orderNo\": \"${ORDER_NO_1}\",
    \"totalAmount\": 15000,
    \"currency\": \"KRW\",
    \"customerId\": \"CUST-001\",
    \"paymentMethod\": \"CARD\",
    \"paymentFailRate\": 0.0,
    \"paymentDelayMs\": 1000,
    \"items\": [
      {\"productId\": \"PROD-001\", \"quantity\": 3, \"unitPrice\": 5000}
    ],
    \"forceOutOfStock\": false,
    \"partialFailIndex\": -1
  }" | tr -d '"')
echo "Workflow ID: ${WORKFLOW_ID_1}"
echo "Expected: BOTH_SUCCESS → reserve_inventory → confirm_order"

echo ""
echo ""

# Test 2: Payment failed - inventory OK
echo "Test 2: Payment failed (payment FAIL + inventory OK)"
ORDER_NO_2="ORD-W6-${TIMESTAMP}-2"
echo "Order No: ${ORDER_NO_2}"
WORKFLOW_ID_2=$(curl -sS -X POST "${CONDUCTOR_BASE_URL}/workflow/parallel_post_payment?version=4" \
  -H "Content-Type: application/json" \
  -d "{
    \"orderNo\": \"${ORDER_NO_2}\",
    \"totalAmount\": 15000,
    \"currency\": \"KRW\",
    \"customerId\": \"CUST-002\",
    \"paymentMethod\": \"CARD\",
    \"paymentFailRate\": 1.0,
    \"paymentDelayMs\": 0,
    \"items\": [
      {\"productId\": \"PROD-001\", \"quantity\": 3, \"unitPrice\": 5000}
    ],
    \"forceOutOfStock\": false,
    \"partialFailIndex\": -1
  }" | tr -d '"')
echo "Workflow ID: ${WORKFLOW_ID_2}"
echo "Expected: PAYMENT_FAILED → cancel_order"

echo ""
echo ""

# Test 3: Inventory failed - payment OK
echo "Test 3: Inventory failed (payment OK + inventory FAIL)"
ORDER_NO_3="ORD-W6-${TIMESTAMP}-3"
echo "Order No: ${ORDER_NO_3}"
WORKFLOW_ID_3=$(curl -sS -X POST "${CONDUCTOR_BASE_URL}/workflow/parallel_post_payment?version=4" \
  -H "Content-Type: application/json" \
  -d "{
    \"orderNo\": \"${ORDER_NO_3}\",
    \"totalAmount\": 20000,
    \"currency\": \"KRW\",
    \"customerId\": \"CUST-003\",
    \"paymentMethod\": \"CARD\",
    \"paymentFailRate\": 0.0,
    \"paymentDelayMs\": 0,
    \"items\": [
      {\"productId\": \"PROD-OUT-OF-STOCK\", \"quantity\": 1, \"unitPrice\": 20000}
    ],
    \"forceOutOfStock\": true,
    \"partialFailIndex\": -1
  }" | tr -d '"')
echo "Workflow ID: ${WORKFLOW_ID_3}"
echo "Expected: INVENTORY_FAILED → refund_payment → cancel_order"

echo ""
echo ""

# Test 4: Both failed
echo "Test 4: Both failed (payment FAIL + inventory FAIL)"
ORDER_NO_4="ORD-W6-${TIMESTAMP}-4"
echo "Order No: ${ORDER_NO_4}"
WORKFLOW_ID_4=$(curl -sS -X POST "${CONDUCTOR_BASE_URL}/workflow/parallel_post_payment?version=4" \
  -H "Content-Type: application/json" \
  -d "{
    \"orderNo\": \"${ORDER_NO_4}\",
    \"totalAmount\": 20000,
    \"currency\": \"KRW\",
    \"customerId\": \"CUST-004\",
    \"paymentMethod\": \"CARD\",
    \"paymentFailRate\": 1.0,
    \"paymentDelayMs\": 0,
    \"items\": [
      {\"productId\": \"PROD-OUT-OF-STOCK\", \"quantity\": 1, \"unitPrice\": 20000}
    ],
    \"forceOutOfStock\": true,
    \"partialFailIndex\": -1
  }" | tr -d '"')
echo "Workflow ID: ${WORKFLOW_ID_4}"
echo "Expected: BOTH_FAILED → cancel_order"

echo ""
echo "Waiting for workflows to complete..."
sleep 8

echo "Checking workflow status..."
echo "Test 1 (Both success):"
curl -sS "${CONDUCTOR_BASE_URL}/workflow/${WORKFLOW_ID_1}" | \
  jq '{status: .status, tasks: [.tasks[] | {name: .referenceTaskName, status: .status}]}'

echo ""
echo "Test 2 (Payment failed):"
curl -sS "${CONDUCTOR_BASE_URL}/workflow/${WORKFLOW_ID_2}" | \
  jq '{status: .status, tasks: [.tasks[] | {name: .referenceTaskName, status: .status}]}'

echo ""
echo "Test 3 (Inventory failed):"
curl -sS "${CONDUCTOR_BASE_URL}/workflow/${WORKFLOW_ID_3}" | \
  jq '{status: .status, tasks: [.tasks[] | {name: .referenceTaskName, status: .status}]}'

echo ""
echo "Test 4 (Both failed):"
curl -sS "${CONDUCTOR_BASE_URL}/workflow/${WORKFLOW_ID_4}" | \
  jq '{status: .status, tasks: [.tasks[] | {name: .referenceTaskName, status: .status}]}'

echo ""
echo ""
echo "=== Database Check Commands ==="
echo "Order 1: docker exec order-db psql -U order_user -d order_db -c \"SELECT order_no, status FROM orders WHERE order_no = '${ORDER_NO_1}';\""
echo "Order 2: docker exec order-db psql -U order_user -d order_db -c \"SELECT order_no, status FROM orders WHERE order_no = '${ORDER_NO_2}';\""
echo "Order 3: docker exec order-db psql -U order_user -d order_db -c \"SELECT order_no, status FROM orders WHERE order_no = '${ORDER_NO_3}';\""
echo "Order 4: docker exec order-db psql -U order_user -d order_db -c \"SELECT order_no, status FROM orders WHERE order_no = '${ORDER_NO_4}';\""
