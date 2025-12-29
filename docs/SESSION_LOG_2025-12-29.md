# Conductor Workflow 구현 세션 로그

**날짜**: 2025-12-29
**작업 내용**: W5~W9 워크플로우 시나리오 구현 및 Saga 패턴 적용

---

## 작업 요약

### W5: Inventory Branch
- **SWITCH 조건 분기**를 사용하여 재고 부족 시 환불+취소 처리
- `reserve_inventory` 결과(output.status)에 따라 분기
  - `RESERVED`: 주문 확정
  - `defaultCase`: 결제 환불 + 주문 취소
- 재고 부족을 "정상적인 비즈니스 케이스"로 처리 (태스크는 COMPLETED)

### W6: Parallel Post Payment
- **FORK/JOIN 병렬 처리** 구현
- 결제 승인(`authorize_payment`)과 재고 체크(`check_inventory`)를 병렬 실행
- 새로운 Worker 생성:
  - `CheckInventoryWorker`: 읽기 전용 재고 체크
- InventoryService에 `checkAvailability()` 메서드 추가 (DB 수정 없이 조회만)
- DECISION으로 4가지 케이스 처리:
  - `BOTH_SUCCESS`: 재고 예약 → 주문 확정
  - `PAYMENT_FAILED`: 주문 취소만
  - `INVENTORY_FAILED`: 결제 환불 → 주문 취소
  - `BOTH_FAILED`: 주문 취소만

### W7: Parallel Multi Item
- W6와 동일한 구조, 여러 품목 주문 테스트
- 단일 워크플로우로 단일/다수 품목 모두 처리 가능함을 증명
- 테스트: PROD-001(2개) + PROD-002(3개) 동시 예약 성공

### W8: Saga Pattern - Inventory Fail
- **Saga 보상 패턴** 구현
- 핵심 차이: 재고 부족을 "예외 상황"으로 처리
- `reserve_inventory`에 `optional: true` 설정
- DECISION으로 task.status 체크하여 보상 실행
- **Worker 수정 문제 발생 및 해결**:
  - 문제: ReserveInventoryWorker를 W8용으로 수정하면 W5 동작 안 함
  - 해결: `useSagaPattern` 파라미터로 조건부 처리
    - W5, W6, W7: 태스크 COMPLETED, output에 status 반환
    - W8, W9: 태스크 FAILED, reasonForIncompletion 설정

### W9: Saga Pattern - Partial Inventory Fail
- 일부 품목만 재고 부족인 경우 전체 보상 트랜잭션 실행
- `partialFailIndex` 파라미터로 특정 인덱스 품목만 실패 시뮬레이션
- "All-or-Nothing" 정책: 일부 실패 시 전체 주문 취소 + 전액 환불
- W8과 동일한 워크플로우 구조, 입력 파라미터만 다름

---

## 해결한 에러들

### 1. W6 FORK_JOIN 구현 에러
**문제 1**: "Fork task definition is not followed by a join task"
- **원인**: FORK_JOIN 뒤에 명시적 JOIN 태스크 없음
- **해결**: `type: "JOIN"` 태스크 추가

**문제 2**: check_inventory 태스크가 SCHEDULED 상태로 멈춤
- **원인**: CheckInventoryWorker가 존재하지 않음
- **해결**: CheckInventoryWorker 생성, InventoryService에 checkAvailability() 추가

**문제 3**: authorize_payment 실패 시 3번 재시도 후 워크플로우 실패
- **원인**: authorize_payment에 retryCount=3 설정되어 있음 (W2에서)
- **해결**: FORK_JOIN 내 authorize_payment에 `retryCount: 0`, `optional: true` 추가

