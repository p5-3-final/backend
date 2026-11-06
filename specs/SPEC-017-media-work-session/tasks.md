---
spec: SPEC-017
repo: backend
updated: 2026-11-06
---

# Tasks: SPEC-017 작업 세션 — backend

규칙

- 태스크 하나는 커밋 하나에서 PR 하나 사이의 크기로 잡는다(반나절 이하).
- `[P]`는 다른 태스크와 병렬로 진행할 수 있다는 뜻이다. `[R-n]`은 담당 요구사항이다.
- 태스크 하나는 요구사항 하나의 Red → Green → Refactor 사이클이다. 테스트와 구현을 같은 태스크에서 끝내서, 커밋마다 테스트가 통과하는 상태를 유지한다.
- 완료한 태스크는 `[x]`로 바꾼다. 커밋한 뒤에는 커밋 해시를 덧붙인다.

경로 약어: `M` = `services/media-service/src`

## Phase 0: 준비

- [x] T001 계약 잠금 2.9.0, 영상 응답에 `session`(null)·`retentionConsent`(false) — 테스트 `MediaContractTest`(기존 케이스가 2.9.0 계약으로 통과), 구현 `service-contracts.lock`, `video/api/VideoResponse`
- [x] T002 마이그레이션 `work_sessions`·`videos.session_id`·`session_role` — 테스트 `M/test/.../SchemaMigrationTest.java`, 구현 `M/main/resources/db/migration/V202611061000__create_work_sessions.sql`

## Phase 1: US-1 작업 전후 영상 묶기 (P1)

- [x] T003 [R-1] 세션 만들기 201·`Location`·소유자=요청자 — 테스트 `M/test/.../session/api/WorkSessionApiTest.java`, 구현 `shared/domain/{WorkSession,WorkSessionRepository}`, `shared/infra/persistence/WorkSession*`, `session/api/*`, `session/application/WorkSessionService`
- [x] T004 [R-2] 이름 없음·101자, 현장 101자면 400 `MEDIA_INVALID_REQUEST` — 테스트 `WorkSessionApiTest`, 구현 `CreateWorkSessionRequest` (T003에서 요청 DTO 검증을 함께 넣어 Red 없이 통과)
- [x] T005 [R-6] [R-13] 세션·역할로 업로드 자리 발급하면 영상이 세션에 들어가고 응답 `session`에 담김 — 테스트 `M/test/.../video/api/CreateVideoSessionApiTest.java`, 구현 `shared/domain/{Video,SessionRole}`, `video/api/{CreateVideoRequest,VideoResponse,VideoController}`, `video/application/CreateVideoService`
- [x] T006 [R-11] [R-12] `video.uploaded`에 세션이 있으면 `sessionId`·`role`, 없으면 두 필드 없음(계약 스키마 통과) — 테스트 `M/test/.../event/VideoUploadedEventTest.java`, 구현 `video/application/event/VideoUploaded` (R-12는 세션 필드가 없던 때부터 통과)
- [x] T007 [R-10] 영상 100개인 세션이면 409 `MEDIA_SESSION_FULL`, 삭제한 영상은 세지 않음 — 테스트 `CreateVideoSessionApiTest`, 구현 `CreateVideoService`, `shared/domain/{MediaErrorCode,VideoRepository}`

## Phase 2: US-2 세션 확인 (P1)

- [x] T008 [R-7] 역할 순서 → 완료 시각 순 → 미완료는 역할 끝에 만든 시각 순 — 테스트 `M/test/.../shared/domain/SessionOrderTest.java`, 구현 `shared/domain/SessionOrder`
- [x] T009 [R-4] 세션 상세에 "삭제"가 아닌 영상을 R-7 순서로, ADMIN도 조회 — 테스트 `WorkSessionApiTest`, 구현 `WorkSessionService#get`, `VideoRepository#findVisibleInSession`
- [x] T010 [R-5] 없는 세션·남의 세션은 404 `MEDIA_SESSION_NOT_FOUND` — 테스트 `WorkSessionApiTest`, 구현 `WorkSessionService#get`
- [x] T011 [R-3] 세션 목록은 내 세션만 최신순 페이지 — 테스트 `WorkSessionApiTest`, 구현 `WorkSessionService#list`

## Phase 3: US-3 잘못된 세션 지정 (P2)

- [x] T012 [R-8] 세션 ID·역할 중 하나만 있거나 역할이 정해진 값이 아니면 400, 영상 안 만듦 — 테스트 `CreateVideoSessionApiTest`, 구현 `CreateVideoRequest`, `CreateVideoService` (정해지지 않은 역할 값은 역직렬화 오류라 처음부터 400)
- [x] T013 [R-9] 없는 세션·남의 세션(ADMIN 포함)이면 404 `MEDIA_SESSION_NOT_FOUND`, 영상 안 만듦 — 테스트 `CreateVideoSessionApiTest`, 구현 `CreateVideoService`

## Phase 4: 마무리

- [x] T014 [P] [NFR-2] 영상 99개인 세션에 동시 10건이면 1건만 성공 — 테스트 `M/test/.../video/application/ConcurrentSessionVideoTest.java`, 구현 `CreateVideoService`(세션 행 잠금) (T007에서 행 잠금을 넣어 통과. 잠금을 뺀 조회로 바꾸면 실패하는 것을 확인)
- [x] T015 [P] 세션 API 3개와 세션 지정 업로드 자리 발급·404·409 응답이 계약 1.3.1과 일치 — 테스트 `MediaContractTest`
- [ ] T016 [P] [NFR-1] 세션 만들기·목록·상세(영상 100개) p95 300ms 이하 — 테스트 `M/test/.../MediaLatencyTest.java`
- [ ] T017 README API 목록에 세션 API 추가

## 완료 조건 (Definition of Done)

- [ ] 이 레포가 담당하는 모든 R-*, NFR-*의 테스트 통과
- [ ] lint, 타입 검사, 전체 테스트 통과
