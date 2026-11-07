package com.vlm.quality.shared.infra.storage;

import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 저장소 설정. 운영 S3와 로컬 SeaweedFS를 값만 바꿔 쓴다(ADR-0006).
 *
 * @param region 리전
 * @param endpoint 서버가 호출할 주소. 비우면 리전 기본 주소(운영 S3)
 * @param publicEndpoint 근거 프레임 주소를 서명할 때 쓸, 브라우저가 닿는 주소. 비우면 {@code endpoint}와 같다
 * @param pathStyle path-style 주소를 쓸지(SeaweedFS는 true)
 * @param sourceBucket 블러 사본이 있는 media 버킷
 * @param goldenBucket Golden Set 확정 버전 버킷
 * @param signedUrlTtl 근거 프레임 서명 주소 유효 시간
 * @param importBucket 외부 데이터셋 가져오기 버킷(SPEC-030). 매니페스트를 읽기만 한다
 * @param exportBucket 특징 내보내기 버킷(SPEC-030 R-14). Golden Set 버킷은 Object Lock이라 따로 둔다
 */
@ConfigurationProperties("vlm.quality.storage")
public record QualityStorageProperties(
    String region,
    @Nullable String endpoint,
    @Nullable String publicEndpoint,
    boolean pathStyle,
    String sourceBucket,
    String goldenBucket,
    Duration signedUrlTtl,
    @Nullable String importBucket,
    @Nullable String exportBucket) {

  /** 설정하지 않은 버킷을 쓰려 하면 기동 설정 오류다. */
  public static String required(@Nullable String bucket, String key) {
    if (bucket == null || bucket.isBlank()) {
      throw new IllegalStateException(key + " is not configured");
    }
    return bucket;
  }
}
