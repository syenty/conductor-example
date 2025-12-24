# W1_basic_order

## 흐름 요약

- create_order: 주문 생성
- authorize_payment: 결제 승인
- reserve_inventory: 재고 예약
- decide_payment(DECISION): 결제 결과 확인
  - AUTHORIZED일 때 → decide_inventory(DECISION)
    - RESERVED일 때 → confirm_order
    - 그 외 → cancel_order → refund_payment
  - 그 외 → cancel_order

## 입력 파라미터

- orderNo
- totalAmount
- currency
- customerId
- paymentMethod
- paymentFailRate
- paymentDelayMs
- items
- forceOutOfStock
- partialFailIndex
