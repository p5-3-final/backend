---
spec: SPEC-001
repo: backend
status: Approved
owner: <이름>
created: 2026-11-02
updated: 2026-11-02
---

# Plan: SPEC-001 회원가입·로그인과 인증된 요청 — backend

- spec: docs 레포 `docs/specs/SPEC-001-auth-and-gateway/spec.md`
- 계약: docs 레포 `docs/contracts/openapi/auth-service.yaml`, `docs/contracts/openapi/_common.yaml`, `docs/contracts/auth/access-token.md`

## 1. 요약
- `services/auth-service`(Spring MVC, PostgreSQL)를 새로 만들어 가입, 로그인, 토큰 발급·회전·폐기, 공개키 목록(JWKS), 알림 티켓, 계정 잠금, 초기 관리자를 구현한다.
- `services/api-gateway`(Spring Cloud Gateway, WebFlux)를 새로 만들어 경로별 전달, JWKS 기반 토큰 검증, 사용자 헤더 전달, 오류 응답, CORS, 알림 티켓 검증, 요청 수 제한을 구현한다.
- 공통 오류 형식은 `libs/common-core`(오류 코드, `BusinessException`)와 `libs/common-web`(MVC용 ProblemDetail 핸들러)에 둔다. 게이트웨이는 WebFlux라서 자체 오류 핸들러를 둔다.
- 두 서비스는 저장소를 공유하지 않는다. 게이트웨이는 auth-service의 공개키 목록만 HTTP로 읽고, 티켓 1회용 처리는 게이트웨이 자기 Redis로 한다.

## 2. 담당 요구사항
| 요구사항 | 담당 모듈 / 컴포넌트 | 검증 테스트 |
| --- | --- | --- |
| R-1 | auth-service `SignUpService` | `SignUpApiTest` |
| R-2 | auth-service `SignUpService` | `SignUpApiTest` |
| R-3 | auth-service `SignUpRequest` 검증 | `SignUpApiTest` |
| R-4 | auth-service `PasswordPolicy` | `PasswordPolicyTest`, `SignUpApiTest` |
| R-5 | auth-service `SignUpRequest` 검증 | `SignUpApiTest` |
| R-6 | auth-service `PasswordHasher` | `SignUpApiTest` (저장값이 원문과 다르고 BCrypt로 일치) |
| R-7 | auth-service `LogInService`, `TokenIssuer` | `LogInApiTest` |
| R-8 | auth-service `LogInService` | `LogInApiTest` |
| R-9 | auth-service `LoginAuditRecorder` | `LogInApiTest` |
| R-10 | auth-service `MeController` | `MeApiTest` |
| R-11 | auth-service `JwksController`, `SigningKeys` | `JwksApiTest` |
| R-12 | api-gateway 라우트, `UserHeaderFilter` | `AuthenticatedRoutingTest` |
| R-13 | api-gateway `SecurityConfig` 공개 경로 | `PublicRoutingTest` |
| R-14 | api-gateway `ProblemAuthenticationEntryPoint` | `TokenRejectionTest` |
| R-15 | api-gateway JWT 검증기(`iss`, `aud`, 서명) | `TokenRejectionTest` |
| R-16 | api-gateway JWT 검증기(`exp`) | `TokenRejectionTest` |
| R-17 | api-gateway `UserHeaderFilter` | `HeaderSpoofingTest` |
| R-18 | api-gateway `GatewayErrorHandler` | `GatewayErrorTest` |
| R-19 | api-gateway 응답 시간 제한, `GatewayErrorHandler` | `GatewayErrorTest` |
| R-20 | api-gateway `GatewayErrorHandler` | `GatewayErrorTest` |
| R-21 | api-gateway CORS 설정 | `CorsTest` |
| R-22 | auth-service `TokenRefreshService` | `RefreshApiTest` |
| R-23 | auth-service `TokenRefreshService` | `RefreshApiTest` |
| R-24 | auth-service `TokenRefreshService` | `RefreshApiTest` |
| R-25 | auth-service `LogOutService` | `LogOutApiTest` |
| R-26 | auth-service `SseTicketIssuer` | `SseTicketApiTest` |
| R-27 | api-gateway `SseTicketFilter` | `SseTicketTest` |
| R-28 | api-gateway `SseTicketFilter` | `SseTicketTest` |
| R-29 | auth-service `User` 잠금 규칙 | `AccountLockTest`, `LogInApiTest` |
| R-30 | api-gateway 요청 수 제한 | `RateLimitTest` |
| R-31 | auth-service `InitialAdminCreator` | `InitialAdminTest` |
| NFR-1 | api-gateway | `GatewayLatencyTest` (WireMock 하위 서비스, p95 측정) |
| NFR-2 | auth-service 재발급 토큰 해시 저장 | `RefreshApiTest` (DB에 원문 없음) |
| NFR-3 | 두 서비스 로그 | `SensitiveLogTest` (출력 캡처에 토큰·비밀번호 없음) |
| NFR-4 | 두 서비스 오류 응답 | 각 오류 테스트에서 `application/problem+json`, `traceId` 확인 |
| NFR-5 | api-gateway JWKS 캐시·재조회 | `KeyRotationTest` |

