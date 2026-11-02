-- PostgreSQL에서 CHAR(n)은 VARCHAR보다 이점이 없고 공백 채움·비교 동작이 달라 VARCHAR로 맞춘다.
ALTER TABLE refresh_tokens ALTER COLUMN token_hash TYPE VARCHAR(64);
