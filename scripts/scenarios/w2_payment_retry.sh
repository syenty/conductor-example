#!/usr/bin/env bash
set -euo pipefail

CONDUCTOR_BASE_URL="${CONDUCTOR_BASE_URL:-http://localhost:8080/api}"

curl -sS -X POST "${CONDUCTOR_BASE_URL}/workflow/payment_retry" \
  -H "Content-Type: application/json" \
  -d '{
    "orderNo": "ORD-W2-001",
    "totalAmount": 10000,
    "currency": "KRW",
    "customerId": "CUST-001",
    "paymentMethod": "CARD",
    "paymentFailRate": 0.6,
    "items": [
      {"productId": "PROD-001", "quantity": 2, "unitPrice": 5000}
    ]
  }'
