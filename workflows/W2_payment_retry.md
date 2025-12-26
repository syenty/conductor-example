# W2: Payment Retry Workflow

## 개요
결제 승인 실패 시 exponential backoff 전략으로 자동 재시도하는 워크플로우입니다.

## 워크플로우 실행 순서

### 기본 흐름

```
1. create_order
   ↓
2. authorize_payment (최대 4회 시도: 초기 1회 + retry 3회)
   ↓
3. decide_payment (분기점)
   ├─ [AUTHORIZED] → 4a. reserve_inventory → 5a. confirm_order ✅
   └─ [기타 상태] → 4b. cancel_order_payment_failed ❌
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
- **동작**: 결제 승인 시도
- **입력 파라미터**:
  - orderNo: 주문번호
  - amount: 결제 금액
  - currency: 통화
  - method: 결제 수단
  - failRate: 실패율 (테스트용)
  - delayMs: 지연 시간 (테스트용)
- **Retry 설정**:
  - `retryCount`: 3
  - `retryLogic`: EXPONENTIAL_BACKOFF
  - `retryDelaySeconds`: 2
  - `backoffScaleFactor`: 1.5
  - **재시도 간격**:
    - 1차 retry: 2초 후
    - 2차 retry: 3초 후 (2 × 1.5)
    - 3차 retry: 4.5초 후 (3 × 1.5)

### 3️⃣ decide_payment
- **타입**: DECISION
- **분기 조건**: `authorize_payment.output.status` 값
- **분기 경로**:
  - `AUTHORIZED`: 결제 성공 → 재고 예약 및 주문 확정
  - 기타 (default): 결제 실패 → 주문 취소

---

## 분기 A: 결제 성공 시 (status = "AUTHORIZED")

### 4️⃣-A reserve_inventory
- **타입**: SIMPLE
- **동작**: 재고 예약
- **입력 파라미터**:
  - orderNo: 주문번호
  - items: 주문 항목 리스트
  - forceOutOfStock: 재고 부족 강제 시뮬레이션 (테스트용)
  - partialFailIndex: 부분 실패 인덱스 (테스트용)

### 5️⃣-A confirm_order
- **타입**: SIMPLE
- **동작**: 주문 확정
- **입력 파라미터**:
  - orderNo: 주문번호
- **결과**: ✅ 워크플로우 완료

---

## 분기 B: 결제 실패 시 (status ≠ "AUTHORIZED")

### 4️⃣-B cancel_order_payment_failed
- **타입**: SIMPLE
- **동작**: 주문 취소 (결제 실패 사유)
- **입력 파라미터**:
  - orderNo: 주문번호
- **결과**: ❌ 워크플로우 완료

---

## 워크플로우 설정

- **timeoutPolicy**: ALERT_ONLY
- **timeoutSeconds**: 300
- **restartable**: true
- **schemaVersion**: 2

## 실행 방법

```bash
./scripts/scenarios/w2_payment_retry.sh
```

### 입력 예시

```json
{
  "orderNo": "ORD-W2-024",
  "totalAmount": 10000,
  "currency": "KRW",
  "customerId": "CUST-001",
  "paymentMethod": "CARD",
  "paymentFailRate": 0.6,
  "items": [
    {"productId": "PROD-001", "quantity": 2, "unitPrice": 5000}
  ]
}
```

## 핵심 포인트

1. **자동 재시도**: 결제 승인 실패 시 exponential backoff으로 최대 4번 시도
2. **조건부 분기**: 결제 성공 여부에 따라 다른 경로로 진행
3. **재고 예약**: 결제 성공 시에만 재고 예약 및 주문 확정 진행
4. **빠른 실패 처리**: 결제 최종 실패 시 즉시 주문 취소

## Task 정의 파일

- 워크플로우: [workflows/W2_payment_retry.json](../../workflows/W2_payment_retry.json)
- Task 정의:
  - [tasks/create_order.json](../../tasks/create_order.json)
  - [tasks/authorize_payment.json](../../tasks/authorize_payment.json)
  - [tasks/reserve_inventory.json](../../tasks/reserve_inventory.json)
  - [tasks/confirm_order.json](../../tasks/confirm_order.json)
  - [tasks/cancel_order.json](../../tasks/cancel_order.json)
