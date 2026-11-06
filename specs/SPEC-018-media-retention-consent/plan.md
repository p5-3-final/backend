---
spec: SPEC-018
repo: backend
status: Approved
owner: <이름>
created: 2026-11-06
updated: 2026-11-06
---

# Plan: SPEC-018 학습용 보존 동의와 보존 키 — backend

- spec: docs 레포 `docs/specs/SPEC-018-media-retention-consent/spec.md`
- 계약: docs 레포 `service-contracts/openapi/media-service.yaml` 1.3.1, `events/video.deleted.schema.json`, `events/video.retention.revoked.schema.json`, `asyncapi/media-service.yaml` 1.1.0 (계약 묶음 2.9.1)
- ADR: ADR-0011

## 1. 요약

- media-service에 기능 패키지 `retention`을 더한다. 계정 기본값, 영상 동의 변경, 보존 중인 삭제 영상 목록·철회 API와 최소 기록 만료 작업을 둔다.
- 영상에 동의 값을 저장한다. 업로드 자리 발급 때 요청 값이나 계정 기본값을 쓴다.
- 동의한 영상을 지우면 같은 트랜잭션에서 보존 키를 계산해 최소 기록을 남기고 `video.deleted`에 `retention`을 싣는다.
- 보존 키는 HMAC-SHA256(비밀 키, sourceId 소문자 문자열)의 16진 소문자다. 비밀 키는 Base64 환경변수 `MEDIA_RETENTION_HMAC_KEY`로 받고, 디코딩한 길이가 32바이트보다 짧거나 없으면 기동에 실패한다(사용자 확인함, 2026-11-06).
- 새 의존성은 없다(JDK `javax.crypto.Mac`). 마이그레이션 1개(사용자 확인함, 2026-11-06).

## 2. 담당 요구사항

경로 약어: `M` = `services/media-service/src`

| 요구사항 | 담당 모듈 / 컴포넌트 | 검증 테스트 |
| --- | --- | --- |
| R-1 | `retention/application/RetentionService#settings` | `RetentionSettingsApiTest` |
| R-2 | `RetentionService#updateSettings` | `RetentionSettingsApiTest` |
| R-3 | `CreateVideoService`(→ `RetentionService#defaultConsent`) | `CreateVideoConsentApiTest` |
| R-4 | `CreateVideoRequest.retentionConsent`, `Video#startUpload` | `CreateVideoConsentApiTest` |
| R-5 | `VideoResponse.retentionConsent` | `CreateVideoConsentApiTest`, `MediaContractTest` |
| R-6 | `RetentionService#changeConsent`, `Video#changeRetentionConsent` | `VideoConsentApiTest` |
| R-7 | `RetentionService#changeConsent`(소유자만) | `VideoConsentApiTest` |
| R-8 | `DeleteVideoService`(→ `RetentionService#retain`), `VideoDeleted.retention` | `VideoDeletedEventTest` |
| R-9 | `RetentionService#retain`, `retained_videos` | `VideoDeletedEventTest` |
| R-10 | `DeleteVideoService`(미동의면 보존 없음) | `VideoDeletedEventTest` |
| R-11 | `retention/application/RetentionKeys` | `RetentionKeysTest`(RFC 4231 시험 벡터, 형식) |
| R-12 | `RetentionSettingsProperties` 검증 | `RetentionKeysTest`(짧은 키·빈 키로 컨텍스트 기동 실패) |
| R-13 | `RetentionService#listRetained` | `RetainedVideosApiTest` |
| R-14 | `RetentionService#revoke` | `RetainedVideosApiTest` |
| R-15 | `RetentionService#revoke`(없음·남의 기록 404) | `RetainedVideosApiTest` |
| R-16 | `RetentionService#revokeAll` | `RetainedVideosApiTest` |
| R-17 | `retention/application/RetentionExpiryJob` | `RetentionExpiryTest` |
| R-18 | `retention/application/event/VideoRetentionRevoked` | `RetainedVideosApiTest`(계약 스키마 검증) |
| NFR-1 | 키를 로그·응답·이벤트에 쓰지 않음 | `SensitiveLogTest`에 삭제·철회 추가 |
| NFR-2 | 전체 | `MediaLatencyTest`에 보존 API 추가 |
| NFR-3 | `RetentionService#revoke`(최소 기록 행 `FOR UPDATE`) | `ConcurrentRevokeTest` |
| NFR-4 | 기존 Outbox 발행(SPEC-003 NFR-3과 같음) | 기존 `OutboxPublisher` 테스트, 로컬 compose 확인 |

