#!/usr/bin/env bash
set -euo pipefail

CONDUCTOR_BASE_URL="${CONDUCTOR_BASE_URL:-http://localhost:8080/api}"
TIMESTAMP=$(date +%s)

echo "=== W9 Saga Partial Inventory Fail Test ==="
echo ""

# Test 1: All items success
echo "Test 1: All items available (no compensation)"
ORDER_NO_1="ORD-W9-${TIMESTAMP}-1"
echo "Order No: ${ORDER_NO_1}"
WORKFLOW_ID_1=$(curl -sS -X POST "${CONDUCTOR_BASE_URL}/workflow/saga_partial_fail" \
  -H "Content-Type: application/json" \
  -d "{
    \"orderNo\": \"${ORDER_NO_1}\",
    \"totalAmount\": 35000,
    \"currency\": \"KRW\",
    \"customerId\": \"CUST-001\",
    \"paymentMethod\": \"CARD\",
    \"paymentFailRate\": 0.0,
    \"paymentDelayMs\": 0,
    \"items\": [
      {\"productId\": \"PROD-001\", \"quantity\": 2, \"unitPrice\": 10000},
      {\"productId\": \"PROD-002\", \"quantity\": 3, \"unitPrice\": 5000}
    ],
    \"forceOutOfStock\": false,
    \"partialFailIndex\": -1
  }" | tr -d '"')
echo "Workflow ID: ${WORKFLOW_ID_1}"
echo "Expected: All items reserved → confirm_order"

echo ""
echo ""

# Test 2: Partial fail (item at index 1 fails)
echo "Test 2: Partial inventory fail (item 2 out of stock, triggers full compensation)"
ORDER_NO_2="ORD-W9-${TIMESTAMP}-2"
echo "Order No: ${ORDER_NO_2}"
WORKFLOW_ID_2=$(curl -sS -X POST "${CONDUCTOR_BASE_URL}/workflow/saga_partial_fail" \
  -H "Content-Type: application/json" \
  -d "{
    \"orderNo\": \"${ORDER_NO_2}\",
    \"totalAmount\": 35000,
    \"currency\": \"KRW\",
    \"customerId\": \"CUST-002\",
    \"paymentMethod\": \"CARD\",
    \"paymentFailRate\": 0.0,
    \"paymentDelayMs\": 0,
    \"items\": [
      {\"productId\": \"PROD-001\", \"quantity\": 2, \"unitPrice\": 10000},
      {\"productId\": \"PROD-002\", \"quantity\": 3, \"unitPrice\": 5000}
    ],
    \"forceOutOfStock\": false,
    \"partialFailIndex\": 1
  }" | tr -d '"')
echo "Workflow ID: ${WORKFLOW_ID_2}"
echo "Expected: Item 1 OK, Item 2 FAIL → full compensation (refund + cancel)"

echo ""
echo ""
echo "=== Check Results ==="
echo "Test 1 (all success): curl -sS \"${CONDUCTOR_BASE_URL}/workflow/${WORKFLOW_ID_1}\" | jq '{status: .status, tasks: [.tasks[] | .referenceTaskName]}'"
echo "Test 2 (partial fail): curl -sS \"${CONDUCTOR_BASE_URL}/workflow/${WORKFLOW_ID_2}\" | jq '{status: .status, tasks: [.tasks[] | .referenceTaskName]}'"
echo ""
echo "=== Database Check Commands ==="
echo "Order 1: docker exec order-db psql -U order_user -d order_db -c \"SELECT order_no, status FROM orders WHERE order_no = '${ORDER_NO_1}';\""
echo "Order 2: docker exec order-db psql -U order_user -d order_db -c \"SELECT order_no, status FROM orders WHERE order_no = '${ORDER_NO_2}';\""
echo "Payment 2: docker exec payment-db psql -U payment_user -d payment_db -c \"SELECT order_no, status FROM payments WHERE order_no = '${ORDER_NO_2}';\""
echo "Inventory 2: docker exec inventory-db psql -U inventory_user -d inventory_db -c \"SELECT order_no, product_id, quantity, status FROM inventory_reservations WHERE order_no = '${ORDER_NO_2}';\""
