---
spec: SPEC-030
repo: backend
updated: 2026-11-06
---

# Tasks: SPEC-030 외부 데이터셋 가져오기·특징 내보내기·성능 Gate — backend

규칙

- 태스크 하나는 커밋 하나에서 PR 하나 사이의 크기로 잡는다(반나절 이하).
- `[P]`는 다른 태스크와 병렬로 진행할 수 있다는 뜻이다. `[R-n]`은 담당 요구사항이다.
- 태스크 하나는 요구사항 하나의 Red → Green → Refactor 사이클이다. 테스트와 구현을 같은 태스크에서 끝내서, 커밋마다 테스트가 통과하는 상태를 유지한다.
- 완료한 태스크는 `[x]`로 바꾼다. 커밋한 뒤에는 커밋 해시를 덧붙인다.

경로 약어: `Q` = `services/quality-service/src`(패키지 `com.vlm.quality`), `M` = `services/media-service/src`(패키지 `com.vlm.media`)

선행: SPEC-029 tasks T001~T004(계약 잠금, 모듈 뼈대, 생성 설정, 마이그레이션), T005(`NameBasedUuid`), T020(Golden Set 확정), T028(`stage_timings` 적재)

## Phase 0: 준비

- [x] T001 계약 잠금 5.0.0 — SPEC-029 수정과 함께 이 PR에서 올렸다(4.1.0 → 5.0.0). `scripts/fetch-service-contracts.sh --update 5.0.0`, 테스트 기존 전체 `./gradlew build`
- [x] T002 [P] quality 마이그레이션 `dataset_imports`, `dataset_import_videos`, `golden_dataset_items`, `source_results`, `feature_exports`, `golden_sets.dataset_origin` — 테스트 `Q/test/.../SchemaMigrationTest.java`, 구현 `Q/main/resources/db/migration/V202611071300__create_dataset_imports_exports.sql`
- [x] T003 [P] media 마이그레이션 `videos.retention_grade` — 테스트 `M/test/.../SchemaMigrationTest.java`, 구현 `M/main/resources/db/migration/V202611071300__add_dataset_videos.sql`

## Phase 1: US-1 외부 데이터셋으로 평가 세트 만들기 (P1) — media-service

- [x] T004 [R-5] 데이터셋 영상 등록: 가져오기 버킷 파일을 `sources/<videoId>/original`로 복사, videoId=uuid5(importId, externalId), `UPLOADED`·`DATASET`, 같은 트랜잭션에서 `video.uploaded` Outbox(계약 스키마 통과), 201 — 테스트 `M/test/.../dataset/api/RegisterDatasetVideoApiTest.java`, 구현 `dataset/api/{DatasetVideoController,RegisterDatasetVideoRequest,DatasetVideoResponse}`, `dataset/application/DatasetVideoService`, `shared/domain/{Video#registerDataset,RetentionGrade,UploadStorage}`, `shared/infra/storage/{MediaStorageProperties,S3UploadStorage}`. SeaweedFS 버킷 사이 CopyObject가 안 되면 멈추고 보고한다
- [x] T005 [R-6] 같은 (importId, externalId)면 복사·발행 없이 200, 파일 없음 404 `MEDIA_IMPORT_OBJECT_NOT_FOUND`, 허용 목록 밖 `X-Caller` 403 `MEDIA_CALLER_NOT_ALLOWED`, 형식·크기 위반 400 — 테스트 `RegisterDatasetVideoApiTest`, 구현 `DatasetVideoService`, `config/{InternalCallerProperties,InternalCallerFilter}`
- [x] T006 [R-6] 같은 요청 5건 동시 → 영상 1개, Outbox 1건, 모두 201 또는 200 — 테스트 `M/test/.../dataset/application/ConcurrentDatasetVideoTest.java`, 구현 `DatasetVideoService`(고유 제약 충돌 시 다시 읽기)
- [x] T007 [P] `registerDatasetVideo` 201·200·400·403·404 응답이 `media-service.yaml` 1.4.0과 일치 — 테스트 `M/test/.../contract/MediaContractTest.java`

## Phase 2: US-1 외부 데이터셋으로 평가 세트 만들기 (P1) — quality-service

