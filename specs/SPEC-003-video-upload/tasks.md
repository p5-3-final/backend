---
spec: SPEC-003
repo: backend
updated: 2026-11-03
---

# Tasks: SPEC-003 영상 업로드와 관리 — backend

규칙

- 태스크 하나는 커밋 하나에서 PR 하나 사이의 크기로 잡는다(반나절 이하).
- `[P]`는 다른 태스크와 병렬로 진행할 수 있다는 뜻이다. `[R-n]`은 담당 요구사항이다.
- 태스크 하나는 요구사항 하나의 Red → Green → Refactor 사이클이다. 테스트와 구현을 같은 태스크에서 끝내서, 커밋마다 테스트가 통과하는 상태를 유지한다.
- 완료한 태스크는 `[x]`로 바꾼다. 커밋한 뒤에는 커밋 해시를 덧붙인다.

경로 약어: `M` = `services/media-service/src`, `CM` = `libs/common-messaging/src`

## Phase 1: 준비

- [x] T001 버전 카탈로그에 AWS SDK BOM, spring-kafka, Testcontainers Kafka, JSON Schema 검증기 추가 — `gradle/libs.versions.toml` (e8094c0)
- [x] T002 [NFR-3] Outbox 기록·발행: 같은 트랜잭션 기록, 1초 주기 발행(키·`traceparent` 헤더), 실패 시 같은 eventId로 재시도 — 테스트 `CM/test/.../OutboxPublisherTest.java`(Testcontainers PostgreSQL·Kafka), 구현 `CM/main/java/com/vlm/common/messaging/` (e8094c0)
- [x] T003 media-service 모듈 생성: settings 등록, 빌드 파일, 애플리케이션 클래스, `application{,-local}.yml`, 마이그레이션 `V202611031200__create_media_tables.sql`, ArchUnit 테스트, 테스트 지원(PostgreSQL·Kafka·SeaweedFS 컨테이너와 버킷), 저장소 설정 테스트(주소를 비우면 S3 기본 주소·가상 호스트 방식, 넣으면 그 주소·path-style) — `services/media-service/`, 테스트 `M/test/.../infra/storage/S3ClientConfigTest.java` (e8094c0)

## Phase 2: US-1 작은 영상 업로드 (P1)

- [x] T004 [R-1] 업로드 자리 요청 시 `UPLOADING` 영상 생성, 201과 `Location` — 테스트 `M/test/.../api/CreateVideoApiTest.java`, 구현 `M/main/.../{api/VideoController,application/CreateVideoService,domain/Video,infra/persistence}` (0383afb)
- [x] T005 [P] [R-4] 크기 0 또는 5GB 초과면 400 `MEDIA_FILE_SIZE_INVALID` — 테스트 `CreateVideoApiTest`, 구현 `domain/VideoFilePolicy` (0383afb)
- [x] T006 [P] [R-5] 확장자·MIME이 허용 4종 짝이 아니면 400 `MEDIA_UNSUPPORTED_FORMAT` — 테스트 `M/test/.../domain/VideoFilePolicyTest.java`, `CreateVideoApiTest`, 구현 `domain/VideoFilePolicy` (0383afb)
- [x] T007 [R-6] 저장 키는 `sources/<videoId>/original`이고 파일 이름을 쓰지 않음 — 테스트 `CreateVideoApiTest`, 구현 `domain/StorageKey` (0383afb)
- [x] T008 [R-2] [NFR-6] 100MB 이하는 POST 정책(크기 고정, 1시간) 발급, 같은 크기는 저장소가 받고 다른 크기는 거절 — 테스트 `M/test/.../infra/storage/PostPolicySignerTest.java`, `M/test/.../api/SingleUploadTest.java`, 구현 `infra/storage/{PostPolicySigner,S3UploadStorage}` (0383afb)
- [x] T009 [R-7] 업로드된 파일이 있으면 완료 요청으로 `UPLOADED`, 200 — 테스트 `M/test/.../api/CompleteUploadApiTest.java`, 구현 `application/CompleteUploadService` (0383afb)
- [x] T010 [R-9] [R-21] 완료 시 `video.uploaded`를 같은 트랜잭션에 한 번 기록, payload가 계약 스키마와 일치 — 테스트 `M/test/.../event/VideoUploadedEventTest.java`, 구현 `application/event/VideoEvents` (0383afb)
- [x] T011 [R-10] 파일이 없거나 크기가 다르면 409 `MEDIA_UPLOAD_INCOMPLETE`, 상태 유지 — 테스트 `CompleteUploadApiTest`, 구현 `CompleteUploadService` (0383afb)
- [x] T012 [R-11] 이미 완료된 영상에 다시 요청하면 200, 이벤트 재기록 없음 — 테스트 `CompleteUploadApiTest`, 구현 `CompleteUploadService` (0383afb)
- [x] T013 [NFR-2] 같은 영상 완료 요청 10건 동시에도 전이·이벤트 한 번 — 테스트 `M/test/.../application/ConcurrentCompleteTest.java`, 구현 `infra/persistence`(행 잠금 조회) (0383afb)