## 3. 원칙 점검
| 원칙 | 준수 | 비고 |
| --- | --- | --- |
| I 스펙 우선 — spec이 Approved 상태인가 | ☑ | SPEC-001 Approved (docs #7) |
| II 계약 우선 — 계약 변경이 먼저 머지됐는가 | ☑ | docs #8(API·토큰), #9(티켓) 머지 |
| III 테스트 우선 — 모든 R-*에 테스트가 매핑되고, 태스크가 요구사항 단위 TDD 사이클로 나뉘었는가 | ☑ | R-1~R-31, NFR-1~5 매핑. tasks.md 참고 |
| IV 서비스 자율성 — 다른 서비스 DB나 코드에 직접 의존하지 않는가 | ☑ | 게이트웨이는 JWKS HTTP만 사용. 티켓 1회용 처리는 게이트웨이 Redis. 두 서비스 간 import 없음(ArchUnit) |
| V 단순성 — 새 의존성이나 추상화가 정당한가 | ☑ | Spring Boot·Cloud BOM 관리 라이브러리만 사용. 사용자 승인 받음(6절). 새 서비스 모듈은 ADR-0003 |
| VI 관측 가능성 — 로그, 추적, health를 갖췄는가 | ☑ | Actuator health, Micrometer Tracing(`traceparent`), JSON 로그, ProblemDetail `traceId` |
| VII 보안 — 입력 검증, 인가, 비밀값 처리를 했는가 | ☑ | Bean Validation, BCrypt, 토큰·티켓 해시 저장, 개인키는 환경변수/Secrets Manager, 위조 헤더 제거 |
| VIII 문서·주석 — spec·계약·plan과 구현이 일치하고, 주석이 왜를 설명하는가 | ☑ | 계약 테스트(T050), 구현 중 차이가 생기면 docs PR 먼저 |
| IX GitOps — AWS 변경은 infra, 클러스터 변경은 gitops 레포를 거치는가 | ☑ | 배포 매니페스트는 gitops `apps/{auth-service,api-gateway}`, Secrets Manager 경로는 infra (이 plan 범위 밖, 8절) |

## 4. 기술 설계

### 변경 대상
| 경로 | 변경 |
| --- | --- |
| `settings.gradle.kts` | `services:auth-service`, `services:api-gateway` 추가 |
| `gradle/libs.versions.toml`, `build-logic` | WireMock, Testcontainers(Boot BOM 관리), JSpecify(`@Nullable`, java-style.md) 추가 |
| `libs/common-core` | `ErrorCode` 인터페이스, `BusinessException` |
| `libs/common-web` | MVC용 `ProblemDetailExceptionHandler`(`code`, `traceId` 포함), Bean Validation 오류 변환 |
| `services/auth-service` | 새 모듈. 패키지 `com.vlm.auth.{api,application,domain,infra}` |
| `services/api-gateway` | 새 모듈. 패키지 `com.vlm.gateway.{config,filter,error}` |

### auth-service 구성
- **가입**: 이메일 소문자 정규화 후 중복 확인 → 비밀번호 정책 → BCrypt(strength 10) 해시 → `users` 저장. 이메일 유니크 제약으로 동시 가입 경쟁도 409로 변환한다.
- **로그인**: 사용자 조회 → 잠금 확인(423) → 비밀번호 확인 → 실패 시 실패 횟수 증가(5회째에 `locked_until = now + 15분`) / 성공 시 초기화 → 로그인 기록 → 토큰 발급.
  - 실패 횟수와 잠금은 401·423 응답과 함께 저장돼야 하므로 로그인 트랜잭션은 `BusinessException`으로 롤백하지 않는다. 동시 실패로 횟수가 덮어써지지 않도록 사용자 행을 잠그고 조회한다(`findByEmailForUpdate`).
  - 잠긴 계정의 시도도 실패로 기록한다(R-9). 잠글 때 실패 횟수를 0으로 비워, 잠금이 풀린 뒤 다시 5회를 센다.
  - 이메일이 없을 때도 BCrypt 비교를 한 번 수행해 응답 시간으로 계정 존재 여부가 드러나지 않게 한다.
- **접근 토큰**: `NimbusJwtEncoder` + EC P-256 키. 헤더 `kid`, 클레임은 `access-token.md` 그대로. `iss`는 환경변수.
  - 서명 키는 PEM 대신 JWK JSON으로 받는다. JDK만으로는 EC 개인키 PEM에서 공개키를 만들 수 없어 BouncyCastle이 필요해지기 때문이다.
- **로그인 기록**: 실패한 로그인은 예외로 끝나 트랜잭션이 롤백되므로, 기록은 `REQUIRES_NEW` 트랜잭션으로 남긴다. 클라이언트 IP는 `server.forward-headers-strategy=framework`로 게이트웨이의 `X-Forwarded-For`에서 읽는다.
- **재발급 토큰**: 256비트 난수(base64url). DB에는 SHA-256 해시만 저장. 로그인마다 새 `family_id`를 만들고, 재발급할 때 같은 family로 새 토큰을 만들며 이전 토큰에 `used_at`을 기록한다.
  - `used_at`이 있는 토큰이 다시 오면 그 family 전체에 `revoked_at`을 기록하고 401 `AUTH_REFRESH_REUSED`.
  - 검사 순서: 쿠키 없음·미발급 → 폐기됨(`AUTH_REFRESH_INVALID`) → 사용됨(`AUTH_REFRESH_REUSED`) → 만료(`AUTH_REFRESH_INVALID`).
  - 같은 토큰으로 동시에 재발급하지 못하도록 조회할 때 행을 잠근다(`SELECT … FOR UPDATE`). 두 번째 요청은 재사용으로 처리된다.
  - family 폐기는 오류 응답 전에 커밋돼야 하므로 재발급 트랜잭션은 `BusinessException`으로 롤백하지 않는다. `REQUIRES_NEW`로 분리하면 바깥 트랜잭션이 잠근 행을 기다려 교착이 생긴다.
  - 로그아웃: 쿠키의 토큰이 요청한 사용자의 것이면 그 family를 폐기한다. 쿠키가 없거나 남의 토큰이어도 204와 삭제 쿠키(`Max-Age=0`)를 반환한다.
  - 쿠키: `refresh_token`, `Path=/api/v1/auth`, `HttpOnly`, `Secure`, `SameSite=Strict`, `Max-Age=1209600`.
- **JWKS**: 현재 서명 키의 공개키 + 환경변수로 지정한 이전 공개키(선택)를 반환한다. 개인키는 응답에 넣지 않는다.
- **알림 티켓**: 같은 서명 키로 `aud: vlm-sse`, 30초 JWT를 발급한다. 저장하지 않는다. 접근 토큰과 클레임 구성이 같으므로 서명은 `UserTokenSigner` 하나로 하고, 역할은 헤더가 아니라 DB에서 읽는다.
- **내 정보**: `X-User-Id`(UUID)로 사용자를 조회한다. 게이트웨이가 넣은 헤더를 신뢰하며, NetworkPolicy로 게이트웨이 외 접근을 막는다(gitops).
- **초기 관리자**: `ApplicationRunner`. ADMIN이 없고 `AUTH_ADMIN_EMAIL`·`AUTH_ADMIN_PASSWORD`가 있으면 생성한다. 값이 없으면 경고 로그만 남기고 건너뛴다(로컬 편의).
  - 비밀번호가 가입 비밀번호 정책에 맞지 않으면 `IllegalStateException`으로 시작을 멈춘다(약한 관리자 비밀번호로 운영하지 않게).
  - 그 이메일로 일반 계정이 이미 있으면 관리자로 올리지 않고 경고 후 건너뛴다. 이메일은 소문자로 저장하고, 닉네임은 `관리자`다.
  - 여러 파드가 동시에 시작하면 이메일 유니크 제약으로 한쪽이 실패하고, 재시작한 뒤 관리자가 있으므로 건너뛴다.

### api-gateway 구성
- **라우트**: `/api/v1/auth/**` → auth-service, `/api/v1/media/**` → media-service, `/api/v1/jobs/**` → pipeline-service, `/api/v1/analysis/**` → analysis-api, `/api/v1/search/**` → search-service, `/api/v1/notify/**` → notification-service. 대상 주소는 환경변수다. 아직 없는 서비스는 503이 정상이다.
- **인증**: Spring Security WebFlux resource server. `NimbusReactiveJwtDecoder.withJwkSetUri(...)`에 검증기 `iss`·`aud(vlm-api)`·시간(허용 오차 30초)을 건다. 알고리즘은 ES256만 허용한다.
  - 공개 경로: `POST /api/v1/auth/{signup,login,refresh}`, `GET /actuator/health/**`. 나머지 `/api/**`는 인증 필요.
  - 실패 응답은 `ServerAuthenticationEntryPoint`에서 ProblemDetail로 만든다. 토큰 없음 / 만료(`JwtValidationException` 중 `exp`) / 그 밖의 실패를 `AUTH_TOKEN_MISSING` / `AUTH_TOKEN_EXPIRED` / `AUTH_TOKEN_INVALID`로 나눈다.
- **사용자 헤더**: 모든 요청에서 `X-User-Id`, `X-User-Roles`를 먼저 지운 뒤(R-17), 인증된 요청에만 `sub`·`roles`로 다시 넣는다(R-12).
- **오류**: `ErrorWebExceptionHandler`로 라우트 없음 404, 응답 시간 초과 504(`spring.cloud.gateway.httpclient.response-timeout`), 연결 실패 503을 ProblemDetail로 바꾼다.
- **CORS**: Spring Security의 CORS 필터(`CorsConfigurationSource`). 인증보다 먼저 처리해야 토큰 없는 사전 요청이 401로 막히지 않는다. 게이트웨이 globalcors는 헤더 중복을 피하려고 쓰지 않는다. 허용 출처는 환경변수(frontend 주소), `allowCredentials=true`(재발급 쿠키).
- **JWKS 캐시**: 직접 구현한 `CachingJwkSource`. 5분마다 갱신(계약), 모르는 `kid`면 즉시 재조회하되 10초에 한 번으로 제한(임의 kid로 auth-service에 요청을 모는 것 방지).
- **응답 제한 시간**: 기본 10초, 연결 2초. 알림 경로는 라우트 metadata로 제한 시간을 끈다(SSE).
- **알림 티켓**: `/api/v1/notify/stream` 전용 필터. `?ticket` JWT를 `aud: vlm-sse` 검증기로 확인하고, Redis `SET sse:jti:<jti> 1 NX EX 60`이 실패하면(이미 사용) 401.
  - 보안 설정에서 이 경로(GET)는 접근 토큰 검사를 건너뛰고 필터가 인증한다. 필터는 `UserHeaderFilter` 바로 다음에 실행해 티켓 주인으로 사용자 헤더를 채운다.
  - 티켓 검증기는 접근 토큰 검증기와 공개키 캐시(`CachingJwkSource`)를 같이 쓴다. `aud`만 `vlm.gateway.jwt.ticket-audience`로 다르다.
  - 하위 서비스로 보낼 때 `ticket` 쿼리를 지운다(NFR-3, 로그에 티켓이 남지 않게).
  - EX 60은 티켓 유효기간 30초 + 허용 오차 30초다.
- **요청 수 제한**: `RequestRateLimiter` + `RedisRateLimiter`, 키는 클라이언트 IP. 분당 10회는 `replenishRate=1, burstCapacity=60, requestedTokens=6`으로 표현한다. 거절 응답을 ProblemDetail로 바꾼다.
  - 로그인·가입(POST)만 별도 라우트 `auth-rate-limited`로 묶어 auth-service 라우트보다 앞에 둔다. 두 경로는 카운터를 같이 쓴다.
  - 클라이언트 IP는 `X-Forwarded-For`의 마지막 값(ALB가 붙인 값) 하나만 믿는다(`XForwardedRemoteAddressResolver.maxTrustedIndex(1)`). 헤더가 없으면 접속 주소다.
  - `RateLimitedResponseFilter`가 거절 응답(본문 없는 429)을 ProblemDetail로 바꾸고, `Retry-After`는 RedisRateLimiter 헤더(남은 토큰, 요청 토큰, 충전 속도)로 계산한다.
  - IP는 ALB가 붙인 `X-Forwarded-For`의 마지막 신뢰 홉을 쓴다(`forwarded-headers-strategy`). 로컬은 원격 주소.

### 데이터 모델 (auth_db)
마이그레이션: `V202611021200__create_auth_tables.sql`, `V202611031200__change_token_hash_to_varchar.sql`(`token_hash`를 `CHAR(64)`에서 `VARCHAR(64)`로)

| 테이블 | 컬럼 | 비고 |
| --- | --- | --- |
| `users` | `id BIGINT IDENTITY PK`, `public_id UUID UNIQUE NOT NULL`, `email VARCHAR(254) UNIQUE NOT NULL`, `password_hash VARCHAR(100) NOT NULL`, `nickname VARCHAR(20) NOT NULL`, `role VARCHAR(20) NOT NULL`, `failed_login_count INT NOT NULL DEFAULT 0`, `locked_until TIMESTAMPTZ NULL`, `created_at`, `updated_at` | 외부에는 `public_id`만 노출(토큰 `sub`, `X-User-Id`, API `id`). 역할은 하나이며 API에서는 배열로 반환 |
| `refresh_tokens` | `id BIGINT IDENTITY PK`, `user_id BIGINT NOT NULL REFERENCES users`, `family_id UUID NOT NULL`, `token_hash VARCHAR(64) UNIQUE NOT NULL`, `expires_at TIMESTAMPTZ NOT NULL`, `used_at TIMESTAMPTZ NULL`, `revoked_at TIMESTAMPTZ NULL`, `created_at`, `updated_at` | 인덱스 `(family_id)` |
| `login_audits` | `id BIGINT IDENTITY PK`, `user_id BIGINT NULL`, `success BOOLEAN NOT NULL`, `client_ip VARCHAR(45) NOT NULL`, `occurred_at TIMESTAMPTZ NOT NULL`, `created_at`, `updated_at` | 없는 이메일이면 `user_id` NULL. 시도한 이메일 원문은 저장하지 않음 |

### API / 이벤트
- auth-service: `signUp`, `logIn`, `refreshToken`, `logOut`, `getMe`, `issueSseTicket`, `getJwks` (`auth-service.yaml` 1.0.1)
- api-gateway: 자체 API 없음. `_common.yaml`의 `XUserId`, `XUserRoles`, `Unauthenticated`, `RateLimited`, `GatewayError`를 따른다.
- 이벤트: 해당 없음

### 상호작용
```mermaid
sequenceDiagram
  participant C as 클라이언트
  participant G as api-gateway
  participant A as auth-service
  participant S as 하위 서비스
  C->>G: POST /api/v1/auth/login (공개)
  G->>A: 전달
  A-->>C: accessToken + refresh_token 쿠키
  C->>G: GET /api/v1/... (Bearer)
  G->>A: GET /.well-known/jwks.json (캐시 없거나 모르는 kid일 때)
  G->>G: 서명·iss·aud·exp 검증, X-User-* 제거 후 재설정
  G->>S: 전달 (X-User-Id, X-User-Roles)
  S-->>C: 응답
```

## 5. 대안과 선택 이유
| 대안 | 장점 | 단점 | 선택 |
| --- | --- | --- | --- |
| 사용자 ID: BIGINT PK + 외부용 UUID | persistence 규칙·계약 모두 유지, 순번 비노출 | 컬럼 하나 추가 | ☑ (사용자 결정) |
| 사용자 ID: UUID PK | 컬럼 하나 | persistence 규칙 예외 필요 | |
| 알림 티켓: 서명된 단기 JWT + 게이트웨이 Redis로 1회용 | 저장소 공유 없음(원칙 IV), 기존 JWKS 재사용 | 게이트웨이에 Redis 필요 | ☑ (사용자 결정, 계약 docs #9) |
| 알림 티켓: 공유 Redis 난수 | 단순 | 두 서비스가 저장소 공유 | |
| 비밀번호 해시: BCrypt | Spring Security 기본, 추가 의존성 없음 | Argon2보다 메모리 하드하지 않음 | ☑ (사용자 결정) |
| 토큰 서명: `spring-security-oauth2-jose`(Nimbus) | 게이트웨이 검증과 같은 라이브러리, BOM 관리 | — | ☑ |
| 토큰 서명: Authorization Server 도입 | 표준 OAuth2 엔드포인트 | 이번 요구사항에 비해 과함(원칙 V) | |

되돌리기 어렵거나 다른 레포에 영향을 주는 결정은 없다. 토큰 형식은 이미 계약으로 확정됐다.

## 6. 테스트 전략
- **새 라이브러리 (사용자 승인)**: gateway `spring-cloud-starter-gateway-server-webflux`(Spring Cloud 2025.0부터 이 이름), `spring-boot-starter-oauth2-resource-server`, `spring-boot-starter-data-redis-reactive` / auth `spring-boot-starter-{web,data-jpa,validation}`, `flyway-core`, `flyway-database-postgresql`, `postgresql`, `spring-security-crypto`, `spring-security-oauth2-jose` / 테스트 `org.testcontainers:{postgresql,junit-jupiter}`, `com.redis:testcontainers-redis` 또는 GenericContainer, `org.wiremock:wiremock-standalone`
- **단위**: `PasswordPolicy`, `User` 잠금 규칙(시계 주입), 재발급 토큰 회전 규칙
- **통합 (auth-service)**: `@SpringBootTest` + Testcontainers PostgreSQL(`@ServiceConnection`) + MockMvc. 요구사항마다 API 테스트
- **통합 (api-gateway)**: `@SpringBootTest(RANDOM_PORT)` + `WebTestClient`. 하위 서비스와 JWKS는 WireMock, 테스트 키 쌍으로 토큰을 직접 서명. P2는 Testcontainers Redis
- **계약**: auth-service 응답을 `auth-service.yaml` 스키마로 검증(T050). 테스트 이름은 `@DisplayName("[SPEC-001 R-n] ...")`
  - `com.atlassian.oai:swagger-request-validator-mockmvc` 2.46.1(사용자 승인, 테스트 전용)로 MockMvc 요청·응답을 검증한다. 일부러 계약을 어긴 요청을 보내는 실패 케이스는 응답만 검증한다.
  - 계약 원본은 docs 레포에 둔다. 로컬은 작업 공간 구조(`../../docs/contracts`)를, CI는 `checks.yml`에서 docs 레포를 체크아웃해 `CONTRACTS_DIR`로 넘긴다. 파일이 없으면 테스트가 실패한다.
  - 추가 필드를 금지하는 엄격 검사를 유지한다. 이 검사로 400 응답의 `errors`가 계약에 없던 것을 찾아 계약에 추가했다(docs #13, `_common.yaml` 1.2.0).
- **추적(NFR-4)**: 서비스 convention plugin에 `micrometer-tracing-bridge-otel`(사용자 승인, Boot BOM 관리)을 넣어 W3C `traceparent`를 이어 받고 하위 서비스로 넘긴다.
  - auth-service는 MDC의 `traceId`를 common-web 핸들러가 넣는다. 게이트웨이는 WebFlux라 MDC 대신 요청의 관측 컨텍스트(`ServerRequestObservationContext`)에서 현재 span을 찾아 `ProblemResponseWriter`가 넣는다.
  - Spring Boot는 테스트에서 추적을 꺼 두므로 통합 테스트 공통 설정에 `@AutoConfigureObservability(metrics = false)`를 붙인다.
  - 수집기(exporter)는 아직 두지 않는다. 운영 수집 방식은 관측 스택을 정할 때 따로 결정한다.
- **민감 정보(NFR-2, NFR-3)**: 두 서비스의 실제 흐름을 실행하고 출력 캡처에 비밀번호·토큰·티켓 원문이 없는지 확인한다(`com.vlm`은 DEBUG). DB에는 재발급 토큰의 SHA-256 해시만 있는지 확인한다.
- **아키텍처**: 서비스마다 ArchUnit(`api → application → domain ← infra`, 서비스 간 import 금지, `GeneralCodingRules`). 게이트웨이는 레이어 대신 패키지 간 순환 금지만 검사
- **E2E**: 로컬 compose에서 가입 → 로그인 → 게이트웨이 경유 `/me` 수동 시연 (성공 지표)

## 7. 리스크와 롤백
- 리스크
  - Spring Boot 3.5는 무료 지원이 끝나 보안 패치가 없다. 의존성 취약점은 CI에서 확인하고, 4.x 전환은 별도 ADR로 다룬다.
  - 게이트웨이 앞단(ALB)의 `X-Forwarded-For` 처리가 잘못되면 IP 제한이 모든 사용자를 하나로 묶을 수 있다. dev 배포 때 확인한다.
  - 서명 키 유출 시 모든 토큰 위조 가능. 키는 Secrets Manager에만 두고 교체 절차(`access-token.md`)를 따른다.
- 롤백 방법: 두 서비스 모두 신규라 이전 이미지로 되돌리거나 gitops에서 Application을 내리면 된다. 마이그레이션은 신규 테이블 생성뿐이다.

## 8. 배포 고려사항
- **환경변수** (레포 `.env.example`, 루트 `local/.env.example`에 반영)
  - auth-service: `AUTH_DB_URL`, `AUTH_DB_USERNAME`, `AUTH_DB_PASSWORD`, `AUTH_JWT_ISSUER`, `AUTH_JWT_SIGNING_KEY`(개인키를 포함한 EC P-256 JWK JSON, `kid` 포함, 비밀값), `AUTH_JWT_PREVIOUS_PUBLIC_KEYS`(선택, JWK Set JSON), `AUTH_ADMIN_EMAIL`, `AUTH_ADMIN_PASSWORD`(비밀값)
  - api-gateway: `GATEWAY_JWKS_URI`, `GATEWAY_JWT_ISSUER`, `GATEWAY_CORS_ALLOWED_ORIGINS`, `GATEWAY_REDIS_HOST`, `GATEWAY_REDIS_PORT`, 서비스별 `*_SERVICE_URI`
- **로컬 compose** (docs 레포 `local/`): PostgreSQL(auth_db), Redis, auth-service(8081), api-gateway(8080). 별도 docs PR로 추가한다.
- **운영**: Secrets Manager 경로(infra), gitops `apps/{auth-service,api-gateway}/base` 매니페스트와 auth-service NetworkPolicy(게이트웨이만 허용)는 각 레포 PR로 한다.
- **배포 순서**: auth-service → api-gateway (게이트웨이가 시작할 때 JWKS를 읽을 수 있어야 함).
