---
spec: SPEC-029
repo: backend
updated: 2026-11-07
---

# Tasks: SPEC-029 quality-service 검토 큐·Golden Set·평가 실행·처리 단계 리포트 — backend

규칙

- 태스크 하나는 커밋 하나에서 PR 하나 사이의 크기로 잡는다(반나절 이하).
- `[P]`는 다른 태스크와 병렬로 진행할 수 있다는 뜻이다. `[R-n]`은 담당 요구사항이다.
- 태스크 하나는 요구사항 하나의 Red → Green → Refactor 사이클이다. 테스트와 구현을 같은 태스크에서 끝내서, 커밋마다 테스트가 통과하는 상태를 유지한다.
- 완료한 태스크는 `[x]`로 바꾼다. 커밋한 뒤에는 커밋 해시를 덧붙인다.

경로 약어: `Q` = `services/quality-service/src`, `G` = `services/api-gateway/src`, `C` = `libs/common-core/src`. `Q/main/...`의 패키지는 `com.vlm.quality` 아래다.

## Phase 0: 준비

- [x] T001 계약 잠금 4.0.0: `scripts/fetch-service-contracts.sh --update 4.0.0`(이미 4.0.0 이상이면 건너뜀) — 테스트 기존 전체 `./gradlew build`(media `MediaContractTest`가 `media-service.yaml` 1.4.0으로 통과), 구현 `service-contracts.lock`. pipeline-service 테스트가 깨지면 고치지 않고 멈춰 보고한다
- [x] T002 quality-service 모듈 뼈대: 기동·health UP, 기능 구조 ArchUnit 규칙 — 테스트 `Q/test/.../ArchitectureTest.java`, `Q/test/.../QualityApplicationTest.java`, 구현 `settings.gradle.kts`, `services/quality-service/build.gradle.kts`, `Q/main/.../QualityServiceApplication.java`, `Q/main/resources/application{,-local}.yml`, `Q/test/.../{TestcontainersConfiguration,support/IntegrationTest}.java`
- [x] T003 openapi-generator로 `quality-service.yaml` 1.2.0의 서버 인터페이스·모델 생성, `-Werror` 컴파일 통과 확인 — 테스트 `./gradlew :services:quality-service:compileJava`와 `ArchitectureTest`(생성 패키지 `vlm.contracts.quality`는 `api` 레이어에서만 씀), 구현 `gradle/libs.versions.toml`(`[plugins] openapi-generator`), `services/quality-service/build.gradle.kts`. 경고로 실패하면 멈추고 plan 10절 1로 확인받는다
- [x] T004 [P] 마이그레이션 `review_items`, `deleted_sources`, `golden_sets`, `golden_draft_entries`, `golden_set_versions`, `evaluation_runs`, `stage_timings`(파티션), `stage_daily_stats` — 테스트 `Q/test/.../SchemaMigrationTest.java`(테이블·제약·파티션 3개 존재), 구현 `Q/main/resources/db/migration/V202611071000__create_review_golden_evaluation.sql`, `V202611071100__create_stage_timings.sql`
- [x] T005 [P] [R-1] UUID v5 생성: RFC 4122 시험 벡터 `uuid5(DNS, "www.example.com") = 2ed6657d-e927-568b-95e1-2665a8aea6a2` — 테스트 `C/test/java/com/vlm/common/id/NameBasedUuidTest.java`, 구현 `C/main/java/com/vlm/common/id/NameBasedUuid.java`
- [x] T006 [R-4] `X-User-Roles`에 OPERATOR가 없으면 `/api/v1/quality/**`가 403 `QUALITY_FORBIDDEN`, `X-User-Id`가 없으면 401 — 테스트 `Q/test/.../config/OperatorRoleTest.java`, 구현 `config/{OperatorRoleInterceptor,QualityWebConfig}`, `shared/domain/QualityErrorCode`

## Phase 1: US-1 애매한 판정 검토 (P1)