### 2. W8 Worker 충돌 문제
**문제**: ReserveInventoryWorker를 W8용으로 수정하면 W5가 동작 안 함
- **원인**: W5는 COMPLETED + output.status를 기대하는데, W8은 FAILED 상태 필요
- **해결**: 조건부 처리 구현
```java
Boolean useSagaPattern = (Boolean) task.getInputData().get("useSagaPattern");
boolean isSagaMode = useSagaPattern != null && useSagaPattern;

if ("FAILED".equals(status)) {
    if (isSagaMode) {
        result.setStatus(TaskResult.Status.FAILED);  // W8, W9용
    } else {
        result.setStatus(TaskResult.Status.COMPLETED);  // W5, W6, W7용
        result.setOutputData(Map.of("status", status));
    }
}
```

### 3. Workflow 버전 관리
- 워크플로우 수정 후 버전 번호를 올려야 새 정의 적용
- W8: version 2 → 3으로 업데이트 (useSagaPattern 추가)

---

## 생성/수정된 파일 목록

### Workflow 정의
- `workflows/W5_inventory_branch.json` (기존)
- `workflows/W6_parallel_post_payment.json` (version 1 → 4)
- `workflows/W7_parallel_multi_item.json` (신규)
- `workflows/W8_saga_inventory_fail.json` (version 1 → 3)
- `workflows/W8_saga_inventory_compensation.json` (신규)
- `workflows/W9_saga_partial_fail.json` (신규)

### Task 정의
- `tasks/check_inventory.json` (신규)

### Worker
- `CheckInventoryWorker.java` (신규) - 읽기 전용 재고 체크
- `ReserveInventoryWorker.java` (수정) - useSagaPattern 조건부 처리

### Service
- `InventoryService.java` (수정)
  - `checkAvailability()` 메서드 추가 - @Transactional 없이 읽기만

### Client
- `InventoryClient.java` (수정)
  - `checkAvailability()` 메서드 추가

### Controller
- `InventoryController.java` (수정)
  - `/inventory/check-availability` 엔드포인트 추가

### 테스트 스크립트
- `scripts/scenarios/w5_inventory_branch.sh` (기존)
- `scripts/scenarios/w6_parallel_post_payment.sh` (수정)
- `scripts/scenarios/w7_parallel_multi_item.sh` (신규)
- `scripts/scenarios/w8_saga_inventory_fail.sh` (수정 - version 3)
- `scripts/scenarios/w9_saga_partial_fail.sh` (신규)

### 문서
- `workflows/W5_inventory_branch.md` (기존)
- `workflows/W6_parallel_post_payment.md` (신규)
- `workflows/W7_parallel_multi_item.md` (신규)
- `workflows/W8_saga_inventory_fail.md` (신규)
- `workflows/W9_saga_partial_fail.md` (신규)

---

## 핵심 학습 포인트

### 1. FORK/JOIN 병렬 처리
```json
{
  "type": "FORK_JOIN",
  "forkTasks": [
    [{"name": "task1", ...}],
    [{"name": "task2", ...}]
  ]
}
```
- 반드시 JOIN 태스크로 합류 필요
- `optional: true`로 실패 시에도 계속 진행 가능
- `retryCount: 0`으로 재시도 방지

### 2. 읽기 전용 vs 쓰기 작업 분리
- `checkAvailability()`: @Transactional 없이 조회만, DB 수정 없음
- `reserve()`: @Transactional로 재고 차감 및 예약 기록

### 3. Saga 패턴 구현 방식

#### 방법 1: failureWorkflow (사용 안 함)
```json
{
  "failureWorkflow": "saga_inventory_compensation"
}
```
- Conductor에서 자동 실행되지 않음
- 수동 트리거 필요

#### 방법 2: DECISION + optional (채택)
```json
{
  "name": "reserve_inventory",
  "optional": true,  // 실패해도 워크플로우 계속
  "inputParameters": {
    "useSagaPattern": true  // Worker에서 FAILED 상태 반환
  }
},
{
  "type": "DECISION",
  "caseExpression": "$.reserveStatus == 'COMPLETED' ? 'SUCCESS' : 'FAILED'",
  "decisionCases": {
    "SUCCESS": [...],
    "FAILED": [
      {"name": "refund_payment", ...},
      {"name": "cancel_order", ...}
    ]
  }
}
```

