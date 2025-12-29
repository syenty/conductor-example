# W8: Saga Pattern - Inventory Fail Compensation

## 개요
**Saga 패턴**을 사용하여 재고 예약 실패 시 자동으로 보상 트랜잭션을 실행하는 워크플로우입니다.

## Saga 패턴이란?

Saga는 분산 트랜잭션을 처리하는 패턴으로, **각 단계가 실패하면 이전에 완료된 단계들을 되돌리는(보상) 메커니즘**을 제공합니다.

### 전통적인 트랜잭션 vs Saga

```
전통적 ACID 트랜잭션 (단일 DB):
BEGIN TRANSACTION
  INSERT INTO orders ...
  INSERT INTO payments ...
  INSERT INTO inventory ...
COMMIT / ROLLBACK

Saga 패턴 (분산 시스템):
1. create_order (성공) → 보상: cancel_order
2. authorize_payment (성공) → 보상: refund_payment
3. reserve_inventory (실패!) → 보상 실행!
   → refund_payment → cancel_order
```

## 워크플로우 구조

### 메인 워크플로우 (saga_inventory_fail)

```
1. create_order
   ↓
2. authorize_payment
   ↓
3. reserve_inventory (forceOutOfStock=true로 실패 유도)
   ↓ (실패!)
   └─→ failureWorkflow 호출 → saga_inventory_compensation
```

### 보상 워크플로우 (saga_inventory_compensation)

```
1. refund_payment (결제 환불)
   ↓
2. cancel_order (주문 취소)
```

## Conductor의 Saga 구현 방식

Conductor는 `failureWorkflow` 속성을 통해 Saga 패턴을 구현합니다:

```json
{
  "name": "saga_inventory_fail",
  "failureWorkflow": "saga_inventory_compensation",
  "tasks": [...]
}
```

- 메인 워크플로우의 어떤 태스크라도 실패하면
- `failureWorkflow`에 지정된 보상 워크플로우가 자동 실행됨
- 원본 워크플로우의 입력이 보상 워크플로우에 전달됨

## W5 vs W6 vs W8 비교: 재고 부족 처리 방식

### 핵심 차이점 요약

| 항목 | W5 (Inventory Branch) | W6 (Parallel Post Payment) | W8 (Saga) |
|------|----------------------|---------------------------|-----------|
| **패턴** | 조건 분기 (SWITCH) | 병렬 처리 + 조건 분기 (FORK/JOIN + DECISION) | Saga 보상 패턴 (DECISION) |
| **실패 처리** | 비즈니스 로직의 정상 분기 | 비즈니스 로직의 정상 분기 | 예외/오류 상황으로 처리 |
| **태스크 상태** | 항상 COMPLETED | 항상 COMPLETED | FAILED 사용 |
| **보상 실행** | SWITCH defaultCase | DECISION 각 케이스 | DECISION FAILED 브랜치 |
| **워크플로우 수** | 1개 | 1개 | 1개 (DECISION 방식) |
| **적용 상황** | 정상적인 비즈니스 분기 | 병렬 체크 후 분기 | 예외/오류 상황 처리 |

### 1. W5 방식: 조건 분기

```
create_order → authorize_payment → reserve_inventory (COMPLETED)
                                    ↓ output: {status: "OUT_OF_STOCK"}
                                    ↓
                                SWITCH (reserveStatus)
                                ↓
                          defaultCase: refund + cancel
```

**특징:**
- `reserve_inventory`는 재고가 부족해도 **태스크는 성공(COMPLETED)**
- 단지 output에 `status: "OUT_OF_STOCK"` 반환
- SWITCH가 output 값을 읽어서 분기 결정
- **재고 부족은 "정상적인 비즈니스 케이스" 중 하나**

**Worker 구현:**
```java
InventoryReserveResponse response = inventoryClient.reserve(request);
String status = response.status(); // "OUT_OF_STOCK"

result.setStatus(TaskResult.Status.COMPLETED); // 항상 성공
result.setOutputData(Map.of("status", status));  // output에만 상태 담음
```

