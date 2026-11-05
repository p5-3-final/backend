---
spec: SPEC-014
repo: backend
updated: 2026-11-05
---

# Tasks: SPEC-014 — backend

## Phase 1: 준비

- [x] T001 확정 계약 2.5.1 고정·마이그레이션·설정

## Phase 2: 결과 수신과 반환

- [x] T002 [R-9] 정상 반환 Red→Green — ExecutionResultTest, ResultService/Repository
- [x] T003 [R-10 R-11 R-12 R-13] 중복·늦은 결과·충돌·동시/CLI 경쟁 — 동일 테스트 및 식별 검사
- [x] T004 [R-14] DB 실패 원자성과 수신 offset — 결과 DB/Kafka 테스트
- [x] T005 [R-15 NFR-1 NFR-2 NFR-3] 실제 Python 이미지·Kafka·다음 청크 배정 — ChunkLifecycleKafkaTest

## Phase 3: 마무리

- [x] T006 환경변수·README·문서 일치·필수 검사

## 완료 조건 (Definition of Done)

- [x] 모든 담당 요구사항·필수 검사 통과
- [x] 실제 worker 경로와 미연결 AI/운영 범위를 구분
- PR·CI·머지는 실제 GitHub 상태로 확인한다. 배포 전 spec Done 금지.

## 검증 기록

- Red: 결과 service 미구현 APPLIED 기대/IGNORED 실제를 확인한 뒤 구현했다.
- Green: 전체 backend 506개/Pipeline158개, 실패·skip0. 기존 IDE DEBUG만 제거하여 필수 spotlessCheck/build를 통과했다.
- 실제 normalize/worker runtime 이미지·Kafka·서로 다른 DB·인증 S3에서 v2 재전달 payload 동일성, 실영상 추출/검증 추론, 성공/실패 종료, 슬롯 반환 후 다음 배정, 결과 DB 실패 중 offset/슬롯 유지와 해소 뒤 재수신을 검증했다.
- 결과 중복/동시/이전 시도/CLI 경쟁/각 저장 경계 롤백/잘못된 식별·결과 경로·producedAt을 검증했다.
- 실제 연결에서 producedAt 덮어쓰기 실패를 발견해 docs #89를 선행 명확화했다. 같은 eventId 전체 비교는 유지하며 새 이벤트 정규화 메타데이터에서만 producedAt을 제외한다. 기존 저장 메타데이터의 producedAt도 비교 경계에서 제외하고 기록 자체를 덮어쓰지 않는다.
- backend/worker의 운영 활성화·기존 offset 변경·실제 AI 호출은 수행하지 않았다.

CI의 실제 worker source는 ai #34 머지 커밋 `5408a90b67f15d3af187d9083709c2c6820db141`로 고정한다. 애플리케이션 의존성은 아니며 테스트 runtime 이미지 빌드에만 사용한다. 복구 CLI 패키징 smoke에서 results enabled=true 환경도 강제로 비활성화함을 확인했다.
