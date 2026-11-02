package com.vlm.media.api;

import com.vlm.media.domain.UploadStorage.UploadedPart;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** 계약 {@code CompleteUploadRequest}. 단일 업로드면 본문이 없거나 {@code parts}를 생략한다. */
record CompleteUploadRequest(@Nullable List<@Valid Part> parts) {

  List<UploadedPart> uploadedParts() {
    return parts == null
        ? List.of()
        : parts.stream().map(p -> new UploadedPart(p.partNumber(), p.eTag())).toList();
  }

  record Part(@Min(1) int partNumber, @NotBlank String eTag) {}
}
