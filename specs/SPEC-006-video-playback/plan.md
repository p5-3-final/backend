---
spec: SPEC-006
repo: backend
status: Approved
owner: <이름>
created: 2026-11-03
updated: 2026-11-03
---

# Plan: SPEC-006 원본 영상 재생 — backend

- spec: docs 레포 `docs/specs/SPEC-006-video-playback/spec.md`
- 계약: docs 레포 `docs/contracts/openapi/media-service.yaml` 1.1.0 (`getVideoPlayback`)
- ADR: ADR-0006(로컬·테스트 S3 대체 SeaweedFS)

## 1. 요약

- media-service에 `GET /api/v1/media/videos/{videoId}/playback`을 추가한다. 원본 파일을 읽는 서명 GET 주소 두 개(재생용, 내려받기용)와 만료 시각을 돌려준다.
- 영상 바이트는 서비스를 거치지 않는다. 업로드와 같은 서명용 클라이언트(`S3Presigner`, 공개 주소)로 GET 주소를 서명하고 브라우저가 저장소에서 바로 읽는다.
- 응답 형식과 내려받기 파일 이름은 서명 주소의 응답 덮어쓰기 파라미터(`response-content-type`, `response-content-disposition`)로 정한다. 저장된 객체의 메타데이터는 바꾸지 않는다.
- 새 테이블, 새 의존성, 새 설정은 없다.

## 2. 담당 요구사항

경로 약어: `M` = `services/media-service/src`

| 요구사항 | 담당 모듈 / 컴포넌트 | 검증 테스트 |
| --- | --- | --- |
| R-1 | `api/VideoController`, `application/PlaybackService`, `domain/Video#isUploadCompleted`, `UploadStorage#presignRead` | `VideoPlaybackApiTest`(SeaweedFS에 올린 파일을 두 주소로 실제 GET해 같은 바이트 확인) |
| R-2 | `PlaybackService`(유효 시간 1시간), `S3UploadStorage#presignRead` | `VideoPlaybackApiTest`(`expiresAt` = 발급 시각 + 1시간, 주소의 `X-Amz-Expires=3600`) |
| R-3 | `S3UploadStorage#presignRead`(`response-content-type`) | `VideoPlaybackApiTest`(재생 주소 응답의 Content-Type이 신고한 형식) |
| R-4 | `domain/Video#isUploadCompleted`, `PlaybackService` | `VideoPlaybackApiTest`(UPLOADING·EXPIRED → 409 `MEDIA_NOT_PLAYABLE`, NORMALIZING·READY·REJECTED → 200) |
| R-5 | `domain/VideoAccess`(SPEC-003과 같음) | `VideoPlaybackApiTest`(없음·삭제·남의 영상 404, ADMIN 200) |
| NFR-1 | 바이트를 내보내는 엔드포인트 없음 | `VideoPlaybackApiTest`(응답이 JSON이고 영상 바이트가 아님), `NoUploadBytesTest` 유지 |
| NFR-2 | 전체 | `MediaLatencyTest`에 재생 주소 발급 추가 |
| NFR-3 | 로그 | `SensitiveLogTest`에 재생 주소 발급 추가(서명이 로그에 없음) |

- 내려받기 주소(R-1)는 `response-content-disposition`으로 원래 파일 이름을 준다. 검증은 내려받기 주소 응답의 `Content-Disposition`이 `attachment`이고 파일 이름을 담는지로 한다.
- 계약 일치는 `MediaContractTest`에 200·404·409 응답을 추가해 확인한다.

## 3. 원칙 점검

