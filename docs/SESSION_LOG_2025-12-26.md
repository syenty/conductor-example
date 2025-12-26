# Conductor Workflow 구현 세션 로그

**날짜**: 2025-12-26
**작업 내용**: W1~W4 워크플로우 시나리오 구현 및 디버깅

---

## 작업 요약

### W2: Payment Retry
- retryCount를 0에서 3으로 변경하여 초단위 재시도 구현
- EXPONENTIAL_BACKOFF 설정 (2초 시작, 2배씩 증가)
- 문서 작성: `workflows/W2_payment_retry.md`

### W3: Timeout Handling
- **핵심 발견**: Conductor의 `timeoutSeconds`는 Worker가 응답하면 동작하지 않음
- **해결책**: Worker 레벨에서 실행 시간 측정 후 timeout 체크
- 수정 파일:
  - `AuthorizePaymentWorker.java`: timeout 체크 로직 추가
  - `CancelOrderWorker.java`: 주문 취소 시 결제 환불(refund) 추가
  - `w3_timeout.sh`: 동적 orderNo 생성 (중복 주문 방지)
- Timeout 시 `COMPLETED` 상태로 반환하고 `status=TIMEOUT` 설정
  - 이렇게 해야 workflow가 `decide_payment`로 진행되어 `cancel_order` 실행 가능

### W4: Payment Method Switch
- **SWITCH Task** 사용 (DECISION의 새로운 버전)
- **WAIT Task** 사용 (무통장입금 대기)
- 분기:
  - CARD → 즉시 결제 승인
  - BANK_TRANSFER → 입금 대기 → 입금 확인
- 수정 내용:
  - BANK_TRANSFER 분기에 `authorize_payment_bank` 추가 (Payment 데이터 먼저 생성)
  - `PaymentClient.java`: `confirmBankTransfer()` 메서드 추가
  - `ConfirmBankTransferWorker.java`: 새 Worker 생성
  - `PaymentEvent.java`: JSONB 타입 변환 어노테이션 추가 (`@JdbcTypeCode`)
  - Workflow 버전 2로 업데이트

---

## 해결한 에러들

### 1. Conductor Timeout 미동작
- **문제**: `timeoutSeconds` 설정해도 Worker가 응답하면 timeout 발생 안 함
- **해결**: Worker에서 실행 시간 측정 후 직접 timeout 처리
```java
long executionTime = System.currentTimeMillis() - startTime;
if (executionTime > timeoutSeconds * 1000L) {
    result.setStatus(TaskResult.Status.COMPLETED);
    result.setOutputData(Map.of("status", "TIMEOUT", ...));
    return result;
}
```

### 2. 빌드 에러 (Optional 처리)
- **문제**: `task.getTaskDefinition()`이 `Optional<TaskDef>` 반환
- **해결**:
```java
long timeoutSeconds = task.getTaskDefinition()
    .map(def -> def.getTimeoutSeconds())
    .filter(t -> t > 0)
    .orElse(10L);
```

### 3. 415 Unsupported Media Type
- **문제**: `confirmBankTransfer()` 호출 시 Content-Type 없음
- **해결**: `null` 대신 `Collections.emptyMap()` 전달
```java
restTemplate.postForEntity(url, java.util.Collections.emptyMap(), PaymentResponse.class);
```

### 4. Payment not found (BANK_TRANSFER)
- **문제**: BANK_TRANSFER 시 Payment 데이터 없이 `confirm_bank_transfer` 호출
- **해결**: `authorize_payment_bank` task 추가하여 Payment 데이터 먼저 생성

### 5. JSONB 타입 변환 에러
- **문제**: PostgreSQL jsonb 컬럼에 String 저장 시 타입 에러
- **해결**: Hibernate 어노테이션 추가
```java
@JdbcTypeCode(SqlTypes.JSON)
@Column(columnDefinition = "jsonb")
private String payload;
```

### 6. Workflow 업데이트 미반영
- **문제**: workflow JSON 수정 후에도 이전 버전 실행
- **해결**: 버전 번호 증가 후 재등록
```bash
curl -X PUT "http://localhost:8080/api/metadata/workflow" \
  -H "Content-Type: application/json" \
  -d "[$(cat workflow.json)]"
```

---

## 생성/수정된 파일 목록

### Workflow 정의
- `workflows/W2_payment_retry.json`
- `workflows/W3_timeout.json`
- `workflows/W4_payment_switch.json` (version 2)

### Task 정의
- `tasks/authorize_payment.json` (timeoutSeconds: 10)
- `tasks/confirm_bank_transfer.json` (신규)

### Worker
- `AuthorizePaymentWorker.java` (timeout 체크 로직)
- `CancelOrderWorker.java` (refund 추가)
- `ConfirmBankTransferWorker.java` (신규)

### Client
- `PaymentClient.java` (`confirmBankTransfer()` 추가)

### 테스트 스크립트
- `scripts/scenarios/w2_payment_retry.sh`
- `scripts/scenarios/w3_timeout.sh`
- `scripts/scenarios/w4_payment_switch.sh`
- `scripts/scenarios/w4_complete_deposit.sh`

### 문서
- `workflows/W1_basic_order.md`
- `workflows/W2_payment_retry.md`
- `workflows/W3_timeout.md`
- `workflows/W4_payment_switch.md`

### 기타
- `PaymentEvent.java` (JSONB 어노테이션)

---

## 핵심 학습 포인트

### 1. Conductor Timeout의 한계
- `timeoutSeconds`: Worker가 task를 시작하지 않을 때만 동작
- `responseTimeoutSeconds`: Worker가 응답을 보내지 않을 때만 동작
- **실무 해결책**: Worker에서 직접 실행 시간 체크

### 2. Task 상태와 Workflow 진행
- `FAILED` 상태: retry 발생, workflow 멈춤
- `COMPLETED` 상태: workflow 계속 진행
- Timeout 시에도 workflow 진행하려면 `COMPLETED` + 상태값으로 분기 처리

### 3. SWITCH vs DECISION
- DECISION: 레거시, 단순 값 비교
- SWITCH: 권장, JavaScript 표현식 지원

### 4. WAIT Task
- 외부 이벤트 대기
- API 호출로 task 완료:
```bash
curl -X POST "http://localhost:8080/api/tasks" \
  -d '{"workflowInstanceId": "...", "taskId": "...", "status": "COMPLETED"}'
```

---

## 다음 작업 예정

- W5: Inventory Shortage Branch (재고 부족 시 분기)
- W6: Parallel Post Payment (FORK/JOIN)
- W7: Parallel Multi Item Reserve
- W8~W9: Saga 패턴 (보상 트랜잭션)
- W10~W11: Manual Approval, Event Wait
- W12: Sub Workflow
