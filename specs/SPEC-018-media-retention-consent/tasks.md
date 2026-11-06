---
spec: SPEC-018
repo: backend
updated: 2026-11-06
---

# Tasks: SPEC-018 학습용 보존 동의와 보존 키 — backend

규칙

- 태스크 하나는 커밋 하나에서 PR 하나 사이의 크기로 잡는다(반나절 이하).
- `[P]`는 다른 태스크와 병렬로 진행할 수 있다는 뜻이다. `[R-n]`은 담당 요구사항이다.
- 태스크 하나는 요구사항 하나의 Red → Green → Refactor 사이클이다. 테스트와 구현을 같은 태스크에서 끝내서, 커밋마다 테스트가 통과하는 상태를 유지한다.
- 완료한 태스크는 `[x]`로 바꾼다. 커밋한 뒤에는 커밋 해시를 덧붙인다.

경로 약어: `M` = `services/media-service/src`

## Phase 0: 준비

- [x] T001 마이그레이션 `videos.retention_consent`, `retention_settings`, `retained_videos` — 테스트 `M/test/.../SchemaMigrationTest.java`, 구현 `M/main/resources/db/migration/V202611061400__create_retention.sql`
- [x] T002 [R-11] 보존 키 = HMAC-SHA256 16진 소문자 64자(RFC 4231 시험 벡터) — 테스트 `M/test/.../retention/application/RetentionKeysTest.java`, 구현 `retention/application/{RetentionKeys,RetentionSettingsProperties}`
- [x] T003 [R-12] 비밀 키가 없거나 32바이트보다 짧으면 기동 실패 — 테스트 `RetentionKeysTest`, 구현 `RetentionKeys` (빈 키·Base64 오류·설정 없음은 구현 전에도 예외로 기동 실패. 31바이트 키로 Red 확인)

## Phase 1: US-1 학습용 보존에 동의 (P1)

- [x] T004 [R-1] 보존 설정 조회, 바꾼 적 없으면 false — 테스트 `M/test/.../retention/api/RetentionSettingsApiTest.java`, 구현 `retention/{api,application,domain,infra}`
- [x] T005 [R-2] 기본값 변경 저장, 기존 영상 동의는 그대로 — 테스트 `RetentionSettingsApiTest`, 구현 `RetentionService#updateSettings`
- [ ] T006 [R-3] [R-5] 동의 값 없이 업로드 자리를 받으면 계정 기본값, 응답에 담김 — 테스트 `M/test/.../video/api/CreateVideoConsentApiTest.java`, 구현 `Video`, `CreateVideoService`, `VideoResponse`
- [ ] T007 [R-4] 요청의 동의 값이 기본값보다 우선 — 테스트 `CreateVideoConsentApiTest`, 구현 `CreateVideoRequest`, `CreateVideoService`
- [ ] T008 [R-8] [R-9] 동의한 영상 삭제 시 `video.deleted.retention`(키·1년 뒤)과 최소 기록 — 테스트 `M/test/.../event/VideoDeletedEventTest.java`, 구현 `RetentionService#retain`, `DeleteVideoService`, `VideoDeleted`
- [ ] T009 [R-17] 보존 만료 시각이 지난 최소 기록을 지움, 이벤트 없음 — 테스트 `M/test/.../retention/application/RetentionExpiryTest.java`, 구현 `RetentionExpiryJob`, `retention/infra/scheduling`

## Phase 2: US-2 동의하지 않은 영상 삭제 (P1)

- [ ] T010 [R-10] 미동의 영상 삭제 시 `retention` 없음, 최소 기록 없음 — 테스트 `VideoDeletedEventTest`, 구현 `DeleteVideoService`

## Phase 3: US-3 동의 철회 (P1)

- [ ] T011 [R-6] 소유자가 영상 동의를 바꾸면 200과 영상, 이벤트 없음 — 테스트 `M/test/.../retention/api/VideoConsentApiTest.java`, 구현 `RetentionService#changeConsent`, `Video#changeRetentionConsent`
- [ ] T012 [R-7] 없음·삭제·남의 영상(ADMIN 포함)이면 404 — 테스트 `VideoConsentApiTest`, 구현 `RetentionService#changeConsent`
- [ ] T013 [R-13] 보존 중인 삭제 영상 목록(내 것만, 삭제 최신순, ID·시각만) — 테스트 `M/test/.../retention/api/RetainedVideosApiTest.java`, 구현 `RetentionService#listRetained`
- [ ] T014 [R-14] [R-18] 영상별 철회: `video.retention.revoked`(같은 키, 계약 스키마) 1건, 기록 삭제, 204 — 테스트 `RetainedVideosApiTest`, 구현 `RetentionService#revoke`, `event/VideoRetentionRevoked`
- [ ] T015 [R-15] 없음·이미 철회·남의 기록이면 404 `MEDIA_RETAINED_VIDEO_NOT_FOUND` — 테스트 `RetainedVideosApiTest`, 구현 `RetentionService#revoke`, `MediaErrorCode`
- [ ] T016 [R-16] 전체 철회: 기록마다 이벤트, 모두 삭제, 기록이 없어도 204 — 테스트 `RetainedVideosApiTest`, 구현 `RetentionService#revokeAll`

## Phase 4: 마무리

- [ ] T017 [P] [NFR-3] 같은 기록에 철회 동시 10건이면 이벤트 1건 — 테스트 `M/test/.../retention/application/ConcurrentRevokeTest.java`
- [ ] T018 [P] 보존 API 6개와 `retentionConsent` 요청이 계약 1.3.1과 일치 — 테스트 `MediaContractTest`
- [ ] T019 [P] [NFR-1] 비밀 키가 로그에 없음 — 테스트 `M/test/.../SensitiveLogTest.java`
- [ ] T020 [P] [NFR-2] 보존 API p95 300ms 이하 — 테스트 `M/test/.../MediaLatencyTest.java`
- [ ] T021 설정·예시: `application.yml`, `.env.example`, README

## 완료 조건 (Definition of Done)

- [ ] 이 레포가 담당하는 모든 R-*, NFR-*의 테스트 통과
- [ ] lint, 타입 검사, 전체 테스트 통과
