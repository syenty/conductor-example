# Session Log - 2026-01-05

## 목표
W10 (Manual Approval), W11 (Event Wait), W12 (Sub-Workflow) 시나리오 구현

---

## W10: Manual Approval (고액 주문 승인)

### 구현 내용
- **HUMAN Task**를 사용하여 100,000원 이상 고액 주문에 대한 관리자 승인 프로세스 구현
- DECISION task로 주문 금액 체크 후 고액 주문만 승인 대기
- Task Update API를 통한 승인/거부 처리

### 워크플로우 구조
```
create_order
  ↓
authorize_payment
  ↓
check_order_value (DECISION)
  ├─ NORMAL (< 100,000원)
  │  └─ reserve_inventory → confirm_order
  │
  └─ HIGH_VALUE (>= 100,000원)
     ↓
     approve_high_value_order (HUMAN Task) ← 승인 대기
        ↓
        decide_approval_result (DECISION)
           ├─ APPROVED (approved=true)
           │  └─ reserve_inventory → confirm_order
           │
           └─ REJECTED (approved=false)
              └─ refund_payment → cancel_order
```

### 주요 파일
- `workflows/W10_manual_approval.json`
- `scripts/scenarios/w10_manual_approval.sh`
- `workflows/W10_manual_approval.md`

### 발생한 문제와 해결

#### 문제 1: HUMAN task 조회 실패
**증상**: 테스트 스크립트에서 "HUMAN task not found!" 에러
**원인**: `.taskDefName` 대신 `.referenceTaskName`을 사용해야 함
**해결**: jq 쿼리 수정
```bash
# Before
jq -r '.tasks[] | select(.taskDefName == "approve_high_value_order") | .taskId'

# After
jq -r '.tasks[] | select(.referenceTaskName == "approve_high_value_order") | .taskId'
```

#### 문제 2: Timing 이슈로 task 생성 전 조회
**증상**: HUMAN task가 생성되기 전에 스크립트가 조회하여 실패
**해결**: Retry 로직 추가 (최대 10회, 1초 간격)
```bash
MAX_RETRIES=10
RETRY_COUNT=0
while [ $RETRY_COUNT -lt $MAX_RETRIES ]; do
  TASK_ID=$(curl -sS "${CONDUCTOR_BASE_URL}/workflow/${WORKFLOW_ID}" | \
    jq -r '.tasks[] | select(.referenceTaskName == "approve_high_value_order") | .taskId')

  if [ -n "$TASK_ID" ] && [ "$TASK_ID" != "null" ]; then
    break
  fi

  RETRY_COUNT=$((RETRY_COUNT + 1))
  sleep 1
done
```

### 테스트 결과
✅ Test 1: 고액 주문 승인 (150,000원)
- HUMAN task로 대기 → API 승인 → reserve_inventory → confirm_order
- Order status: CONFIRMED

✅ Test 2: 고액 주문 거부 (200,000원)
- HUMAN task로 대기 → API 거부 → refund_payment → cancel_order
- Order status: CANCELED, Payment: REFUNDED

---

## W11: Event Wait (입금 대기)

### 구현 내용
- **WAIT Task**를 사용하여 무통장 입금 확인 대기 프로세스 구현
- 커스텀 REST API (`DepositController`)를 통한 입금 확인 및 워크플로우 재개
- `WorkflowClient`와 `TaskClient` Bean 설정

### 워크플로우 구조
```
create_order (paymentMethod: BANK_TRANSFER)
  ↓
wait_for_deposit (WAIT Task) ← 입금 대기
  ↓ (커스텀 API 호출로 완료)
reserve_inventory
  ↓
confirm_order
```

### 주요 파일
- `workflows/W11_event_wait.json`
- `apps/orchestrator-worker/src/main/java/com/example/conductor/orchestrator/controller/DepositController.java`
- `apps/orchestrator-worker/src/main/java/com/example/conductor/orchestrator/config/WorkerConfig.java`
- `scripts/scenarios/w11_event_wait.sh`
- `workflows/W11_event_wait.md`

### 발생한 문제와 해결

#### 문제 1: WorkflowClient Bean 없음
**증상**:
```
Error creating bean with name 'depositController':
Unsatisfied dependency expressed through constructor parameter 0:
No qualifying bean of type 'com.netflix.conductor.client.http.WorkflowClient' available
```

