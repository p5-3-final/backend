---
spec: SPEC-007
repo: backend
updated: 2026-11-03
---

# Tasks: SPEC-007 영상 정규화 — backend (media-service)

규칙

- 태스크 하나는 커밋 하나에서 PR 하나 사이의 크기로 잡는다(반나절 이하).
- `[P]`는 다른 태스크와 병렬로 진행할 수 있다는 뜻이다. `[R-n]`은 담당 요구사항이다.
- 태스크 하나는 요구사항 하나의 Red → Green → Refactor 사이클이다. 테스트와 구현을 같은 태스크에서 끝내서, 커밋마다 테스트가 통과하는 상태를 유지한다.
- 완료한 태스크는 `[x]`로 바꾼다. 커밋한 뒤에는 커밋 해시를 덧붙인다.

## Phase 1: 준비

- [ ] T001 마이그레이션(정규화 결과 컬럼) — `db/migration/V202611031500__add_video_normalization.sql`, 검증 `SchemaMigrationTest`

## Phase 2: US-1·US-2 상태 전이 (P1)

- [ ] T002 [R-15] [R-16] [R-17] [R-18] 도메인 전이 규칙 — 테스트 `domain/VideoTest`, 구현 `domain/Video`
- [ ] T003 [P] [R-20] 예상 처리 시간 계산 — 테스트 `application/ProcessingEstimateTest`, 구현 `application/ProcessingEstimate`
- [ ] T004 [R-15] [R-16] [R-17] [R-18] [R-19] 행 잠금 후 전이·저장, 삭제 영상 정리 재요청 — 테스트 `application/NormalizationEventServiceTest`, 구현 `application/NormalizationEventService`
- [ ] T005 [NFR-6] 같은 이벤트 동시 10건 — 테스트 `application/ConcurrentNormalizationTest`

## Phase 3: 수신

- [x] T006 메시지 해석·계약 규칙 검증 — 테스트 `api/NormalizationEventParserTest`, 구현 `api/NormalizationEventParser`
- [ ] T007 Kafka 리스너·컨테이너 설정, 잘못된 메시지 건너뜀 — 테스트 `event/NormalizationKafkaTest`, 구현 `api/NormalizationEventListener`, `config/NormalizationKafkaConfig`

## Phase 4: US-1·US-2 조회 응답 (P1)

- [ ] T008 [R-21] 응답 필드(길이·예상 시간·거절 사유) — 테스트 `api/GetVideoApiTest`, `api/ListVideosApiTest`, `contract/MediaContractTest`, 구현 `api/VideoResponse`

## Phase 5: 마무리

- [ ] T009 설정(`application.yml`), README, 로컬 compose 확인

## 완료 조건 (Definition of Done)

- [ ] 이 레포가 담당하는 모든 R-*의 테스트 통과
- [ ] lint, 타입 검사, 전체 테스트 통과
- [ ] plan.md와 실제 구현이 일치함
- [ ] docs 레포 spec 상태 갱신 (`Implementing` / `Done`)
