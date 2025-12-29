# W9: Saga Pattern - Partial Inventory Fail

## 개요
**Saga 패턴**을 사용하여 **여러 품목 중 일부만 재고 부족**인 경우에도 전체 보상 트랜잭션을 실행하는 워크플로우입니다.

## W8 vs W9 차이점

| 항목 | W8 (Inventory Fail) | W9 (Partial Inventory Fail) |
|------|---------------------|----------------------------|
| **실패 케이스** | 전체 재고 부족 (`forceOutOfStock=true`) | 일부 품목만 재고 부족 (`partialFailIndex=1`) |
| **주문 품목 수** | 단일 또는 다수 | 다수 품목 (2개 이상) |
| **실패 트리거** | 모든 품목 OUT_OF_STOCK | 특정 인덱스 품목만 OUT_OF_STOCK |
| **워크플로우 구조** | 동일 | 동일 |
| **핵심 차이** | "전체 실패" 시나리오 | "부분 실패" 시나리오 |

## 워크플로우 구조

W8과 동일한 구조를 사용합니다:

```
1. create_order
   ↓
2. authorize_payment
   ↓
3. reserve_inventory (일부 품목 실패!)
   ↓ (optional: true로 워크플로우는 계속)
   ↓
4. decide_saga_compensation
   ↓ reserveStatus == 'FAILED'
   ↓
   FAILED 브랜치:
   ├─ refund_payment (전액 환불)
   └─ cancel_order (주문 전체 취소)
```

## 핵심 개념: Partial Fail

### partialFailIndex 파라미터

```json
{
  "items": [
    {"productId": "PROD-001", "quantity": 2, "unitPrice": 10000},  // index 0
    {"productId": "PROD-002", "quantity": 3, "unitPrice": 5000}    // index 1
  ],
  "partialFailIndex": 1  // items[1] (PROD-002)만 재고 부족으로 설정
}
```

- `partialFailIndex=-1`: 모든 품목 정상 (또는 forceOutOfStock에 따름)
- `partialFailIndex=0`: items[0] 품목만 재고 부족
- `partialFailIndex=1`: items[1] 품목만 재고 부족
- `partialFailIndex=2`: items[2] 품목만 재고 부족

### Inventory Service의 처리 로직

```java
// InventoryService.reserve()
for (int i = 0; i < items.size(); i++) {
    ReservationItemRequest itemRequest = items.get(i);

    // partialFailIndex와 일치하면 재고 부족으로 처리
    if (request.partialFailIndex() == i) {
        status = "OUT_OF_STOCK";
        anyFailed = true;
    } else {
        // 정상적으로 재고 체크 및 예약
        InventoryItem item = inventoryRepository.findByProductId(itemRequest.productId()).orElse(null);
        if (item == null || available < itemRequest.quantity()) {
            status = "OUT_OF_STOCK";
            anyFailed = true;
        } else {
            // 재고 예약 성공
            status = "RESERVED";
        }
    }
}

// 하나라도 실패하면 전체 실패
String overallStatus = anyFailed ? "FAILED" : "RESERVED";
```

## 실행 방법

```bash
./scripts/scenarios/w9_saga_partial_fail.sh
```

## 입력 예시

### 테스트 1: 모든 품목 재고 충분 (성공)
```json
{
  "orderNo": "ORD-W9-001",
  "totalAmount": 35000,
  "currency": "KRW",
  "customerId": "CUST-001",
  "paymentMethod": "CARD",
  "items": [
    {"productId": "PROD-001", "quantity": 2, "unitPrice": 10000},
    {"productId": "PROD-002", "quantity": 3, "unitPrice": 5000}
  ],
  "partialFailIndex": -1
}
```

### 테스트 2: 일부 품목만 재고 부족 (보상)
```json
{
  "orderNo": "ORD-W9-002",
  "totalAmount": 35000,
  "currency": "KRW",
  "customerId": "CUST-002",
  "paymentMethod": "CARD",
  "items": [
    {"productId": "PROD-001", "quantity": 2, "unitPrice": 10000},
    {"productId": "PROD-002", "quantity": 3, "unitPrice": 5000}
  ],
  "partialFailIndex": 1
}
```

## 시나리오별 실행 경로

### ✅ 정상 시나리오 (모든 품목 재고 OK)
```
create_order (COMPLETED)
→ authorize_payment (COMPLETED)
→ reserve_inventory (COMPLETED)
  - items[0] PROD-001: RESERVED
  - items[1] PROD-002: RESERVED
→ decide_saga_compensation → SUCCESS
→ confirm_order (COMPLETED)
→ Workflow: COMPLETED ✅
```

**결과**: Order=CONFIRMED, Payment=AUTHORIZED, Inventory=RESERVED (모든 품목)

### ❌ 부분 실패 시나리오 (일부 품목 재고 부족)
```
create_order (COMPLETED)
→ authorize_payment (COMPLETED)
→ reserve_inventory (FAILED!) ❌
  - items[0] PROD-001: (예약 시도 안 함)
  - items[1] PROD-002: OUT_OF_STOCK
  - overallStatus: FAILED
→ decide_saga_compensation → FAILED
→ refund_payment_saga (COMPLETED)
→ cancel_order_saga (COMPLETED)
→ Workflow: COMPLETED (with compensation) ✅
```

**결과**: Order=CANCELED, Payment=REFUNDED, Inventory=(일부 예약 실패로 전체 예약 안 됨)

## 실무적 의미

### 왜 일부 실패 시 전체를 보상해야 하는가?

1. **원자성(Atomicity)**: "모두 성공" 또는 "모두 실패"
   - 주문은 모든 품목이 함께 배송되어야 함
   - 일부만 배송하면 고객 불만 발생

2. **재고 일관성**: 부분 예약은 재고 관리 복잡도를 증가
   - 예약된 일부 품목을 다시 풀어야 함
   - 차라리 전체 취소 후 재주문이 간단

3. **사용자 경험**: 명확한 실패 처리
   - "일부 품목만 주문됩니다" → 혼란
   - "재고 부족으로 전체 취소" → 명확

### 대안: 부분 성공 허용

일부 실패를 허용하고 싶다면:
- W9 대신 다른 워크플로우 설계 필요
- 각 품목별로 개별 주문 생성
- 또는 "부분 주문 확정" 로직 추가

## W8 vs W9 사용 시나리오

**W8 사용 예**:
- 단일 품목 주문
- 전체 재고 부족 상황 (품절 상품)
- forceOutOfStock=true로 테스트

**W9 사용 예**:
- 다수 품목 주문 (장바구니)
- 일부 품목만 재고 부족
- partialFailIndex로 특정 품목 실패 테스트
- 실무: "모두 아니면 전무(All-or-Nothing)" 정책 검증

## 핵심 포인트

1. **부분 실패도 전체 실패로 처리**: 트랜잭션 원자성 보장
2. **partialFailIndex**: 테스트용 파라미터로 특정 품목 실패 시뮬레이션
3. **Saga 보상**: 일부 실패 시에도 전체 보상 트랜잭션 수행
4. **실무 적용**: 장바구니 주문에서 일부 품목 품절 시 전체 주문 취소 정책

## Task 정의 파일

- 메인 워크플로우: [W9_saga_partial_fail.json](W9_saga_partial_fail.json)
- 보상 워크플로우: [W8_saga_inventory_compensation.json](W8_saga_inventory_compensation.json) (W8과 공유)
- Task 정의: W8과 동일한 Task 사용