- [x] T007 [R-1] `reviewRequired: true`인 `event.analyzed`를 받으면 itemId=uuid5(eventId), 사유 `review_required`, `UNASSIGNED` 항목 생성 — 테스트 `Q/test/.../review/infra/messaging/EventAnalyzedIntakeTest.java`(Kafka로 계약 예시를 보냄), 구현 `review/infra/messaging/{EventAnalyzedListener,EventAnalyzedParser}`, `config/QualityKafkaConfig`, `review/application/ReviewIntakeService`, `review/domain/{ReviewItem,ReviewItemRepository,ReviewStatus,ReviewReason}`, `review/infra/persistence/*`, `shared/infra/ContractSchemas`
- [x] T008 [R-3] 같은 eventId를 다시 받으면(내용이 달라도) 항목 하나뿐이고 기존 항목·라벨이 그대로 — 테스트 `EventAnalyzedIntakeTest`, 구현 `ReviewItemRepository#insertIfAbsent`(`ON CONFLICT (event_id) DO NOTHING`). T007의 삽입이 일반 `INSERT`면 고유 제약 오류로 Red가 난다
- [x] T009 [R-2] 표본: `reviewRequired: false`·판정 `confirmed`·`rejected`일 때만, eventId 해시가 유형별 비율 안이면 사유 `sample` — 테스트 `Q/test/.../review/domain/ReviewSamplingTest.java`(고정 eventId의 해시 값, 비율 0·1 경계, 유형별 설정), `EventAnalyzedIntakeTest`(`unverified`는 항목 없음), 구현 `review/domain/ReviewSampling`, `config/ReviewProperties`, `ReviewIntakeService`
- [x] T010 [R-5] 목록(상태·사유·유형 필터, createdAt·itemId 오름차순 쪽), 상세(근거 프레임 경로·서명 주소·판정 결과·라벨), 없으면 404 `QUALITY_REVIEW_NOT_FOUND` — 테스트 `Q/test/.../review/api/ReviewQueueApiTest.java`(서명 주소로 SeaweedFS 이미지를 받아짐), 구현 `review/api/ReviewItemController`, `review/application/ReviewQueryService`, `shared/infra/storage/{QualityStorageProperties,S3Clients,EvidenceUrlSigner}`
- [x] T011 [R-6] 맡기: `UNASSIGNED`면 200·`ASSIGNED`·assignee=요청자, 이미 맡음 409 `QUALITY_REVIEW_ALREADY_ASSIGNED`, 라벨 끝남 409 `QUALITY_REVIEW_ALREADY_LABELED` — 테스트 `Q/test/.../review/api/ReviewAssignmentApiTest.java`, 구현 `review/application/ReviewAssignmentService#assign`, `ReviewItemRepository#assignIfUnassigned`
- [x] T012 [R-7] 내려놓기: 맡은 사람이면 204·`UNASSIGNED`, 다른 사람 403 `QUALITY_REVIEW_NOT_ASSIGNEE`, 맡은 상태 아님 409 `QUALITY_REVIEW_NOT_ASSIGNED` — 테스트 `ReviewAssignmentApiTest`, 구현 `ReviewAssignmentService#release`
- [x] T013 [R-8] 라벨 저장: 맡은 사람이면 200·`LABELED`·라벨·저장 시각, 같은 사람이 다시 저장하면 덮어씀, 다른 사람 403, `UNASSIGNED`면 409 `QUALITY_REVIEW_NOT_ASSIGNED` — 테스트 `Q/test/.../review/api/ReviewLabelApiTest.java`, 구현 `review/application/ReviewLabelService`, `review/domain/ReviewLabel`
- [x] T014 [R-9] `eventPresent=true`인데 유형·시작·끝 중 하나 없음, `endMs <= startMs`, `eventPresent=false`인데 유형 있음이면 400 `QUALITY_INVALID_REQUEST`, 항목 그대로 — 테스트 `Q/test/.../review/domain/ReviewLabelRulesTest.java`, `ReviewLabelApiTest`, 구현 `review/domain/ReviewLabelRules`, `ReviewLabelService`
- [x] T015 [R-10] 24시간 넘게 라벨 없이 `ASSIGNED`인 항목을 `UNASSIGNED`로, 다른 Pod가 잠금을 잡고 있으면 아무것도 안 함 — 테스트 `Q/test/.../review/application/StaleAssignmentJobTest.java`(시계 고정, 다른 연결로 advisory lock을 잡은 채 실행), 구현 `review/application/StaleAssignmentJob`, `shared/infra/persistence/SingleRunLock`, `config/QualityJobScheduling`
- [x] T016 [P] [NFR-1] 같은 항목에 맡기 10건 동시 → 200 1건, 409 9건 — 테스트 `Q/test/.../review/application/ConcurrentAssignTest.java`, 구현 `ReviewAssignmentService#assign`(T011의 조건부 UPDATE로 통과. 읽고 쓰는 방식으로 바꾸면 실패하는 것을 확인)

