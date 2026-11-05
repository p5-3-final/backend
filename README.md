# backend

Spring Boot 멀티모듈 MSA. 구조와 규칙은 [AGENTS.md](AGENTS.md)에 있다.

| 모듈 | 내용 | 로컬 포트 |
| --- | --- | --- |
| `services/api-gateway` | 라우팅, 토큰 검증(JWKS), CORS, 알림 티켓 검증, 요청 수 제한 | 8080 |
| `services/auth-service` | 가입, 로그인, 토큰 발급·재발급·폐기, 공개키 목록, 알림 티켓 | 8081 |
| `services/media-service` | 영상 업로드 자리 발급(단일·멀티파트), 완료 확인, 목록·상세·삭제, 원본 재생·내려받기 주소 발급, 만료·저장소 정리 | 8082 |
| `libs/common-core`, `libs/common-web` | 공통 오류 타입, ProblemDetail 핸들러 | — |
| `libs/common-messaging` | Outbox 기록·발행(Kafka) | — |

## 준비물

- JDK 21 (`brew install openjdk@21`). Gradle은 wrapper가 받는다.
- Docker. 테스트가 Testcontainers로 PostgreSQL, Redis, Kafka, SeaweedFS(S3 대체)를 띄운다.
- 계약 테스트는 docs 레포의 OpenAPI를 읽는다. 루트 구조(`project/repos/backend`)로 클론하면 따로 설정할 것이 없다. 다른 위치라면 `CONTRACTS_DIR`에 docs 레포의 `service-contracts` 경로를 넣는다.

## 명령어

```bash
./gradlew build                                   # 전체 빌드 + 테스트
./gradlew :services:auth-service:test             # 서비스 하나 테스트
./gradlew spotlessApply                           # 포맷 (커밋 전)
./gradlew spotlessCheck                           # 포맷 검사
./gradlew :services:auth-service:generateSigningKey   # 로컬용 서명 키(JWK) 출력
```

## 로컬 실행

전체 서비스는 docs 레포 `local/`의 compose로 띄운다. 아래는 auth-service와 api-gateway만 단독으로 띄우는 방법이다.

1. 환경변수 파일을 만든다. 예시 파일에 로컬 전용 샘플값(DB 비밀번호, 서명 키, 초기 관리자)이 들어 있어 복사만 하면 된다. 서명 키를 새로 만들려면 `generateSigningKey` 출력을 `AUTH_JWT_SIGNING_KEY`에 작은따옴표로 감싸 넣는다.

   ```bash
   cp .env.example .env
   ```

2. 셸에 환경변수를 올리고 PostgreSQL과 Redis를 띄운다.

   ```bash
   set -a; source .env; set +a
   docker run -d --name vlm-auth-db -p 5432:5432 \
     -e POSTGRES_DB=auth_db -e POSTGRES_USER="$AUTH_DB_USERNAME" -e POSTGRES_PASSWORD="$AUTH_DB_PASSWORD" \
     postgres:17-alpine
   docker run -d --name vlm-gateway-redis -p 6379:6379 redis:7.4-alpine
   ```

3. auth-service를 먼저 띄운다. 게이트웨이가 auth-service의 공개키 목록을 읽어야 한다.

   ```bash
   ./gradlew :services:auth-service:bootRun --args='--spring.profiles.active=local'
   ./gradlew :services:api-gateway:bootRun        # 다른 터미널에서, 같은 환경변수를 올린 뒤
   ```

4. 게이트웨이를 거쳐 가입 → 로그인 → 내 정보를 확인한다.

   ```bash
   curl -s -X POST localhost:8080/api/v1/auth/signup -H 'Content-Type: application/json' \
     -d '{"email":"user@example.com","password":"secret123","nickname":"테스터"}'
   TOKEN=$(curl -s -X POST localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' \
     -d '{"email":"user@example.com","password":"secret123"}' | jq -r .accessToken)
   curl -s localhost:8080/api/v1/auth/me -H "Authorization: Bearer $TOKEN"
   ```

### media-service 단독 실행

