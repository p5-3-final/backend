---
spec: SPEC-006
repo: backend
updated: 2026-11-03
---

# Tasks: SPEC-006 원본 영상 재생 — backend

규칙

- 태스크 하나는 커밋 하나에서 PR 하나 사이의 크기로 잡는다(반나절 이하).
- `[P]`는 다른 태스크와 병렬로 진행할 수 있다는 뜻이다. `[R-n]`은 담당 요구사항이다.
- 태스크 하나는 요구사항 하나의 Red → Green → Refactor 사이클이다. 테스트와 구현을 같은 태스크에서 끝내서, 커밋마다 테스트가 통과하는 상태를 유지한다.
- 완료한 태스크는 `[x]`로 바꾼다. 커밋한 뒤에는 커밋 해시를 덧붙인다.

경로 약어: `M` = `services/media-service/src`

## Phase 1: US-1 내 영상 재생 (P1)

- [ ] T001 [R-1] 업로드 완료된 영상의 재생·내려받기 주소와 만료 시각 반환, 두 주소로 GET하면 올린 바이트 — 테스트 `M/test/.../api/VideoPlaybackApiTest.java`, 구현 `api/{VideoController,VideoPlaybackResponse}`, `application/PlaybackService`, `domain/UploadStorage`, `infra/storage/S3UploadStorage`
- [ ] T002 [R-2] 두 주소 유효 시간 1시간(`X-Amz-Expires=3600`), `expiresAt` = 발급 시각 + 1시간 — 테스트 `VideoPlaybackApiTest`, 구현 `PlaybackService`
- [ ] T003 [R-3] 재생 주소 응답의 Content-Type이 신고한 형식, 내려받기 주소는 `attachment`와 원래 파일 이름(한글 포함) — 테스트 `VideoPlaybackApiTest`, `M/test/.../infra/storage/ContentDispositionTest.java`, 구현 `S3UploadStorage`

## Phase 2: US-2 재생할 수 없는 영상 (P1)

- [ ] T004 [R-4] `UPLOADING`·`EXPIRED`면 409 `MEDIA_NOT_PLAYABLE`, 완료 이후 4개 상태는 재생 가능 — 테스트 `M/test/.../domain/VideoTest.java`, `VideoPlaybackApiTest`, 구현 `domain/{Video,MediaErrorCode}`, `PlaybackService`
- [ ] T005 [R-5] 없음·삭제·남의 영상은 404 `MEDIA_VIDEO_NOT_FOUND`, ADMIN은 남의 영상도 발급 — 테스트 `VideoPlaybackApiTest`, 구현 `PlaybackService`(`VideoAccess`)

## Phase 3: 마무리

- [ ] T006 `getVideoPlayback` 200·404·409 응답이 `media-service.yaml` 1.1.0과 일치 — 테스트 `M/test/.../contract/MediaContractTest.java`
- [ ] T007 [P] [NFR-1] 재생 주소 발급 응답이 JSON이고 영상 바이트를 담지 않음 — 테스트 `VideoPlaybackApiTest`
- [ ] T008 [P] [NFR-2] 재생 주소 발급 p95 300ms 이하 — 테스트 `M/test/.../MediaLatencyTest.java`
- [ ] T009 [P] [NFR-3] 재생·내려받기 주소의 서명이 로그에 없음 — 테스트 `M/test/.../SensitiveLogTest.java`
- [ ] T010 README API 목록에 재생 주소 발급 추가

## 완료 조건 (Definition of Done)

- [ ] 이 레포가 담당하는 모든 R-*, NFR-*의 테스트 통과
- [ ] lint, 타입 검사, 전체 테스트 통과
- [ ] plan.md와 실제 구현이 일치함
- [ ] docs 레포 spec 상태 갱신 (`Implementing` / `Done`)