## 3. 원칙 점검

| 원칙 | 준수 | 비고 |
| --- | --- | --- |
| I 스펙 우선 — spec이 Approved 상태인가 | ☑ | SPEC-018 Approved (docs #101) |
| II 계약 우선 — 계약 변경이 먼저 머지됐는가 | ☑ | docs #101·#103, 계약 묶음 2.9.1 |
| III 테스트 우선 — 모든 R-*에 테스트가 매핑되고, 태스크가 요구사항 단위 TDD 사이클로 나뉘었는가 | ☑ | R-1~R-18, NFR-1~4 매핑 |
| IV 서비스 자율성 — 다른 서비스 DB나 코드에 직접 의존하지 않는가 | ☑ | 자기 DB만 쓴다. 다른 서비스는 이벤트로만 안다 |
| V 단순성 — 새 의존성이나 추상화가 정당한가 | ☑ | 새 의존성 없음. HMAC은 JDK |
| VI 관측 가능성 — 로그, 추적, health를 갖췄는가 | ☑ | 기존 요청 로그·추적. 만료 작업은 지운 수를 로그로 남긴다 |
| VII 보안 — 입력 검증, 인가, 비밀값 처리를 했는가 | ☑ | 비밀 키는 환경변수, `toString`에 남기지 않음. 동의·철회는 소유자만. 최소 기록에 파일 이름 없음 |
| VIII 문서·주석 — spec·계약·plan과 구현이 일치하고, 주석이 왜를 설명하는가 | ☑ | `MediaContractTest`, 이벤트 스키마 검증 |
| IX GitOps — AWS 변경은 infra, 클러스터 변경은 gitops 레포를 거치는가 | ☑ | 운영 비밀 키(Kubernetes Secret)는 사용자 지시 시 gitops에서 한다 |

## 4. 기술 설계

### 스키마

```sql
ALTER TABLE videos ADD COLUMN retention_consent BOOLEAN NOT NULL DEFAULT false;

CREATE TABLE retention_settings (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_id        UUID        NOT NULL UNIQUE,
    default_consent BOOLEAN     NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE retained_videos (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_id    UUID        NOT NULL UNIQUE,
    owner_id     UUID        NOT NULL,
    deleted_at   TIMESTAMPTZ NOT NULL,
    retain_until TIMESTAMPTZ NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_retained_videos_owner_deleted ON retained_videos (owner_id, deleted_at DESC, id DESC);
CREATE INDEX idx_retained_videos_retain_until ON retained_videos (retain_until);
```

- 계정 설정 행이 없으면 기본값 `false`다(R-1). 처음 바꿀 때 행을 만든다.
- `retained_videos`는 ADR-0011의 최소 기록이다. 동의 여부는 행이 있는 것으로 나타낸다(미동의 영상은 행이 없다, R-10). 철회·만료 때 행을 지운다.

### 패키지

| 경로 | 내용 |
| --- | --- |
| `retention/api` | `RetentionController`(설정·동의 변경·보존 목록·철회), 요청·응답 record |
| `retention/application` | `RetentionService`, `RetentionKeys`(HMAC), `RetentionSettingsProperties`, `RetentionExpiryJob`, `event/VideoRetentionRevoked` |
| `retention/domain` | `RetentionSetting`, `RetainedVideo`와 리포지토리 |
| `retention/infra` | JPA 구현, 만료 작업 스케줄 |

- 영상 기능은 `retention/application`만 부른다(업로드 자리 발급의 기본값, 삭제의 보존 기록). `retention`은 영상 기능을 부르지 않고 `shared`의 `Video`만 쓴다. 기능 사이에 순환이 없다.

### 삭제 흐름 (R-8~R-10)

1. 기존처럼 영상 행을 잠그고 "삭제"로 바꾼다.
2. 동의한 영상이면 `RetentionService#retain(video, now)`가 보존 키와 `retainUntil = now + 1년`(UTC 달력)을 계산해 최소 기록을 저장하고 `Retention(key, retainUntil)`을 돌려준다.
3. `VideoDeleted`에 `retention`을 담는다. 미동의면 필드를 뺀다(`@JsonInclude(NON_NULL)`).

### 철회 흐름 (R-14~R-16, NFR-3)

- 영상별: 최소 기록을 `FOR UPDATE`로 잠그고 소유자를 확인한다. 보존 키를 다시 계산해 `video.retention.revoked`를 Outbox에 넣고 행을 지운다. 동시에 온 두 번째 요청은 잠금이 풀린 뒤 행이 없어 404다.
- 전체: 요청자의 행을 모두 `FOR UPDATE`로 잠그고 행마다 이벤트를 넣은 뒤 지운다.

### 보존 키 (R-11, R-12)

- `RetentionKeys`는 기동 때 비밀 키를 Base64로 디코딩하고 길이를 확인한다. 실패하면 빈 생성에서 예외를 던져 기동이 멈춘다.
- `hex(HMAC-SHA256(key, sourceId.toString()))`. `UUID#toString`은 소문자다.
- 설정 record의 `toString`은 키를 가린다(NFR-1).

### 만료 (R-17)

- `RetentionExpiryJob`은 `retain_until < now`인 행을 한 묶음씩 지운다. 1시간마다 돈다(24시간 안).

### 설정

- `vlm.media.retention.hmac-key: ${MEDIA_RETENTION_HMAC_KEY}` (기본값 없음)
- 로컬: backend `.env.example`, docs `local/.env.example`·`local/docker-compose.yml`에 로컬 전용 샘플 키, `video.retention.revoked` 토픽

## 5. 대안과 선택 이유

| 대안 | 장점 | 단점 | 선택 |
| --- | --- | --- | --- |
| 별도 최소 기록 테이블 | ADR-0011 항목만 남음, 철회·만료가 단순 | 테이블 하나 늘어남 | ☑ |
| 삭제된 `videos` 행을 1년 보존 | 테이블이 늘지 않음 | 파일 이름 등 최소 기록보다 많은 값이 남음 | |
| 보존 키를 최소 기록에 저장 | 철회 때 다시 계산하지 않음 | 키와 sourceId가 DB에 함께 남아 다시 잇기가 쉬워짐(ADR-0011은 다시 계산) | |

## 6. 테스트 전략

- **단위**: `RetentionKeysTest`(RFC 4231 Test Case 2 시험 벡터로 HMAC 확인, 64자 소문자, 짧은·빈 키 거절)
- **통합**: `@IntegrationTest`에 테스트용 키를 넣는다. 설정·동의 API, 삭제 이벤트 payload, 철회 이벤트 payload를 계약 스키마로 검증
- **동시성**: `ConcurrentRevokeTest` — 같은 기록에 철회 10건 동시, 이벤트 1건
- **계약**: `MediaContractTest`에 보존 API 6개와 `retentionConsent` 요청 추가
- **비기능**: `SensitiveLogTest`(키가 로그에 없음), `MediaLatencyTest`(p95 300ms)

## 7. 리스크와 롤백

- 리스크: 비밀 키를 잃으면 철회 때 보존 키를 다시 계산할 수 없다(ADR-0011). 운영 키는 Secret으로 관리하고 교체 절차는 범위 밖이다.
- 롤백: 마이그레이션은 추가뿐이다. 이전 이미지는 새 컬럼·테이블을 무시한다. 다만 이전 이미지는 `video.deleted`에 보존 정보를 싣지 않으므로, 롤백 중 지운 동의 영상은 보존되지 않는다(데이터가 덜 남는 쪽이라 안전).

## 8. 배포 고려사항

- 환경변수 `MEDIA_RETENTION_HMAC_KEY`(필수, Base64 32바이트 이상). 운영은 Kubernetes Secret(사용자 지시 시 gitops).
- Kafka 토픽 `video.retention.revoked`(키 sourceId). 로컬은 kafka-init, 운영은 infra·gitops 작업 때 만든다.