DB·Kafka·SeaweedFS는 docs 레포 local compose에서 필요한 것만 띄운다(루트 `project/`에서 실행). `local/.env`가 없으면 `cp local/.env.example local/.env`로 만든다.

```bash
docker compose -f local/docker-compose.yml up -d postgres kafka seaweedfs-init
```

backend 레포에서 환경변수를 올리고 띄운다. 게이트웨이(`MEDIA_SERVICE_URI`)를 거치거나 `X-User-Id`, `X-User-Roles` 헤더를 직접 붙여 8082로 부른다.

```bash
set -a; source .env; set +a
./gradlew :services:media-service:bootRun --args='--spring.profiles.active=local'
curl -s localhost:8082/api/v1/media/videos -H "X-User-Id: $(uuidgen)" -H 'X-User-Roles: USER'
```

업로드부터 이벤트 확인까지의 예시는 docs 레포 `local/README.md`의 "영상 업로드"에 있다.

### 정규화 결과 수신 (SPEC-007)

media-service는 그룹 `media-service`로 `video.normalizing`(v1)과 `video.normalized`(v2)를 받아 상태를 `UPLOADED → NORMALIZING → READY / REJECTED`로 바꾼다.

- 토픽은 미리 만들어 둔다. 소비자는 토픽을 자동으로 만들지 않으므로, 토픽이 없으면 리스너는 할당 없이 기다린다.
- 계약을 어긴 메시지(키와 sourceId 불일치, v1 결과, 다른 영상 폴더의 경로 등)는 경고 로그를 남기고 건너뛴다. DB 장애 같은 일시 오류는 offset을 멈춘 채 1초부터 최대 30초 간격으로 계속 다시 시도한다.
- 설정: `vlm.media.normalization.estimate-factor`(예상 처리 시간 계수, 기본 1.0), `vlm.media.normalization.consumer-enabled`(기본 true)

## 문서

- 기능 spec과 계약: docs 레포 `docs/specs/`, `service-contracts/`
- 구현 계획: [specs/](specs/)

## Pipeline 계획 저장 (SPEC-004)

`services/pipeline-service`는 외부 HTTP 업무 API와 Kafka 없이 내부 `PlanningService.create`/`find`로 계획을 생성·재조회한다. `PlanInput.production`은 영상 길이별 운영 프로필, `PlanInput.local`은 60초 프로필이다. 시간은 정규화 사본 기준 소수 초이며 입력 비교는 소수 표기의 자릿수와 무관하다.

- 동일 계획 UUID·동일 입력: 저장된 계획 재사용
- 동일 계획 UUID·다른 입력: `PIPELINE_INPUT_CONFLICT`
- 다른 계획 UUID·같은 source의 활성 Job 존재: `PIPELINE_ACTIVE_JOB_EXISTS`
- 활성 상태: PLANNED/RUNNING/ANALYZED. 종료·배정·완료 판정 기능은 이번 범위 밖이다.

별도 PostgreSQL DB와 계정을 준비하고 `.env.example`의 `PIPELINE_DB_URL`, `PIPELINE_DB_USERNAME`, `PIPELINE_DB_PASSWORD`를 환경변수로 주입한다. `./gradlew :services:pipeline-service:bootRun --args=--server.port=8083`으로 실행한다. 업무 API는 없으며 Actuator health만 제공한다. 운영 배포·ECR·GitOps·local compose 등록은 후속 작업이다.

검증: `./gradlew :services:pipeline-service:build`. PostgreSQL은 Testcontainers로 생성하며 Docker가 필요하다.

### 로컬 테스트의 DEBUG 환경변수

IDE가 `DEBUG=release`를 전달하면 Spring Boot의 debug 설정으로도 읽혀 프레임워크 DEBUG 로그가 활성화되고 SensitiveLogTest가 실패할 수 있다. 기준 main과 같은 CI 환경을 재현할 때는 `env -u DEBUG ./gradlew build`를 사용한다. 민감 로그 검증과 CI 검사 설정은 변경하지 않는다.

## 정규화 v2 수신과 계획 저장 (SPEC-008)

SPEC-007은 다른 작업의 영상 정규화 명세다. Pipeline 수신은 번호 충돌을 정리해 SPEC-008로 관리한다.

