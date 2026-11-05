---
spec: SPEC-010
repo: backend
updated: 2026-11-05
---

# Tasks: SPEC-010 공정 배정·슬롯·요청 Outbox — backend

태스크마다 테스트 Red → Green → Refactor를 끝낸다. 실패 테스트를 커밋하지 않는다.

## Phase 1: 준비

- [x] T001 의존성·additive 마이그레이션·설정 및 최소 배정 인터페이스 — `services/pipeline-service/build.gradle.kts`, `.../db/migration`, `.../config`

## Phase 2: US-1 제한된 공정 배정 (P1)

- [x] T002 [R-1 R-5 R-6] 정상 배정·실행/슬롯/상태/Outbox 원자 저장 — `DispatchServiceTest`, `application/DispatchService`, `domain/DispatchRepository`, `infra/persistence/JdbcDispatchRepository`
- [x] T003 [R-2 R-3 R-4 R-13] 상한·다중 서버·영속 공정성·opt-in — `DispatchServiceTest`, `config/DispatchProperties`, 후보/슬롯 쿼리

## Phase 3: US-2 전달·장애와 회수 (P1)

- [x] T004 [R-8] 모든 DB 저장 실패와 재시도 — `DispatchServiceTest`, 배정 트랜잭션
- [x] T005 [R-10 R-11 R-12] 만료·증거 기반 반환·중복/충돌·회수 롤백 — `DispatchServiceTest`, `DispatchRecoveryService`, 복구 CLI
- [x] T006 [R-6 R-7 R-9] 실제 Kafka·기존 Publisher·실패/재시작/중복 흡수 — `DispatchKafkaTest`, 신규 요청 계약

## Phase 4: 마무리

- [x] T007 계약과 실제 payload·traceparent·회수 메트릭 검증
- [x] T008 환경변수·README·plan/tasks 결과 갱신
- [x] T009 필수 검사·최종 변경 자체 검토

## 완료 조건 (Definition of Done)

- [x] 모든 R-* 테스트 통과 (검증용 소비자 범위)
- [x] lint·컴파일·전체 테스트 통과
- [x] plan과 실제 구현 일치, 슬롯 회수 명령 확인
- docs spec 상태는 구현 PR과 연결한 docs PR에서 Implementing으로 반영한다. 운영 배포/실제 worker 연결 전 Done 금지

## TDD 검증 기록

- Red: 배정 미구현 상태의 `dispatchesAtomically`가 `NoSuchElementException: No value present`로 실패했다(`/private/tmp/spec010-red.log`).
- Green: 정상 배정 테스트가 통과했다. 세 단계 상한·공정 순환·16개 동시 배정 요청에서 최대 4슬롯을 검증했다.
- 회복 Red/Green: DB 저장 실패 주입 후 재시도가 singleton IDENTITY 증가로 실패하는 것을 7개 케이스에서 확인했다. 구성 행을 명시적 id=1로 저장하여 롤백/재시도 모두 통과했다.
- 배정 8개 저장 경계(실행·슬롯·청크·Job·구성 순서·사용자 순서·Outbox·trace), 반환 3개 저장 경계의 전체 롤백을 검증했다.
- 기존 데이터 대상 expand migration 및 마지막 125001ms 경계·비정수 ms 거부를 검증했다.
- Kafka 전송 실패/전송 후 DB 확인 실패/새 Publisher 재발행, 검증 소비자 내구 원장의 같은 실행 중복/다른 입력 충돌·기한 후 신규 시작 거부를 검증했다.
- `env -u DEBUG ./gradlew spotlessApply spotlessCheck build`: 전체 backend 468개 / Pipeline 134개 통과, 실패·skip 0. 기존 IDE DEBUG 환경변수만 제거했으며 검사 기준 변경 없음.
- 패키징 JAR 복구 CLI smoke: 실제 임시 PostgreSQL에서 알려지지 않은 실행 거부, STOPPED/FAILED/슬롯 반환, 재시도 멱등, 충돌 거부를 확인했다. 환경변수 enabled=true에서도 메시지 수신·배정·발행·웹을 강제로 끄는 것을 확인했다.
- CI·Draft 해제·머지의 최종 상태는 구현 PR에서 확인한다. 결과가 나오기 전에 완료했다고 기록하지 않는다.

- opt-in 주기 통합 검증을 추가했다. 수동 배정 호출 없이 실제 Kafka 발행·만료 대기·종료 확인·다음 청크 배정까지 확인했다(`DispatchScheduleTest`).
- 최종 ai main `40356fe`의 새 normalize v2를 읽고 occurredAt 재생성 충돌을 확인했다. 기존 eventConflict 테스트에 해당 경우를 추가했으며 비교 정책·생산자 코드는 변경하지 않았다.

- 추가 검증 중 호스트 JVM/컨테이너 DB의 서로 다른 wall clock으로 검증 소비자가 유효 요청을 만료로 보는 실패를 확인했다. 검증 소비자의 수신 시각을 생성자와 동일한 실제 DB clock_timestamp에서 읽도록 정렬했다. 기한 연장이나 만료 비교 완화 없이 기한 후 신규 점유 거부도 그대로 검증한다.

구현 커밋: `1788c88`. 이후 자동 주기·producer 재처리 충돌 경계 테스트와 연결 상태 문서를 보완했다.