- [x] T008 [R-3] 분할: `sha256(splitSeed + ":" + externalId)` 앞 8바이트 / 2^64 < `evalRatio`면 `eval` — 테스트 `Q/test/.../dataset/domain/DatasetSplitterTest.java`(고정 값, 비율 0·1), 구현 `dataset/domain/DatasetSplitter`
- [x] T009 [R-4] media 클라이언트: 생성 클라이언트로 `POST /internal/v1/media/dataset-videos`에 importId·externalId·ownerId(요청 운영자)·importKey·contentType과 `X-Caller: quality-service`, `X-Request-Deadline`을 싣고 videoId를 돌려줌, 503·연결 실패 2회 재시도, 4xx는 재시도 안 함 — 테스트 `Q/test/.../dataset/infra/client/MediaDatasetClientTest.java`(WireMock, 요청 본문을 계약 스키마로 검증), 구현 `services/quality-service/build.gradle.kts`(클라이언트 생성), `dataset/infra/client/MediaDatasetClient`, `config/MediaClientConfig`. 재시도 부분은 plan 10절 1(Resilience4j) 확인 뒤 진행한다
- [x] T010 [R-1] 가져오기 요청 → 202 `PENDING`(importId=uuid5(manifestKey), `datasetName: null`), 같은 경로가 진행 중이면 409 `QUALITY_IMPORT_IN_PROGRESS`, 끝난 경로는 다시 `PENDING` — 테스트 `Q/test/.../dataset/api/DatasetImportApiTest.java`, 구현 `dataset/api/DatasetImportController`, `dataset/application/DatasetImportService#request`, `dataset/domain/{DatasetImport,DatasetImportRepository,ImportStatus}`, `dataset/infra/persistence/*`
- [x] T011 [R-2] 매니페스트 없음·스키마 위반·지원하지 않는 `taxonomyVersion`·중복 externalId면 영상 등록 없이 `FAILED`, 오류에 이유 — 테스트 `Q/test/.../dataset/application/ImportManifestValidationTest.java`(WireMock 호출 0건 확인), 구현 `dataset/application/{ImportManifestReader,ImportWorker}`, `dataset/infra/scheduling/ImportWorkerScheduling`, `config/ImportExecutorConfig`
- [x] T012 [R-7] 영상 하나의 등록이 실패하면 그 영상만 오류(처음 100건)에 남고 나머지를 계속, 하나도 없으면 `FAILED`, 아니면 `COMPLETED` — 테스트 `Q/test/.../dataset/application/DatasetImportFlowTest.java`(media 404 하나, 전부 실패, 오류 101건 이상), 구현 `dataset/application/ImportRegistrar`
- [x] T013 [R-8] 등록된 영상 라벨을 `<이름>-dev`·`<이름>-eval`(없으면 `origin: import`로 만듦)에 넣고 각 세트를 새 버전으로 확정, 버전을 가져오기에 기록, 항목 없는 분할은 확정 안 함, 같은 이름의 검토 세트가 있으면 `FAILED`·`QUALITY_GOLDEN_ORIGIN_MISMATCH` — 테스트 `DatasetImportFlowTest`, 구현 `dataset/application/ImportGoldenWriter`, `golden/application/GoldenDatasetService`, `golden/domain/GoldenDatasetItem`, `GoldenFreezeService`
- [x] T014 [R-4] 같은 매니페스트를 다시 가져오면 같은 videoId로 media를 부르고(media 200), 데이터셋 항목은 item_id로 갱신되며 새 버전이 생김 — 테스트 `DatasetImportFlowTest`, 구현 `DatasetImportService`, `GoldenDatasetService#upsert`
- [x] T015 [R-9] 상태 조회: 상태, 영상·등록·실패·라벨·분할별 개수, Golden Set 버전, 오류, 없으면 404 `QUALITY_IMPORT_NOT_FOUND`, 목록은 접수 내림차순 — 테스트 `DatasetImportApiTest`, 구현 `DatasetImportService#get·list`
- [x] T016 [R-10] 데이터셋 세트 확정 매니페스트에 `origin: import`·`datasetOrigin`·`datasetItems`(sourceId·externalId·라벨), `golden-manifest.schema.json` 통과 — 테스트 `Q/test/.../golden/application/DatasetGoldenManifestTest.java`, 구현 `golden/application/GoldenManifestWriter`
- [x] T017 [R-10] 데이터셋 세트에 검토 항목을 넣으면 409 `QUALITY_GOLDEN_ORIGIN_MISMATCH` — 테스트 `Q/test/.../golden/api/GoldenDraftApiTest.java`, 구현 `golden/application/GoldenDraftService#add`
- [x] T018 [P] Pod가 죽어 heartbeat가 끊긴 가져오기를 다른 작업자가 이어받아 남은 영상만 등록 — 테스트 `DatasetImportFlowTest`(진행 중 행과 오래된 heartbeat를 넣고 작업자 실행), 구현 `ImportWorker`(R-1·R-7 동작 유지 확인)

## Phase 3: US-3 예측 학습 특징 (P2)