`pipeline-service`는 `video.normalized`의 schemaVersion=2 계약을 검증하고 정규화 결과·수신 기록과 기존 Job·Chunk 계획을 단일 트랜잭션으로 저장한다. READY만 계획하며 REJECTED는 결과·수신 사실만 기록한다. v1 계약은 보존하지만 이 consumer는 v1에서 정규화 ID를 추정하지 않고 처리하지 않는다. normalize producer의 v2 구현은 ai #12로 머지됐지만 이 consumer와의 실제 연결 검증은 별도다.

- 같은 eventId·같은 payload는 최초 결과를 반환한다. JSON 키 순서·공백·동등한 숫자 표기는 JSONB로 비교한다.
- 새 eventId라도 같은 normalizationId·분석 설정 버전이면 기존 Job을 재사용한다. 같은 결과/설정 ID의 입력 변경은 충돌이다.
- 기존 활성 Job 제한과 SPEC-004 입력 비교는 유지한다. legacy Job에 normalizationId를 자동 백필하지 않는다.
- durationMs와 durationSec는 정확히 일치해야 하며 기존 내부 초 단위를 일괄 변경하지 않는다.
- 계획이 있으나 Outbox가 없는 상태는 정상이다. SPEC-008 자체는 발행·슬롯·실행을 만들지 않으며、아래 SPEC-010 배정에서만 생성한다. 영상 처리·AI는 연결하지 않는다.

단독 실행은 기존 PIPELINE_DB_*와 Kafka 주소를 설정하고, `PIPELINE_INTAKE_ENABLED=true`, 불변 `PIPELINE_INTAKE_PROFILE_VERSION`, `PIPELINE_INTAKE_INFERENCE_PROFILE_VERSION`을 지정한다. 이 버전 값은 실제 모델을 호출하거나 모델 존재를 검증한다는 뜻이 아니라 분석 설정의 식별이다. `KAFKA_BOOTSTRAP_SERVERS`의 `video.normalized` 토픽은 운영자가 미리 준비한다. 토픽 자동 생성은 하지 않는다. 기본 소비 그룹은 pipeline-normalized-intake-v2이며 기존 v1 그룹을 그대로 재사용하지 않는다. 새 그룹은 earliest에서 시작하므로 기존 토픽에 v1 이력이 있으면 운영자가 v2 전환 위치를 확인해 그룹 시작 offset을 준비해야 한다. v1을 자동으로 건너뛰거나 정규화 ID를 추정하지 않는다.

동기 처리·RECORD ack·auto commit=false로 DB 커밋 성공 뒤 offset을 처리한다. 파싱 오류·충돌·DB 오류는 설정된 backoff 이후 같은 입력을 다시 seek하고 자동 skip/성공 커밋하지 않는다. 설정 횟수 소진 후에도 recoverer가 실패를 유지하므로 잘못된 입력은 해당 파티션을 막는다. 원인 해소·명시적인 운영 재처리/DLQ 도구는 후속 범위다. 운영 설정 변경만으로 이미 처리된 eventId를 새 Job에 연결하지 않는다.

검증: `env -u DEBUG ./gradlew :services:pipeline-service:build`. 실제 PostgreSQL·Kafka Testcontainers로 정상·중복·동시 수신·각 저장 단계 롤백·DB 커밋 후 ack 전 중단을 검증한다. 이벤트 스키마는 빌드 시 docs 계약 원본에서 JAR에 포함한다. CI는 CONTRACTS_DIR을 지정하고 계약이 없으면 빌드가 실패한다.

## Pipeline 배정·슬롯·요청 발행 (SPEC-010)

SPEC-008의 정규화 연결 Job에서 PENDING 청크를 선택한다. 사용자별(기본 2)·영상별(1)·전체(4) 상한과 사용자 한 청크 순환을 적용한다. 실행 시도·슬롯·DISPATCHED·chunk.requested Outbox를 한 DB 트랜잭션에 저장한다. 기존 common-messaging Publisher가 sourceId 키와 원 수신 traceparent로 Kafka에 발행한다. 요청 eventId/executionId/requestId는 재발행 때 바뀌지 않는다. 내부 PK는 보존하고 외부 chunkId UUID를 추가한다.

