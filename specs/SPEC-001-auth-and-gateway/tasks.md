---
spec: SPEC-001
repo: backend
updated: 2026-11-02
---

# Tasks: SPEC-001 회원가입·로그인과 인증된 요청 — backend

규칙
- 태스크 하나는 커밋 하나에서 PR 하나 사이의 크기로 잡는다(반나절 이하).
- `[P]`는 다른 태스크와 병렬로 진행할 수 있다는 뜻이다. `[R-n]`은 담당 요구사항이다.
- 태스크 하나는 요구사항 하나의 Red → Green → Refactor 사이클이다. 테스트와 구현을 같은 태스크에서 끝내서, 커밋마다 테스트가 통과하는 상태를 유지한다.
- 완료한 태스크는 `[x]`로 바꾼다. 커밋한 뒤에는 커밋 해시를 덧붙인다.

경로 약어: `A` = `services/auth-service/src`, `G` = `services/api-gateway/src`

## Phase 1: 준비
- [x] T001 버전 카탈로그에 WireMock·Testcontainers·JSpecify 추가 — `gradle/libs.versions.toml`, `build-logic`
- [x] T002 [P] 공통 오류 타입 `ErrorCode`, `BusinessException` — 테스트 `libs/common-core/src/test/.../BusinessExceptionTest.java`, 구현 `libs/common-core/src/main/java/com/vlm/common/error/`
- [x] T003 [P] MVC용 ProblemDetail 핸들러(`code`, `traceId`, Bean Validation 변환) — 테스트 `libs/common-web/src/test/.../ProblemDetailExceptionHandlerTest.java`, 구현 `libs/common-web/src/main/java/com/vlm/common/web/`
- [x] T004 auth-service 모듈 생성: settings 등록, 빌드 파일, 애플리케이션 클래스, `application{,-local}.yml`, 마이그레이션 `V202611021200__create_auth_tables.sql`, ArchUnit 테스트 — `services/auth-service/`
- [x] T005 [P] api-gateway 모듈 생성: settings 등록, 빌드 파일, 애플리케이션 클래스, 라우트 설정, WireMock 지원 클래스, ArchUnit 테스트 — `services/api-gateway/`

## Phase 2: US-1 회원가입 (P1)
- [ ] T006 [R-1] 정상 가입 시 USER 계정 생성, 201 — 테스트 `A/test/.../api/SignUpApiTest.java`, 구현 `A/main/.../{api/SignUpController,application/SignUpService,domain/User,infra/persistence}`
- [ ] T007 [R-2] 중복 이메일 409 `AUTH_EMAIL_DUPLICATED` (대소문자 무시, 동시 가입 포함) — 테스트 `SignUpApiTest`, 구현 `SignUpService`
- [ ] T008 [R-3] 이메일 형식 오류 400 `AUTH_INVALID_REQUEST` — 테스트 `SignUpApiTest`, 구현 `api/SignUpRequest`
- [ ] T009 [R-4] 비밀번호 정책 위반 400 `AUTH_PASSWORD_POLICY` — 테스트 `domain/PasswordPolicyTest`, `SignUpApiTest`, 구현 `domain/PasswordPolicy`
- [ ] T010 [R-5] 닉네임 2~20자·한글/영문/숫자 위반 400 — 테스트 `SignUpApiTest`, 구현 `SignUpRequest`
- [ ] T011 [R-6] 비밀번호를 BCrypt 해시로만 저장 — 테스트 `SignUpApiTest`, 구현 `infra/BcryptPasswordHasher`

## Phase 3: US-2 로그인과 내 정보 (P1)
- [ ] T012 [R-11] JWKS 경로에서 현재·이전 공개키 반환, 개인키 비노출 — 테스트 `A/test/.../api/JwksApiTest.java`, 구현 `api/JwksController`, `infra/token/SigningKeys`
- [ ] T013 [R-7] 로그인 성공 시 접근 토큰(ES256, 계약 클레임) + 재발급 쿠키 — 테스트 `api/LogInApiTest`, 구현 `application/LogInService`, `infra/token/JwtTokenIssuer`, `domain/RefreshToken`
- [ ] T014 [R-8] 이메일 없음·비밀번호 틀림을 구분하지 않고 401 — 테스트 `LogInApiTest`, 구현 `LogInService`
- [ ] T015 [R-9] 로그인 시도마다 기록(시각, 계정, 성공 여부, IP) — 테스트 `LogInApiTest`, 구현 `domain/LoginAudit`, `application/LoginAuditRecorder`
- [ ] T016 [R-10] `X-User-Id`로 내 정보 반환 — 테스트 `api/MeApiTest`, 구현 `api/MeController`, `application/MeService`

