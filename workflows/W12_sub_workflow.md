# W12: Sub-Workflow (배송 처리 모듈화)

## 개요
**SUB_WORKFLOW** 타입을 사용하여 배송 처리 로직을 독립적인 서브 워크플로우로 분리하고, 일반 배송과 특급 배송에서 재사용하는 워크플로우입니다.

## Sub-Workflow란?

**Sub-Workflow**는 워크플로우 내에서 다른 워크플로우를 task처럼 호출하여 재사용하는 패턴입니다.

### 핵심 특징
- **모듈화**: 복잡한 로직을 독립적인 워크플로우로 분리
- **재사용성**: 여러 메인 워크플로우에서 동일한 서브 워크플로우 재사용
- **독립 실행**: 별도의 워크플로우 ID로 실행 추적
- **출력 전달**: 서브 워크플로우의 output을 메인 워크플로우로 전달

### Java 함수 호출과 비교
```java
// Java 함수
public OrderResult processOrder() {
    createOrder();
    authorizePayment();
    ShippingResult shipping = processShipping();  // 함수 호출
    confirmOrder(shipping);
}

// Conductor Sub-Workflow
메인 워크플로우:
  - create_order
  - authorize_payment
  - SUB_WORKFLOW: shipping_process_subflow  ← 워크플로우 호출
  - confirm_order (서브 워크플로우 output 사용)
```

## 워크플로우 구조

### 서브 워크플로우: `shipping_process_subflow`
```
1. allocate_warehouse
   - 배송 유형(STANDARD/EXPRESS)에 따라 창고 할당
   - Output: warehouseId, warehouseLocation

2. pack_items
   - 할당된 창고에서 상품 포장
   - Output: packagesCount, packingStatus

3. assign_courier
   - 배송 유형에 따라 택배사 배정
   - Output: courierId, estimatedDeliveryDate, deliveryDays

4. generate_tracking
   - 운송장 번호 생성
   - Output: trackingNumber, trackingUrl
```

### 메인 워크플로우: `order_with_shipping_subflow`
```
1. create_order
   ↓
2. authorize_payment
   ↓
3. reserve_inventory
   ↓
4. process_shipping (SUB_WORKFLOW) ← 서브 워크플로우 호출
   ├─ allocate_warehouse
   ├─ pack_items
   ├─ assign_courier
   └─ generate_tracking
   ↓
5. confirm_order (서브 워크플로우 output 사용)
   - trackingNumber: ${process_shipping.output.trackingNumber}
   - estimatedDeliveryDate: ${process_shipping.output.estimatedDeliveryDate}
```

## Sub-Workflow 정의 방법

### 1. 서브 워크플로우 정의 (sub_shipping_process.json)
```json
{
  "name": "shipping_process_subflow",
  "version": 1,
  "tasks": [...],
  "outputParameters": {
    "warehouseId": "${allocate_warehouse.output.warehouseId}",
    "trackingNumber": "${generate_tracking.output.trackingNumber}",
    "estimatedDeliveryDate": "${assign_courier.output.estimatedDeliveryDate}"
  }
}
```

**핵심**: `outputParameters`로 서브 워크플로우의 최종 출력 정의

### 2. 메인 워크플로우에서 호출
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

**핵심**: `type: "SUB_WORKFLOW"`, `subWorkflowParam`으로 호출할 워크플로우 지정

### 3. 서브 워크플로우 출력 사용
```json
{
  "name": "confirm_order",
  "inputParameters": {
    "trackingNumber": "${process_shipping.output.trackingNumber}",
    "estimatedDeliveryDate": "${process_shipping.output.estimatedDeliveryDate}"
  }
}
```

**핵심**: `${taskReferenceName.output.fieldName}` 형식으로 접근

## 실행 방법

```bash
./scripts/scenarios/w12_sub_workflow.sh
```

## 테스트 시나리오

