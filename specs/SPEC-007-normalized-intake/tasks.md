---
spec: SPEC-007
repo: backend
updated: 2026-11-03
---

# Tasks: SPEC-007 정규화 수신과 계획 저장 — backend

## Phase 1: 문서 정합성

- [x] T001 사용자 확정 방향으로 SPEC·ADR·Draft plan 정리 (기능 코드·계약 수정 없음)
- [ ] T002 backend 메시지 직렬화·설정 버전·과거 데이터 처리 정책 확정 후 계약 문서 작성 (별도 요청 범위)

## Phase 2: US-1 정규화 수신·계획 (후속 구현)

- [ ] T003 [R-1] 확정 스키마 수신 검증 — 테스트 NormalizedContractTest, 구현 수신 adapter
- [ ] T004 [R-2 R-12] 정확한 밀리초 경계 변환 — 테스트 MillisecondPlanningTest, 구현 입력 경계·기존 계산 연결
- [ ] T005 [R-3 R-9] 수신 결과와 계획 원자 저장 — 테스트 IntakeTransactionTest, 구현 application·repository
- [ ] T006 [R-4] 계획 생성·재전달 시 Outbox 0건 — 테스트 IntakeNoDispatchTest

## Phase 3: US-2 중복·장애 (후속 구현)

- [ ] T007 [R-5 R-8 R-11] 메시지/의미상 중복·동시 요청 — 테스트 IntakeIdempotencyTest
- [ ] T008 [R-6 R-7] 입력 충돌·영상당 활성 Job 제한 — 테스트 IntakeConflictTest
- [ ] T009 [R-10] DB 커밋 후 연속 offset 처리 — 테스트 IntakeRecoveryTest

## Phase 4: 마무리

- [ ] T010 계약 검증·필수 빌드·회귀 테스트·문서 결과 갱신

## 완료 조건 (Definition of Done)

- [x] 이번 설계 문서 작성
- [ ] SPEC·backend 계약 확정과 머지
- [ ] 기능별 Red → Green → Refactor 및 필수 검사
- [ ] 구현 결과에 맞게 spec 상태 갱신

배정기·실행·슬롯·Outbox 발행·영상 처리·추론 연동은 후속 별도 SPEC이다. 이 목록은 이번 요청에서 코드를 작성하라는 지시가 아니다.
