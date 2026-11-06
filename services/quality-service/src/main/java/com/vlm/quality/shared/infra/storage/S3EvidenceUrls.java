package com.vlm.quality.shared.infra.storage;

import com.vlm.quality.shared.domain.EvidenceUrls;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** 원본 버킷의 블러 사본을 GET 서명 주소로 바꾼다. 서명은 로컬 계산이라 저장소를 호출하지 않는다. */
@Component
@RequiredArgsConstructor
class S3EvidenceUrls implements EvidenceUrls {

  private final S3Presigner presigner;
  private final QualityStorageProperties properties;

  @Override
  public List<URI> sign(List<String> evidenceFrameKeys) {
    return evidenceFrameKeys.stream().map(this::sign).toList();
  }

  private URI sign(String key) {
    var request =
        presigner.presignGetObject(
            p ->
                p.signatureDuration(properties.signedUrlTtl())
                    .getObjectRequest(g -> g.bucket(properties.sourceBucket()).key(key)));
    try {
      return request.url().toURI();
    } catch (URISyntaxException e) {
      throw new IllegalStateException("presigned URL is not a URI", e);
    }
  }
}
