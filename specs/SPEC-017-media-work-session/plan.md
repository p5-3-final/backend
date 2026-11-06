---
spec: SPEC-017
repo: backend
status: Approved
owner: <이름>
created: 2026-11-06
updated: 2026-11-06
---

# Plan: SPEC-017 작업 세션 — backend

- spec: docs 레포 `docs/specs/SPEC-017-media-work-session/spec.md`
- 계약: docs 레포 `service-contracts/openapi/media-service.yaml` 1.3.0, `events/video.uploaded.schema.json` (계약 묶음 2.9.0)
- ADR: ADR-0010 ④

## 1. 요약

- media-service에 기능 패키지 `session`을 더한다. 세션 만들기·목록·상세 API를 둔다.
- 업로드 자리 발급 요청의 `sessionId`·`role`로 영상을 세션에 넣는다. 세션은 요청자 소유여야 하고, "삭제"가 아닌 영상이 100개면 거절한다.
- `video.uploaded`와 영상 응답에 세션 정보를 싣는다.
- 계약 잠금 파일을 2.9.0으로 올린다. 2.9.0의 영상 응답은 `retentionConsent`(SPEC-018)를 필수로 두므로, 이번에는 `false`를 담는다. 동의 수단이 아직 없어 모든 영상이 미동의(SPEC-018 R-1 기본값)라 사실과 같다. SPEC-018 구현에서 저장 값으로 바꾼다.
- 새 의존성은 없다. 마이그레이션 1개를 더한다(사용자 확인함, 2026-11-06).

## 2. 담당 요구사항

경로 약어: `M` = `services/media-service/src`

| 요구사항 | 담당 모듈 / 컴포넌트 | 검증 테스트 |
| --- | --- | --- |
| R-1 | `session/api/WorkSessionController`, `session/application/WorkSessionService`, `shared/domain/WorkSession` | `WorkSessionApiTest` |
| R-2 | `CreateWorkSessionRequest` Bean Validation | `WorkSessionApiTest` |
| R-3 | `WorkSessionService#list`, `WorkSessionRepository#findByOwner` | `WorkSessionApiTest` |
| R-4 | `WorkSessionService#get`, `VideoRepository#findVisibleInSession` | `WorkSessionApiTest` |
| R-5 | `WorkSessionService#get`(소유자·ADMIN) | `WorkSessionApiTest` |
| R-6 | `CreateVideoService`, `Video#startUpload`(세션·역할) | `CreateVideoSessionApiTest` |
| R-7 | `shared/domain/SessionOrder` | `SessionOrderTest`, `WorkSessionApiTest` |
| R-8 | `CreateVideoRequest`(둘 중 하나만), `SessionRole` 역직렬화 | `CreateVideoSessionApiTest` |
| R-9 | `CreateVideoService`(세션 소유자 확인) | `CreateVideoSessionApiTest` |
| R-10 | `CreateVideoService`(세션 행 잠금 후 개수 확인) | `CreateVideoSessionApiTest` |
| R-11 | `VideoUploaded`(`sessionId`·`role`) | `VideoUploadedEventTest` |
| R-12 | `VideoUploaded`(null이면 필드 생략) | `VideoUploadedEventTest` |
| R-13 | `VideoResponse.session` | `CreateVideoSessionApiTest`, `MediaContractTest` |
| NFR-1 | 전체 | `MediaLatencyTest`에 세션 API 추가 |
| NFR-2 | `CreateVideoService`(세션 행 `FOR UPDATE`) | `ConcurrentSessionVideoTest` |

## 3. 원칙 점검

