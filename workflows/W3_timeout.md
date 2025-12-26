# W3: Timeout Handling Workflow

## 개요
지연된 결제 요청을 timeout으로 실패 처리하는 워크플로우입니다. **Worker 레벨에서 timeout을 체크**하여 응답이 늦은 작업을 자동으로 실패 처리합니다.

### Conductor Timeout의 한계와 해결책

**Conductor의 기본 timeout 동작**:
- `timeoutSeconds`: Worker가 task를 **시작하지 않을 때** (scheduled 상태) timeout
- `responseTimeoutSeconds`: Worker가 **응답을 보내지 않을 때** timeout
- **문제점**: Worker가 늦게라도 응답을 보내면, timeout이 발생하지 않음

**해결책**:
- **Worker 레벨에서 timeout 체크**: Worker가 실행 시간을 측정하고, timeout 초과 시 스스로 FAILED 반환
- 이 방식이 **실무에서 권장되는 패턴**입니다

## 워크플로우 실행 순서

### 기본 흐름

```
1. create_order
   ↓
2. authorize_payment (timeout: 10초, responseTimeout: 8초)
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
  - Task 정의 (`tasks/authorize_payment.json`): `timeoutSeconds: 10`
  - **Worker 구현** (`AuthorizePaymentWorker.java`):
    ```java
    long startTime = System.currentTimeMillis();
    PaymentResponse response = paymentClient.authorize(request);
    long executionTime = System.currentTimeMillis() - startTime;

    if (executionTime > timeoutSeconds * 1000L) {
        result.setStatus(TaskResult.Status.FAILED);
        result.setReasonForIncompletion("Payment authorization timed out");
        return result;
    }
    ```
  - Worker가 실행 시간을 측정하고, timeout 초과 시 FAILED 반환
  - **참고**: 실제 PG사 API는 대부분 3-5초 내 응답, 네트워크 지연 고려해 10초 설정

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
1. **정상 케이스**: delayMs=2000 (2초) - timeout 10초 이내 완료
2. **타임아웃 케이스**: delayMs=12000 (12초) - timeout 10초 초과로 실패

**주의**: 스크립트는 매번 실행 시 고유한 `orderNo`를 생성합니다 (예: `ORD-W3-1766731048-1`).
Order 서비스가 중복 주문을 거부하기 때문에 동적 생성이 필요합니다.

### 개별 테스트 입력 예시

#### Test 1: 정상 처리 (timeout 이내)
```json
{
  "orderNo": "ORD-W3-{TIMESTAMP}-1",
  "totalAmount": 10000,
  "currency": "KRW",
  "customerId": "CUST-001",
  "paymentMethod": "CARD",
  "paymentFailRate": 0.0,
  "paymentDelayMs": 2000,
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
  "orderNo": "ORD-W3-{TIMESTAMP}-2",
  "totalAmount": 10000,
  "currency": "KRW",
  "customerId": "CUST-001",
  "paymentMethod": "CARD",
  "paymentFailRate": 0.0,
  "paymentDelayMs": 12000,
  "items": [
    {"productId": "PROD-001", "quantity": 2, "unitPrice": 5000}
  ],
  "forceOutOfStock": false,
  "partialFailIndex": -1
}
```

## 핵심 포인트

1. **Worker 레벨 Timeout 체크**: Worker가 실행 시간을 측정하고, timeout 초과 시 FAILED 반환
2. **Conductor Timeout의 한계**: Worker가 응답을 보내면 timeout이 발생하지 않음
3. **실무 권장 패턴**: Worker에서 timeout 체크 + HTTP 클라이언트 timeout 설정
4. **테스트 제어**: `delayMs` 파라미터로 timeout 동작 시뮬레이션
5. **자동 실패 처리**: timeout 발생 시 decide_payment에서 defaultCase로 분기

## Timeout 설정 상세

### Conductor Timeout vs Worker Timeout

#### 1. Conductor 기본 Timeout (제한적)

- **responseTimeoutSeconds (8초)**:
  - Worker가 task를 poll한 후 **응답을 보내지 않을 때** timeout
  - Worker가 응답하지 않으면 task가 IN_PROGRESS 상태로 유지됨
  - 이 시간이 지나면 다른 worker가 task를 다시 poll 할 수 있음
  - **한계**: Worker가 늦게라도 응답하면 timeout 발생 안 함

- **timeoutSeconds (10초)**:
  - Worker가 task를 **시작하지 않을 때** (scheduled 상태) timeout
  - **한계**: Worker가 실행 중이고 응답을 보내면 timeout 발생 안 함
  - `timeoutPolicy`에 따라 워크플로우 동작 결정

#### 2. Worker 레벨 Timeout (W3에서 사용) ⭐ 권장

```java
// AuthorizePaymentWorker.java
long startTime = System.currentTimeMillis();
PaymentResponse response = paymentClient.authorize(request);
long executionTime = System.currentTimeMillis() - startTime;

