package com.vlm.quality.export.infra.storage;

import com.vlm.quality.export.domain.ExportFiles;
import com.vlm.quality.export.domain.ResultFiles;
import com.vlm.quality.shared.infra.storage.QualityStorageProperties;
import java.nio.file.Path;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

@Component
@RequiredArgsConstructor
class S3ExportStorage implements ResultFiles, ExportFiles {

  private final S3Client s3;
  private final QualityStorageProperties properties;

  @Override
  public Optional<byte[]> read(String resultKey) {
    try {
      return Optional.of(
          s3.getObjectAsBytes(g -> g.bucket(properties.sourceBucket()).key(resultKey))
              .asByteArray());
    } catch (NoSuchKeyException e) {
      return Optional.empty();
    }
  }

  @Override
  public void upload(String key, Path file) {
    String bucket =
        QualityStorageProperties.required(
            properties.exportBucket(), "vlm.quality.storage.export-bucket");
    s3.putObject(
        p -> p.bucket(bucket).key(key).contentType("application/x-ndjson"),
        RequestBody.fromFile(file));
  }
}