### 4. 태스크 상태와 워크플로우 분기

| 상황 | W5/W6 방식 | W8/W9 방식 |
|------|-----------|-----------|
| **재고 처리** | 비즈니스 로직 | 예외 상황 |
| **태스크 상태** | COMPLETED | FAILED (optional: true) |
| **상태 전달** | output.status | task.status |
| **분기 방법** | SWITCH(output) | DECISION(task.status) |
| **워크플로우 상태** | COMPLETED | COMPLETED (보상 실행) |

### 5. partialFailIndex 활용
```json
{
  "items": [
    {"productId": "PROD-001", ...},  // index 0
    {"productId": "PROD-002", ...}   // index 1
  ],
  "partialFailIndex": 1  // items[1]만 실패
}
```
- W9에서 일부 품목 실패 시나리오 테스트
- 실무: 장바구니 주문에서 All-or-Nothing 정책 검증

### 6. Worker의 조건부 동작
- 같은 Worker를 여러 워크플로우에서 다르게 사용
- 입력 파라미터(`useSagaPattern`)로 동작 분기
- W5~W7: 비즈니스 로직 분기 (태스크 성공, output 반환)
- W8~W9: Saga 보상 패턴 (태스크 실패)

---

## W5~W9 비교표

| 워크플로우 | 핵심 패턴 | 재고 처리 방식 | 태스크 상태 | 사용 케이스 |
|----------|---------|-------------|-----------|----------|
| **W5** | SWITCH 분기 | 순차 처리 | COMPLETED | 재고 부족을 정상 케이스로 처리 |
| **W6** | FORK/JOIN | 병렬 체크 (읽기) | COMPLETED | 결제+재고 동시 체크 후 분기 |
| **W7** | FORK/JOIN | 병렬 체크 (다수 품목) | COMPLETED | W6 + 여러 품목 |
| **W8** | Saga 보상 | 순차 처리 (쓰기) | FAILED | 재고 부족을 예외로 처리, 보상 |
| **W9** | Saga 보상 | 순차 처리 (일부 실패) | FAILED | 일부 품목 부족 시 전체 보상 |

---

## 실무 적용 시나리오

### W5/W6 방식 선택
- 재고 부족이 자주 발생하는 상황 (선착순 이벤트)
- 실패를 "대안 처리 필요"로 보는 경우
- 워크플로우 전체를 성공으로 표시하고 싶은 경우
- 사용자에게 다른 옵션 제시 가능

### W8/W9 방식 선택
- 여러 마이크로서비스를 거치는 복잡한 트랜잭션
- 중간 단계 실패 시 이전 단계를 반드시 되돌려야 하는 경우
- 실패를 "예외 상황"으로 명확히 처리
- 분산 시스템에서 데이터 일관성이 중요

---

## 다음 작업 예정

- W10: Manual Approval (관리자 승인 대기)
- W11: Event Wait (외부 이벤트 대기)
- W12: Sub Workflow (워크플로우 재사용)

---

## 참고 자료

### 생성된 문서
- [W5_inventory_branch.md](../workflows/W5_inventory_branch.md)
- [W6_parallel_post_payment.md](../workflows/W6_parallel_post_payment.md)
- [W7_parallel_multi_item.md](../workflows/W7_parallel_multi_item.md)
- [W8_saga_inventory_fail.md](../workflows/W8_saga_inventory_fail.md)
- [W9_saga_partial_fail.md](../workflows/W9_saga_partial_fail.md)

### 테스트 결과
모든 워크플로우(W5~W9) 정상 동작 확인:
- W5: 재고 부족 시 SWITCH defaultCase로 환불+취소
- W6: FORK/JOIN으로 병렬 체크, 4가지 케이스 모두 검증
- W7: 다수 품목 병렬 처리 성공
- W8: Saga 보상 패턴 정상 동작 (전체 재고 부족)
- W9: 부분 재고 부족 시 전체 보상 정상 동작
