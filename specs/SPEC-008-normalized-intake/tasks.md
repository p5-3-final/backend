---
spec: SPEC-008
repo: backend
updated: 2026-11-03
---

# Tasks: SPEC-008 정규화 수신과 계획 저장 — backend

## Phase 1: 문서 정합성

- [x] T001 사용자 확정 방향으로 SPEC·ADR·Draft plan 정리 (기능 코드·계약 수정 없음)
- [x] T002 backend 계약·정수 ms·legacy 무백필·REJECTED 기록 정책 확정 및 docs #40 머지

## Phase 2: US-1 정규화 수신·계획 (이번 구현)

- [x] T003 [R-1] 확정 스키마 수신 검증 — 테스트 NormalizedContractTest, 구현 수신 adapter
- [x] T004 [R-2 R-12] 정확한 밀리초 경계 변환 — 테스트 MillisecondPlanningTest, 구현 입력 경계·기존 계산 연결
- [x] T005 [R-3 R-9] 수신 결과와 계획 원자 저장 — 테스트 IntakeTransactionTest, 구현 application·repository
- [x] T006 [R-4] 계획 생성·재전달 시 Outbox 0건 — 테스트 IntakeNoDispatchTest

## Phase 3: US-2 중복·장애 (이번 구현)

- [x] T007 [R-5 R-8 R-11] 메시지/의미상 중복·동시 요청 — 테스트 IntakeIdempotencyTest
- [x] T008 [R-6 R-7] 입력 충돌·영상당 활성 Job 제한 — 테스트 IntakeConflictTest
- [x] T009 [R-10 R-13] DB 커밋 후 연속 offset 처리 — 테스트 IntakeRecoveryTest

## Phase 4: 마무리

- [x] T010 계약 검증·필수 빌드·회귀 테스트·문서 결과 갱신

## 완료 조건 (Definition of Done)

- [x] 이번 설계 문서 작성
- [x] SPEC·backend 계약 확정과 docs #40 머지
- [x] 기능별 Red → Green → Refactor 및 필수 검사
- [ ] 구현 결과에 맞게 spec 상태 갱신

배정기·실행·슬롯·Outbox 발행·영상 처리·추론 연동은 후속 별도 SPEC이다. 후속 실행 기능은 이번 구현에 포함하지 않는다.

## 검증 기록

- Red: 정상 수신 골격이 Job ID를 반환하지 않아 DB 수신 테스트 실패. 저장 구현 후 Green.
- Green: Pipeline 99개 모두 통과 (기존 43, 신규 계약 32·PostgreSQL 수신 21·Kafka 3).
- 저장 장애는 정규화 결과·설정·Job·청크·연결·수신 기록의 6곳에서 각각 검증. 실제 Kafka에서 저장 실패 전후 offset 및 afterCommit 중단 재전달 검증.
- 전체 포맷·build 및 CI 결과는 최종 검증 후 갱신한다.

- 최종 로컬 검증: `env -u DEBUG ./gradlew spotlessCheck build` 통과. 전체 backend 386개, 실패·skip 0. Pipeline 99개.

- 구현 및 T002~T010의 코드·검증 기록: 8b9cdb6. 후속 문서 커밋은 결과 추적만 보완한다.
