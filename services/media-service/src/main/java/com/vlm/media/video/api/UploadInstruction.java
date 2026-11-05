package com.vlm.media.video.api;

import com.vlm.media.shared.domain.UploadStorage.MultipartUploadTarget;
import com.vlm.media.shared.domain.UploadStorage.PartTarget;
import com.vlm.media.shared.domain.UploadStorage.SingleUploadTarget;
import com.vlm.media.shared.domain.UploadStorage.UploadTarget;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** 계약 {@code UploadInstruction}. {@code method}로 업로드 방식을 구분한다. */
sealed interface UploadInstruction {

  static UploadInstruction from(UploadTarget target) {
    return switch (target) {
      case SingleUploadTarget single ->
          new Single("SINGLE", single.url(), single.fields(), single.expiresAt());
      case MultipartUploadTarget multipart ->
          new Multipart(
              "MULTIPART",
              multipart.partSizeBytes(),
              multipart.parts().stream().map(Part::from).toList(),
              multipart.expiresAt());
    };
  }

  /** 계약 {@code SingleUpload}. */
  record Single(String method, String url, Map<String, String> fields, Instant expiresAt)
      implements UploadInstruction {}

  /** 계약 {@code MultipartUpload}. */
  record Multipart(String method, long partSizeBytes, List<Part> parts, Instant expiresAt)
      implements UploadInstruction {}

  /** 조각 하나의 업로드 주소. */
  record Part(int partNumber, String url) {

    static Part from(PartTarget target) {
      return new Part(target.partNumber(), target.url());
    }
  }
}
