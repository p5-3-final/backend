package com.vlm.quality.shared.infra.storage;

import com.vlm.quality.shared.domain.GoldenStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;

@Component
@RequiredArgsConstructor
class S3GoldenStorage implements GoldenStorage {

  private final S3Client s3;
  private final QualityStorageProperties properties;

  @Override
  public void copyFromSource(String sourceKey, String goldenKey) {
    s3.copyObject(
        c ->
            c.sourceBucket(properties.sourceBucket())
                .sourceKey(sourceKey)
                .destinationBucket(properties.goldenBucket())
                .destinationKey(goldenKey));
  }

  @Override
  public void putJson(String goldenKey, byte[] json) {
    s3.putObject(
        p -> p.bucket(properties.goldenBucket()).key(goldenKey).contentType("application/json"),
        RequestBody.fromBytes(json));
  }
}