**원인**: DepositController가 WorkflowClient와 TaskClient를 필요로 하지만 Bean이 설정되지 않음

**해결**: `WorkerConfig.java`에 Bean 추가
```java
@Bean
public WorkflowClient workflowClient(ConductorWorkerProperties properties) {
    WorkflowClient client = new WorkflowClient();
    client.setRootURI(properties.getConductorBaseUrl());
    return client;
}
```

#### 문제 2: Search API 쿼리 문법 오류
**증상**: `Expecting an operator (=, >, <, !=, BETWEEN, IN, STARTS_WITH), but found none`

**원인**: 검색 쿼리에서 `:` 대신 `=` 사용해야 함
```java
// Before
"workflowType:event_wait_deposit AND status:RUNNING"

// After
"workflowType=event_wait_deposit AND status=RUNNING"
```

#### 문제 3: Elasticsearch 에러로 Search API 사용 불가
**증상**: `Elasticsearch exception [type=search_phase_execution_exception]`

**해결**: Search API 대신 workflowId를 직접 받는 방식으로 변경
```java
// Before: Search로 workflow 찾기
SearchResult<WorkflowSummary> searchResult = workflowClient.search(...);

// After: workflowId 직접 사용
String workflowId = request.get("workflowId");
Workflow targetWorkflow = workflowClient.getWorkflow(workflowId, true);
```

#### 문제 4: getWorkflow()로 tasks가 조회되지 않음
**증상**: `Total tasks: 0` (WAIT task를 찾을 수 없음)

**원인**: `getWorkflow(workflowId, false)` 사용 - tasks를 포함하지 않음

**해결**: `includeTasks` 파라미터를 `true`로 변경
```java
// Before
Workflow targetWorkflow = workflowClient.getWorkflow(workflowId, false);

// After
Workflow targetWorkflow = workflowClient.getWorkflow(workflowId, true);
```

#### 문제 5: W11 스크립트에 retry 로직 없음
**해결**: W10과 동일하게 retry 로직 추가
```bash
# Wait for WAIT task to be created (retry logic)
MAX_RETRIES=10
RETRY_COUNT=0
WAIT_TASK_STATUS=""

while [ $RETRY_COUNT -lt $MAX_RETRIES ]; do
  WAIT_TASK_STATUS=$(curl -sS "${CONDUCTOR_BASE_URL}/workflow/${WORKFLOW_ID_1}" | \
    jq -r '.tasks[] | select(.referenceTaskName == "wait_for_deposit") | .status')

  if [ -n "$WAIT_TASK_STATUS" ] && [ "$WAIT_TASK_STATUS" != "null" ]; then
    break
  fi

  RETRY_COUNT=$((RETRY_COUNT + 1))
  sleep 1
done
```

### DepositController API

#### POST /deposit/confirm
입금 확인 및 WAIT task 완료
```bash
curl -X POST "http://localhost:8084/deposit/confirm" \
  -H "Content-Type: application/json" \
  -d '{"workflowId": "xxx", "orderNo": "ORD-W11-001"}'
```

#### GET /deposit/status/{orderNo}
주문의 입금 대기 상태 조회

### 테스트 결과
✅ Test: 무통장 입금 주문 (100,000원)
- create_order → wait_for_deposit (IN_PROGRESS)
- `/deposit/confirm` API 호출 → WAIT task COMPLETED
- reserve_inventory → confirm_order → Workflow COMPLETED
- Order status: CONFIRMED

### WAIT Task vs HUMAN Task 비교

| 항목 | HUMAN Task | WAIT Task |
|------|------------|-----------|
| **용도** | 사람의 승인/개입 대기 | 외부 이벤트 대기 |
| **의미론적** | "관리자 승인 필요" | "이벤트 대기" |
| **완료 방법** | Task Update API | Task Update API / Event |
| **Worker** | 불필요 | 불필요 |
| **실무 사용** | 승인 워크플로우 | 입금 확인, 외부 시스템 응답 |

---

## W12: Sub-Workflow (배송 처리 모듈화)

