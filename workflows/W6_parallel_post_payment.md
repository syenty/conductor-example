# W3: Parallel Processing Workflow

## 개요
결제 승인과 재고 확인을 병렬로 실행하여 처리 시간을 단축하는 워크플로우입니다. FORK/JOIN 패턴을 사용하여 성능을 최적화합니다.

## 워크플로우 실행 순서

### 기본 흐름

```
1. create_order
   ↓
2. FORK (병렬 실행)
   ├─ authorize_payment
   └─ check_inventory
   ↓
3. JOIN (두 작업 완료 대기)
   ↓
4. decide_parallel_result (분기점)
   ├─ [BOTH_SUCCESS] → 5a. reserve_inventory → 6a. confirm_order ✅
   ├─ [PAYMENT_FAILED] → 5b. cancel_order ❌
   ├─ [INVENTORY_FAILED] → 5c. refund_payment → 6c. cancel_order ❌
   └─ [BOTH_FAILED] → 5d. cancel_order ❌
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

### 2️⃣ parallel_tasks (FORK_JOIN)
- **타입**: FORK_JOIN
- **동작**: 결제 승인과 재고 확인을 병렬로 실행
- **병렬 실행 Task**:
  - **2a. authorize_payment**: 결제 승인
  - **2b. check_inventory**: 재고 가용성 확인 (예약 없음)

#### 2a. authorize_payment
- **타입**: SIMPLE
- **동작**: 결제 승인
- **입력 파라미터**:
  - orderNo: 주문번호
  - amount: 결제 금액
  - currency: 통화
  - method: 결제 수단
  - failRate: 실패율 (테스트용)
  - delayMs: 지연 시간 (테스트용)

#### 2b. check_inventory
- **타입**: SIMPLE
- **동작**: 재고 가용성 확인 (실제 예약은 하지 않음)
- **입력 파라미터**:
  - orderNo: 주문번호
  - items: 주문 항목 리스트
  - forceOutOfStock: 재고 부족 강제 시뮬레이션 (테스트용)
  - partialFailIndex: 부분 실패 인덱스 (테스트용)

### 3️⃣ JOIN
- **동작**: 두 병렬 작업(authorize_payment, check_inventory)이 모두 완료될 때까지 대기
- **자동 처리**: FORK_JOIN task에 포함되어 자동으로 처리됨

### 4️⃣ decide_parallel_result
- **타입**: DECISION
- **분기 조건**:
  ```javascript
  paymentStatus == 'AUTHORIZED' && inventoryStatus == 'AVAILABLE'
    ? 'BOTH_SUCCESS'
    : paymentStatus != 'AUTHORIZED' && inventoryStatus != 'AVAILABLE'
      ? 'BOTH_FAILED'
      : paymentStatus != 'AUTHORIZED'
        ? 'PAYMENT_FAILED'
        : 'INVENTORY_FAILED'
  ```
- **분기 경로**:
  - `BOTH_SUCCESS`: 결제 성공 + 재고 충분 → 재고 예약 및 주문 확정
  - `PAYMENT_FAILED`: 결제 실패 (재고는 OK) → 주문 취소
  - `INVENTORY_FAILED`: 재고 부족 (결제는 OK) → 환불 및 주문 취소
  - `BOTH_FAILED`: 둘 다 실패 → 주문 취소

---

## 분기 A: 둘 다 성공 (BOTH_SUCCESS)

### 5️⃣-A reserve_inventory
- **타입**: SIMPLE
- **동작**: 실제 재고 예약 (check_inventory는 확인만, reserve_inventory가 실제 예약)
- **입력 파라미터**:
  - orderNo: 주문번호
  - items: 주문 항목 리스트
  - forceOutOfStock: 재고 부족 강제 시뮬레이션 (테스트용)
  - partialFailIndex: 부분 실패 인덱스 (테스트용)

### 6️⃣-A confirm_order
- **타입**: SIMPLE
- **동작**: 주문 확정
- **입력 파라미터**:
  - orderNo: 주문번호
- **결과**: ✅ 워크플로우 완료

---

## 분기 B: 결제만 실패 (PAYMENT_FAILED)

### 5️⃣-B cancel_order
- **타입**: SIMPLE
- **taskReferenceName**: cancel_order_payment_failed
- **동작**: 주문 취소 (결제 실패 사유)
- **입력 파라미터**:
  - orderNo: 주문번호
- **결과**: ❌ 워크플로우 완료 (결제 실패)

---

## 분기 C: 재고만 부족 (INVENTORY_FAILED)

### 5️⃣-C refund_payment
- **타입**: SIMPLE
- **taskReferenceName**: refund_payment_inventory_failed
- **동작**: 결제 환불 (재고 부족으로 인한)
- **입력 파라미터**:
  - orderNo: 주문번호
  - reason: "inventory_not_available"

### 6️⃣-C cancel_order
- **타입**: SIMPLE
- **taskReferenceName**: cancel_order_inventory_failed
- **동작**: 주문 취소 (재고 부족 사유)
- **입력 파라미터**:
  - orderNo: 주문번호
- **결과**: ❌ 워크플로우 완료 (재고 부족으로 환불)

---

## 분기 D: 둘 다 실패 (BOTH_FAILED)

### 5️⃣-D cancel_order
- **타입**: SIMPLE
- **taskReferenceName**: cancel_order_both_failed
- **동작**: 주문 취소 (결제 및 재고 모두 실패)
- **입력 파라미터**:
  - orderNo: 주문번호
- **결과**: ❌ 워크플로우 완료 (전체 실패)

---

## 워크플로우 설정

- **schemaVersion**: 2
- **restartable**: true
- **workflowStatusListenerEnabled**: false
- **timeoutPolicy**: ALERT_ONLY
- **timeoutSeconds**: 300

## 실행 방법

```bash
./scripts/scenarios/w3_parallel_processing.sh
```

### 입력 예시

```json
{
  "orderNo": "ORD-W3-001",
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
}
```

## 핵심 포인트

1. **병렬 처리**: 결제 승인과 재고 확인을 동시에 실행하여 처리 시간 단축
2. **FORK/JOIN 패턴**: Conductor의 FORK_JOIN task를 사용한 병렬 실행
3. **복합 조건 분기**: 4가지 케이스(둘 다 성공/결제 실패/재고 부족/둘 다 실패)를 처리
4. **2단계 재고 처리**:
   - `check_inventory`: 재고 가용성만 확인 (병렬 실행용)
   - `reserve_inventory`: 실제 재고 예약 (확정 전)

## 성능 비교

### W1 (순차 처리)
```
create_order (100ms) → authorize_payment (1000ms) → reserve_inventory (500ms)
총 소요 시간: ~1600ms
```

### W3 (병렬 처리)
```
create_order (100ms) → [authorize_payment (1000ms) || check_inventory (500ms)] → reserve_inventory (500ms)
총 소요 시간: ~1600ms → ~1100ms (약 30% 단축)
```

## 시나리오별 실행 경로

### ✅ 정상 시나리오 (결제 성공 + 재고 충분)
```
create_order
→ FORK [authorize_payment || check_inventory]
→ JOIN
→ decide_parallel_result [BOTH_SUCCESS]
→ reserve_inventory
→ confirm_order ✅
```

### ❌ 결제 실패 시나리오
```
create_order
→ FORK [authorize_payment (FAILED) || check_inventory (AVAILABLE)]
→ JOIN
→ decide_parallel_result [PAYMENT_FAILED]
→ cancel_order_payment_failed ❌
```

### ❌ 재고 부족 시나리오
```
create_order
→ FORK [authorize_payment (AUTHORIZED) || check_inventory (OUT_OF_STOCK)]
→ JOIN
→ decide_parallel_result [INVENTORY_FAILED]
→ refund_payment_inventory_failed
→ cancel_order_inventory_failed ❌
```

### ❌ 둘 다 실패 시나리오
```
create_order
→ FORK [authorize_payment (FAILED) || check_inventory (OUT_OF_STOCK)]
→ JOIN
→ decide_parallel_result [BOTH_FAILED]
→ cancel_order_both_failed ❌
```

## Task 정의 파일

- 워크플로우: [workflows/W3_parallel_processing.json](../../workflows/W3_parallel_processing.json)
- Task 정의:
  - [tasks/create_order.json](../../tasks/create_order.json)
  - [tasks/authorize_payment.json](../../tasks/authorize_payment.json)
  - [tasks/check_inventory.json](../../tasks/check_inventory.json) (신규)
  - [tasks/reserve_inventory.json](../../tasks/reserve_inventory.json)
  - [tasks/confirm_order.json](../../tasks/confirm_order.json)
  - [tasks/cancel_order.json](../../tasks/cancel_order.json)
  - [tasks/refund_payment.json](../../tasks/refund_payment.json)

## W1 vs W3 비교

| 항목 | W1 (Basic Order) | W3 (Parallel Processing) |
|------|------------------|--------------------------|
| **실행 방식** | 순차 처리 | 병렬 처리 (FORK/JOIN) |
| **결제/재고** | 순차 실행 | 동시 실행 |
| **처리 시간** | 느림 (순차) | 빠름 (병렬) |
| **재고 처리** | reserve_inventory 1회 | check_inventory + reserve_inventory |
| **분기 수** | 2단계 (결제 → 재고) | 1단계 (4-way 분기) |
| **복잡도** | 낮음 | 중간 |
| **사용 사례** | 단순 주문 처리 | 고성능 주문 처리 |
