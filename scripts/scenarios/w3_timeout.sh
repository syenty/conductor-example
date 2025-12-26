#!/usr/bin/env bash
set -euo pipefail

CONDUCTOR_BASE_URL="${CONDUCTOR_BASE_URL:-http://localhost:8080/api}"
TIMESTAMP=$(date +%s)

echo "=== W3 Timeout Handling Test ==="
echo ""
echo "Test 1: Normal payment (delayMs=2000, timeout=10s)"
ORDER_NO_1="ORD-W3-${TIMESTAMP}-1"
echo "Order No: ${ORDER_NO_1}"
curl -sS -X POST "${CONDUCTOR_BASE_URL}/workflow/timeout_handling" \
  -H "Content-Type: application/json" \
  -d "{
    \"orderNo\": \"${ORDER_NO_1}\",
    \"totalAmount\": 10000,
    \"currency\": \"KRW\",
    \"customerId\": \"CUST-001\",
    \"paymentMethod\": \"CARD\",
    \"paymentFailRate\": 0.0,
    \"paymentDelayMs\": 2000,
    \"items\": [
      {\"productId\": \"PROD-001\", \"quantity\": 2, \"unitPrice\": 5000}
    ],
    \"forceOutOfStock\": false,
    \"partialFailIndex\": -1
  }"

echo ""
echo ""
echo "Test 2: Timeout payment (delayMs=12000, timeout=10s) - Should FAIL with TIMEOUT"
ORDER_NO_2="ORD-W3-${TIMESTAMP}-2"
echo "Order No: ${ORDER_NO_2}"
curl -sS -X POST "${CONDUCTOR_BASE_URL}/workflow/timeout_handling" \
  -H "Content-Type: application/json" \
  -d "{
    \"orderNo\": \"${ORDER_NO_2}\",
    \"totalAmount\": 10000,
    \"currency\": \"KRW\",
    \"customerId\": \"CUST-001\",
    \"paymentMethod\": \"CARD\",
    \"paymentFailRate\": 0.0,
    \"paymentDelayMs\": 12000,
    \"items\": [
      {\"productId\": \"PROD-001\", \"quantity\": 2, \"unitPrice\": 5000}
    ],
    \"forceOutOfStock\": false,
    \"partialFailIndex\": -1
  }"