## Phase 2: US-2 Golden Set 버전으로 평가 고정 (P1)

- [x] T017 [R-11] Golden Set 만들기 201(이름·분할, `origin: review`), 같은 이름 409 `QUALITY_GOLDEN_SET_DUPLICATED`, 목록(이름 순)·상세(초안 수·최신 버전·기준 실행) — 테스트 `Q/test/.../golden/api/GoldenSetApiTest.java`, 구현 `golden/api/GoldenSetController`, `golden/application/GoldenSetService`, `golden/domain/{GoldenSet,GoldenSetRepository}`, `golden/infra/persistence/*`
- [x] T018 [R-12] 초안에 넣기: `LABELED`·verdict가 uncertain이 아닌 항목만 그때 라벨을 복사해 201, 아니면 409 `QUALITY_REVIEW_NOT_LABELED`, 이미 든 항목 409 `QUALITY_GOLDEN_ENTRY_DUPLICATED`, 넣은 뒤 라벨을 고쳐도 초안 라벨은 그대로, 초안 목록(추가 순) — 테스트 `Q/test/.../golden/api/GoldenDraftApiTest.java`, 구현 `golden/application/GoldenDraftService#add·list`, `golden/domain/GoldenDraftEntry`, `review/application/ReviewQueryService#labeledForGolden`
- [x] T019 [R-13] 초안 항목 빼기 204, 없으면 404 `QUALITY_GOLDEN_ENTRY_NOT_FOUND`, 확정 버전 매니페스트·항목 수는 그대로 — 테스트 `GoldenDraftApiTest`(T020 이후 확정 버전 확인 케이스를 더함), 구현 `GoldenDraftService#remove`
- [x] T020 [R-14] 버전 확정: 근거 프레임을 `golden/<setId>/v<버전>/frames/<entryId>/<순번>.<확장자>`로 복사, 매니페스트를 쓴 뒤 버전 행(마지막 + 1), 201 — 테스트 `Q/test/.../golden/api/GoldenFreezeApiTest.java`(라벨 항목 3개 → 버전 1, 매니페스트가 `golden-manifest.schema.json` 통과, 프레임 파일 존재, 두 번째 확정은 버전 2), 구현 `golden/application/{GoldenFreezeService,GoldenManifestWriter}`, `golden/domain/GoldenSetVersion`, `shared/domain/ObjectStorage`, `shared/infra/storage/S3ObjectStorage`
- [x] T021 [R-15] 초안이 비면 409 `QUALITY_GOLDEN_EMPTY`, 확정 진행 중이면 409 `QUALITY_GOLDEN_FREEZE_IN_PROGRESS`, 둘 다 버전 행 없음, 오래된 진행 표시는 무시 — 테스트 `GoldenFreezeApiTest`, `Q/test/.../golden/application/ConcurrentFreezeTest.java`, 구현 `GoldenFreezeService`(`freeze_started_at` 조건부 UPDATE)
- [x] T022 [R-16] `video.deleted`를 받으면 그 영상의 검토 항목·초안 항목 삭제, 확정 버전 행·매니페스트·프레임은 남음, 삭제 뒤 온 `event.analyzed`로 항목을 다시 만들지 않음 — 테스트 `Q/test/.../deletion/infra/messaging/VideoDeletedIntakeTest.java`, 구현 `deletion/infra/messaging/{VideoDeletedListener,VideoDeletedParser}`, `deletion/application/SourceDeletionService`, `review/application/ReviewDeletion`, `ReviewIntakeService`(삭제 흔적 확인)

## Phase 3: US-3 CI Gate (P1)

