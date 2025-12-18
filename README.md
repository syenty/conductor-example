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
│ Service │ │ Service   │ │ Service    │
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
├─ services/
│  ├─ order-service/
│  ├─ payment-service/
│  ├─ inventory-service/
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

- Payment Service
  - `failRate=0.3`
  - `delayMs=5000`
- Inventory Service
  - `forceOutOfStock=true`
  - `partialFailIndex=2`

→ **워크플로우는 그대로 두고**, 입력만 바꿔 다양한 실패/보상 시나리오를 재현할 수 있습니다.

---

## 🧠 이 프로젝트로 보여줄 수 있는 것

- 메시지 큐 없이도 복잡한 비즈니스 흐름을 관리하는 방법
- 상태 기반 워크플로우의 장점 (재시도, 가시성, 복구)
- Saga 패턴을 코드가 아닌 워크플로우로 구현하는 방식
- “정해진 흐름”이라도 운영 관점에서는 왜 오케스트레이션이 필요한지

---

## ✨ 한 줄 요약

> **메시지 큐는 ‘일 전달’을 담당하고,  
> Conductor는 그 일을 ‘어떤 순서와 상태로 끝낼지’를 관리한다.**
