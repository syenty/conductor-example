# Spring Boot + Conductor Workflow Sample

이 프로젝트는 **Netflix Conductor(OSS)** 를 사용해  
**마이크로서비스 간 비즈니스 흐름을 워크플로우로 오케스트레이션**하는 방식을 학습·검증하기 위한 샘플입니다.

단순한 메시지 큐 기반 비동기 처리와 달리,  
**Conductor를 통해 흐름·상태·재시도·분기·병렬·보상(Saga)을 중앙에서 관리**하는 것이 목표입니다.

---

## 🎯 목표

- Conductor의 핵심 개념(Workflow / Task / Worker)을 실제로 체감
- “정해진 서비스 흐름”이라도 **왜 워크플로우 엔진이 유용한지** 확인
- Kafka 없이도(또는 최소한으로) 복잡한 비즈니스 흐름을 관리하는 구조 이해
- 포트폴리오로 설명 가능한 **다양한 워크플로우 시나리오** 확보

---

## 🧱 전체 구성

### 기술 스택

- Java 21
- Spring Boot 3.5.7
- Netflix Conductor OSS
- Docker / docker-compose

### Conductor 구성 역할

- **conductor-server**: 워크플로우 정의/실행을 관리하고, 태스크를 스케줄링하며 상태를 중앙에서 제어
- **orchestrator-worker**: Conductor가 할당한 Task를 실제로 수행 (주문/결제/재고 서비스 호출)하고 결과를 보고

### 전통적인 API 호출 + 메시지 큐 방식과 비교

- **전통 방식**: 각 서비스가 직접 API를 호출하거나 메시지 큐를 통해 다음 작업을 트리거
  - 장점: 단순한 흐름에는 구현이 빠르고 인프라가 가벼움
  - 한계: 흐름/상태/재시도/분기/보상 로직이 서비스에 분산돼 가시성과 운영 난이도가 증가
- **Conductor 방식**: 워크플로우를 중앙에서 정의하고 상태를 관리
  - 장점: 흐름 변경이 코드 수정 없이 가능하고, 재시도/분기/보상을 선언적으로 관리
  - 한계: 워크플로우 엔진 운영 및 워커 관리가 필요

### Conductor 핵심 개념

#### 1. Workflow (워크플로우)
- **정의**: 비즈니스 흐름을 정의한 JSON 파일
- **역할**: Task들을 어떤 순서로 실행할지 선언
- **등록**: Conductor Server에 등록 필요 (`/api/metadata/workflow`)
- **예시**: `W1_basic_order.json` → "주문 생성 → 결제 → 재고 예약 → 확정" 흐름 정의

#### 2. Task (태스크)
- **정의**: Workflow 내의 개별 작업 단위
- **Task 정의 (선택)**: Timeout, Retry 등 메타데이터 설정 가능 (JSON 파일)
- **등록**: 부가 기능이 필요할 때만 등록 (`/api/metadata/taskdefs`)
- **등록 없이도 동작**: Worker만 구현되어 있으면 Task 정의 없이도 실행 가능

**Task 정의를 등록해야 하는 경우:**
- ✅ **재시도 필요**: 실패 시 자동 재시도 (예: `authorize_payment` - 3회 재시도)
- ✅ **타임아웃 설정**: 장시간 실행 방지 (예: 외부 API 호출 - 60초 제한)
- ✅ **Rate Limiting**: API 호출 속도 제한 (예: 외부 서비스 - 분당 100회)
- ✅ **Input/Output 검증**: 필수 파라미터 강제 (예: orderNo, amount 필수)
- ❌ **단순 작업**: 위 기능이 불필요하면 Worker만 구현

#### 3. Worker (워커)
- **정의**: Task의 실제 비즈니스 로직을 수행하는 코드
- **역할**: Conductor에서 Task를 polling하고 실행 후 결과 반환
- **필수**: Worker 구현이 없으면 Task가 SCHEDULED 상태로 멈춤
- **예시**: `CreateOrderWorker.java` → order-app API 호출하여 주문 생성

#### Workflow vs Task vs Worker 비교