## Phase 3: US-2 큰 영상 멀티파트 업로드 (P1)

- [x] T014 [R-3] [NFR-6] 100MB 초과면 멀티파트 시작, 조각 크기(64MiB)와 조각별 주소(1시간) 반환 — 테스트 `M/test/.../domain/UploadPlanTest.java`, `M/test/.../api/MultipartUploadTest.java`, 구현 `domain/UploadPlan`, `S3UploadStorage` (d24603a)
- [x] T015 [R-8] 조각 목록으로 완료 요청 시 합친 뒤 `UPLOADED` — 테스트 `MultipartUploadTest`, 구현 `CompleteUploadService`, `S3UploadStorage` (d24603a)

## Phase 4: US-3 내 영상 보기 (P1)

- [x] T016 [R-14] 내 영상만 최신순, 기본 20·최대 100개, 삭제 제외 — 테스트 `M/test/.../api/ListVideosApiTest.java`, 구현 `application/VideoQueryService`, `infra/persistence` (8d50cf4)
- [x] T017 [R-15] 상세에 ID·파일 이름·크기·형식·상태·시각 — 테스트 `M/test/.../api/GetVideoApiTest.java`, 구현 `VideoQueryService` (8d50cf4)
- [x] T018 [R-16] 없음·삭제됨·권한 없음은 구분 없이 404 `MEDIA_VIDEO_NOT_FOUND` — 테스트 `GetVideoApiTest`, 구현 `domain/VideoAccess` (8d50cf4)

## Phase 5: US-4 영상 삭제 (P2)

- [x] T019 [R-18] 소유자가 삭제하면 `DELETED`, 204, 목록·상세에서 사라짐 — 테스트 `M/test/.../api/DeleteVideoApiTest.java`, 구현 `application/DeleteVideoService` (bbba357)
- [x] T020 [R-17] ADMIN은 남의 영상도 상세 조회·삭제 가능 — 테스트 `GetVideoApiTest`, `DeleteVideoApiTest`, 구현 `domain/VideoAccess` (bbba357)
- [x] T021 [R-19] [R-21] 삭제 시 `video.deleted`를 한 번 기록, payload가 계약 스키마와 일치 — 테스트 `M/test/.../event/VideoDeletedEventTest.java`, 구현 `DeleteVideoService`, `VideoEvents` (bbba357)
- [x] T022 [R-20] 삭제된 영상의 저장소 파일과 끝나지 않은 조각 정리, 실패하면 다음 주기에 재시도 — 테스트 `M/test/.../application/StorageCleanupTest.java`, 구현 `application/StorageCleanupJob` (bbba357)

## Phase 6: US-5 끝나지 않은 업로드 정리 (P2)

- [x] T023 [R-13] `UPLOADING` 24시간 경과 시 `EXPIRED`, 저장소 정리 — 테스트 `M/test/.../application/UploadExpiryTest.java`, 구현 `application/UploadExpiryJob` (9dbc4df)
- [x] T024 [R-12] `EXPIRED`·`DELETED` 영상의 완료 요청은 409 `MEDIA_INVALID_STATE` — 테스트 `M/test/.../domain/VideoTest.java`, `CompleteUploadApiTest`, 구현 `domain/Video` (9dbc4df)

## Phase 7: 마무리

- [x] T025 media-service 응답과 `media-service.yaml` 스키마 일치 확인 — 테스트 `M/test/.../contract/MediaContractTest.java` (c6d99dc)
- [x] T026 [P] [NFR-1] 영상 바이트를 받는 엔드포인트가 없음(multipart/form-data 거절) — 테스트 `M/test/.../api/NoUploadBytesTest.java` (c6d99dc)
- [x] T027 [P] [NFR-4] 업로드 자리·완료·목록·상세 p95 300ms 이하 — 테스트 `M/test/.../MediaLatencyTest.java` (c6d99dc)
- [x] T028 [P] [NFR-5] 로그에 업로드 주소(서명)·파일 내용 없음 — 테스트 `M/test/.../SensitiveLogTest.java` (c6d99dc)
- [x] T029 환경변수 반영: 레포 `.env.example`, README 명령어·로컬 실행. 루트 `local/`(Kafka, SeaweedFS, media_db, media-service)은 docs 레포 PR로 추가하고 compose로 단일·멀티파트 업로드와 `video.uploaded` 수신 시연
- [ ] T030 (infra 준비 후 진행) 서비스 workflow `.github/workflows/media-service.yml` (이미지 빌드·ECR push·gitops 갱신)

## 완료 조건 (Definition of Done)

- [x] 이 레포가 담당하는 모든 R-*, NFR-*의 테스트 통과
- [x] lint, 타입 검사, 전체 테스트 통과
- [x] plan.md와 실제 구현이 일치함
- [ ] docs 레포 spec 상태 갱신 (`Implementing` / `Done`)