- [ ] T019 [R-13] `source.completed`를 받으면 영상별로 마지막에 받은 결과 경로·jobId·받은 날짜를 기록, 삭제된 영상이면 무시, `video.deleted`를 받으면 그 행 삭제 — 테스트 `Q/test/.../export/infra/messaging/SourceCompletedIntakeTest.java`, 구현 `export/infra/messaging/{SourceCompletedListener,SourceCompletedParser}`, `export/application/SourceResultService`, `export/domain/{SourceResult,SourceResultRepository}`, `deletion/application/SourceDeletionService`
- [ ] T020 [P] [R-14] 결과 파일의 위험 상태 하나를 특징 한 줄로: durationMs=영상 길이, exposure·relatedEventCount·forecast 매핑, `feature-row.schema.json` 통과 — 테스트 `Q/test/.../export/domain/FeatureRowMapperTest.java`(계약 `source-result` 예시 사용), 구현 `export/domain/FeatureRowMapper`
- [ ] T021 [R-14] 내보내기: goldenSetId(최신 확정 버전의 영상, 줄 `split`=세트 분할) 또는 기간(받은 UTC 날짜, `split` null)으로 고른 영상의 위험 상태를 `exports/<exportId>/features.jsonl`에 쓰고 `COMPLETED`·줄 수·영상 수, 결과 파일 없는 영상은 건너뜀, 없는 Golden Set 404, 상태 조회·없으면 404 `QUALITY_EXPORT_NOT_FOUND` — 테스트 `Q/test/.../export/application/FeatureExportFlowTest.java`, `Q/test/.../export/api/FeatureExportApiTest.java`, 구현 `export/api/FeatureExportController`, `export/application/{FeatureExportService,FeatureExportWorker}`, `export/domain/FeatureExport`
- [ ] T022 [R-15] goldenSetId와 기간이 둘 다·둘 다 없음·기간 한쪽만·`from > to`·90일 초과면 400 `QUALITY_INVALID_REQUEST`, 행 없음 — 테스트 `Q/test/.../export/domain/ExportScopeTest.java`, `FeatureExportApiTest`, 구현 `export/domain/ExportScope`

## Phase 4: US-4 처리 단계 성능 Gate (P2)

- [ ] T023 [R-16] 기준·후보 영상 목록의 90일 안 원본으로 단계별 p95를 계산해 후보/기준 − 1 > `maxRegressionRatio`면 `failed`(3,400 → 4,300ms, 0.2), `stageIds` 지정 시 그 단계만, 허용 목록 밖 `X-Caller` 403 — 테스트 `Q/test/.../stage/domain/StageGateCalculatorTest.java`, `Q/test/.../stage/api/StageGateApiTest.java`, 구현 `stage/api/InternalStageGateController`, `stage/application/StageGateService`, `stage/domain/StageGateCalculator`, `JdbcStageTimingRepository#p95BySources`
- [ ] T024 [R-17] 한쪽 기록 수가 `minSamples`보다 적은 단계(그리고 기준 p95가 0인 단계)는 `skipped`이고 결과에 영향 없음 — 테스트 `StageGateCalculatorTest`, 구현 `StageGateCalculator`
- [ ] T025 [R-18] `failed`가 없으면 `passed: true`, 계산 뒤 DB 행 수가 그대로(저장 안 함) — 테스트 `StageGateApiTest`, 구현 `StageGateService`

## Phase 5: 마무리

- [ ] T026 [P] 6개 작업(가져오기 3, 내보내기 2, 성능 Gate 1)의 정상·오류 응답이 `quality-service.yaml` 3.0.0과 일치 — 테스트 `Q/test/.../contract/QualityContractTest.java`
- [ ] T027 [P] [NFR-1] 영상 1,000개·라벨 100,000개 매니페스트 검증 10초 이하 — 테스트 `Q/test/.../dataset/application/ImportManifestPerformanceTest.java`
- [ ] T028 [P] [NFR-2] 영상 1,000개 가져오기 중(WireMock media 지연 50ms) 검토 목록 p95 300ms — 테스트 `Q/test/.../dataset/ImportIsolationLatencyTest.java`, 구현 `config/ImportExecutorConfig`(T011에서 전용 실행기를 넣어 통과. 요청 스레드 풀에서 돌리면 실패하는 것을 확인)
- [ ] T029 [P] [NFR-3] 영상 200개 기준·후보 성능 Gate p95 2초 — 테스트 `Q/test/.../stage/StageGateLatencyTest.java`
- [ ] T030 설정·예시·문서: 두 서비스 `application.yml`, `.env.example`, README(내부 API·가져오기·내보내기). docs `local/`(가져오기 버킷, 환경변수)은 별도 docs PR
- [ ] T031 로그 확인: 가져오기·내보내기 시작·끝·실패가 importId·exportId와 함께 남고 서명 주소·파일 내용은 남지 않음 — 테스트 `Q/test/.../SensitiveLogTest.java`에 추가

## 완료 조건 (Definition of Done)

- [ ] 이 레포가 담당하는 모든 R-*, NFR-*의 테스트 통과(R-11·R-12·R-19는 ai 레포)
- [ ] lint, 타입 검사, 전체 테스트 통과(`./gradlew spotlessCheck build`)
- [ ] plan.md와 실제 구현이 일치함
- [ ] docs 레포 spec 상태 갱신 (`Implementing` / `Done`)