### 구현 내용
- **SUB_WORKFLOW** 타입을 사용하여 배송 처리 로직을 독립적인 워크플로우로 분리
- 일반 배송(STANDARD)과 특급 배송(EXPRESS)에서 동일한 서브 워크플로우 재사용
- 서브 워크플로우 output을 메인 워크플로우로 전달

### 워크플로우 구조

#### 서브 워크플로우: `shipping_process_subflow`
```
allocate_warehouse (창고 할당)
  ├─ STANDARD: WH-STANDARD-01 (경기도)
  └─ EXPRESS: WH-EXPRESS-01 (서울 도심)
  ↓
pack_items (상품 포장)
  ↓
assign_courier (택배사 배정)
  ├─ STANDARD: 3일 배송
  └─ EXPRESS: 익일 배송
  ↓
generate_tracking (운송장 생성)
  ↓
Output: warehouseId, trackingNumber, estimatedDeliveryDate
```

#### 메인 워크플로우: `order_with_shipping_subflow`
```
create_order
  ↓
authorize_payment
  ↓
reserve_inventory
  ↓
process_shipping (SUB_WORKFLOW)
  ├─ allocate_warehouse
  ├─ pack_items
  ├─ assign_courier
  └─ generate_tracking
  ↓
confirm_order (서브 워크플로우 output 사용)
  - trackingNumber: ${process_shipping.output.trackingNumber}
  - estimatedDeliveryDate: ${process_shipping.output.estimatedDeliveryDate}
```

### 주요 파일

#### 워크플로우
- `workflows/sub_shipping_process.json` (서브 워크플로우)
- `workflows/W12_sub_workflow.json` (메인 워크플로우)

#### Worker 구현 (4개 추가)
- `AllocateWarehouseWorker.java` - 배송 유형별 창고 할당
- `PackItemsWorker.java` - 상품 포장
- `AssignCourierWorker.java` - 택배사 배정 및 배송일 계산
- `GenerateTrackingWorker.java` - 운송장 번호 생성

#### 스크립트
- `scripts/scenarios/w12_sub_workflow.sh`
- `scripts/register_workflows.sh` (수정)
- `workflows/W12_sub_workflow.md`

### 등록 스크립트 개선

**문제**: 서브 워크플로우를 먼저 등록하지 않으면 메인 워크플로우 등록 실패

**해결**: `register_workflows.sh` 수정하여 등록 순서 보장
```bash
# 1. 서브 워크플로우 먼저 등록 (sub_*.json)
for workflow in "${WORKFLOW_DIR}"/sub_*.json; do
  register_workflow "$workflow"
done

# 2. 메인 워크플로우 나중에 등록 (W*.json)
for workflow in "${WORKFLOW_DIR}"/W*.json; do
  register_workflow "$workflow"
done
```

### Sub-Workflow 정의 방법

#### 1. 서브 워크플로우에서 outputParameters 정의
```json
{
  "name": "shipping_process_subflow",
  "outputParameters": {
    "warehouseId": "${allocate_warehouse.output.warehouseId}",
    "trackingNumber": "${generate_tracking.output.trackingNumber}",
    "estimatedDeliveryDate": "${assign_courier.output.estimatedDeliveryDate}"
  }
}
```

#### 2. 메인 워크플로우에서 SUB_WORKFLOW로 호출
```json
{
  "name": "shipping_process_subflow",
  "taskReferenceName": "process_shipping",
  "type": "SUB_WORKFLOW",
  "subWorkflowParam": {
    "name": "shipping_process_subflow",
    "version": 1
  },
  "inputParameters": {
    "orderNo": "${workflow.input.orderNo}",
    "deliveryType": "${workflow.input.deliveryType}"
  }
}
```

#### 3. 서브 워크플로우 output 사용
```json
{
  "name": "confirm_order",
  "inputParameters": {
    "trackingNumber": "${process_shipping.output.trackingNumber}",
    "estimatedDeliveryDate": "${process_shipping.output.estimatedDeliveryDate}"
  }
}
```

### 테스트 결과

✅ **Test 1: 일반 배송 (STANDARD)**
- 메인 Workflow ID: `8df0a653-ed98-40b1-9b46-edc8b2a03ce4`
- 서브 Workflow ID: `69c03cf4-afc2-45f8-b88e-d6d844ff061d` (독립 실행)
- Warehouse: WH-STANDARD-01 (경기도)
- Tracking: TRK-560F0EB8
- ETA: 2026-01-08 (3일 배송)
- Order status: CONFIRMED

