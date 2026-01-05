# W10: Manual Approval for High Value Orders

## 개요
**HUMAN Task**를 사용하여 고액 주문(100,000원 이상)에 대해 관리자 승인을 받는 워크플로우입니다.

## HUMAN Task란?

**HUMAN Task**는 Conductor에서 제공하는 System Task로, 워크플로우를 일시 정지하고 **사람의 개입(승인/거부)**을 기다립니다.

### 핵심 특징
- **Worker 불필요**: 일반 SIMPLE Task와 달리 Worker 구현 안 해도 됨
- **자동 대기**: Task가 생성되면 `IN_PROGRESS` 상태로 멈춤
- **외부 완료**: API 호출로 `COMPLETED` 또는 `FAILED`로 상태 변경

### 정의 예시
```json
{
  "name": "approve_high_value_order",
  "taskReferenceName": "approve_high_value_order",
  "type": "HUMAN",
  "inputParameters": {
    "orderNo": "${workflow.input.orderNo}",
    "totalAmount": "${workflow.input.totalAmount}",
    "message": "High value order requires manual approval"
  }
}
```

## 워크플로우 구조

```
1. create_order
   ↓
2. authorize_payment
   ↓
3. check_order_value (DECISION)
   ├─ NORMAL (< 100,000원)
   │  └─ reserve_inventory → confirm_order
   │
   └─ HIGH_VALUE (>= 100,000원)
      ↓
      4. approve_high_value_order (HUMAN) ← 여기서 승인 대기!
         ↓
         5. decide_approval_result (DECISION)
            ├─ APPROVED (approved=true)
            │  └─ reserve_inventory → confirm_order
            │
            └─ REJECTED (approved=false 또는 FAILED)
               └─ refund_payment → cancel_order
```

## HUMAN Task 완료 방법

### 1. Task Update API (기본)

#### 승인 (COMPLETED)
```bash
curl -X POST "http://localhost:8080/api/tasks" \
  -H "Content-Type: application/json" \
  -d '{
    "taskId": "abc-123-def-456",
    "workflowInstanceId": "workflow-id",
    "status": "COMPLETED",
    "outputData": {
      "approved": true,
      "approver": "admin@example.com",
      "comment": "Approved"
    }
  }'
```

#### 거부 (COMPLETED with approved=false)
```bash
curl -X POST "http://localhost:8080/api/tasks" \
  -H "Content-Type: application/json" \
  -d '{
    "taskId": "abc-123-def-456",
    "workflowInstanceId": "workflow-id",
    "status": "COMPLETED",
    "outputData": {
      "approved": false,
      "approver": "admin@example.com",
      "comment": "Rejected due to fraud suspicion"
    }
  }'
```

### 2. Conductor UI
- http://localhost:5001/execution/{workflowId}
- HUMAN task 선택 → Complete/Fail 버튼

### 3. 실무 적용 방식
- **관리자 포털**: 승인 대기 목록 UI → 버튼 클릭 시 API 호출
- **Slack/Teams 봇**: 메시지 + 승인/거부 버튼
- **모바일 앱**: 푸시 알림 → 승인 화면
- **이메일**: 승인 링크 클릭 → 웹 페이지 → API 호출

## 실행 방법

```bash
./scripts/scenarios/w10_manual_approval.sh
```

## 테스트 시나리오

### Test 1: 일반 주문 (50,000원)
```json
{
  "orderNo": "ORD-W10-001",
  "totalAmount": 50000,
  "items": [{"productId": "PROD-001", "quantity": 1, "unitPrice": 50000}]
}
```

**흐름:**
```
create_order → authorize_payment → check_order_value (NORMAL)
→ reserve_inventory_normal → confirm_order_normal
→ Workflow: COMPLETED ✅
```

**결과:** Order=CONFIRMED (승인 없이 바로 처리)

### Test 2: 고액 주문 승인 (150,000원)
```json
{
  "orderNo": "ORD-W10-002",
  "totalAmount": 150000,
  "items": [{"productId": "PROD-001", "quantity": 3, "unitPrice": 50000}]
}
```

**흐름:**
```
create_order → authorize_payment → check_order_value (HIGH_VALUE)
→ approve_high_value_order (HUMAN - IN_PROGRESS) ⏸️
→ [API 호출로 승인: approved=true]
→ decide_approval_result (APPROVED)
→ reserve_inventory_approved → confirm_order_approved
→ Workflow: COMPLETED ✅
```

