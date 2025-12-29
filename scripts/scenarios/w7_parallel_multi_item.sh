#!/usr/bin/env bash
set -euo pipefail

CONDUCTOR_BASE_URL="${CONDUCTOR_BASE_URL:-http://localhost:8080/api}"
TIMESTAMP=$(date +%s)

echo "=== W7 Parallel Multi Item Test ==="
echo ""

# Test 1: Multi item success (2 items)
echo "Test 1: Multi item success (2 items)"
ORDER_NO_1="ORD-W7-${TIMESTAMP}-1"
echo "Order No: ${ORDER_NO_1}"
WORKFLOW_ID_1=$(curl -sS -X POST "${CONDUCTOR_BASE_URL}/workflow/parallel_multi_item" \
  -H "Content-Type: application/json" \
  -d "{
    \"orderNo\": \"${ORDER_NO_1}\",
    \"totalAmount\": 25000,
    \"currency\": \"KRW\",
    \"customerId\": \"CUST-001\",
    \"paymentMethod\": \"CARD\",
    \"paymentFailRate\": 0.0,
    \"paymentDelayMs\": 1000,
    \"items\": [
      {\"productId\": \"PROD-001\", \"quantity\": 2, \"unitPrice\": 5000},
      {\"productId\": \"PROD-002\", \"quantity\": 3, \"unitPrice\": 5000}
    ],
    \"forceOutOfStock\": false,
    \"partialFailIndex\": -1
  }" | tr -d '"')
echo "Workflow ID: ${WORKFLOW_ID_1}"
echo "Expected: BOTH_SUCCESS → reserve 2 items → confirm_order"

echo ""
echo ""
echo "=== Check Results ==="
echo "Test 1 (multi item success): curl -sS \"${CONDUCTOR_BASE_URL}/workflow/${WORKFLOW_ID_1}\" | jq '{status: .status, output: .output}'"
echo ""
echo "=== Database Check Commands ==="
echo "Orders: docker exec order-db psql -U order_user -d order_db -c \"SELECT order_no, status FROM orders WHERE order_no = '${ORDER_NO_1}';\""
echo "Payments: docker exec payment-db psql -U payment_user -d payment_db -c \"SELECT order_no, status FROM payments WHERE order_no = '${ORDER_NO_1}';\""
echo "Inventory: docker exec inventory-db psql -U inventory_user -d inventory_db -c \"SELECT order_no, product_id, quantity, status FROM inventory_reservations WHERE order_no = '${ORDER_NO_1}';\""