- [x] T023 [R-17] 평가 실행 제출: 버전이 있으면 201, 같은 runId는 처음 실행을 200, 없는 Golden Set 버전 404 `QUALITY_GOLDEN_SET_NOT_FOUND`, 허용 목록 밖 `X-Caller` 403 `QUALITY_CALLER_NOT_ALLOWED` — 테스트 `Q/test/.../evaluation/api/SubmitEvaluationRunApiTest.java`, 구현 `evaluation/api/InternalEvaluationRunController`, `evaluation/application/EvaluationRunService#submit`, `evaluation/domain/{EvaluationRun,EvaluationRunRepository}`, `config/InternalCallerFilter`
- [x] T024 [R-18] Gate: 기준 0.78·허용 0.02에서 0.74면 `passed: false`·실패에 `judge_precision`, 낮을수록 좋은 지표는 반대 방향, 허용폭 안이면 `passed: true`, 기준 없으면 `passed: null` — 테스트 `Q/test/.../evaluation/domain/GateCalculatorTest.java`, `SubmitEvaluationRunApiTest`, 구현 `evaluation/domain/{GateCalculator,GateResult}`, `config/GateProperties`
- [x] T025 [R-18] 기준에만 있는 지표를 실패로 기록 — 계약 `GateResult.failures[].value`가 null을 허용하지 않아(plan 10절 2, 계약 C-30 대기) 기본안으로 구현: 기준에만 있는 지표가 있으면 `passed: false`로 하되 `failures`에는 넣지 않고 지표 키를 경고 로그로 남긴다. 계약이 `number | null`로 바뀌면 `failures`에 `value: null`로 넣는다 — 테스트 `GateCalculatorTest`, 구현 `evaluation/domain/{GateCalculator,MetricKey}`, `EvaluationRunService#submit`
- [x] T026 [R-19] 기준 실행 지정: 같은 Golden Set의 실행이면 200·`baselineRunId` 갱신, 다른 Golden Set이면 409 `QUALITY_RUN_SET_MISMATCH`, 없는 실행 404 `QUALITY_RUN_NOT_FOUND` — 테스트 `Q/test/.../evaluation/api/BaselineApiTest.java`, 구현 `evaluation/application/BaselineService`, `golden/application/GoldenSetService#setBaseline`
- [x] T027 [P] [R-20] 실행 목록(Golden Set으로 거르기, 받은 시각 내림차순)·상세, 없으면 404 `QUALITY_RUN_NOT_FOUND` — 테스트 `Q/test/.../evaluation/api/EvaluationRunQueryApiTest.java`, 구현 `evaluation/api/EvaluationRunController`, `EvaluationRunService#list·get`

## Phase 4: US-4 처리 단계 리포트 (P2)

- [x] T028 [R-21] `stage.timing`을 받으면 기록마다 저장, 같은 timingId를 다시 받으면 마지막 값 하나만 남음, 90일보다 오래된 기록은 건너뜀 — 테스트 `Q/test/.../stage/infra/messaging/StageTimingIntakeTest.java`, 구현 `stage/infra/messaging/{StageTimingListener,StageTimingParser}`, `stage/application/StageTimingIngestService`, `stage/infra/persistence/JdbcStageTimingRepository`
- [x] T029 [R-22] 영상별 리포트: 경로별 개수·합계·p50·p95·오류 수, 음수 `durationMs`는 개수에만, 기록 없으면 404 `QUALITY_SOURCE_NOT_FOUND` — 테스트 `Q/test/.../stage/api/SourceStageReportApiTest.java`(값을 아는 기록 10건으로 `percentile_cont` 결과 확인), 구현 `stage/api/StageReportController`, `stage/application/StageReportService#sourceReport`
- [x] T030 [R-23] 전날(UTC) 원본을 `stageId`별, `stageId`·버전 키·값별로 집계해 저장, 다시 돌려도 같은 행, 다른 Pod가 잠금을 잡고 있으면 건너뜀 — 테스트 `Q/test/.../stage/application/DailyStageAggregationTest.java`, 구현 `stage/application/DailyStageAggregationJob`, `JdbcStageTimingRepository#aggregateDay`
- [x] T031 [R-24] 추이: 날짜별 p50·p95, 버전 키·값이 있으면 그 버전만, 기간 > 90일·`from > to`·`versionValue`만 있음은 400 `QUALITY_INVALID_REQUEST` — 테스트 `Q/test/.../stage/domain/TrendQueryTest.java`, `Q/test/.../stage/api/StageTrendApiTest.java`, 구현 `stage/domain/TrendQuery`, `StageReportService#trend`. `versionKey`만 있는 경우는 plan 10절 3 확인 뒤 더한다
- [x] T032 [R-25] 파티션 작업: 3개월 앞 파티션을 만들고, 상한이 90일 전 이하인 월 파티션을 지움, 일 단위 집계는 남음 — 테스트 `Q/test/.../stage/application/StageTimingPartitionTest.java`(시계를 옮겨 가며 파티션 목록 확인), 구현 `stage/application/StageTimingPartitionJob`, `JdbcStageTimingRepository#{createPartition,dropPartitionsBefore}`
- [x] T033 [P] [R-26] api-gateway가 `/api/v1/quality/**`를 quality-service로 전달하고 `/internal/v1/quality/**`는 전달하지 않음(404) — 테스트 `G/test/.../RoutingTest.java`(경로 추가), `G/test/.../QualityRoutingTest.java`, 구현 `G/main/resources/application.yml`, `G/test/.../support/DownstreamServices.java`