if (executionTime > timeoutSeconds * 1000L) {
    result.setStatus(TaskResult.Status.FAILED);
    result.setReasonForIncompletion("Payment authorization timed out after " + executionTime + "ms");
    return result;
}
```

**장점**:
- Worker가 실제 실행 시간을 측정하여 정확한 timeout 제어
- HTTP 클라이언트 timeout과 함께 사용하면 더욱 안전

#### 3. 실무 권장 구조

```
[HTTP Client Timeout] → [Worker Timeout Check] → [Conductor Timeout]
     5-10초                    10초                  15초 (fallback)
```

1. **HTTP Client Timeout**: 외부 API 호출 시 `RestTemplate`, `OkHttp` 등의 timeout 설정
2. **Worker Timeout Check**: Worker에서 실행 시간 체크 (W3 방식)
3. **Conductor Timeout**: Worker 무응답 시 fallback

### timeoutPolicy 옵션

- **TIME_OUT_WF**: Task timeout 시 워크플로우 전체를 TIMED_OUT 상태로 변경 (이 예제에서 사용)
- **RETRY**: Task를 재시도 (retryCount 설정 필요)
- **ALERT_ONLY**: 알림만 발생하고 워크플로우는 계속 진행

## 시나리오별 실행 경로

### ✅ 정상 시나리오 (delayMs=2000, timeout 이내)
```
create_order
→ authorize_payment (2초 소요, timeout 10초)
→ decide_payment [AUTHORIZED]
→ reserve_inventory
→ confirm_order ✅
```

### ❌ 타임아웃 시나리오 (delayMs=12000, timeout 초과)
```
create_order
→ authorize_payment (12초 소요, Worker가 timeout 체크)
→ FAILED (Worker가 10초 초과 감지하여 FAILED 반환)
→ decide_payment [default case]
→ cancel_order_payment_failed ❌
```

**실행 흐름 상세**:
1. Worker가 PaymentClient.authorize() 호출 (12초 소요)
2. API 응답은 받았지만, Worker가 실행 시간 체크: 12000ms > 10000ms
3. Worker가 `TaskResult.Status.FAILED` 반환
4. Conductor가 FAILED task로 처리
5. decide_payment에서 defaultCase로 분기

## Task 정의 파일

- 워크플로우: [workflows/W3_timeout.json](../../workflows/W3_timeout.json)
- Task 정의:
  - [tasks/create_order.json](../../tasks/create_order.json)
  - [tasks/authorize_payment.json](../../tasks/authorize_payment.json)
  - [tasks/reserve_inventory.json](../../tasks/reserve_inventory.json)
  - [tasks/confirm_order.json](../../tasks/confirm_order.json)
  - [tasks/cancel_order.json](../../tasks/cancel_order.json)

## 실무 권장 Timeout 값

### API 유형별 권장값

| API 유형 | 권장 Timeout | 최대 Timeout | 설명 |
|---------|------------|------------|------|
| **결제 승인 (PG사)** | 5-10초 | 15초 | 토스, 네이버페이 등 대부분 3-5초 응답 |
| **외부 API (일반)** | 3-5초 | 10초 | 써드파티 서비스 연동 |
| **내부 서비스** | 1-3초 | 5초 | 마이크로서비스 간 통신 |
| **재고 확인/예약** | 2-5초 | 10초 | DB 조회 + 비즈니스 로직 |
| **파일 업로드** | 30-60초 | 120초 | 파일 크기에 따라 조정 |

### 실제 PG사 권장값

- **토스페이먼츠**: 10초
- **네이버페이**: 10초
- **KG이니시스**: 15초 (해외카드 고려)
- **NHN KCP**: 10초

## W2 vs W3 비교

| 항목 | W2 (Payment Retry) | W3 (Timeout Handling) |
|------|-------------------|----------------------|
| **실패 처리 방식** | Retry (재시도) | Timeout (시간 제한) |
| **retryCount** | 3 | 0 (retry 없음) |
| **timeoutSeconds** | 10 | 10 |
| **timeoutPolicy** | TIME_OUT_WF | TIME_OUT_WF |
| **목적** | 일시적 실패 복구 | 느린 응답 차단 |
| **사용 사례** | 네트워크 오류, 일시적 장애 | 무응답, 느린 외부 API |
| **delayMs 테스트** | 정상 범위 내 | timeout 초과 시뮬레이션 |

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
