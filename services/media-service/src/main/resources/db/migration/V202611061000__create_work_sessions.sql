-- SPEC-017: 작업 세션. 영상 여러 개를 작업 하나(작업 전·중·후)로 묶는다.

CREATE TABLE work_sessions (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- 외부(API id, 이벤트 sessionId)에는 이 값만 노출한다.
    public_id  UUID         NOT NULL UNIQUE,
    -- auth-service 사용자 UUID. 다른 서비스 테이블이라 FK를 두지 않는다.
    owner_id   UUID         NOT NULL,
    name       VARCHAR(100) NOT NULL,
    -- 표시용 문자열. 분석의 구역 정의와 잇지 않는다(SPEC-017 범위 제외).
    site       VARCHAR(100),
    area       VARCHAR(100),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_work_sessions_owner_created ON work_sessions (owner_id, created_at DESC, id DESC);

-- 영상 응답과 video.uploaded가 세션의 공개 ID를 쓰므로 내부 PK 대신 public_id를 가리킨다(조인 없이 응답).
-- 기존 영상은 둘 다 NULL(세션 없음)이다.
ALTER TABLE videos ADD COLUMN session_id UUID REFERENCES work_sessions (public_id);
ALTER TABLE videos ADD COLUMN session_role VARCHAR(10);
ALTER TABLE videos ADD CONSTRAINT ck_videos_session_role
    CHECK ((session_id IS NULL) = (session_role IS NULL));

CREATE INDEX idx_videos_session ON videos (session_id) WHERE session_id IS NOT NULL;
