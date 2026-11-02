package com.vlm.media.api;

import com.vlm.media.domain.UploadStorage.SingleUploadTarget;
import java.time.Instant;
import java.util.Map;

/** 계약 {@code UploadInstruction}. {@code method}로 업로드 방식을 구분한다. */
sealed interface UploadInstruction {

  /** 계약 {@code SingleUpload}. */
  record Single(String method, String url, Map<String, String> fields, Instant expiresAt)
      implements UploadInstruction {

    static Single from(SingleUploadTarget target) {
      return new Single("SINGLE", target.url(), target.fields(), target.expiresAt());
    }
  }
}