### Test 1: 일반 배송 (STANDARD)
```json
{
  "orderNo": "ORD-W12-001",
  "deliveryType": "STANDARD",
  "deliveryAddress": "Seoul, Gangnam",
  "items": [{"productId": "PROD-001", "quantity": 3}]
}
```

**흐름:**
```
create_order → authorize_payment → reserve_inventory
→ SUB_WORKFLOW: shipping_process_subflow
  ├─ allocate_warehouse → WH-STANDARD-01 (경기도 창고)
  ├─ pack_items → 1 package
  ├─ assign_courier → COURIER-STANDARD-XX (3일 배송)
  └─ generate_tracking → TRK-XXXXXX
→ confirm_order (tracking: TRK-XXXXXX, ETA: 2026-01-08)
→ Workflow: COMPLETED ✅
```

**결과:**
- Sub-workflow ID: 독립적인 워크플로우 ID 생성
- Order status: CONFIRMED
- Warehouse: WH-STANDARD-01
- Estimated delivery: 3 days (2026-01-08)

### Test 2: 특급 배송 (EXPRESS)
```json
{
  "orderNo": "ORD-W12-002",
  "deliveryType": "EXPRESS",
  "deliveryAddress": "Seoul, Gangnam",
  "items": [{"productId": "PROD-001", "quantity": 2}]
}
```

**흐름:**
```
create_order → authorize_payment → reserve_inventory
→ SUB_WORKFLOW: shipping_process_subflow
  ├─ allocate_warehouse → WH-EXPRESS-01 (서울 도심 창고)
  ├─ pack_items → 1 package
  ├─ assign_courier → COURIER-EXPRESS-XX (익일 배송)
  └─ generate_tracking → TRK-YYYYYY
→ confirm_order (tracking: TRK-YYYYYY, ETA: 2026-01-06)
→ Workflow: COMPLETED ✅
```

**결과:**
- Sub-workflow ID: 다른 독립적인 워크플로우 ID 생성
- Order status: CONFIRMED
- Warehouse: WH-EXPRESS-01 (도심 창고)
- Estimated delivery: 1 day (2026-01-06)

## 서브 워크플로우 실행 확인

```bash
# 메인 워크플로우에서 서브 워크플로우 ID 추출
SUB_WF_ID=$(curl -sS "http://localhost:8080/api/workflow/${MAIN_WF_ID}" | \
  jq -r '.tasks[] | select(.taskType == "SUB_WORKFLOW") | .subWorkflowId')

# 서브 워크플로우 상세 조회
curl -sS "http://localhost:8080/api/workflow/${SUB_WF_ID}" | jq '.'
```

**서브 워크플로우 특징:**
- 독립적인 `workflowId` 생성
- 메인 워크플로우와 별도로 추적 가능
- Conductor UI에서 독립된 워크플로우로 표시

## Sub-Workflow vs SIMPLE Task

| 항목 | Sub-Workflow | SIMPLE Task |
|------|--------------|-------------|
| **복잡도** | 여러 task의 조합 | 단일 작업 |
| **재사용성** | 여러 워크플로우에서 재사용 | 워크플로우마다 정의 |
| **독립성** | 독립 워크플로우 ID | 메인 워크플로우의 task |
| **추적** | 별도 실행 기록 | 메인 워크플로우 내 task |
| **출력** | outputParameters 정의 | task output |
| **등록** | 별도 워크플로우 등록 필요 | task 정의만 필요 |

## 등록 순서 중요!

```bash
# 1. 서브 워크플로우 먼저 등록
curl -X POST "http://localhost:8080/api/metadata/workflow" \
  -d @workflows/sub_shipping_process.json

# 2. 메인 워크플로우 등록 (서브를 참조)
curl -X POST "http://localhost:8080/api/metadata/workflow" \
  -d @workflows/W12_sub_workflow.json
```

**주의**: 서브 워크플로우를 먼저 등록하지 않으면 메인 워크플로우 등록 실패!

