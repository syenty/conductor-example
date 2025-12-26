#!/usr/bin/env bash
set -euo pipefail

CONDUCTOR_BASE_URL="${CONDUCTOR_BASE_URL:-http://localhost:8080/api}"

echo "=== W3 Timeout Handling Test ==="
echo ""
echo "Test 1: Normal payment (delayMs=1000, timeout=5s)"
curl -sS -X POST "${CONDUCTOR_BASE_URL}/workflow/timeout_handling" \
  -H "Content-Type: application/json" \
  -d '{
    "orderNo": "ORD-W3-001",
    "totalAmount": 10000,
    "currency": "KRW",
    "customerId": "CUST-001",
    "paymentMethod": "CARD",
    "paymentFailRate": 0.0,
    "paymentDelayMs": 1000,
    "items": [
      {"productId": "PROD-001", "quantity": 2, "unitPrice": 5000}
    ],
    "forceOutOfStock": false,
    "partialFailIndex": -1
  }'

echo ""
echo ""
echo "Test 2: Timeout payment (delayMs=6000, timeout=5s) - Should FAIL with TIMEOUT"
curl -sS -X POST "${CONDUCTOR_BASE_URL}/workflow/timeout_handling" \
  -H "Content-Type: application/json" \
  -d '{
    "orderNo": "ORD-W3-002",
    "totalAmount": 10000,
    "currency": "KRW",
    "customerId": "CUST-001",
    "paymentMethod": "CARD",
    "paymentFailRate": 0.0,
    "paymentDelayMs": 6000,
    "items": [
      {"productId": "PROD-001", "quantity": 2, "unitPrice": 5000}
    ],
    "forceOutOfStock": false,
    "partialFailIndex": -1
  }'
