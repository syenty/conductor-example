#!/usr/bin/env bash
set -euo pipefail

CONDUCTOR_BASE_URL="${CONDUCTOR_BASE_URL:-http://localhost:8080/api}"

curl -sS -X POST "${CONDUCTOR_BASE_URL}/workflow/parallel_post_payment" \
  -H "Content-Type: application/json" \
  -d '{
    "orderNo": "ORD-W6-001",
    "totalAmount": 15000,
    "currency": "KRW",
    "customerId": "CUST-001",
    "paymentMethod": "CARD",
    "paymentFailRate": 0.0,
    "paymentDelayMs": 1000,
    "items": [
      {"productId": "PROD-001", "quantity": 3, "unitPrice": 5000}
    ],
    "forceOutOfStock": false,
    "partialFailIndex": -1
  }'
