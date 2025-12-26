# W1: Basic Order Workflow

## 개요
기본 주문 처리 워크플로우입니다. 주문 생성, 결제 승인, 재고 예약을 순차적으로 진행하고, 각 단계의 결과에 따라 주문을 확정하거나 취소합니다.

## 워크플로우 실행 순서

### 기본 흐름

```
1. create_order
   ↓
2. authorize_payment
   ↓
3. reserve_inventory
   ↓
4. decide_payment (분기점)
   ├─ [AUTHORIZED] → 5a. decide_inventory (분기점)
   │                  ├─ [RESERVED] → 6a. confirm_order ✅
   │                  └─ [기타] → 6b. cancel_order → 7b. refund_payment ❌
   └─ [기타] → 5b. cancel_order ❌
```

## Task 상세

### 1️⃣ create_order
- **타입**: SIMPLE
- **동작**: 주문 생성
- **입력 파라미터**:
  - orderNo: 주문번호
  - totalAmount: 총 금액
  - currency: 통화
  - customerId: 고객 ID
  - paymentMethod: 결제 수단
  - items: 주문 항목 리스트

### 2️⃣ authorize_payment
- **타입**: SIMPLE
- **동작**: 결제 승인
- **입력 파라미터**:
  - orderNo: 주문번호
  - amount: 결제 금액
  - currency: 통화
  - method: 결제 수단
  - failRate: 실패율 (테스트용)
  - delayMs: 지연 시간 (테스트용)

### 3️⃣ reserve_inventory
- **타입**: SIMPLE
- **동작**: 재고 예약
- **입력 파라미터**:
  - orderNo: 주문번호
  - items: 주문 항목 리스트
  - forceOutOfStock: 재고 부족 강제 시뮬레이션 (테스트용)
  - partialFailIndex: 부분 실패 인덱스 (테스트용)

### 4️⃣ decide_payment
- **타입**: DECISION
- **분기 조건**: `authorize_payment.output.status` 값
- **분기 경로**:
  - `AUTHORIZED`: 결제 성공 → 재고 상태 확인
  - 기타 (default): 결제 실패 → 주문 취소

---

## 분기 A: 결제 성공 시 (status = "AUTHORIZED")

### 5️⃣-A decide_inventory
- **타입**: DECISION
- **분기 조건**: `reserve_inventory.output.status` 값
- **분기 경로**:
  - `RESERVED`: 재고 예약 성공 → 주문 확정
  - 기타 (default): 재고 부족/실패 → 주문 취소 및 환불

#### 분기 A-1: 재고 예약 성공 (status = "RESERVED")

##### 6️⃣-A confirm_order
- **타입**: SIMPLE
- **동작**: 주문 확정
- **입력 파라미터**:
  - orderNo: 주문번호
- **결과**: ✅ 워크플로우 완료

#### 분기 A-2: 재고 예약 실패 (status ≠ "RESERVED")

##### 6️⃣-B cancel_order
- **타입**: SIMPLE
- **taskReferenceName**: cancel_order_inventory
- **동작**: 주문 취소 (재고 부족 사유)
- **입력 파라미터**:
  - orderNo: 주문번호

##### 7️⃣-B refund_payment
- **타입**: SIMPLE
- **taskReferenceName**: refund_payment_inventory
- **동작**: 결제 환불
- **입력 파라미터**:
  - orderNo: 주문번호
  - reason: "inventory_failed"
- **결과**: ❌ 워크플로우 완료 (재고 부족으로 환불)

---

## 분기 B: 결제 실패 시 (status ≠ "AUTHORIZED")

### 5️⃣-B cancel_order
- **타입**: SIMPLE
- **taskReferenceName**: cancel_order_payment
- **동작**: 주문 취소 (결제 실패 사유)
- **입력 파라미터**:
  - orderNo: 주문번호
- **결과**: ❌ 워크플로우 완료 (결제 실패)

---

## 워크플로우 설정

- **schemaVersion**: 2
- **restartable**: true (기본값)

## 실행 방법

```bash
./scripts/scenarios/w1_basic_order.sh
```

### 입력 예시

```json
{
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
}
```

## 핵심 포인트

1. **순차 처리**: 주문 생성 → 결제 승인 → 재고 예약을 순서대로 실행
2. **이중 분기**: 결제와 재고 상태를 각각 확인하여 분기 처리
3. **자동 환불**: 재고 부족 시 승인된 결제를 자동으로 환불
4. **명확한 실패 처리**: 결제 실패와 재고 부족을 구분하여 처리

## 시나리오별 실행 경로

### ✅ 정상 시나리오 (결제 성공 + 재고 충분)
```
create_order → authorize_payment → reserve_inventory
→ decide_payment [AUTHORIZED]
→ decide_inventory [RESERVED]
→ confirm_order ✅
```

### ❌ 결제 실패 시나리오
```
create_order → authorize_payment → reserve_inventory
→ decide_payment [FAILED]
→ cancel_order_payment ❌
```

### ❌ 재고 부족 시나리오 (결제 성공했으나 재고 부족)
```
create_order → authorize_payment → reserve_inventory
→ decide_payment [AUTHORIZED]
→ decide_inventory [OUT_OF_STOCK]
→ cancel_order_inventory → refund_payment_inventory ❌
```

## Task 정의 파일

- 워크플로우: [workflows/W1_basic_order.json](../../workflows/W1_basic_order.json)
- Task 정의:
  - [tasks/create_order.json](../../tasks/create_order.json)
  - [tasks/authorize_payment.json](../../tasks/authorize_payment.json)
  - [tasks/reserve_inventory.json](../../tasks/reserve_inventory.json)
  - [tasks/confirm_order.json](../../tasks/confirm_order.json)
  - [tasks/cancel_order.json](../../tasks/cancel_order.json)
  - [tasks/refund_payment.json](../../tasks/refund_payment.json)