### 2. W6 방식: 병렬 처리 + 조건 분기

```
create_order → FORK_JOIN
               ├─ authorize_payment (COMPLETED) → {status: "AUTHORIZED"}
               └─ check_inventory (COMPLETED) → {status: "OUT_OF_STOCK"}
               ↓
             JOIN
               ↓
          DECISION (paymentStatus, inventoryStatus)
               ↓
          INVENTORY_FAILED: refund + cancel
```

**특징:**
- `check_inventory`는 **읽기 전용 체크**, 재고가 없어도 **태스크는 성공(COMPLETED)**
- output에 `status: "OUT_OF_STOCK"` 반환
- DECISION이 두 태스크의 output 조합해서 분기 결정
- **재고 부족은 "정상적인 비즈니스 케이스" 중 하나**
- W5와 본질적으로 같지만 병렬 처리만 추가

**Worker 구현:**
```java
// CheckInventoryWorker
InventoryReserveResponse response = inventoryClient.checkAvailability(request);
String status = response.status(); // "OUT_OF_STOCK"

result.setStatus(TaskResult.Status.COMPLETED); // 항상 성공
result.setOutputData(Map.of("status", status));
```

### 3. W8 방식: Saga 보상 패턴

```
create_order → authorize_payment → reserve_inventory (FAILED!) ❌
                                    ↓ task.status = FAILED
                                    ↓ (optional: true로 워크플로우는 계속)
                                    ↓
                          decide_saga_compensation
                                    ↓
                          reserveStatus == 'FAILED'
                                    ↓
                          FAILED 브랜치: refund + cancel
```

**특징:**
- `reserve_inventory`는 재고가 부족하면 **태스크 자체가 실패(FAILED)**
- `optional: true` 덕분에 워크플로우는 계속 진행
- DECISION이 **태스크의 실패 상태**를 감지해서 보상 실행
- **재고 부족은 "예외 상황"으로 처리**

**Worker 구현:**
```java
// ReserveInventoryWorker (W8용 수정)
InventoryReserveResponse response = inventoryClient.reserve(request);
String status = response.status();

if ("FAILED".equals(status)) {
    result.setStatus(TaskResult.Status.FAILED);  // 태스크 자체를 실패로!
    result.setReasonForIncompletion("Inventory reservation failed: out of stock");
} else {
    result.setStatus(TaskResult.Status.COMPLETED);
    result.setOutputData(Map.of("status", status));
}
```

### 실무 관점에서의 차이

#### W5/W6 방식 (비즈니스 로직 분기)
- **사용 시점**: 재고 부족이 예상 가능한 정상 케이스
- **장점**:
  - 태스크는 성공적으로 실행되고, 결과값에 따라 분기
  - 모니터링 시 워크플로우는 성공(COMPLETED)으로 보임
  - 실패가 아닌 "대안 처리" 관점
- **사용 예**:
  - 주문 가능 여부 사전 체크
  - 재고 확인 후 다른 상품 추천
  - 선착순 이벤트 (재고 부족 자주 발생)

#### W8 방식 (Saga 보상 패턴)
- **사용 시점**: 재고 부족을 예외 상황으로 처리해야 할 때
- **장점**:
  - 태스크가 실패하고, 이미 완료된 작업들을 되돌림(보상)
  - 모니터링 시 보상 실행 여부 확인 가능
  - 분산 트랜잭션에 적합
- **사용 예**:
  - 여러 마이크로서비스를 거치는 복잡한 트랜잭션
  - 중간 단계 실패 시 이전 단계를 반드시 되돌려야 하는 경우
  - 외부 API 연동 실패 등 예외 상황

### 언제 어떤 방식을 쓸까?

**W5/W6 방식을 선택하는 경우:**
- 재고 부족이 자주 발생하는 상황 (예: 선착순 이벤트)
- 실패를 "실패"가 아닌 "대안 처리 필요"로 보는 경우
- 사용자에게 다른 옵션을 제시하려는 경우
- 워크플로우 전체를 성공으로 표시하고 싶은 경우