| 원칙 | 준수 | 비고 |
| --- | --- | --- |
| I 스펙 우선 — spec이 Approved 상태인가 | ☑ | SPEC-017 Approved (docs #101) |
| II 계약 우선 — 계약 변경이 먼저 머지됐는가 | ☑ | docs #101(`media-service.yaml` 1.3.0, 계약 묶음 2.9.0) 머지 |
| III 테스트 우선 — 모든 R-*에 테스트가 매핑되고, 태스크가 요구사항 단위 TDD 사이클로 나뉘었는가 | ☑ | R-1~R-13, NFR-1~2 매핑. tasks.md 참고 |
| IV 서비스 자율성 — 다른 서비스 DB나 코드에 직접 의존하지 않는가 | ☑ | 자기 DB만 쓴다 |
| V 단순성 — 새 의존성이나 추상화가 정당한가 | ☑ | 새 의존성 없음. 세션 안 순서는 영상이 100개 이하라 DB 대신 코드에서 정렬한다 |
| VI 관측 가능성 — 로그, 추적, health를 갖췄는가 | ☑ | 기존 요청 로그·추적 그대로 |
| VII 보안 — 입력 검증, 인가, 비밀값 처리를 했는가 | ☑ | 세션 이름·현장·구역 길이 검증, 세션 소유자 확인(남의 세션은 404로 숨김) |
| VIII 문서·주석 — spec·계약·plan과 구현이 일치하고, 주석이 왜를 설명하는가 | ☑ | `MediaContractTest`로 계약 검증 |
| IX GitOps — AWS 변경은 infra, 클러스터 변경은 gitops 레포를 거치는가 | ☑ | 인프라 변경 없음 |

## 4. 기술 설계

### 변경 대상

| 경로 | 변경 |
| --- | --- |
| `M/main/resources/db/migration/V202611061000__create_work_sessions.sql` | `work_sessions` 테이블, `videos.session_id`·`session_role` |
| `M/main/.../shared/domain/{WorkSession,WorkSessionRepository,SessionRole,SessionOrder}.java` | 세션 엔티티·리포지토리·역할·순서. 영상 기능(업로드 자리 발급)과 세션 기능(상세)이 함께 써서 `shared`에 둔다 |
| `M/main/.../shared/infra/persistence/WorkSession*` | JPA 구현 |
| `M/main/.../shared/domain/{Video,VideoRepository,MediaErrorCode}.java` | 세션 필드, 세션 안 영상 조회, 오류 코드 2개 |
| `M/main/.../session/{api,application}/` | 세션 API |
| `M/main/.../video/api/{CreateVideoRequest,VideoResponse,VideoController}.java`, `video/application/CreateVideoService.java`, `video/application/event/VideoUploaded.java` | 세션 지정, 응답·이벤트 필드 |
| `service-contracts.lock` | 2.9.0 |

### 스키마

```sql
CREATE TABLE work_sessions (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id  UUID         NOT NULL UNIQUE,
    owner_id   UUID         NOT NULL,
    name       VARCHAR(100) NOT NULL,
    site       VARCHAR(100),
    area       VARCHAR(100),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_work_sessions_owner_created ON work_sessions (owner_id, created_at DESC, id DESC);

ALTER TABLE videos ADD COLUMN session_id UUID REFERENCES work_sessions (public_id);
ALTER TABLE videos ADD COLUMN session_role VARCHAR(10);
CREATE INDEX idx_videos_session ON videos (session_id) WHERE session_id IS NOT NULL;
```

- `videos.session_id`는 세션의 `public_id`를 가리킨다. 영상 응답과 이벤트가 세션의 공개 ID를 쓰므로, 내부 PK를 쓰면 응답마다 조인이 필요하다. 같은 서비스 테이블이라 FK를 둔다.
- 기존 영상은 둘 다 NULL(세션 없음)이다. 두 컬럼은 함께 NULL이거나 함께 값이 있다(CHECK).

### 업로드 자리 발급 흐름

1. `sessionId`·`role`이 둘 다 없으면 SPEC-003 흐름 그대로다. 하나만 있으면 400 `MEDIA_INVALID_REQUEST`(R-8). 역할이 정해진 값이 아니면 역직렬화 오류로 같은 400이다.
2. 세션을 `FOR UPDATE`로 잠그고 읽는다. 없거나 요청자가 소유자가 아니면 404 `MEDIA_SESSION_NOT_FOUND`(R-9).
3. 세션의 "삭제"가 아닌 영상 수가 100이면 409 `MEDIA_SESSION_FULL`(R-10). 세션 행을 잠근 채 세므로, 동시에 와도 한 요청씩 세고 넣는다(NFR-2).
4. 저장소 자리 준비(업로드 주소 서명·멀티파트 시작)는 세션 확인 뒤에 한다. 거절될 요청에 멀티파트 업로드를 만들지 않으려는 것이다.

### 세션 안 순서 (R-7)

`SessionOrder` 비교자: 역할 순서(`BEFORE` < `DURING` < `AFTER`) → 업로드 완료 시각이 있는 영상 먼저, 완료 시각 순 → 만든 시각 → 내부 id. 세션 상세가 이 순서로 정렬한다. 영상이 100개 이하라 DB 정렬 대신 코드에서 정렬해 순서 규칙을 한곳에 둔다.

### API

- `POST /api/v1/media/sessions` → 201 + `Location`, 400, 401
- `GET /api/v1/media/sessions?page&size` → 200 `WorkSessionPage`
- `GET /api/v1/media/sessions/{sessionId}` → 200 `WorkSessionDetail`, 404 `MEDIA_SESSION_NOT_FOUND`
- `POST /api/v1/media/videos`에 `sessionId`·`role`, 404·409 추가
- 영상 응답에 `session`(`{sessionId, role}` 또는 null)과 `retentionConsent`(이번에는 항상 `false`)

### 이벤트

- `VideoUploaded`에 `@Nullable UUID sessionId`, `@Nullable String role`. 값이 없으면 JSON에서 필드를 뺀다(`@JsonInclude(NON_NULL)`). 계약이 두 필드를 함께 있거나 함께 없게 정한다(`dependentRequired`).

## 5. 대안과 선택 이유

| 대안 | 장점 | 단점 | 선택 |
| --- | --- | --- | --- |
| `videos`에 세션 공개 ID·역할 컬럼 | 응답·이벤트에 조인 없음, 영상당 세션 하나와 맞음 | `videos` 컬럼 증가 | ☑ |
| 연결 테이블 `session_videos` | `videos`를 그대로 둠 | 영상은 세션 하나에만 속해 연결 테이블이 이득이 없고 조인이 늘어남 | |
| 세션 개수 상한을 DB 트리거로 | 동시성 보장이 DB에 있음 | 로직이 둘로 나뉘고 테스트가 어렵다 | |

## 6. 테스트 전략

- **단위**: `SessionOrderTest`(역할·완료 시각·미완료 순서)
- **통합**: `@IntegrationTest`(PostgreSQL·Kafka·SeaweedFS). 세션 API, 세션 지정 업로드 자리 발급, `video.uploaded` payload를 계약 스키마로 검증
- **동시성**: `ConcurrentSessionVideoTest` — 영상 99개인 세션에 업로드 자리 발급 10건을 동시에 보내 1건만 성공
- **계약**: `MediaContractTest`에 세션 API 3개와 세션 지정 업로드 자리 발급 응답 추가
- **비기능**: `MediaLatencyTest`에 세션 만들기·목록·상세(영상 100개) 추가

## 7. 리스크와 롤백

- 리스크: 계약 2.9.0은 `retentionConsent`를 필수로 둔다. SPEC-018 구현 전까지 `false`를 담으므로 frontend가 이 값을 동의 상태로 보여 줘도 사실과 같다.
- 롤백: 마이그레이션은 컬럼·테이블 추가뿐이다. 이전 이미지로 되돌려도 새 컬럼은 무시된다(`ddl-auto: validate`는 없는 컬럼만 검사).

## 8. 배포 고려사항

- 환경변수 추가 없음. 마이그레이션 1개(Flyway 자동 적용).
