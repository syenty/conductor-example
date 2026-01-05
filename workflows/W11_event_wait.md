# W11: Event Wait - Bank Transfer Deposit Confirmation

## 개요
**WAIT Task**와 **외부 API**를 사용하여 무통장 입금 확인을 기다리는 워크플로우입니다.

## W10 vs W11 비교

| 항목 | W10 (Manual Approval) | W11 (Event Wait) |
|------|----------------------|------------------|
| **Task Type** | HUMAN | WAIT |
| **용도** | 사람의 승인 결정 | 외부 이벤트 대기 |
| **완료 방법** | Conductor Task Update API 직접 호출 | 외부 시스템 API → Conductor API 호출 |
| **API 구현** | 불필요 | **커스텀 API 필요** |
| **실무 예시** | 관리자 승인, 검토 | 입금 확인, 외부 시스템 응답 |

## 워크플로우 구조

```
1. create_order (BANK_TRANSFER)
   ↓
2. wait_for_deposit (WAIT - IN_PROGRESS) ⏸️
   ↓ 외부 입금 확인 API 호출 대기
   ↓
   [외부 시스템/사용자가 입금]
   ↓
   [입금 확인 API 호출: POST /deposit/confirm]
   ↓
   WAIT task COMPLETED
   ↓
3. reserve_inventory
   ↓
4. confirm_order
```

## 커스텀 입금 확인 API

### 1. 입금 확인 (POST /deposit/confirm)

**Endpoint**: `POST http://localhost:8084/deposit/confirm`

**Request**:
```json
{
  "orderNo": "ORD-W11-001"
}
```

**내부 동작**:
1. orderNo로 해당 워크플로우 찾기
2. `wait_for_deposit` WAIT task 찾기
3. Conductor Task Update API 호출하여 task 완료
4. 워크플로우 계속 진행

**Response (성공)**:
```json
{
  "success": true,
  "message": "Deposit confirmed successfully",
  "orderNo": "ORD-W11-001",
  "workflowId": "abc-123",
  "taskId": "def-456"
}
```

**Response (실패)**:
```json
{
  "success": false,
  "message": "Workflow not found for order: ORD-W11-001"
}
```

### 2. 입금 상태 조회 (GET /deposit/status/{orderNo})

**Endpoint**: `GET http://localhost:8084/deposit/status/{orderNo}`

**Response**:
```json
{
  "orderNo": "ORD-W11-001",
  "workflowId": "abc-123",
  "workflowStatus": "RUNNING",
  "waitTaskStatus": "IN_PROGRESS"
}
```

## 구현 방식

### DepositController.java

```java
@RestController
@RequestMapping("/deposit")
public class DepositController {

    private final WorkflowClient workflowClient;
    private final TaskClient taskClient;

    @PostMapping("/confirm")
    public ResponseEntity<Map<String, Object>> confirmDeposit(@RequestBody Map<String, String> request) {
        String orderNo = request.get("orderNo");

        // 1. orderNo로 워크플로우 찾기
        List<Workflow> workflows = workflowClient.getWorkflows("event_wait_deposit", ...);
        Workflow targetWorkflow = findByOrderNo(workflows, orderNo);

        // 2. WAIT task 찾기
        Task waitTask = findWaitTask(targetWorkflow);

        // 3. Task 완료 처리
        TaskResult result = new TaskResult(waitTask);
        result.setStatus(TaskResult.Status.COMPLETED);
        result.setOutputData(Map.of(
            "depositConfirmed", true,
            "confirmedAt", System.currentTimeMillis()
        ));

        taskClient.updateTask(result);

        return ResponseEntity.ok(...);
    }
}
```

## 실행 방법

```bash
./scripts/scenarios/w11_event_wait.sh
```

## 테스트 시나리오

### 1. 워크플로우 시작
```bash
POST /api/workflow/event_wait_deposit
{
  "orderNo": "ORD-W11-001",
  "totalAmount": 100000,
  "items": [...]
}
```

### 2. WAIT task 상태 확인
```
create_order (COMPLETED)
→ wait_for_deposit (IN_PROGRESS) ⏸️
```

### 3. 입금 확인 API 호출
```bash
POST http://localhost:8084/deposit/confirm
{
  "orderNo": "ORD-W11-001"
}
```

### 4. 워크플로우 계속 진행
```
wait_for_deposit (COMPLETED)
→ reserve_inventory (COMPLETED)
→ confirm_order (COMPLETED)
→ Workflow: COMPLETED ✅
```

## W10 vs W11: 어떻게 다른가?

### W10 (HUMAN Task)
```bash
# 직접 Conductor API 호출
curl -X POST "http://localhost:8080/api/tasks" \
  -d '{
    "taskId": "...",
    "status": "COMPLETED",
    "outputData": {"approved": true}
  }'
```

### W11 (WAIT Task + 커스텀 API)
```bash
# 커스텀 비즈니스 API 호출
curl -X POST "http://localhost:8084/deposit/confirm" \
  -d '{"orderNo": "ORD-W11-001"}'

# 내부적으로 Conductor API 호출됨
```

## 실무 활용 예시

### 1. 무통장 입금 확인
- 고객이 입금
- 은행 시스템에서 입금 확인
- 입금 확인 API 호출 → WAIT task 완료
- 주문 처리 계속 진행

### 2. 외부 시스템 응답 대기
- 결제 게이트웨이 승인 대기
- 배송 시스템 픽업 완료 대기
- 재고 시스템 확보 완료 대기

### 3. 사용자 액션 대기
- 이메일 인증 링크 클릭
- SMS 인증 번호 입력
- 약관 동의 체크

## WAIT Task의 특징

### 장점
1. **비동기 처리**: 워크플로우를 멈추지 않고 이벤트 대기
2. **타임아웃 설정**: 일정 시간 대기 후 자동 처리 가능
3. **외부 시스템 통합**: 다양한 외부 이벤트 소스 연동

### 주의사항
1. **장기 대기**: WAIT task는 무한정 대기 가능 → 타임아웃 설정 권장
2. **리소스**: 대기 중인 워크플로우도 리소스 소비
3. **에러 처리**: 외부 시스템 장애 시 복구 전략 필요

## 타임아웃 설정 예시

```json
{
  "name": "wait_for_deposit",
  "type": "WAIT",
  "timeoutSeconds": 86400,
  "timeoutPolicy": "TIME_OUT_WF"
}
```

24시간 내 입금 없으면 워크플로우 타임아웃

## 핵심 포인트

1. **WAIT Task**: 외부 이벤트를 기다리는 System Task
2. **커스텀 API**: 비즈니스 로직을 감싼 API로 사용자 친화적
3. **이벤트 기반**: 외부 시스템/사용자 액션에 따라 워크플로우 진행
4. **실무 적용**: 결제 확인, 배송 추적, 사용자 인증 등 다양한 시나리오

## Task 정의 파일

- 메인 워크플로우: [W11_event_wait.json](W11_event_wait.json)
- 커스텀 API: [DepositController.java](../apps/orchestrator-worker/src/main/java/com/example/conductor/orchestrator/controller/DepositController.java)
