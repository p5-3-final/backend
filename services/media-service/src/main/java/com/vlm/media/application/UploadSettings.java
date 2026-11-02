package com.vlm.media.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 업로드 규칙 값. 기본값은 SPEC-003과 계약 값이고, 테스트만 작은 파일로 확인하려고 낮춘다.
 *
 * @param singleMaxBytes 이 크기 이하는 단일 업로드(SPEC-003 R-2, R-3: 100MB)
 * @param partSizeBytes 멀티파트 조각 크기(64MiB: S3 최소 조각 5MiB 이상, 5GB를 80조각 안에)
 * @param urlTtl 업로드 주소 유효 시간(SPEC-003 NFR-6)
 * @param uploadExpiry 이 시간 안에 완료하지 않으면 만료(SPEC-003 R-13)
 */
@ConfigurationProperties("vlm.media.upload")
public record UploadSettings(
    @DefaultValue("104857600") long singleMaxBytes,
    @DefaultValue("67108864") long partSizeBytes,
    @DefaultValue("1h") Duration urlTtl,
    @DefaultValue("24h") Duration uploadExpiry) {}
