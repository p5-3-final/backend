-- SPEC-018: 학습용 보존 동의와 삭제 영상 최소 기록(ADR-0011)

-- 기본은 "보존하지 않음"이다. 기존 영상은 동의 수단이 없던 때 올렸으므로 모두 미동의다.
ALTER TABLE videos ADD COLUMN retention_consent BOOLEAN NOT NULL DEFAULT false;

-- 계정 기본 동의 값. 행이 없으면 false로 본다(SPEC-018 R-1).
CREATE TABLE retention_settings (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- auth-service 사용자 UUID. 다른 서비스 테이블이라 FK를 두지 않는다.
    owner_id        UUID        NOT NULL UNIQUE,
    default_consent BOOLEAN     NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 동의한 채 지운 영상의 최소 기록. 철회 때 보존 키를 다시 계산하는 데만 쓴다(ADR-0011).
-- 보존 키는 sourceId와 함께 남기지 않으려고 저장하지 않는다. 파일 이름도 남기지 않는다.
-- 행이 있다는 것이 "동의했고 아직 철회·만료되지 않았다"는 뜻이다. 철회·만료 때 지운다.
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
