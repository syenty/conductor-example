#!/usr/bin/env bash
set -euo pipefail

CONDUCTOR_BASE_URL="${CONDUCTOR_BASE_URL:-http://localhost:8080/api}"

curl -sS -X POST "${CONDUCTOR_BASE_URL}/workflow/basic_order" \
  -H "Content-Type: application/json" \
  -d '{
    "orderNo": "ORD-W1-001",
    "totalAmount": 12050,
    "currency": "KRW",
    "customerId": "CUST-001",
    "paymentMethod": "CARD",
    "paymentFailRate": 0.0,
    "paymentDelayMs": 0,
    "items": [
      { "productId": "P-1", "quantity": 2, "unitPrice": 5000 },
      { "productId": "P-2", "quantity": 1, "unitPrice": 2050 }
    ]
  }'
