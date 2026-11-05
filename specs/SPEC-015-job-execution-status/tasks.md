---
spec: SPEC-015
repo: backend
updated: 2026-11-05
---

# Tasks: SPEC-015 — backend

## Phase 1: 준비

- [x] T001 계약 2.6.0 고정·집계/정책 마이그레이션

## Phase 2: 실행 집계

- [x] T002 [R-1 R-2 R-3 R-4 R-11 R-12] JobExecutionTest 정상·혼합·불명 Red→Green, JobExecutionService/Repository
- [x] T003 [R-5 R-6] 재시도 예산·지연·새 실행·소진, 기존 DispatchService/Repository
- [x] T004 [R-7 R-8 NFR-1] 중복·역순·동시·CLI·실패 주입, 집계와 Outbox 원자성
- [x] T005 [R-9 NFR-3] 누락/불일치 집계 점검 복구, JobExecutionConfiguration
- [x] T006 [R-10] JobExecutionController 소유자·입력·계약 검증
- [x] T007 [NFR-2] ChunkLifecycleKafkaTest 실제 경로·job.status Kafka 검증

## Phase 3: 마무리

- [x] T008 필수 검사·문서·로컬 검증 결과 정리

## 완료 조건 (Definition of Done)

- [x] 모든 담당 R-* 테스트·필수 검사 통과
- [x] 실제 동작·적재 미연결 경계 명시

## 검증 기록

- Red: 집계 미구현 조회 NoSuchElementException, API 404, 누락 projection 복구 시 순번 1로 회귀, 변경 없는 결과·점검 시 순번 증가를 확인했다.
- Green: 전체 backend 546개/Pipeline 198개, 실패·오류·건너뜀 0개. `env -u DEBUG ./gradlew spotlessApply spotlessCheck build` 통과. 기존 IDE DEBUG 환경만 제거했으며 검사 기준을 낮추지 않았다.
- 실제 normalize·chunk-processor(추론 대역)·Kafka·서로 다른 DB·객체 저장소 경로에서 실행 종료와 job.status의 실제 발행을 확인했다. 상태 메시지 개수/마지막 위치 대신 커밋한 종료 eventId를 소비해 중간 상태·중복 전달을 허용하면서 종료 발행을 검증한다.
- 마지막 결과·CLI·점검의 동시 경쟁, 중복·이전 실행·역순, 재시도 예산·지연·식별자, 집계/Outbox 실패 전체 롤백, 새 서비스 인스턴스 점검 복구, 소유자 조회·입력·계약을 검증했다.
- 패키징된 복구 CLI smoke 통과. intake/results/dispatch/publisher/reconciliation enabled 환경에서도 일회성 명령이 수신·스케줄·웹을 강제로 비활성화함을 확인했다.
- 계약 2.6.0 릴리스 SHA-256 검증 후 잠금 갱신. 작업의 커밋·PR·CI·머지 상태는 GitHub에서 확인한다. 배포 전 spec Done 표시 금지.