**W8 방식을 선택하는 경우:**
- 여러 마이크로서비스를 거치는 복잡한 트랜잭션
- 중간 단계 실패 시 이전 단계를 되돌려야 하는 경우
- 실패를 "예외 상황"으로 명확히 처리하고 싶은 경우
- 분산 시스템에서 데이터 일관성이 중요한 경우

## 실행 방법

```bash
./scripts/scenarios/w8_saga_inventory_fail.sh
```

## 입력 예시

### 성공 케이스
```json
{
  "orderNo": "ORD-W8-001",
  "totalAmount": 15000,
  "currency": "KRW",
  "customerId": "CUST-001",
  "paymentMethod": "CARD",
  "paymentFailRate": 0.0,
  "paymentDelayMs": 0,
  "items": [
    {"productId": "PROD-001", "quantity": 3, "unitPrice": 5000}
  ],
  "forceOutOfStock": false,
  "partialFailIndex": -1
}
```

### 실패 케이스 (재고 부족)
```json
{
  "orderNo": "ORD-W8-002",
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

## 시나리오별 실행 경로

### ✅ 정상 시나리오
```
create_order (COMPLETED)
→ authorize_payment (COMPLETED)
→ reserve_inventory (COMPLETED)
→ confirm_order (COMPLETED)
→ Workflow: COMPLETED ✅
```

**결과**: Order=CONFIRMED, Payment=AUTHORIZED, Inventory=RESERVED

### ❌ 재고 부족 시나리오 (Saga 보상)
```
[메인 워크플로우]
create_order (COMPLETED)
→ authorize_payment (COMPLETED)
→ reserve_inventory (FAILED!) ❌
→ Workflow: FAILED

[보상 워크플로우 자동 실행]
saga_inventory_compensation 시작
→ refund_payment (COMPLETED)
→ cancel_order (COMPLETED)
→ Compensation: COMPLETED ✅
```

**결과**: Order=CANCELED, Payment=REFUNDED, Inventory=(예약 안 됨)

## ReserveInventoryWorker의 실패 처리

현재 구현에서 `ReserveInventoryWorker`는 재고 부족 시:

```java
if (status.equals("FAILED")) {
    result.setStatus(TaskResult.Status.FAILED);
    result.setReasonForIncompletion("Inventory reservation failed");
}
```

이렇게 태스크를 FAILED로 마킹하면, Conductor가 자동으로 `failureWorkflow`를 호출합니다.

## 핵심 포인트

1. **자동 보상**: 실패 시 수동 체크 없이 자동으로 보상 워크플로우 실행
2. **워크플로우 분리**: 정상 흐름과 보상 흐름을 별도 워크플로우로 관리
3. **재사용성**: 같은 보상 워크플로우를 여러 메인 워크플로우에서 재사용 가능
4. **실패 전파**: 어떤 태스크라도 실패하면 즉시 보상 실행

## 실무 활용 예시

1. **분산 트랜잭션**: 여러 마이크로서비스를 거치는 주문 처리
2. **장기 실행 프로세스**: 결제 → 재고 → 배송 예약 등 여러 단계
3. **외부 API 연동**: 실패 가능성이 있는 외부 시스템 호출
4. **복잡한 비즈니스 로직**: 단계별 성공/실패에 따른 보상 필요

## Task 정의 파일

- 메인 워크플로우: [workflows/W8_saga_inventory_fail.json](W8_saga_inventory_fail.json)
- 보상 워크플로우: [workflows/W8_saga_inventory_compensation.json](W8_saga_inventory_compensation.json)
- Task 정의:
  - [tasks/create_order.json](../tasks/create_order.json)
  - [tasks/authorize_payment.json](../tasks/authorize_payment.json)
  - [tasks/reserve_inventory.json](../tasks/reserve_inventory.json)
  - [tasks/confirm_order.json](../tasks/confirm_order.json)
  - [tasks/refund_payment.json](../tasks/refund_payment.json)
  - [tasks/cancel_order.json](../tasks/cancel_order.json)
