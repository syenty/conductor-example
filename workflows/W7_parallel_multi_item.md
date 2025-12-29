# W7: Parallel Multi Item Workflow

## 개요
하나의 주문에 여러 품목이 있을 때, 재고 예약을 처리하는 워크플로우입니다. W6과 동일한 구조이지만, **여러 품목을 동시에 처리**하는 시나리오입니다.

## 워크플로우 실행 순서

```
1. create_order (여러 품목 포함)
   ↓
2. FORK (병렬 실행)
   ├─ authorize_payment
   └─ check_inventory (여러 품목 확인)
   ↓
3. JOIN
   ↓
4. decide_parallel_result
   └─ [BOTH_SUCCESS] → reserve_inventory (여러 품목 예약) → confirm_order ✅
```

## W6과의 차이점

| 항목 | W6 | W7 |
|------|----|----|
| **품목 수** | 단일 품목 (1개) | 다중 품목 (2개 이상) |
| **워크플로우** | 동일 | 동일 |
| **목적** | 결제/재고 병렬 처리 | 여러 품목 동시 주문 |

## 핵심 포인트

W7은 **W6과 동일한 워크플로우**를 사용합니다. 차이점은:
- `items` 배열에 여러 품목을 포함
- `check_inventory`와 `reserve_inventory`가 모든 품목을 처리

## 실행 방법

```bash
./scripts/scenarios/w7_parallel_multi_item.sh
```

## 입력 예시

```json
{
  "orderNo": "ORD-W7-001",
  "totalAmount": 25000,
  "currency": "KRW",
  "customerId": "CUST-001",
  "paymentMethod": "CARD",
  "paymentFailRate": 0.0,
  "paymentDelayMs": 1000,
  "items": [
    {"productId": "PROD-001", "quantity": 2, "unitPrice": 5000},
    {"productId": "PROD-002", "quantity": 3, "unitPrice": 5000}
  ],
  "forceOutOfStock": false,
  "partialFailIndex": -1
}
```

## 재고 처리 방식

### check_inventory
- PROD-001 재고 확인: 2개 필요 → AVAILABLE
- PROD-002 재고 확인: 3개 필요 → AVAILABLE
- 결과: 모두 AVAILABLE이면 "AVAILABLE" 반환

### reserve_inventory
- PROD-001 재고 예약: 2개 예약 → inventory_reservations 테이블에 기록
- PROD-002 재고 예약: 3개 예약 → inventory_reservations 테이블에 기록
- 결과: 모두 성공하면 "RESERVED" 반환

## 실행 결과 예상

**성공 케이스:**
- Order: CONFIRMED
- Payment: AUTHORIZED
- Inventory Reservations: 2건 (PROD-001, PROD-002 각각)

## W7이 W6을 재사용하는 이유

실무에서는 **품목별로 별도의 태스크를 만들지 않습니다**:

1. **복잡도**: 품목 개수가 동적이므로 FORK_JOIN_DYNAMIC 사용 필요
2. **트랜잭션**: 여러 품목을 하나의 트랜잭션으로 처리하는 것이 안전
3. **성능**: 재고 예약은 매우 빠른 작업이라 병렬화 이득이 적음

따라서 W7은 W6 워크플로우를 그대로 사용하고, 입력만 다중 품목으로 변경합니다.
