package com.vlm.media.api;

import com.vlm.media.application.CompleteUploadService;
import com.vlm.media.application.CreateVideoService;
import com.vlm.media.application.CreatedVideo;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class VideoController {

  private static final String VIDEOS = "/api/v1/media/videos";

  private final CreateVideoService createVideoService;
  private final CompleteUploadService completeUploadService;

  @PostMapping(VIDEOS)
  ResponseEntity<CreateVideoResponse> create(
      @RequestHeader(value = UserHeaders.USER_ID, required = false) @Nullable String userId,
      @Valid @RequestBody CreateVideoRequest request) {
    CreatedVideo created =
        createVideoService.create(
            UserHeaders.userId(userId),
            request.fileName(),
            request.sizeBytes(),
            request.contentType());
    VideoResponse video = VideoResponse.from(created.video());
    return ResponseEntity.created(URI.create(VIDEOS + "/" + video.id()))
        .body(new CreateVideoResponse(video, UploadInstruction.Single.from(created.upload())));
  }

  @PostMapping(VIDEOS + "/{videoId}/complete")
  VideoResponse complete(
      @RequestHeader(value = UserHeaders.USER_ID, required = false) @Nullable String userId,
      @PathVariable UUID videoId) {
    return VideoResponse.from(completeUploadService.complete(videoId, UserHeaders.userId(userId)));
  }
}
