package com.vlm.media.dataset.api;

import com.vlm.media.dataset.application.DatasetVideoService;
import com.vlm.media.dataset.application.RegisterDatasetVideoCommand;
import com.vlm.media.dataset.application.RegisteredDatasetVideo;
import jakarta.validation.Valid;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내부 데이터셋 영상 등록(계약 {@code registerDatasetVideo}). 호출자 검사는 {@code InternalCallerInterceptor}가 먼저 한다.
 */
@RestController
@RequiredArgsConstructor
class DatasetVideoController {

  private final DatasetVideoService datasetVideos;

  @PostMapping("/internal/v1/media/dataset-videos")
  ResponseEntity<DatasetVideoResponse> register(
      @RequestHeader(value = "X-Request-Deadline", required = false) @Nullable Long deadlineEpochMs,
      @Valid @RequestBody RegisterDatasetVideoRequest request) {
    RegisteredDatasetVideo registered =
        datasetVideos.register(
            new RegisterDatasetVideoCommand(
                request.importId(),
                request.externalId(),
                request.ownerId(),
                request.importKey(),
                request.contentType(),
                deadlineEpochMs == null ? null : Instant.ofEpochMilli(deadlineEpochMs)));
    return ResponseEntity.status(registered.created() ? HttpStatus.CREATED : HttpStatus.OK)
        .body(DatasetVideoResponse.from(registered.video()));
  }
}
