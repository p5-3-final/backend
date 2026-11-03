---
spec: SPEC-004
repo: backend
updated: 2026-11-03
---

# Tasks: SPEC-004 Pipeline 계획 — backend

## Phase 1: 준비

- [x] T001 모듈과 JDBC·Flyway 구성 — services/pipeline-service/build.gradle.kts (4a3ef9f)

## Phase 2: US-1 구간 계산 (P1)

- [x] T002 [R-1 R-2 R-6] 프로필 선택 — domain/PlanInput.java, ChunkPlannerTest (4a3ef9f)
- [x] T003 [R-3 R-4 R-5] 경계 구간 계산 — domain/ChunkPlanner.java, ChunkPlannerTest (4a3ef9f)
- [x] T004 [R-7] 입력 검증 — domain/PlanInput.java, ChunkPlannerTest (4a3ef9f)

## Phase 3: US-2/US-3 계획 저장 (P1)

- [x] T005 [R-8 R-9 R-11] DB 보존·정렬 — application/PlanningService.java, infra/persistence/JdbcPlanRepository.java, PlanningServiceTest (4a3ef9f)
- [x] T006 [R-10] 부분 저장 실패 롤백 — PlanningServiceTest (4a3ef9f)
- [x] T007 [R-12 R-13] 동일 식별자 재시도·충돌 — PlanningServiceTest (4a3ef9f)
- [x] T008 [R-14 R-15] 활성 제한·종료 후 생성 — PlanningServiceTest (4a3ef9f)
- [x] T009 [R-16 R-17] 동시 요청 검증 — PlanningServiceTest (4a3ef9f)

## Phase 4: 마무리

- [x] T010 필수 검사·아키텍처·README·환경변수 반영 (4a3ef9f)

## 완료 조건 (Definition of Done)

- [x] 담당 요구사항 테스트 통과
- [x] 포맷·서비스 빌드 통과
- [x] plan과 구현 일치
- [x] docs 정책 PR에서 명세 확정 상태 Approved 정리 (배포 완료 전 Done 금지)

## 검증 기록

- Red: 계산 골격은 빈 목록을 반환하여 구간 테스트 1건 실패. 구현 후 통과.
- Red: 저장 없는 서비스 골격에서 PostgreSQL 통합 17건 실패(재조회 없음·충돌 미거절·롤백 미실행). 저장·잠금 구현 후 통과.
- Green: 계산 17건, PostgreSQL 통합 20건, ArchUnit 6건, 합계 43건 통과.
- spotlessCheck와 pipeline-service:build 통과. 전체 backend 빌드는 auth-service·api-gateway·media-service SensitiveLogTest 각 1건 실패. 전체 309건 중 306건 통과, 3건 실패. 타 서비스 코드는 이번 변경 대상이 아니다.
- ADR-0007에 따라 별도 팀원 Approve 대기는 제거하고 명세 확정 상태를 정리한다. 승인 리뷰 이력은 만들지 않는다.
- 배포 workflow·local compose·infra·gitops는 범위 밖으로 보류.

## 기준 main 비교와 재검증

- 기준 main a61b449의 민감 로그 테스트도 IDE의 DEBUG=release를 상속하면 동일 3건 실패.
- DEBUG만 제거한 main은 민감 로그 테스트 4건 모두 통과.
- 같은 환경에서 PR head 전체 build --rerun-tasks는 309건 모두 통과, 실패·skip 0건.
- Spring의 debug 속성으로 해석되는 IDE 환경변수로 프레임워크 DEBUG 로그가 켜져 발생한 차이다. 테스트·로깅 검증 기준을 완화하지 않았다.
