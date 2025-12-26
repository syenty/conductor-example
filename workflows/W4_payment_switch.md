# W4: Payment Method Switch Workflow

## 개요
결제 수단에 따라 다른 처리 흐름을 적용하는 워크플로우입니다. **SWITCH** task를 사용하여 조건 분기를 구현합니다.

- **CARD**: 즉시 결제 승인 후 진행
- **BANK_TRANSFER**: 입금 확인 대기 후 진행

## 워크플로우 실행 순서

### 기본 흐름

```
1. create_order
   ↓
2. decide_payment_method (SWITCH)
   ├─ [CARD] → 3a. authorize_payment_card
   ├─ [BANK_TRANSFER] → 3b. authorize_payment_bank → 3c. wait_for_bank_deposit (WAIT) → 3d. confirm_bank_transfer
   └─ [default] → cancel_order_invalid_method
   ↓
4. reserve_inventory
   ↓
5. confirm_order ✅
```

## Task 상세

### 1. create_order
- **타입**: SIMPLE
- **동작**: 주문 생성
- **입력 파라미터**:
  - orderNo: 주문번호
  - totalAmount: 총 금액
  - currency: 통화
  - customerId: 고객 ID
  - paymentMethod: 결제 수단 (CARD / BANK_TRANSFER)
  - items: 주문 항목 리스트

### 2. decide_payment_method
- **타입**: SWITCH
- **동작**: 결제 수단에 따라 분기
- **분기 조건**: `paymentMethod` 값
- **분기 경로**:
  - `CARD`: 카드 결제 → 즉시 승인
  - `BANK_TRANSFER`: 무통장입금 → 입금 확인 대기
  - `default`: 잘못된 결제 수단 → 주문 취소

---

## 분기 A: CARD 결제

### 3a. authorize_payment_card
- **타입**: SIMPLE
- **동작**: 카드 결제 승인
- **입력 파라미터**:
  - orderNo: 주문번호
  - amount: 결제 금액
  - currency: 통화
  - method: "CARD"
  - failRate: 실패율 (테스트용)
  - delayMs: 지연 시간 (테스트용)
- **결과**: 즉시 결제 승인 → reserve_inventory로 진행

---

## 분기 B: BANK_TRANSFER (무통장입금)

### 3b. authorize_payment_bank
- **타입**: SIMPLE
- **동작**: 무통장입금 결제 정보 생성 (Payment 데이터 생성)
- **입력 파라미터**:
  - orderNo: 주문번호
  - amount: 결제 금액
  - currency: 통화
  - method: "BANK_TRANSFER"
  - failRate: 0 (무통장입금은 실패 없음)
  - delayMs: 0
- **결과**: Payment 데이터 생성 (상태: AUTHORIZED) → wait_for_bank_deposit으로 진행
- **중요**: 이 단계에서 Payment 데이터가 생성되어야 이후 confirm_bank_transfer에서 조회 가능

### 3c. wait_for_bank_deposit
- **타입**: WAIT
- **동작**: 외부 입금 확인 이벤트 대기
- **입력 파라미터**:
  - orderNo: 주문번호
  - expectedAmount: 예상 입금 금액
  - currency: 통화
- **특징**:
  - 워크플로우가 이 task에서 **일시 정지**됨
  - 외부에서 API 호출로 task를 완료시켜야 진행
  - 무통장입금 → 은행 입금 확인 → 관리자 확인 → API 호출

### 3d. confirm_bank_transfer
- **타입**: SIMPLE
- **동작**: 입금 확인 이벤트 기록
- **입력 파라미터**:
  - orderNo: 주문번호
  - amount: 결제 금액
  - currency: 통화
- **결과**: PaymentEvent에 입금 확인 이벤트 기록 → reserve_inventory로 진행

---

## 공통 후속 Task

### 4. reserve_inventory
- **타입**: SIMPLE
- **동작**: 재고 예약
- **입력 파라미터**:
  - orderNo: 주문번호
  - items: 주문 항목 리스트
  - forceOutOfStock: 재고 부족 강제 시뮬레이션 (테스트용)
  - partialFailIndex: 부분 실패 인덱스 (테스트용)

### 5. confirm_order
- **타입**: SIMPLE
- **동작**: 주문 확정
- **입력 파라미터**:
  - orderNo: 주문번호
- **결과**: ✅ 워크플로우 완료

---