| 구분 | Workflow | Task 정의 | Worker |
|------|----------|-----------|--------|
| **형태** | JSON 파일 | JSON 파일 (선택) | Java/Python 코드 |
| **등록** | 필수 | 선택 | 코드 배포 |
| **역할** | 흐름 정의 | 메타데이터 설정 | 실제 로직 수행 |
| **없으면?** | 실행 불가 | 기본 설정으로 동작 | **Task가 멈춤** |

### Task Type 간략 설명

- **SIMPLE**: 워커가 수행하는 일반 Task (예: create_order, authorize_payment)
- **DECISION**: 조건 분기 (입력/이전 결과에 따라 다음 Task 선택)
- **FORK_JOIN**: 병렬 실행 후 JOIN으로 합류
- **JOIN**: 병렬 처리 결과를 합류
- **SUB_WORKFLOW**: 다른 워크플로우 호출 (독립된 워크플로우 ID로 실행)
- **WAIT**: 외부 이벤트 대기 (API 호출로 완료)
- **HUMAN**: 사람의 승인/개입 대기

### 서비스 구성

```
┌────────────────────┐
│  Conductor Server  │
│  + UI              │
└─────────▲──────────┘
          │ poll / report
┌─────────┴──────────┐
│ OrchestratorWorker │  ← Conductor Task Worker (Spring Boot)
└─────┬─────┬────────┘
      │     │
      │     │
┌─────▼───┐ ┌▼──────────┐ ┌────────────┐
│ Order   │ │ Payment   │ │ Inventory  │
│ App     │ │ App       │ │ App        │
└─────────┘ └───────────┘ └────────────┘
```

---

## 🔑 설계 원칙

### 1. 기존 서비스 구조 유지

- `order`, `payment`, `inventory` 서비스는 **순수 도메인 API**만 제공
- 오케스트레이션/재시도/분기 로직은 서비스 코드에 넣지 않음

### 2. 워커는 별도 컨테이너로 분리

- `orchestrator-worker` 하나만 두고 모든 Task를 처리
- 워커는 Conductor에서 Task를 poll → 서비스 API 호출 → 결과 보고

### 3. 워크플로우는 “데이터(JSON)”로 관리

- 흐름 변경 시 **서비스 코드 수정 없이** 워크플로우 정의만 변경
- 재시도/타임아웃/보상 정책은 선언적으로 설정

---

## 📦 프로젝트 구조

```
conductor-spring-sample/
├─ docker-compose.yml
├─ workflows/
│  ├─ W1_basic_order.json
│  ├─ W2_payment_retry.json
│  ├─ W3_timeout.json
│  ├─ W4_payment_switch.json
│  ├─ W5_inventory_branch.json
│  ├─ W6_parallel_post_payment.json
│  ├─ W7_parallel_multi_item.json
│  ├─ W8_saga_inventory_fail.json
│  ├─ W9_saga_partial_fail.json
│  ├─ W10_manual_approval.json
│  ├─ W11_event_wait.json
│  └─ W12_sub_workflow.json
├─ apps/
│  ├─ order-app/
│  ├─ payment-app/
│  ├─ inventory-app/
│  └─ orchestrator-worker/
└─ scripts/
   ├─ register_tasks.sh
   └─ register_workflows.sh
```

---

## 🔁 워크플로우 시나리오 구성

### A. 기본 흐름

1. **W1 – Basic Order Flow**  
   `CreateOrder → AuthorizePayment → ReserveInventory → ConfirmOrder`

2. **W2 – Payment Retry**  
   결제 실패 시 자동 재시도 (retry 정책 확인)

3. **W3 – Timeout Handling**  
   지연된 결제 요청을 timeout으로 실패 처리

---

### B. 조건 분기

4. **W4 – Payment Method Switch**

   - CARD → 바로 진행
   - BANK_TRANSFER → 입금 확인 대기

5. **W5 – Inventory Shortage Branch**  
   재고 부족 시 결제 취소 + 주문 취소 분기

---

### C. 병렬 처리

6. **W6 – Parallel Post Payment**  
   결제 후 재고 예약 + 주문 상태 변경 병렬 실행

7. **W7 – Parallel Multi Item Reserve**  
   주문 품목별 재고 예약을 병렬 처리 후 join

---

### D. 보상(Saga)