## Phase 4: US-3 인증된 요청 전달 (P1)
- [ ] T017 [R-13] 공개 경로(signup, login, refresh, health)는 토큰 없이 전달 — 테스트 `G/test/.../PublicRoutingTest.java`, 구현 `G/main/.../config/SecurityConfig`
- [ ] T018 [R-12] 유효한 토큰이면 해당 서비스로 전달하고 `X-User-Id`·`X-User-Roles` 부착 — 테스트 `AuthenticatedRoutingTest`(테스트용 EC 키 쌍·토큰 서명 지원 클래스 포함), 구현 `config/SecurityConfig`, `filter/UserHeaderFilter`
- [ ] T019 [R-14] 토큰 없음 401 `AUTH_TOKEN_MISSING` — 테스트 `TokenRejectionTest`, 구현 `error/ProblemAuthenticationEntryPoint`
- [ ] T020 [R-15] 서명·`iss`·`aud`·`alg` 불일치 401 `AUTH_TOKEN_INVALID` — 테스트 `TokenRejectionTest`, 구현 `config/JwtDecoderConfig`
- [ ] T021 [R-16] 만료 401 `AUTH_TOKEN_EXPIRED` (허용 오차 30초) — 테스트 `TokenRejectionTest`, 구현 `ProblemAuthenticationEntryPoint`
- [ ] T022 [R-17] 클라이언트가 보낸 `X-User-*` 헤더 제거 (공개·보호 경로 모두) — 테스트 `HeaderSpoofingTest`, 구현 `UserHeaderFilter`
- [ ] T023 [P] [R-18] 등록되지 않은 경로 404 `GATEWAY_ROUTE_NOT_FOUND` — 테스트 `GatewayErrorTest`, 구현 `error/GatewayErrorHandler`
- [ ] T024 [R-19] 하위 서비스 응답 시간 초과 504 `GATEWAY_TIMEOUT` — 테스트 `GatewayErrorTest`, 구현 `GatewayErrorHandler`, 라우트 응답 시간 설정
- [ ] T025 [R-20] 하위 서비스 연결 불가 503 `GATEWAY_UNAVAILABLE` — 테스트 `GatewayErrorTest`, 구현 `GatewayErrorHandler`
- [ ] T026 [P] [R-21] 허용 목록 밖 출처에는 CORS 허용 헤더 없음, 허용 출처는 자격 증명 허용 — 테스트 `CorsTest`, 구현 `application.yml` globalcors
- [ ] T027 [NFR-5] 모르는 `kid`면 JWKS 즉시 재조회, 교체 전 키 토큰 계속 허용 — 테스트 `KeyRotationTest`, 구현 `JwtDecoderConfig`

## Phase 5: US-4 로그인 유지와 로그아웃 (P2)
- [ ] T028 [R-22] 재발급 시 새 접근·재발급 토큰, 이전 토큰 무효 — 테스트 `A/test/.../api/RefreshApiTest.java`, 구현 `application/TokenRefreshService`
- [ ] T029 [R-23] 사용된 재발급 토큰 재제출 시 family 전체 폐기, 401 `AUTH_REFRESH_REUSED` — 테스트 `RefreshApiTest`, 구현 `TokenRefreshService`, `domain/RefreshToken`
- [ ] T030 [R-24] 재발급 토큰 없음·만료·폐기 401 `AUTH_REFRESH_INVALID` — 테스트 `RefreshApiTest`, 구현 `TokenRefreshService`
- [ ] T031 [R-25] 로그아웃 시 재발급 토큰 폐기, 쿠키 삭제, 204 — 테스트 `LogOutApiTest`, 구현 `api/LogOutController`, `application/LogOutService`

## Phase 6: US-5 실시간 알림 연결 인증 (P2)
- [ ] T032 [R-26] 30초 티켓(JWT, `aud: vlm-sse`) 발급 — 테스트 `A/test/.../api/SseTicketApiTest.java`, 구현 `application/SseTicketIssuer`
- [ ] T033 [R-27] 유효 티켓이면 사용 처리 후 사용자 헤더 붙여 notification-service로 전달 — 테스트 `G/test/.../SseTicketTest.java`(Testcontainers Redis), 구현 `filter/SseTicketFilter`
- [ ] T034 [R-28] 티켓 없음·만료·재사용·접근 토큰 제출 401 `AUTH_TICKET_INVALID` — 테스트 `SseTicketTest`, 구현 `SseTicketFilter`

## Phase 7: US-6 로그인 남용 방지 (P2)
- [ ] T035 [R-29] 연속 5회 실패 시 15분간 423 `AUTH_ACCOUNT_LOCKED`, 성공 시 횟수 초기화 — 테스트 `domain/AccountLockTest`, `LogInApiTest`, 구현 `domain/User`, `LogInService`
- [ ] T036 [P] [R-30] IP당 로그인·가입 분당 10회 초과 시 429 `GATEWAY_RATE_LIMITED` + `Retry-After` — 테스트 `RateLimitTest`(Testcontainers Redis), 구현 라우트 `RequestRateLimiter`, `error/RateLimitedResponseFilter`

## Phase 8: 운영 준비
- [ ] T037 [R-31] ADMIN이 없으면 환경변수로 초기 관리자 생성, 값이 없으면 경고 후 건너뜀 — 테스트 `A/test/.../application/InitialAdminTest.java`, 구현 `application/InitialAdminCreator`

## Phase 9: 마무리
- [ ] T050 auth-service 응답과 `auth-service.yaml` 스키마 일치 확인 — 테스트 `A/test/.../contract/AuthContractTest.java`
- [ ] T051 [NFR-2, NFR-3] DB에 토큰 원문 없음, 로그에 비밀번호·토큰·티켓 없음 — 테스트 `SensitiveLogTest`(두 서비스)
- [ ] T052 [NFR-1] 게이트웨이 추가 지연 p95 50ms 이하 — 테스트 `G/test/.../GatewayLatencyTest.java`
- [ ] T053 [NFR-4] 모든 오류 응답 `application/problem+json`·`traceId` 확인, Actuator health·tracing 설정 확인
- [ ] T054 환경변수 반영: 레포 `.env.example`, 루트 `local/.env.example`, README 명령어
- [ ] T055 서비스 workflow `.github/workflows/{auth-service,api-gateway}.yml` (이미지 빌드·ECR push·gitops 갱신)

## 완료 조건 (Definition of Done)
- [ ] 이 레포가 담당하는 모든 R-*의 테스트 통과
- [ ] lint, 타입 검사, 전체 테스트 통과
- [ ] plan.md와 실제 구현이 일치함
- [ ] docs 레포 spec 상태 갱신 (`Implementing` / `Done`)
