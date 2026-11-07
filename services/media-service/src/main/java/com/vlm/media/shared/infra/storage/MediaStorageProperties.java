package com.vlm.media.shared.infra.storage;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 영상 저장소 설정. 운영 S3와 로컬 SeaweedFS를 값만 바꿔 쓴다(plan "저장소 교체 방식", ADR-0006).
 *
 * @param bucket 버킷 이름
 * @param region 리전
 * @param endpoint 서버가 호출할 주소. 비우면 리전 기본 주소(운영 S3)
 * @param publicEndpoint 업로드 주소를 서명할 때 쓸, 브라우저가 닿는 주소. 비우면 {@code endpoint}와 같다
 * @param pathStyle path-style 주소를 쓸지(SeaweedFS는 true)
 * @param importBucket 외부 데이터셋 가져오기 버킷(SPEC-030). 읽기·서버 쪽 복사만 한다. 비우면 데이터셋 등록을 쓸 수 없다
 */
@ConfigurationProperties("vlm.media.storage")
public record MediaStorageProperties(
    String bucket,
    String region,
    @Nullable String endpoint,
    @Nullable String publicEndpoint,
    boolean pathStyle,
    @Nullable String importBucket) {}
