---
spec: SPEC-007
repo: backend
status: Draft
owner: backend 담당자
created: 2026-11-03
updated: 2026-11-03
---

# Plan: SPEC-007 정규화 수신과 계획 저장 — backend

- spec: docs/specs/SPEC-007-normalized-intake/spec.md (이전 PR #40의 SPEC-006 초안은 재생 명세와 충돌하여 이동)
- 근거: ADR-0009, ADR-0008의 v3 구조, SPEC-004
- 상태: 사용자가 요청한 문서 전용 설계 초안. 계약과 spec 확정 전 구현 착수 문서가 아님

## 1. 요약

이번 구현 대상은 video.normalized 수신 → 입력 검증 → 정규화 결과/분석 설정에 맞는 기존 Job 재사용 또는 새 Job·Chunk 저장 → 수신 완료 기록 → DB 커밋 → offset 처리다. 계획 생성 시 실행 시도·슬롯·chunk.requested Outbox를 만들지 않는다.

후속 배정기가 실행할 청크를 고른 뒤 실행 시도·배정 상태·슬롯 점유·요청 Outbox를 같은 pipeline DB 트랜잭션으로 저장한다. 공통 Publisher는 발행 전용이다. 영상 추출은 backend의 chunk-processor가 배정받은 processingRange만 수행한다. AI팀은 준비된 입력을 받아 모델 추론을 제공한다.

### 현재 구현과 차이 (저장소 확인)

| 항목 | 확인한 현재 구현 | 목표 / 후속 변경 |
| --- | --- | --- |
| 구간 계산 | PlanInput/ChunkPlanner: 소수 초 BigDecimal, 운영 60/120/180초·로컬 60초·5초 겹침 | 연동 경계는 정수 ms, 계산 규칙 재사용 |
| 계획 ID | jobs.plan_id UUID, API에서 입력받는 내부 계획 ID | jobId 의미로 재사용 검토. eventId를 복사하지 않음 |
| Chunk ID | chunks.id BIGINT + Job별 ordinal | 현재 내부 PK 보존, 외부 chunkId 표현은 계약에서 확정 |
| 중복 | planId + source·길이·프로필·길이·겹침 비교, plan/source advisory lock | normalizationId + 전체 분석 설정의 의미상 유일성 추가 |
| 활성 제한 | source_id 활성 상태 부분 유일 인덱스 | 유지. 동일 Job 재사용 판정이 활성 충돌 검사보다 먼저 |
| 저장 | Job·Chunk만 @Transactional 저장 | 수신 완료 기록·불변 정규화 정보와 원자적 연결 추가 |
| 정규화·실행 정보 | normalizationId·추론 프로필 버전·실행 시도·슬롯·수신 기록 없음 | 후속 마이그레이션 필요. 이미 존재한다고 간주하지 않음 |
| 공통 메시징 | OutboxRecorder는 트랜잭션 필요. Publisher는 미발행 행을 ID 순으로 SKIP LOCKED 후 event_type/aggregate_id로 전송 | 재사용하되 계획 단계에는 연결하지 않음. Publisher에 배정 책임을 넣지 않음 |
| 순서 | Publisher는 행 단위 잠금. source 단위 일괄 잠금/전체 순서 보장은 구현되지 않음 | Kafka sourceId 키만으로 여러 발행 Pod의 인과 순서를 보장한다고 하지 않음 |
| 워커·추론 | 기존 docs는 ai 레포 Python 경로·AI Gateway /v1/analyze·/v1/embed·/v1/parse를 목표로 기술 | 담당은 backend/AI 추론으로 분리. 레포 이동·새 서버 구현 사실로 표현하지 않음 |

## 2. 담당 요구사항

아래 테스트 이름은 후속 구현 대상이며 이번에는 파일을 만들거나 실행하지 않는다.

| 요구사항 | 대상 | 예정 검증 |
| --- | --- | --- |
| R-1 | 수신 검증 | NormalizedContractTest: envelope·길이·버전·객체 참조 |
| R-2, R-12 | 입력 경계·기존 계산 | MillisecondPlanningTest: 600000/7200000 경계·겹침·나머지 |
| R-3, R-9 | 수신+계획 트랜잭션 | IntakeTransactionTest: 각 저장 실패 지점의 전체 롤백 |
| R-4 | 배정 전 저장 | IntakeNoDispatchTest: 정상·재전달 모두 Outbox/실행/슬롯 0건 |
| R-5, R-8, R-11 | 의미상/메시지 중복 | IntakeIdempotencyTest: 같은/다른 eventId·같은 결과·설정·동시 수신 |
| R-6, R-7 | 충돌·활성 제한 | IntakeConflictTest: ID 입력 충돌·다른 결과의 활성 Job 충돌 |
| R-10 | 커밋 후 offset | IntakeRecoveryTest: 커밋 전 장애·커밋 후 ack 전 중단 |
| NFR-1~3 | 통합 경계 | 실제 PostgreSQL·Kafka, 영상 처리·AI 호출 0회 |

## 3. 원칙 점검

| 원칙 | 상태 | 근거 |
| --- | --- | --- |
| I 스펙 우선 | 초안 예외 | 사용자 요청은 설계 문서만. SPEC-007 Draft로 보존, 기능 구현 금지 |
| II 계약 우선 | 유지 | 이번에는 JSON Schema·AsyncAPI·OpenAPI를 변경하지 않음 |
| III 테스트 우선 | 유지 | 예정 테스트만 매핑, 통과했다고 기록하지 않음 |
| IV 서비스 자율성 | 유지 | pipeline DB와 worker 소유 저장소 분리, 타 서비스 DB 직접 쓰기 금지 |
| V 단순성 | 유지 | 기존 계산·공통 기록기 재사용, 배정기·추론 서버를 이번에 구현하지 않음 |
| VI 관측 가능성 | 설계 | source/normalization/job/chunk/execution/event 추적과 traceparent, 입력 바이트·민감 URL 로그 금지 |
| VII 보안 | 설계 | 객체 저장소 최소 읽기 권한과 준비된 입력 허용 범위. IAM 변경은 후속 infra 작업 |
| VIII 문서 | 유지 | 아키텍처·SPEC·ADR와 현재/목표 구분 |
| IX GitOps | 유지 | 배포·CI·클러스터·AWS 변경 없음 |

## 4. 기술 설계

### 책임과 저장 경계

| 담당 | 책임 | 소유 상태 |
| --- | --- | --- |
| 업로드 API | 원본 객체 보존 | media 서비스 원본 상태 |
| normalize (backend) | 정규화·불변 결과 저장·길이/메타데이터 | 정규화 결과 식별·객체 참조. 재정규화는 새 ID/경로 |
| pipeline-service | 구간 계산·계획 저장 | 정규화 참조·분석 설정·Job·Chunk·수신 기록 |
| 배정기 (backend, 후속) | 공정성·사용자→source→전체 상한 적용 | 실행 시도·배정·슬롯·chunk.requested Outbox |
| chunk-processor (backend, 후속) | 배정 구간 추출·AI 입력 준비·추론 호출·결과 저장 | executionId 기준 워커 실행 원장·임대·완료/실패 이벤트 Outbox |
| AI 추론 서버 | 입력 검증·추론·중복 실행 제어·결과 재사용 | requestId별 실행 상태/완료 결과 |

배정기는 pipeline-service의 책임 구성요소이며 이번에 새 배포 단위를 만들지 않는다. Python 경로에 있다는 이유로 normalize·chunk-processor를 AI팀 소관으로 돌리지 않는다. 실제 언어·레포 재배치는 후속 작업이다.

worker 실행 원장은 worker 소유 저장소에 둔다. 배정기가 소유한 pipeline 실행/슬롯 원장을 worker가 직접 갱신하지 않는다. 두 원장은 executionId로 연결하고 완료/실패 이벤트로 동기화한다. worker에서 결과 이벤트를 Outbox에 기록하려면 worker 실행 원장과 Outbox가 같은 DB 트랜잭션에 있어야 한다. 해당 저장소·마이그레이션은 아직 없다.

### 식별자와 중복

| 식별자 | 의미 | 재사용 규칙 |
| --- | --- | --- |
| sourceId | 원본 영상 | 기존 공통 UUID 재사용, Kafka 키 |
| normalizationId | 불변 정규화 결과 | 다른 결과면 새 ID·새 경로. 같은 ID의 다른 객체/길이/버전은 충돌 |
| jobId | 정규화 결과+불변 분석 설정의 계획 | 동일 조합이면 기존 Job. 현 plan_id 재사용 검토 |
| chunkId | 해당 Job의 계획 청크 | 다른 Job과 혼동하지 않음. ordinal만 전역 ID로 쓰지 않음 |
| executionId | 실제 청크 실행 시도 | 메시지 재전달·동일 시도 복구 시 유지, 실제 새 실행은 새 ID |
| eventId | 메시지 | 재전달은 같은 값, 계획 식별자로 치환 금지 |

의미상 Job 키는 normalizationId와 정규화된 분석 설정 전체다. 설정에는 청크 계산 프로필·길이·겹침과 추론 프로필 불변 버전 등 계획/결과에 영향을 주는 값이 포함된다. fingerprint를 사용하더라도 원본 정규 설정을 보존·비교해 같은 키의 다른 입력을 덮어쓰지 않는다. 해시 알고리즘·정규 표현은 backend 계약 버전으로 결정하며 이 문서는 wire 구조를 확정하지 않는다.

DB 유일 제약과 트랜잭션 잠금으로 (소비자,eventId), normalizationId, (normalizationId, 분석 설정 키), (Job, 청크 순번)을 보호하는 후속 설계다. 기존 plan/source 잠금과 활성 Job 부분 인덱스는 유지한다. 먼저 eventId·입력 일치, 정규화 결과 불변성, 의미상 기존 Job을 확인한 뒤 신규 Job의 활성 충돌을 검사한다.

같은 Job 재수신은 종료 여부와 관계없이 기존 계획을 반환하며 다시 활성화하지 않는다. 의미상 키를 바꾸지 않은 강제 재분석은 새 Job을 자동 생성하지 않는다. 실제 새 분석 설정 버전 또는 별도 재실행 정책은 후속 명세로 다룬다.

### 이번 수신 트랜잭션과 과거 데이터

1. 확정된 backend 계약으로 envelope·객체 참조·durationMs·normalizationVersion을 검증한다.
2. 메시지 ID 중복 및 같은 ID의 변경 입력을 확인한다.
3. 정규화 ID에 연결된 sourceId·ownerId·객체 참조·길이·정규화 버전의 불변성 및 전체 설정을 검증하고 기존 Job을 재사용하거나 활성 제한 안에서 Job·Chunk를 만든다.
4. 수신 처리 결과와 연결 관계를 같은 트랜잭션에 저장한다. 실행·슬롯·Outbox는 0건이다.
5. DB 커밋 후 offset을 처리한다. 병렬 소비는 해당 파티션의 연속 성공 offset까지만 처리한다.

기존 Job에 Outbox가 없으면 미배정 상태로 정상이다. 재전달은 Outbox 보완 트리거가 아니다. 기존 SPEC-004 데이터에는 normalizationId가 없으므로 sourceId만 보고 임의 결과에 연결하지 않는다. 필요하면 검증 가능한 정규화 이력으로 백필하거나 legacy로 구분한다. 백필·누락 복구는 배정 상태와 실행 원장을 확인하는 별도 절차이며 자동 전체 재발행은 금지한다.

### 시간 단위와 실제 매핑

연동 시간은 정규화 영상 시작 기준 정수 ms이며 ownedRange·processingRange는 [start,end)다. SPEC-004의 60/120/180초와 5초는 60000/120000/180000ms·5000ms로 대응한다. 경계 600000/7200000ms에서도 원래 선택 규칙을 유지한다.

현재 내부 BigDecimal 초와의 경계는 ms÷1000의 정확한 변환을 사용할 수 있다. 정수 ms를 내부에서 반올림하지 않는다. 기존 sub-ms 데이터는 ms로 조용히 반올림·덮어쓰기하지 않으며 legacy 처리나 재계획이 필요하다. NUMERIC 컬럼의 값을 단위 확인 없이 1000배/나눗셈하는 일괄 수정은 금지한다.

AI 결과 시간은 준비된 입력 청크 시작 기준 ms다. chunk-processor는 실제 추출한 프레임/타임스탬프의 원본 정규화 PTS 매핑을 보존한다. 단순 무재인코딩·시간축 보존인 경우에만 실제 시작 오프셋 덧셈을 쓸 수 있다. 키프레임 seek·리샘플링·프레임 누락·가변 fps는 해당 변환표로 매핑한다. 요청 processingRange.start를 실제 시작으로 가정하지 않는다. AI 로컬 시간이 입력 범위를 벗어나거나 매핑 불가능하면 결과를 유효한 전역 시각으로 꾸미지 않고 오류 처리한다.

### 후속 배정 트랜잭션

공정성으로 선택한 Chunk를 잠그고 활성 Job·현재 실행·사용자/source/전체 슬롯 한도를 재검증한다. 한 트랜잭션에서 새 executionId, 배정 상태, 슬롯 점유 원장, chunk.requested Outbox를 저장한다. 하나라도 실패하면 모두 롤백한다. 유일 실행/슬롯 원장과 조건부 갱신으로 동시 배정을 막는다. 운영 한도·재시도 횟수·임대/추론 제한 시간은 버전 관리된 운영 설정으로 두며 임의 숫자를 문서에 박지 않는다.

공통 Publisher는 커밋된 요청을 발행하고 재전송 시 eventId·executionId를 유지한다. 일시적인 브로커 실패는 새 executionId를 만들지 않는다. 새 실행 재시도는 배정기에 다시 들어와 새 슬롯·새 executionId를 얻는다. Kafka에는 바이트가 아니라 객체 참조와 작업 정보만 싣는다.

### 후속 실행·임대·결과 반영

- worker는 executionId의 입력 일치 여부를 확인하고 유일 제약+CAS로 원자 점유한다. 완료된 같은 실행은 결과를 재사용하며 중복 호출을 새 실행으로 해석하지 않는다.
- worker 임대에는 소유자·만료·갱신 세대를 두고 현재 점유 세대만 결과를 확정하도록 fencing한다. 소유권·세대는 내부 저장 설계이며 메시지 필드로 확정하지 않는다.
- 임대 만료는 장애 의심이지 AI 실행 종료 증거가 아니다. AI HTTP 타임아웃도 실행 종료가 아니다. 같은 requestId로 상태/결과를 재확인하고 안전한 재점유는 같은 executionId로 한다. 원격 실행 종료가 확인되기 전 새 실제 실행을 중복 시작하거나 슬롯을 조기에 반환하지 않는다.
- 상태 확인/취소 응답의 실제 프로토콜은 AI팀과 합의한다. 무응답 시 unknown/보류 상태로 관측하고 운영 경보를 내며 성공·실패를 추측하지 않는다. 재확인 상한·수동 복구 경로는 backend 후속 명세에서 확정한다.
- 결과 파일은 executionId별 불변 경로로 먼저 저장한다. 저장 성공 뒤 worker 실행 완료와 결과 이벤트 Outbox를 같은 worker DB 트랜잭션에 기록한다. DB 실패 시 파일은 고아 정리 대상으로 남고 완료 이벤트는 확정되지 않는다.
- pipeline은 DB의 현재 executionId와 결과의 executionId가 같을 때만 Chunk 상태를 반영한다. 이전 실행 결과는 현재 상태를 덮어쓰지 않는다.
- 슬롯은 pipeline의 실행 원장에 조건부 반환 표시와 용량 갱신을 같은 트랜잭션으로 수행한다. 성공/실패/중복 이벤트가 경쟁해도 한 번만 반환한다. 이전 실행의 확정 종료가 뒤늦게 오면 그 실행의 미반환 슬롯만 정리하고 현재 실행 슬롯은 건드리지 않는다.

### 메시지·HTTP 의미 초안 (스키마 수정 아님)

공통 envelope의 기존 UUID와 eventType=토픽·sourceId=Kafka 키·traceparent는 유지한다. 신규 식별자의 타입, 객체 참조의 bucket/key/version 표현, 범위의 JSON 구조는 backend 계약에서 확정한다.

| 경계 | 의미 초안 |
| --- | --- |
| normalize → pipeline | video.normalized: normalizationId, 불변 객체 참조, durationMs(정수), normalizationVersion |
| 배정기 → worker | chunk.requested: jobId/chunkId/executionId, normalizationId, 정규화 객체 참조, ownedRange/processingRange, processingProfile |
| worker → AI | POST /v1/inferences: requestId를 executionId와 연결, input은 준비한 청크 객체 참조, profile은 불변 추론 버전 |
| AI → worker | requestId/modelVersion/outputSchemaVersion/output. output 구조는 별도 결과 스키마 |
| worker → pipeline | 기존 chunk.manifest는 건수 보고다. 완료·실패를 이미 구현한 계약으로 보지 않는다. executionId+결과 참조 또는 오류 코드가 필요한 후보 chunk.execution.completed / chunk.execution.failed를 후속 검토한다. 이름·스키마는 미확정 |

### 기존 구조와 충돌·대안

- ADR-0008의 AI Gateway는 /v1/analyze·/v1/embed·/v1/parse이며 실제 OpenAPI 파일은 없다. POST /v1/inferences는 새 의미 초안이지 기존 구현 API가 아니다.
- 기본 대안 A: 기존 AI Gateway에 /v1/inferences facade/adapter를 추가해 기존 Adapter·모델 프로필을 재사용한다. 장점은 배포 수 유지, 단점은 청크 입력/출력·requestId 실행 원장 확장이다.
- 대안 B: 별도 내부 추론 서버로 adapter를 연결한다. 장점은 추론 수명주기 격리, 단점은 새 배포·권한·운영 비용이다. 어떤 실제 서버를 쓸지는 저장소만으로 확인할 수 없어 이번에 교체하지 않는다.
- AI는 준비된 입력 prefix만 읽을 권한을 받아 객체 참조를 해석한다. 외부 공급자에 원본 경로를 직접 넘기지 않는다. 기존 블러 사본 규칙을 유지하며 새 입력 영상의 비식별 규격을 AI 입력 협의에서 확인한다. IAM·NetworkPolicy는 후속 infra/gitops 변경이다.
- 기존 AI 워커 PostgreSQL 금지 설명과 충돌하는 worker 실행/결과 Outbox는 ADR-0009에 backend 소유 저장소 예외로 구분했다. DB 공유로 해결하지 않는다.
- 기존 문서의 chunkId Kafka 키와 envelope의 sourceId 규칙은 sourceId로 정리한다. 이벤트 명칭을 바꾸거나 다른 토픽의 미확정 계약까지 자동 변경하지 않는다.

## 5. 대안과 선택 이유

| 대안 | 판단 |
| --- | --- |
| 계획 단계에서 전체 청크 요청을 적재 | 폐기. 배정·슬롯 제한 우회 |
| Publisher에서 공정성 판단 | 폐기. 발행과 실행 허가의 책임 혼합 |
| 배정 트랜잭션에서 실행·슬롯·Outbox 저장 | 채택. 최초 실행·재시도에 같은 제한 |
| AI팀이 normalize·Kafka 계약 소유 | 폐기. 이번 사용자 확정 책임에 어긋남 |

## 6. 테스트 전략

이번에는 설계 Markdown·링크·diff만 검사한다. 후속 기능은 PostgreSQL·Kafka Testcontainers와 합의된 추론 Stub으로 검증한다. 출력 검출/설명/임베딩을 임의 생성해 실제 모델 결과로 간주하지 않는다.

현재 범위는 요구사항 매핑의 정상·동시·커밋 전후 장애와 Outbox 0건 검증이다. 후속 배정은 슬롯 경쟁·배정 원자 롤백·재전송 ID 유지, 실행은 임대 만료·중복 HTTP·타임아웃 후 성공·실제 seek 오차·지연 결과·중복 슬롯 반환·파일 저장 후 DB 실패·고아 파일 정리를 검증한다.

## 7. 리스크와 롤백

기존 마이그레이션을 수정하지 않고 additive 변경·입력 경계 변환을 검토한다. 기존 데이터의 normalizationId를 sourceId나 eventId로 날조하지 않는다. 향후 기능별 migration과 데이터 검증·복귀 절차를 별도 계획에 명시한다. 원격 AI가 끝났는지 모르는 상태를 즉시 실패로 바꾸면 중복 추론·슬롯 초과가 가능하므로 unknown 관측과 재확인 절차가 필요하다.

## 8. 배포 고려사항과 실제 미결 사항

이번에는 배포·라이브러리·애플리케이션 코드·계약 스키마·환경변수를 바꾸지 않는다. normalize·worker의 실제 레포/런타임 선택과 worker DB·임대 갱신 통로·운영 설정 기본값은 backend 후속 구현 계획의 결정 사항이다.

AI팀 협의는 입력 영상 규격/제한, 불변 프로필과 모델 버전, output 스키마와 청크 기준 시간 의미, 오류 코드, 추론 제한 시간, requestId 중복 실행 제어/진행 중 상태/결과 재사용으로 좁힌다. pipeline·Kafka·Outbox·배정 정책은 backend가 정한다.

이번 intake 구현에는 backend가 video.normalized의 실제 직렬화·정규화 버전 의미·설정 정규 표현·기존 데이터 처리·재시도/불량 입력 정책을 확정하는 것이 선행 조건이다. AI output·추론 timeout 협의는 후속 실행 기능의 선행 조건이며 이번 계획 저장을 AI팀 계약 전체 확정까지 묶지 않는다. 새 endpoint를 기존 Gateway에 붙일지 다른 서버 adapter로 연결할지는 AI 구현 담당과 확인해야 한다.