## 워크플로우 설정

- **schemaVersion**: 2
- **restartable**: true
- **workflowStatusListenerEnabled**: false
- **timeoutPolicy**: ALERT_ONLY
- **timeoutSeconds**: 3600 (1시간 - 무통장입금 대기 시간 고려)

## 실행 방법

### 테스트 스크립트 실행

```bash
./scripts/scenarios/w4_payment_switch.sh
```

이 스크립트는 2가지 테스트를 실행합니다:
1. **CARD 결제**: 즉시 처리 → 주문 확정
2. **BANK_TRANSFER 결제**: WAIT task에서 대기

### BANK_TRANSFER 입금 확인 (WAIT 완료)

```bash
./scripts/scenarios/w4_complete_deposit.sh <workflow_id>
```

또는 직접 API 호출:

```bash
# 1. Workflow에서 task ID 조회
curl -sS "http://localhost:8080/api/workflow/<workflow_id>" | jq '.tasks[] | select(.referenceTaskName == "wait_for_bank_deposit")'

# 2. WAIT task 완료
curl -X POST "http://localhost:8080/api/tasks" \
  -H "Content-Type: application/json" \
  -d '{
    "workflowInstanceId": "<workflow_id>",
    "taskId": "<task_id>",
    "status": "COMPLETED",
    "outputData": {
      "deposited": true,
      "depositedAt": "2024-01-15T10:30:00Z"
    }
  }'
```

## 핵심 포인트

1. **SWITCH Task**: 조건에 따른 분기 처리 (DECISION의 새로운 버전)
2. **WAIT Task**: 외부 이벤트 대기 (무통장입금 확인)
3. **결제 수단별 처리**: 같은 워크플로우에서 다양한 결제 수단 지원
4. **Human-in-the-loop**: 관리자가 입금 확인 후 워크플로우 진행

## SWITCH vs DECISION

| 항목 | DECISION (레거시) | SWITCH (권장) |
|------|------------------|---------------|
| **표현식** | caseValueParam | expression + evaluatorType |
| **유연성** | 단순 값 비교 | JavaScript 표현식 지원 |
| **권장** | 하위 호환용 | 신규 개발 권장 |

### SWITCH 설정 예시

```json
{
  "type": "SWITCH",
  "expression": "paymentMethod",
  "evaluatorType": "value-param",
  "decisionCases": {
    "CARD": [...],
    "BANK_TRANSFER": [...]
  },
  "defaultCase": [...]
}
```

## 시나리오별 실행 경로

### ✅ CARD 결제 시나리오
```
create_order
→ decide_payment_method [CARD]
→ authorize_payment_card
→ reserve_inventory
→ confirm_order ✅
```

### ✅ BANK_TRANSFER 결제 시나리오
```
create_order
→ decide_payment_method [BANK_TRANSFER]
→ authorize_payment_bank (Payment 데이터 생성)
→ wait_for_bank_deposit (WAIT - 대기)
   ↓ (외부 API 호출로 완료)
→ confirm_bank_transfer (입금 확인 이벤트 기록)
→ reserve_inventory
→ confirm_order ✅
```

### ❌ 잘못된 결제 수단 시나리오
```
create_order
→ decide_payment_method [default]
→ cancel_order_invalid_method ❌
```

## Task 정의 파일

- 워크플로우: [workflows/W4_payment_switch.json](W4_payment_switch.json)
- Task 정의:
  - [tasks/create_order.json](../tasks/create_order.json)
  - [tasks/authorize_payment.json](../tasks/authorize_payment.json)
  - [tasks/confirm_bank_transfer.json](../tasks/confirm_bank_transfer.json)
  - [tasks/reserve_inventory.json](../tasks/reserve_inventory.json)
  - [tasks/confirm_order.json](../tasks/confirm_order.json)
  - [tasks/cancel_order.json](../tasks/cancel_order.json)

## 실무 활용 예시

1. **다양한 결제 수단 지원**:
   - 카드, 무통장입금, 가상계좌, 간편결제 등
   - 각 결제 수단별 처리 흐름을 SWITCH로 분기

2. **입금 확인 자동화**:
   - 은행 API 연동으로 입금 확인
   - 확인 시 자동으로 WAIT task 완료 호출

3. **입금 대기 타임아웃**:
   - timeoutSeconds 설정으로 입금 기한 관리
   - 기한 초과 시 자동 주문 취소