8. **W8 – Inventory Fail → Refund**  
   재고 실패 시 결제 환불 + 주문 취소

9. **W9 – Partial Inventory Fail**  
   일부 품목 실패 시 전체 보상 트랜잭션 수행

---

### E. 사람/외부 이벤트 개입

10. **W10 – Manual Approval**  
    특정 금액 이상 주문은 관리자 승인 후 진행

11. **W11 – Event Wait**  
    외부 이벤트(입금 확인 API 호출)를 기다렸다가 다음 단계 진행

---

### F. 구조화

12. **W12 – Sub Workflow**  
    결제 처리를 서브 워크플로우로 분리하여 재사용

---

## 🧪 테스트 제어 방식

각 서비스는 시나리오 테스트를 위해 **입력 파라미터로 동작을 제어**합니다.

예:

- Payment App
  - `failRate=0.3`
  - `delayMs=5000`
- Inventory App
  - `forceOutOfStock=true`
  - `partialFailIndex=2`

→ **워크플로우는 그대로 두고**, 입력만 바꿔 다양한 실패/보상 시나리오를 재현할 수 있습니다.

---

## 🧠 이 프로젝트로 보여줄 수 있는 것

- 메시지 큐 없이도 복잡한 비즈니스 흐름을 관리하는 방법
- 상태 기반 워크플로우의 장점 (재시도, 가시성, 복구)
- Saga 패턴을 코드가 아닌 워크플로우로 구현하는 방식
- "정해진 흐름"이라도 운영 관점에서는 왜 오케스트레이션이 필요한지

---

## 🚀 사용 방법

### 1. 워크플로우 등록

```bash
# Task 정의 등록 (Retry, Timeout 등 부가 기능 필요 시)
./scripts/register_tasks.sh

# Workflow 등록 (필수)
./scripts/register_workflows.sh
```

**주의**: Sub-workflow는 메인 워크플로우보다 먼저 등록됨 (스크립트가 자동 처리)

### 2. 워크플로우 실행

```bash
# 시나리오별 테스트 스크립트
./scripts/scenarios/w1_basic_order.sh
./scripts/scenarios/w2_payment_retry.sh
# ... (W3 ~ W12)
```

### 3. 워크플로우 상태 확인

#### Conductor UI 사용 (추천)
```
브라우저에서 http://localhost:5001 접속
→ Executions 메뉴에서 workflow 검색
```

#### API로 확인
```bash
# Workflow 상태 조회
curl -sS "http://localhost:8080/api/workflow/${WORKFLOW_ID}" | jq '.'

# Task 목록 확인
curl -sS "http://localhost:8080/api/workflow/${WORKFLOW_ID}" | \
  jq '.tasks[] | {name: .referenceTaskName, status: .status}'
```

### 4. Sub-Workflow 확인 (W12)

Sub-workflow의 실행 로그를 확인하려면:

```bash
# Sub-workflow ID 자동 추출 및 상세 조회
./scripts/check_subworkflow.sh <메인-워크플로우-ID>

# 예시
./scripts/check_subworkflow.sh 8df0a653-ed98-40b1-9b46-edc8b2a03ce4
```

**출력 내용:**
- 메인 워크플로우 정보
- Sub-workflow ID (자동 추출)
- Sub-workflow의 모든 task 상세 정보:
  - Input/Output 데이터
  - 실행한 Worker ID
  - 시작/종료 시간
  - Task 실행 순서

**Sub-workflow 특징:**
- 독립적인 Workflow ID를 가짐
- Conductor UI에서 별도 워크플로우로 조회 가능
- 각 task의 input/output이 모두 Conductor에 저장됨
- 여러 메인 워크플로우에서 재사용 가능

### 5. Worker 로그 확인

```bash
# 전체 로그
tail -f /tmp/orchestrator-worker.log

# 특정 주문 로그만
grep "ORD-W12-001" /tmp/orchestrator-worker.log

# 특정 Worker 로그만
grep "AllocateWarehouseWorker" /tmp/orchestrator-worker.log
```

---

## ✨ 한 줄 요약

> **메시지 큐는 '일 전달'을 담당하고,
> Conductor는 그 일을 '어떤 순서와 상태로 끝낼지'를 관리한다.**
