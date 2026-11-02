package com.vlm.media.support;

import com.vlm.media.TestcontainersConfiguration;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;

/** 업로드 정책을 거치지 않고 저장소에 직접 넣는다. 크기가 다른 파일 같은 비정상 상태를 만들 때 쓴다. */
public final class StoragePuts {

  private StoragePuts() {}

  public static void put(S3Client s3, String key, byte[] content) {
    s3.putObject(
        r -> r.bucket(TestcontainersConfiguration.BUCKET).key(key), RequestBody.fromBytes(content));
  }
}
