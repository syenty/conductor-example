# W5: Inventory Shortage Branch Workflow

## 개요
재고 부족 시 결제 취소 및 주문 취소 분기를 처리하는 워크플로우입니다. **결제 후 재고 확인** 패턴으로, 재고가 없으면 결제를 환불하고 주문을 취소합니다.

## 워크플로우 실행 순서

### 기본 흐름

```
1. create_order
   ↓
2. authorize_payment
   ↓
3. reserve_inventory
   ↓
4. decide_inventory (SWITCH)
   ├─ [RESERVED] → 5a. confirm_order ✅
   └─ [default/OUT_OF_STOCK] → 5b. refund_payment → 6b. cancel_order ❌
```

## Task 상세

### 1. create_order
- **타입**: SIMPLE
- **동작**: 주문 생성
- **입력 파라미터**:
  - orderNo: 주문번호
  - totalAmount: 총 금액
  - currency: 통화
  - customerId: 고객 ID
  - paymentMethod: 결제 수단
  - items: 주문 항목 리스트

### 2. authorize_payment
- **타입**: SIMPLE
- **동작**: 결제 승인
- **입력 파라미터**:
  - orderNo: 주문번호
  - amount: 결제 금액
  - currency: 통화
  - method: 결제 수단
  - failRate: 실패율 (테스트용)
  - delayMs: 지연 시간 (테스트용)

### 3. reserve_inventory
- **타입**: SIMPLE
- **동작**: 재고 예약 시도
- **입력 파라미터**:
  - orderNo: 주문번호
  - items: 주문 항목 리스트
  - forceOutOfStock: 재고 부족 강제 시뮬레이션 (테스트용)
  - partialFailIndex: 부분 실패 인덱스 (테스트용)
- **출력**:
  - status: "RESERVED" 또는 "OUT_OF_STOCK"

### 4. decide_inventory
- **타입**: SWITCH
- **동작**: 재고 상태에 따라 분기
- **분기 조건**: `reserve_inventory.output.status` 값
- **분기 경로**:
  - `RESERVED`: 재고 예약 성공 → 주문 확정
  - `default` (OUT_OF_STOCK): 재고 부족 → 환불 및 주문 취소

---

## 분기 A: 재고 예약 성공 (status = "RESERVED")

### 5a. confirm_order
- **타입**: SIMPLE
- **동작**: 주문 확정
- **입력 파라미터**:
  - orderNo: 주문번호
- **결과**: ✅ 워크플로우 완료

---

## 분기 B: 재고 부족 (status = "OUT_OF_STOCK")

### 5b. refund_payment
- **타입**: SIMPLE
- **동작**: 결제 환불
- **입력 파라미터**:
  - orderNo: 주문번호
  - reason: 환불 사유 ("Inventory out of stock")
- **결과**: Payment 상태를 REFUNDED로 변경

### 6b. cancel_order_inventory_failed
- **타입**: SIMPLE
- **동작**: 주문 취소
- **입력 파라미터**:
  - orderNo: 주문번호
  - reason: 취소 사유 ("Inventory out of stock")
- **결과**: ❌ 워크플로우 완료 (재고 부족으로 취소)

---

## 워크플로우 설정

- **schemaVersion**: 2
- **restartable**: true
- **workflowStatusListenerEnabled**: false
- **timeoutPolicy**: ALERT_ONLY
- **timeoutSeconds**: 120

## 실행 방법

### 워크플로우 등록

```bash
./scripts/register_workflows.sh
```

### 테스트 스크립트 실행

```bash
./scripts/scenarios/w5_inventory_branch.sh
```

이 스크립트는 2가지 테스트를 실행합니다:
1. **정상 케이스**: forceOutOfStock=false → 주문 확정
2. **재고 부족 케이스**: forceOutOfStock=true → 환불 + 주문 취소

### 개별 테스트 입력 예시

#### Test 1: 정상 흐름 (재고 있음)
```json
{
  "orderNo": "ORD-W5-001",
  "totalAmount": 10000,
  "currency": "KRW",
  "customerId": "CUST-001",
  "paymentMethod": "CARD",
  "paymentFailRate": 0.0,
  "paymentDelayMs": 0,
  "items": [
    {"productId": "PROD-001", "quantity": 2, "unitPrice": 5000}
  ],
  "forceOutOfStock": false,
  "partialFailIndex": -1
}
```

#### Test 2: 재고 부족
```json
{
  "orderNo": "ORD-W5-002",
  "totalAmount": 20000,
  "currency": "KRW",
  "customerId": "CUST-002",
  "paymentMethod": "CARD",
  "paymentFailRate": 0.0,
  "paymentDelayMs": 0,
  "items": [
    {"productId": "PROD-OUT-OF-STOCK", "quantity": 1, "unitPrice": 20000}
  ],
  "forceOutOfStock": true,
  "partialFailIndex": -1
}
```

## 핵심 포인트

1. **결제 후 재고 확인 패턴**: 결제를 먼저 승인받고, 재고 확인 후 문제 시 환불
2. **보상 트랜잭션**: 재고 부족 시 refund_payment → cancel_order 순서로 보상
3. **테스트 제어**: `forceOutOfStock` 파라미터로 재고 부족 상황 시뮬레이션

## W5 vs W8 (Saga) 비교

| 항목 | W5 (Inventory Branch) | W8 (Saga Inventory Fail) |
|------|----------------------|--------------------------|
| **패턴** | 조건 분기 | Saga 보상 패턴 |
| **재고 확인 시점** | reserve_inventory 결과로 분기 | reserve_inventory 실패 시 보상 |
| **처리 방식** | SWITCH로 분기 | 실패 시 자동 보상 트리거 |
| **적용 상황** | 재고 상태를 명시적으로 확인 | 시스템 오류/예외 상황 처리 |

## 시나리오별 실행 경로

### ✅ 재고 예약 성공 시나리오
```
create_order
→ authorize_payment (AUTHORIZED)
→ reserve_inventory (RESERVED)
→ decide_inventory [RESERVED]
→ confirm_order ✅
```
**결과**: Order=CONFIRMED, Payment=AUTHORIZED

### ❌ 재고 부족 시나리오
```
create_order
→ authorize_payment (AUTHORIZED)
→ reserve_inventory (OUT_OF_STOCK)
→ decide_inventory [default]
→ refund_payment
→ cancel_order_inventory_failed ❌
```
**결과**: Order=CANCELLED, Payment=REFUNDED

## Task 정의 파일

- 워크플로우: [workflows/W5_inventory_branch.json](W5_inventory_branch.json)
- Task 정의:
  - [tasks/create_order.json](../tasks/create_order.json)
  - [tasks/authorize_payment.json](../tasks/authorize_payment.json)
  - [tasks/reserve_inventory.json](../tasks/reserve_inventory.json)
  - [tasks/refund_payment.json](../tasks/refund_payment.json)
  - [tasks/confirm_order.json](../tasks/confirm_order.json)
  - [tasks/cancel_order.json](../tasks/cancel_order.json)

## 실무 활용 예시

1. **재고 부족 처리**:
   - 인기 상품 주문 시 결제는 성공했지만 재고 소진
   - 자동 환불 및 주문 취소로 고객 경험 개선

2. **예약 구매 시스템**:
   - 결제 후 재고 확보 여부 확인
   - 확보 실패 시 자동 환불

3. **플래시 세일**:
   - 동시 주문 시 재고 경합
   - 재고 확보 실패한 주문 자동 취소 및 환불
