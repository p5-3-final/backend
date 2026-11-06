package com.vlm.quality.support;

import com.vlm.quality.TestcontainersConfiguration;
import java.util.List;
import java.util.UUID;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Object;

/** 테스트에서 SeaweedFS 버킷을 직접 쓰고 읽는다. */
public final class StorageFixtures {

  private StorageFixtures() {}

  /** 원본 버킷에 블러 사본을 올리고 경로를 돌려준다. 내용은 경로 바이트다. */
  public static String putSourceFrame(UUID sourceId, int index) {
    String key = ReviewFixtures.frameKey(sourceId, index);
    try (S3Client s3 = TestcontainersConfiguration.s3()) {
      s3.putObject(
          p -> p.bucket(TestcontainersConfiguration.SOURCE_BUCKET).key(key),
          RequestBody.fromString(key));
    }
    return key;
  }

  public static byte[] goldenObject(String key) {
    try (S3Client s3 = TestcontainersConfiguration.s3()) {
      return s3.getObjectAsBytes(g -> g.bucket(TestcontainersConfiguration.GOLDEN_BUCKET).key(key))
          .asByteArray();
    }
  }

  public static List<String> goldenKeys(String prefix) {
    try (S3Client s3 = TestcontainersConfiguration.s3()) {
      return s3
          .listObjectsV2(l -> l.bucket(TestcontainersConfiguration.GOLDEN_BUCKET).prefix(prefix))
          .contents()
          .stream()
          .map(S3Object::key)
          .toList();
    }
  }
}