자동 배정·발행은 `PIPELINE_DISPATCH_ENABLED=false`가 기본이다. `.env.example`의 상한·시작 기한·주기를 확인하고 모든 Pod에 동일 구성을 적용해야 한다. 슬롯이 남아 있을 때 구성 충돌은 배정을 거부한다. 상한 전환은 배정을 끄고 모든 슬롯을 확인·반환한 후 재시작한다. 배정 플래그는 정규화 intake 플래그와 별개다. 기존 Kafka offset을 바꾸지 않는다. `chunk.requested` 토픽과 worker 접근 권한은 별도 준비하며 이번에 운영 활성화하지 않았다.

### 슬롯 만료와 종료 확인 복구

`startBefore`(기본 배정 후 5분)는 신규 실행 시작 기한이다. AI 실행 종료 시간이 아니다. 만료는 RECOVERY_REQUIRED로 표시하고 슬롯을 유지한다. `pipeline.dispatch.recovery.required` gauge로 미확인 건수를 확인한다. 전송 후 DB 확인에 실패할 수 있으므로 Outbox의 `published_at IS NULL`도 미실행 증거로 쓰지 않는다.

실행이 종료됐거나 시작하지 않았음을 **외부에서 확인한 뒤에만** 아래 일회성 명령을 실행한다. DB 접속용 `PIPELINE_DB_URL`, `PIPELINE_DB_USERNAME`, `PIPELINE_DB_PASSWORD`를 해당 환경에서 주입한다. 증거는 비밀값 없는 운영 기록 참조(1~500자, 한 줄)다. 명령 자체가 worker를 중단하거나 종료 사실을 검증하지는 않는다.

```bash
java -Dloader.main=com.vlm.pipeline.config.DispatchRecoveryCommand \
  -cp services/pipeline-service/build/libs/pipeline-service.jar \
  org.springframework.boot.loader.launch.PropertiesLauncher \
  <executionId> <confirmationId> <evidence-reference>
```

명령은 웹·정규화 수신·배정·Outbox 주기를 강제로 끈 별도 프로세스로 실행하고 종료한다. 기한 전 반환은 거부한다. 같은 확인 ID와 같은 증거는 재시도해도 한 번만 반환하고, 다른 확인/증거나 확인 ID 재사용은 거부한다. 슬롯 반환·실행 STOPPED·청크 FAILED는 원자적이며 자동 재실행하지 않는다. 안전한 반환을 위해 worker가 startBefore 이후 요청을 새로 시작하지 않는 계약도 충족해야 한다. 검증용 소비자로만 이 동작을 확인했다.

### 현재 연결과 제한

작업 중 ai #12로 normalize v2 구현이 머지됐고 docs #57로 로컬 compose에 추가됐다. 최종 확인한 ai main `40356fe`는 v2 식별자·정수 ms·불변 key 형식으로 발행한다. 다만 재처리 때 같은 eventId의 occurredAt을 새 시각으로 만들므로 SPEC-008의 전체 payload 비교에서 EVENT_CONFLICT가 발생한다. 해당 경계는 자동화 테스트로 확인했으며 producer가 최초 결과 이벤트를 불변하게 재사용하도록 보완해야 한다. SPEC-008 수신과 이번 배정은 기본 비활성이고 root local compose에도 pipeline 실행이 없다. 테스트 v2 입력을 실제 정규화 연결로 취급하지 않는다.

실제 chunk-processor·영상 추출·AI·완료/실패 이벤트·정상 완료 자동 슬롯 반환·heartbeat/fencing·자동 재시도·Job 완료 판정은 후속이다. 현재는 배정→검증용 Kafka 소비까지 동작하며, 슬롯 자동 순환이 완성된 처리 파이프라인은 아니다. 기한 후 외부 종료 확인과 복구 명령으로 수동 반환할 수 있다. 명세·계약은 docs SPEC-010/ADR-0009, 계획·tasks는 `specs/SPEC-010-chunk-dispatch/`를 참고한다.
