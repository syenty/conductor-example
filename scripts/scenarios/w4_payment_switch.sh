#!/usr/bin/env bash
set -euo pipefail

CONDUCTOR_BASE_URL="${CONDUCTOR_BASE_URL:-http://localhost:8080/api}"
TIMESTAMP=$(date +%s)

echo "=== W4 Payment Method Switch Test ==="
echo ""

# Test 1: CARD payment - proceeds immediately
echo "Test 1: CARD payment (immediate processing)"
ORDER_NO_1="ORD-W4-${TIMESTAMP}-1"
echo "Order No: ${ORDER_NO_1}"
WORKFLOW_ID_1=$(curl -sS -X POST "${CONDUCTOR_BASE_URL}/workflow/payment_method_switch" \
  -H "Content-Type: application/json" \
  -d "{
    \"orderNo\": \"${ORDER_NO_1}\",
    \"totalAmount\": 10000,
    \"currency\": \"KRW\",
    \"customerId\": \"CUST-001\",
    \"paymentMethod\": \"CARD\",
    \"paymentFailRate\": 0.0,
    \"paymentDelayMs\": 1000,
    \"items\": [
      {\"productId\": \"PROD-001\", \"quantity\": 2, \"unitPrice\": 5000}
    ],
    \"forceOutOfStock\": false,
    \"partialFailIndex\": -1
  }" | tr -d '"')
echo "Workflow ID: ${WORKFLOW_ID_1}"
echo "Expected: Order confirmed immediately after card authorization"

echo ""
echo ""

# Test 2: BANK_TRANSFER payment - waits for deposit confirmation
echo "Test 2: BANK_TRANSFER payment (waits for deposit)"
ORDER_NO_2="ORD-W4-${TIMESTAMP}-2"
echo "Order No: ${ORDER_NO_2}"
WORKFLOW_ID_2=$(curl -sS -X POST "${CONDUCTOR_BASE_URL}/workflow/payment_method_switch" \
  -H "Content-Type: application/json" \
  -d "{
    \"orderNo\": \"${ORDER_NO_2}\",
    \"totalAmount\": 50000,
    \"currency\": \"KRW\",
    \"customerId\": \"CUST-002\",
    \"paymentMethod\": \"BANK_TRANSFER\",
    \"paymentFailRate\": 0.0,
    \"paymentDelayMs\": 0,
    \"items\": [
      {\"productId\": \"PROD-002\", \"quantity\": 1, \"unitPrice\": 50000}
    ],
    \"forceOutOfStock\": false,
    \"partialFailIndex\": -1
  }" | tr -d '"')
echo "Workflow ID: ${WORKFLOW_ID_2}"
echo "Expected: Workflow paused at wait_for_bank_deposit task"

echo ""
echo ""
echo "=== To complete BANK_TRANSFER workflow ==="
echo "Run the following command to signal deposit confirmation:"
echo ""
echo "curl -X POST \"${CONDUCTOR_BASE_URL}/tasks/wait_for_bank_deposit/complete\" \\"
echo "  -H \"Content-Type: application/json\" \\"
echo "  -d '{\"workflowId\": \"${WORKFLOW_ID_2}\", \"taskRefName\": \"wait_for_bank_deposit\", \"output\": {\"deposited\": true}}'"
echo ""
echo "Or use: ./scripts/scenarios/w4_complete_deposit.sh ${WORKFLOW_ID_2}"