## Phase 5: 마무리

- [ ] T034 [P] 19개 작업의 정상·오류 응답이 `quality-service.yaml` 1.2.0과 일치 — 테스트 `Q/test/.../contract/QualityContractTest.java`
- [ ] T035 [P] [NFR-2] [NFR-4] 항목 10,000개 목록 p95 300ms, 기록 5,000건 영상 리포트 p95 500ms — 테스트 `Q/test/.../QualityLatencyTest.java`
- [ ] T036 [P] [NFR-3] `stage.timing` 기록을 Pod 하나에서 초당 200건 이상 적재 — 테스트 `Q/test/.../stage/StageTimingThroughputTest.java`(6,000건을 30초 안에)
- [ ] T037 이미지 빌드: Dockerfile `CONTRACTS_DIR` 빌드 인자, `docker build --build-arg SERVICE=quality-service` 성공 확인 — 구현 `Dockerfile`(ci-common.md, hadolint)
- [ ] T038 설정·예시·문서: `application.yml`, `.env.example`, README(서비스·API·환경변수). docs 레포 `local/`(compose·`kafka-init` 그룹·`.env.example`)는 별도 docs PR
- [ ] T039 로그·추적 확인: 건너뛴 메시지·재시도·주기 작업 처리 수 로그, 서명 주소가 로그에 없음 — 테스트 `Q/test/.../SensitiveLogTest.java`

## Phase 9: v5.1 추가(계약 묶음 4.0.0)

- [ ] T040 [R-27] 생성 코드 2.0.0, `ReviewReason` 목록과 계약 대조, `unverified` → 사유별 큐 적재 — 테스트 `QualityContractTest`, `EventAnalyzedIntakeTest`, 구현 `review/domain/ReviewReason`, `review/application/ReviewIntakeService`
- [ ] T041 [R-27] 목록 필터 `reason`과 인덱스 — 테스트 `ReviewQueueApiTest`, 구현 `review/api/ReviewItemController`, `ReviewQueryService`, 마이그레이션(인덱스)
- [ ] T042 [R-28] `alert_events` 테이블, `alert.raised` 소비와 candidateId upsert(순서 역전, 재수신 무시) — 테스트 `AlertRaisedIntakeTest`, 구현 `A/infra/messaging/AlertRaisedListener`, `A/application/AlertIntakeService`, `V202611071200__create_alert_events.sql`, Kafka 소비 설정
- [ ] T043 [R-28] `dismissed`를 검토 큐에 사유 `alert_dismissed`로 적재 — 테스트 `AlertRaisedIntakeTest`, 구현 `A/application/AlertIntakeService`(→ `ReviewIntakeService`)
- [ ] T044 [R-28] `GET /api/v1/quality/alert-reports`(p50·p95·건수·dismissed 비율, 기간·카메라 필터, OPERATOR) — 테스트 `AlertReportApiTest`, 구현 `A/api/AlertReportController`, `A/application/AlertReportService`
- [ ] T045 [R-16] 삭제 전파에 `alert_events` 포함 — 테스트 `VideoDeletedIntakeTest`, 구현 `deletion/application/SourceDeletionService`

## 완료 조건 (Definition of Done)

- [ ] 이 레포가 담당하는 모든 R-*, NFR-*의 테스트 통과(T025는 계약 수정 뒤)
- [ ] lint, 타입 검사, 전체 테스트 통과(`./gradlew spotlessCheck build`)
- [ ] plan.md와 실제 구현이 일치함
- [ ] docs 레포 spec 상태 갱신 (`Implementing` / `Done`)
