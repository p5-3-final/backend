-- SPEC-001: 계정, 재발급 토큰, 로그인 기록

CREATE TABLE users (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- 외부(토큰 sub, X-User-Id, API id)에는 이 값만 노출해 내부 순번이 드러나지 않게 한다.
    public_id          UUID         NOT NULL UNIQUE,
    email              VARCHAR(254) NOT NULL UNIQUE,
    password_hash      VARCHAR(100) NOT NULL,
    nickname           VARCHAR(20)  NOT NULL,
    role               VARCHAR(20)  NOT NULL,
    failed_login_count INT          NOT NULL DEFAULT 0,
    locked_until       TIMESTAMPTZ,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE refresh_tokens (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id),
    -- 같은 로그인에서 회전된 토큰 묶음. 재사용이 감지되면 묶음 전체를 폐기한다(SPEC-001 R-23).
    family_id  UUID        NOT NULL,
    -- 원문은 저장하지 않고 SHA-256 hex만 저장한다(SPEC-001 NFR-2).
    token_hash CHAR(64)    NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_refresh_tokens_family_id ON refresh_tokens (family_id);

CREATE TABLE login_audits (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- 없는 이메일로 시도하면 NULL. 시도한 이메일 원문은 개인정보라 저장하지 않는다.
    user_id     BIGINT,
    success     BOOLEAN     NOT NULL,
    client_ip   VARCHAR(45) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