우리 프로젝트의 `register_workflows.sh`는 자동으로 순서 보장:
```bash
# sub_*.json 먼저 등록
for workflow in "${WORKFLOW_DIR}"/sub_*.json; do
  register_workflow "$workflow"
done

# W*.json 나중에 등록
for workflow in "${WORKFLOW_DIR}"/W*.json; do
  register_workflow "$workflow"
done
```

## 실무 활용 예시

### 1. 공통 결제 처리
```
[payment_subflow]
  - validate_card
  - authorize_payment
  - fraud_check
  - confirm_payment

[일반 주문] → SUB_WORKFLOW: payment_subflow
[정기 구독] → SUB_WORKFLOW: payment_subflow
[선물 주문] → SUB_WORKFLOW: payment_subflow
```

### 2. 알림 발송
```
[notification_subflow]
  - send_email
  - send_sms
  - send_push

[주문 완료] → SUB_WORKFLOW: notification_subflow
[배송 시작] → SUB_WORKFLOW: notification_subflow
[배송 완료] → SUB_WORKFLOW: notification_subflow
```

### 3. 승인 프로세스
```
[approval_subflow]
  - request_approval (HUMAN Task)
  - check_approval_result
  - notify_decision

[고액 주문] → SUB_WORKFLOW: approval_subflow
[환불 요청] → SUB_WORKFLOW: approval_subflow
[특별 할인] → SUB_WORKFLOW: approval_subflow
```

## 핵심 포인트

1. **모듈화**: 복잡한 로직을 독립 워크플로우로 분리
2. **재사용성**: 여러 워크플로우에서 동일 로직 재사용
3. **독립 추적**: 별도 워크플로우 ID로 실행 이력 관리
4. **출력 전달**: outputParameters로 메인에 결과 전달
5. **등록 순서**: 서브 워크플로우 먼저 등록 필수

## 개선 아이디어

### 동적 서브 워크플로우 선택
```json
{
  "type": "SUB_WORKFLOW",
  "subWorkflowParam": {
    "name": "${workflow.input.deliveryType == 'PREMIUM' ? 'premium_shipping_subflow' : 'standard_shipping_subflow'}",
    "version": 1
  }
}
```

### 병렬 서브 워크플로우 실행
```json
{
  "type": "FORK_JOIN",
  "forkTasks": [
    [{"type": "SUB_WORKFLOW", "subWorkflowParam": {"name": "shipping_subflow"}}],
    [{"type": "SUB_WORKFLOW", "subWorkflowParam": {"name": "notification_subflow"}}]
  ]
}
```

### 서브 워크플로우 버전 관리
```json
{
  "subWorkflowParam": {
    "name": "shipping_process_subflow",
    "version": 2  // 새로운 버전으로 업그레이드
  }
}
```

## Task 정의 파일

- 서브 워크플로우: [sub_shipping_process.json](sub_shipping_process.json)
- 메인 워크플로우: [W12_sub_workflow.json](W12_sub_workflow.json)
- Worker 구현:
  - [AllocateWarehouseWorker.java](../apps/orchestrator-worker/src/main/java/com/example/conductor/orchestrator/worker/AllocateWarehouseWorker.java)
  - [PackItemsWorker.java](../apps/orchestrator-worker/src/main/java/com/example/conductor/orchestrator/worker/PackItemsWorker.java)
  - [AssignCourierWorker.java](../apps/orchestrator-worker/src/main/java/com/example/conductor/orchestrator/worker/AssignCourierWorker.java)
  - [GenerateTrackingWorker.java](../apps/orchestrator-worker/src/main/java/com/example/conductor/orchestrator/worker/GenerateTrackingWorker.java)

## 참고 문서

- [Conductor Sub-Workflow Documentation](https://conductor.netflix.com/documentation/configuration/workflowdef/operators/sub-workflow.html)
- [Workflow Composition Best Practices](https://conductor.netflix.com/documentation/advanced/best-practices.html)
