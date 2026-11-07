package com.vlm.quality.dataset.infra.storage;

import com.vlm.quality.dataset.domain.ImportFiles;
import com.vlm.quality.shared.infra.storage.QualityStorageProperties;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

@Component
@RequiredArgsConstructor
class S3ImportFiles implements ImportFiles {

  private final S3Client s3;
  private final QualityStorageProperties properties;

  @Override
  public Optional<byte[]> read(String key) {
    String bucket =
        QualityStorageProperties.required(
            properties.importBucket(), "vlm.quality.storage.import-bucket");
    try {
      return Optional.of(s3.getObjectAsBytes(g -> g.bucket(bucket).key(key)).asByteArray());
    } catch (NoSuchKeyException e) {
      return Optional.empty();
    }
  }
}