✅ **Test 2: 특급 배송 (EXPRESS)**
- 메인 Workflow ID: `91ac8a15-f44e-4889-bb9d-67e7a8120f81`
- 서브 Workflow ID: `0e82d006-9be8-4704-b6e7-7cfa21361854` (독립 실행)
- Warehouse: WH-EXPRESS-01 (서울 도심)
- Tracking: TRK-C962444A
- ETA: 2026-01-06 (익일 배송)
- Order status: CONFIRMED

### Sub-Workflow 핵심 특징

1. **독립적인 워크플로우 ID**: 각 서브 워크플로우는 별도 ID로 실행 추적
2. **재사용성**: 일반 배송과 특급 배송 모두 동일한 서브 워크플로우 사용
3. **출력 전달**: `outputParameters`로 메인 워크플로우에 결과 전달
4. **모듈화**: 복잡한 배송 로직을 독립 모듈로 분리

### Worker 카운트 변화
- 기존: 8개 workers
- W12 이후: 12개 workers (배송 관련 4개 추가)

---

## 전체 통계

### 구현된 시나리오
- **W10**: HUMAN Task를 이용한 고액 주문 승인
- **W11**: WAIT Task와 커스텀 API를 이용한 입금 대기
- **W12**: SUB_WORKFLOW를 이용한 배송 처리 모듈화

### 생성된 파일

#### 워크플로우 정의 (3개)
- `workflows/W10_manual_approval.json`
- `workflows/W11_event_wait.json`
- `workflows/W12_sub_workflow.json`
- `workflows/sub_shipping_process.json` (서브 워크플로우)

#### Worker 구현 (5개)
- `DepositController.java` (REST Controller)
- `AllocateWarehouseWorker.java`
- `PackItemsWorker.java`
- `AssignCourierWorker.java`
- `GenerateTrackingWorker.java`

#### Configuration (1개)
- `WorkerConfig.java` (WorkflowClient, TaskClient Bean 추가)

#### 테스트 스크립트 (3개)
- `scripts/scenarios/w10_manual_approval.sh`
- `scripts/scenarios/w11_event_wait.sh`
- `scripts/scenarios/w12_sub_workflow.sh`

#### 문서 (3개)
- `workflows/W10_manual_approval.md`
- `workflows/W11_event_wait.md`
- `workflows/W12_sub_workflow.md`

#### 스크립트 개선 (1개)
- `scripts/register_workflows.sh` (서브 워크플로우 등록 순서 보장)

### 주요 배운 점

1. **HUMAN Task vs WAIT Task**
   - HUMAN: 명시적으로 "사람의 승인" 의미
   - WAIT: 일반적인 "외부 이벤트 대기"
   - 실질적으로 동일하게 동작하지만 의미론적 차이

2. **Conductor Client Bean 설정**
   - WorkflowClient, TaskClient를 Spring Bean으로 등록
   - REST Controller에서 워크플로우 제어 가능

3. **Sub-Workflow의 힘**
   - 복잡한 로직을 독립 워크플로우로 모듈화
   - 여러 워크플로우에서 재사용 가능
   - 독립적인 실행 추적으로 디버깅 용이

4. **Retry 패턴의 중요성**
   - Task 생성은 비동기적으로 발생
   - 테스트 스크립트에서 retry 로직 필수

5. **등록 순서 관리**
   - 서브 워크플로우는 메인 워크플로우보다 먼저 등록
   - 스크립트로 순서 자동 보장

---

## 다음 단계

### 가능한 확장
1. **W10 개선**: 다단계 승인 (팀장 → 임원)
2. **W11 개선**: 타임아웃 시 자동 주문 취소
3. **W12 확장**: 알림 발송 서브 워크플로우 추가
4. **새로운 시나리오**:
   - W13: DO_WHILE 반복 처리
   - W14: Dynamic Task 동적 실행
   - W15: Event Handler 통합

### 개선 아이디어
- 서브 워크플로우 버전 관리 전략
- 병렬 서브 워크플로우 실행 (FORK + SUB_WORKFLOW)
- 서브 워크플로우 에러 처리 및 보상 로직