| 원칙 | 준수 | 비고 |
| --- | --- | --- |
| I 스펙 우선 — spec이 Approved 상태인가 | ☑ | SPEC-006 Approved (docs #41) |
| II 계약 우선 — 계약 변경이 먼저 머지됐는가 | ☑ | docs #43(`media-service.yaml` 1.1.0) 머지 |
| III 테스트 우선 — 모든 R-*에 테스트가 매핑되고, 태스크가 요구사항 단위 TDD 사이클로 나뉘었는가 | ☑ | R-1~R-5, NFR-1~3 매핑. tasks.md 참고 |
| IV 서비스 자율성 — 다른 서비스 DB나 코드에 직접 의존하지 않는가 | ☑ | 자기 DB와 저장소만 쓴다 |
| V 단순성 — 새 의존성이나 추상화가 정당한가 | ☑ | 새 의존성·설정 없음. 기존 `UploadStorage`에 메서드 하나 추가. 유효 시간은 spec 고정값(1시간)이라 설정으로 빼지 않는다 |
| VI 관측 가능성 — 로그, 추적, health를 갖췄는가 | ☑ | 기존 요청 로그·추적 그대로. 서명 주소는 로그에 남기지 않는다 |
| VII 보안 — 입력 검증, 인가, 비밀값 처리를 했는가 | ☑ | 소유자·ADMIN 인가(404로 숨김), 서명 주소 1시간, `Content-Disposition` 파일 이름은 RFC 5987 인코딩으로 헤더 주입을 막는다 |
| VIII 문서·주석 — spec·계약·plan과 구현이 일치하고, 주석이 왜를 설명하는가 | ☑ | `MediaContractTest`로 계약 검증 |
| IX GitOps — AWS 변경은 infra, 클러스터 변경은 gitops 레포를 거치는가 | ☑ | 운영 S3 버킷 CORS(GET·Range)는 infra 몫(8절). 이 plan에서 변경 없음 |

## 4. 기술 설계

### 변경 대상

| 경로 | 변경 |
| --- | --- |
| `M/main/.../domain/MediaErrorCode.java` | `NOT_PLAYABLE(409, "MEDIA_NOT_PLAYABLE")` |
| `M/main/.../domain/UploadStorage.java` | `String presignRead(String storageKey, String contentType, @Nullable String downloadFileName, Duration ttl)` |
| `M/main/.../infra/storage/S3UploadStorage.java`, `ContentDispositions.java` | `S3Presigner#presignGetObject`로 구현, 내려받기용 `Content-Disposition` 값 |
| `M/main/.../application/PlaybackService.java` | 권한·상태 확인 후 두 주소와 만료 시각을 만든다 |
| `M/main/.../api/VideoController.java`, `api/VideoPlaybackResponse.java` | 엔드포인트와 응답 record |

### 재생 주소 발급 흐름

1. `findByPublicId` → `VideoAccess.canView`로 거르고, 볼 수 없으면 404 `MEDIA_VIDEO_NOT_FOUND`(R-5). 상세 조회와 같은 규칙이다.
2. 업로드 완료 이후 상태(`isUploadCompleted()`: `UPLOADED`, `NORMALIZING`, `READY`, `REJECTED`)가 아니면 409 `MEDIA_NOT_PLAYABLE`(R-4). 볼 수 있는지를 먼저 확인해야 남의 영상 상태가 드러나지 않는다.
3. `now = clock.instant()`, `ttl = 1시간`(R-2). 두 주소를 같은 `ttl`로 서명하고 `expiresAt = now + ttl`을 돌려준다.
   - SPEC-003에서 겪은 것처럼 남은 시간을 다시 계산해 넘기면 3599초로 잘린다. `ttl`을 그대로 넘긴다.
4. 트랜잭션은 `readOnly`다. 행을 바꾸지 않는다.

### 서명 GET 주소

- 재생 주소: `response-content-type = 신고한 형식`(R-3). SeaweedFS·S3 모두 이 덮어쓰기를 지원한다. 업로드할 때도 같은 형식으로 저장하지만, 멀티파트·POST 경로 차이로 저장 메타데이터가 다를 수 있어 주소에서 정한다.
- 내려받기 주소: 위에 더해 `response-content-disposition = attachment; filename="<ASCII 대체 이름>"; filename*=UTF-8''<퍼센트 인코딩 이름>`. 한글 파일 이름을 그대로 내려받게 하려는 것이다. ASCII 대체 이름은 ASCII가 아닌 글자와 `"`, `\`를 `_`로 바꾼다.
- 서명은 업로드 조각 주소와 같은 서명용 클라이언트(공개 주소, path-style 설정)를 쓴다. 로컬은 `http://localhost:8333`, 운영은 S3 기본 주소다.

### API

- `GET /api/v1/media/videos/{videoId}/playback` → 200 `{url, downloadUrl, expiresAt}`, 401, 404, 409 (`media-service.yaml` 1.1.0)

## 5. 대안과 선택 이유

| 대안 | 장점 | 단점 | 선택 |
| --- | --- | --- | --- |
| 서명 GET 주소(응답 덮어쓰기 파라미터) | 바이트가 서비스를 거치지 않음, 저장소가 Range 요청 처리 | 주소가 1시간 뒤 만료(frontend가 다시 발급) | ☑ |
| media-service가 바이트를 스트리밍 | 만료 처리 불필요 | NFR-1 위반, 서비스 대역폭·메모리 부담 | |
| 업로드 때 객체 메타데이터(`Content-Disposition`)를 저장 | 주소가 짧다 | 재생과 내려받기를 한 객체로 구분할 수 없음, 이미 올린 영상은 고칠 수 없음 | |

## 6. 테스트 전략

- **단위**: `ContentDispositionsTest`(한글·따옴표 파일 이름). 재생 가능 상태는 SPEC-003의 `Video#isUploadCompleted`를 그대로 쓰고 통합 테스트로 상태별로 확인한다.
- **통합**: `@IntegrationTest`(PostgreSQL·Kafka·SeaweedFS 컨테이너). 테스트가 업로드 자리를 받아 SeaweedFS에 실제로 올리고 완료한 뒤, 발급받은 두 주소로 GET해서 상태 코드, 바이트, `Content-Type`, `Content-Disposition`을 확인한다.
  - 상태를 바꿔야 하는 경우(EXPIRED, NORMALIZING 등)는 SPEC-003 테스트처럼 `JdbcTemplate`으로 행을 바꾼다.
- **계약**: `MediaContractTest`에 `getVideoPlayback` 200·404·409 추가
- **비기능**: `MediaLatencyTest`(p95 300ms), `SensitiveLogTest`(서명 미기록)에 재생 주소 발급 요청 추가
- **E2E**: 로컬 compose에서 업로드 후 재생 주소로 브라우저 재생은 frontend SPEC-006에서 시연한다.

## 7. 리스크와 롤백

- 리스크
  - SeaweedFS가 `response-content-disposition`의 RFC 5987 값을 그대로 돌려주지 않을 수 있다. 통합 테스트로 확인하고, 안 되면 사용자에게 알린 뒤 ASCII 이름만 쓰는 방식으로 바꾼다.
  - 운영 S3 버킷 CORS에 GET이 빠져 있으면 frontend가 `crossOrigin` 요청을 할 때 막힌다. `<video>`의 일반 재생은 CORS가 필요 없지만 infra 작업 때 GET·`Range`를 함께 허용한다(8절).
- 롤백: 엔드포인트 추가뿐이라 이전 이미지로 되돌리면 된다. 데이터 변경 없음.

## 8. 배포 고려사항

- 환경변수 추가 없음. 마이그레이션 없음.
- 운영 S3 버킷 CORS 규칙(infra 레포, 사용자 지시 시)에 업로드용 `POST`·`PUT`과 함께 `GET`·`HEAD`, 요청 헤더 `Range`를 넣는다.
- 로컬 SeaweedFS는 기본 CORS가 모든 Origin과 메서드를 허용한다(SPEC-005에서 확인).
