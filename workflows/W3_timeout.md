# W3: Timeout Handling Workflow

## 개요
지연된 결제 요청을 timeout으로 실패 처리하는 워크플로우입니다. Task 레벨의 timeout 설정을 통해 응답이 늦은 작업을 자동으로 실패 처리합니다.

## 워크플로우 실행 순서

### 기본 흐름

```
1. create_order
   ↓
2. authorize_payment (timeout: 5초, responseTimeout: 3초)
   ↓
3. decide_payment (분기점)
   ├─ [AUTHORIZED] → 4a. reserve_inventory → 5a. confirm_order ✅
   └─ [기타 또는 TIMEOUT] → 4b. cancel_order ❌
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

### 2️⃣ authorize_payment (with Timeout)
- **타입**: SIMPLE
- **동작**: 결제 승인 (timeout 제한 포함)
- **입력 파라미터**:
  - orderNo: 주문번호
  - amount: 결제 금액
  - currency: 통화
  - method: 결제 수단
  - failRate: 실패율 (테스트용)
  - **delayMs**: 지연 시간 (테스트용) - 이 값이 timeout보다 크면 실패
- **Timeout 설정**:
  - `timeoutSeconds`: 5초 - task 전체 실행 시간 제한
  - `responseTimeoutSeconds`: 3초 - worker 응답 대기 시간
  - `timeoutPolicy`: TIME_OUT_WF - timeout 시 워크플로우 실패

### 3️⃣ decide_payment
- **타입**: DECISION
- **분기 조건**: `authorize_payment.output.status` 값
- **분기 경로**:
  - `AUTHORIZED`: 결제 성공 → 재고 예약 및 주문 확정
  - 기타 (default): 결제 실패 또는 타임아웃 → 주문 취소

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

## 분기 B: 결제 실패 또는 타임아웃 시

### 4️⃣-B cancel_order
- **타입**: SIMPLE
- **taskReferenceName**: cancel_order_payment_failed
- **동작**: 주문 취소 (결제 실패 또는 타임아웃 사유)
- **입력 파라미터**:
  - orderNo: 주문번호
- **결과**: ❌ 워크플로우 완료 (결제 실패/타임아웃)

---

## 워크플로우 설정

- **schemaVersion**: 2
- **restartable**: true
- **workflowStatusListenerEnabled**: false
- **timeoutPolicy**: TIME_OUT_WF
- **timeoutSeconds**: 30 (워크플로우 전체 timeout)

## 실행 방법

```bash
./scripts/scenarios/w3_timeout.sh
```

이 스크립트는 2가지 테스트를 실행합니다:
1. **정상 케이스**: delayMs=1000 (1초) - timeout 이내 완료
2. **타임아웃 케이스**: delayMs=6000 (6초) - timeout 초과로 실패

### 개별 테스트 입력 예시

#### Test 1: 정상 처리 (timeout 이내)
```json
{
  "orderNo": "ORD-W3-001",
  "totalAmount": 10000,
  "currency": "KRW",
  "customerId": "CUST-001",
  "paymentMethod": "CARD",
  "paymentFailRate": 0.0,
  "paymentDelayMs": 1000,
  "items": [
    {"productId": "PROD-001", "quantity": 2, "unitPrice": 5000}
  ],
  "forceOutOfStock": false,
  "partialFailIndex": -1
}
```

#### Test 2: 타임아웃 발생 (timeout 초과)
```json
{
  "orderNo": "ORD-W3-002",
  "totalAmount": 10000,
  "currency": "KRW",
  "customerId": "CUST-001",
  "paymentMethod": "CARD",
  "paymentFailRate": 0.0,
  "paymentDelayMs": 6000,
  "items": [
    {"productId": "PROD-001", "quantity": 2, "unitPrice": 5000}
  ],
  "forceOutOfStock": false,
  "partialFailIndex": -1
}
```

## 핵심 포인트

1. **Task 레벨 Timeout**: `timeoutSeconds`와 `responseTimeoutSeconds`로 작업 시간 제한
2. **Timeout 정책**: `TIME_OUT_WF`로 설정하여 timeout 시 워크플로우 실패 처리
3. **테스트 제어**: `delayMs` 파라미터로 timeout 동작 시뮬레이션
4. **자동 실패 처리**: timeout 발생 시 decide_payment에서 defaultCase로 분기

## Timeout 설정 상세

### timeoutSeconds vs responseTimeoutSeconds

- **responseTimeoutSeconds (3초)**:
  - Worker가 task를 poll한 후 응답을 보내야 하는 시간
  - Worker가 응답하지 않으면 task가 IN_PROGRESS 상태로 유지됨
  - 이 시간이 지나면 다른 worker가 task를 다시 poll 할 수 있음

- **timeoutSeconds (5초)**:
  - Task가 시작된 후 완료되어야 하는 전체 시간
  - 이 시간 내에 완료되지 않으면 task가 TIMED_OUT 상태로 변경
  - `timeoutPolicy`에 따라 워크플로우 동작 결정

### timeoutPolicy 옵션

- **TIME_OUT_WF**: Task timeout 시 워크플로우 전체를 TIMED_OUT 상태로 변경 (이 예제에서 사용)
- **RETRY**: Task를 재시도 (retryCount 설정 필요)
- **ALERT_ONLY**: 알림만 발생하고 워크플로우는 계속 진행

## 시나리오별 실행 경로

### ✅ 정상 시나리오 (delayMs=1000, timeout 이내)
```
create_order
→ authorize_payment (1초 소요, timeout 5초)
→ decide_payment [AUTHORIZED]
→ reserve_inventory
→ confirm_order ✅
```

### ❌ 타임아웃 시나리오 (delayMs=6000, timeout 초과)
```
create_order
→ authorize_payment (6초 소요 시도, timeout 5초)
→ TIMED_OUT (5초에 timeout 발생)
→ decide_payment [default case]
→ cancel_order_payment_failed ❌
```

## Task 정의 파일

- 워크플로우: [workflows/W3_timeout.json](../../workflows/W3_timeout.json)
- Task 정의:
  - [tasks/create_order.json](../../tasks/create_order.json)
  - [tasks/authorize_payment.json](../../tasks/authorize_payment.json)
  - [tasks/reserve_inventory.json](../../tasks/reserve_inventory.json)
  - [tasks/confirm_order.json](../../tasks/confirm_order.json)
  - [tasks/cancel_order.json](../../tasks/cancel_order.json)

## W2 vs W3 비교

| 항목 | W2 (Payment Retry) | W3 (Timeout Handling) |
|------|-------------------|----------------------|
| **실패 처리 방식** | Retry (재시도) | Timeout (시간 제한) |
| **retryCount** | 3 | 0 (retry 없음) |
| **timeoutSeconds** | 60 | 5 (짧은 timeout) |
| **timeoutPolicy** | TIME_OUT_WF | TIME_OUT_WF |
| **목적** | 일시적 실패 복구 | 느린 응답 차단 |
| **사용 사례** | 네트워크 오류, 일시적 장애 | 무응답, 느린 외부 API |

## 실무 활용 예시

1. **외부 결제 게이트웨이 호출**:
   - 결제 API 응답이 늦을 때 무한 대기하지 않고 timeout
   - 사용자 경험 향상 (빠른 실패 피드백)

2. **써드파티 서비스 연동**:
   - 외부 서비스 장애 시 전체 시스템 영향 최소화
   - timeout으로 빠르게 실패 처리하여 리소스 절약

3. **SLA 보장**:
   - 특정 시간 내에 응답하지 않으면 자동 실패 처리
   - 시스템 응답 시간 SLA 준수