**결과:** Order=CONFIRMED (승인 후 처리)

### Test 3: 고액 주문 거부 (200,000원)
```json
{
  "orderNo": "ORD-W10-003",
  "totalAmount": 200000,
  "items": [{"productId": "PROD-001", "quantity": 4, "unitPrice": 50000}]
}
```

**흐름:**
```
create_order → authorize_payment → check_order_value (HIGH_VALUE)
→ approve_high_value_order (HUMAN - IN_PROGRESS) ⏸️
→ [API 호출로 거부: approved=false]
→ decide_approval_result (REJECTED)
→ refund_payment_rejected → cancel_order_rejected
→ Workflow: COMPLETED ✅
```

**결과:** Order=CANCELED, Payment=REFUNDED (거부 후 환불)

## HUMAN Task ID 조회 방법

```bash
# 워크플로우 실행
WORKFLOW_ID=$(curl -sS -X POST "http://localhost:8080/api/workflow/manual_approval" \
  -H "Content-Type: application/json" -d '{...}' | tr -d '"')

# HUMAN task ID 조회
TASK_ID=$(curl -sS "http://localhost:8080/api/workflow/${WORKFLOW_ID}" | \
  jq -r '.tasks[] | select(.taskDefName == "approve_high_value_order") | .taskId')

echo "Task ID: ${TASK_ID}"
```

## WAIT Task vs HUMAN Task

| 항목 | HUMAN Task | WAIT Task |
|------|-----------|-----------|
| **용도** | 사람의 승인/개입 대기 | 외부 이벤트 대기 |
| **의미론적** | "관리자 승인 필요" | "이벤트 대기" |
| **완료 방법** | Task Update API | Task Update API / Event |
| **Worker** | 불필요 | 불필요 |
| **실무 사용** | 승인 워크플로우 | 입금 확인, 외부 시스템 응답 |

실질적으로는 거의 동일하지만, HUMAN은 명확히 "사람의 개입"을 나타냅니다.

## 실무 활용 예시

### 1. 고액 주문 승인
- 특정 금액 이상 주문은 관리자 승인 필요
- 사기 의심 주문 차단

### 2. 위험 거래 검토
- 첫 구매 고객의 고액 주문
- 해외 배송 주문
- 특이 패턴 감지 시

### 3. 재고 부족 시 대체 상품 제안
- HUMAN task로 CS 담당자에게 알림
- 담당자가 고객에게 대체 상품 제안
- 승인 시 대체 상품으로 주문 진행

### 4. B2B 견적 승인
- 대량 구매 견적 요청
- 영업 담당자 검토 후 승인

## 핵심 포인트

1. **Worker 불필요**: HUMAN Task는 System Task로 별도 구현 없음
2. **API 기반 제어**: 모든 승인/거부는 Task Update API로 처리
3. **유연한 통합**: Slack, 이메일, 모바일 앱 등 다양한 방식으로 통합 가능
4. **타임아웃 설정 가능**: workflow timeout으로 장기간 미승인 시 자동 처리

## 개선 아이디어

### 타임아웃 자동 거부
```json
{
  "timeoutPolicy": "TIME_OUT_WF",
  "timeoutSeconds": 3600
}
```
1시간 내 승인 없으면 워크플로우 타임아웃

### 승인 이력 기록
```java
// ApprovalHistoryWorker 추가
{
  "name": "record_approval",
  "inputParameters": {
    "orderNo": "...",
    "approver": "${approve_high_value_order.output.approver}",
    "decision": "${approve_high_value_order.output.approved}",
    "timestamp": "..."
  }
}
```

### 다단계 승인
```
approve_level1 (HUMAN) → approve_level2 (HUMAN) → confirm
```
팀장 승인 → 임원 승인 → 최종 확정

## Task 정의 파일

- 메인 워크플로우: [W10_manual_approval.json](W10_manual_approval.json)
- Task 정의: 기존 Task 재사용 (HUMAN Task는 별도 정의 불필요)

## 참고 문서

- [Conductor HUMAN Task Documentation](https://conductor.netflix.com/documentation/configuration/workflowdef/systemtasks/human-task.html)
- [Conductor Task Update API](https://conductor.netflix.com/devguide/how-tos/Tasks/updating-tasks.html)
