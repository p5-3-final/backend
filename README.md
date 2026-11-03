# backend

Spring Boot 멀티모듈 MSA. 구조와 규칙은 [AGENTS.md](AGENTS.md)에 있다.

| 모듈 | 내용 | 로컬 포트 |
| --- | --- | --- |
| `services/api-gateway` | 라우팅, 토큰 검증(JWKS), CORS, 알림 티켓 검증, 요청 수 제한 | 8080 |
| `services/auth-service` | 가입, 로그인, 토큰 발급·재발급·폐기, 공개키 목록, 알림 티켓 | 8081 |
| `services/media-service` | 영상 업로드 자리 발급(단일·멀티파트), 완료 확인, 목록·상세·삭제, 만료·저장소 정리 | 8082 |
| `libs/common-core`, `libs/common-web` | 공통 오류 타입, ProblemDetail 핸들러 | — |
| `libs/common-messaging` | Outbox 기록·발행(Kafka) | — |

## 준비물

- JDK 21 (`brew install openjdk@21`). Gradle은 wrapper가 받는다.
- Docker. 테스트가 Testcontainers로 PostgreSQL, Redis, Kafka, SeaweedFS(S3 대체)를 띄운다.
- 계약 테스트는 docs 레포의 OpenAPI를 읽는다. 루트 구조(`project/repos/backend`)로 클론하면 따로 설정할 것이 없다. 다른 위치라면 `CONTRACTS_DIR`에 docs 레포의 `docs/contracts` 경로를 넣는다.

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

## 문서

- 기능 spec과 계약: docs 레포 `docs/specs/`, `docs/contracts/`
- 구현 계획: [specs/](specs/)

## Pipeline 계획 저장 (SPEC-004)

`services/pipeline-service`는 외부 HTTP 업무 API와 Kafka 없이 내부 `PlanningService.create`/`find`로 계획을 생성·재조회한다. `PlanInput.production`은 영상 길이별 운영 프로필, `PlanInput.local`은 60초 프로필이다. 시간은 정규화 사본 기준 소수 초이며 입력 비교는 소수 표기의 자릿수와 무관하다.

- 동일 계획 UUID·동일 입력: 저장된 계획 재사용
- 동일 계획 UUID·다른 입력: `PIPELINE_INPUT_CONFLICT`
- 다른 계획 UUID·같은 source의 활성 Job 존재: `PIPELINE_ACTIVE_JOB_EXISTS`
- 활성 상태: PLANNED/RUNNING/ANALYZED. 종료·배정·완료 판정 기능은 이번 범위 밖이다.

별도 PostgreSQL DB와 계정을 준비하고 `.env.example`의 `PIPELINE_DB_URL`, `PIPELINE_DB_USERNAME`, `PIPELINE_DB_PASSWORD`를 환경변수로 주입한다. `./gradlew :services:pipeline-service:bootRun --args=--server.port=8083`으로 실행한다. 업무 API는 없으며 Actuator health만 제공한다. 운영 배포·ECR·GitOps·local compose 등록은 후속 작업이다.

검증: `./gradlew :services:pipeline-service:build`. PostgreSQL은 Testcontainers로 생성하며 Docker가 필요하다.
