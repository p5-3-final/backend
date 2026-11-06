package com.vlm.media.video.api;

import com.vlm.common.web.UserHeaders;
import com.vlm.media.shared.api.VideoResponse;
import com.vlm.media.shared.domain.UploadStorage.UploadedPart;
import com.vlm.media.video.application.CompleteUploadService;
import com.vlm.media.video.application.CreateVideoService;
import com.vlm.media.video.application.CreatedVideo;
import com.vlm.media.video.application.DeleteVideoService;
import com.vlm.media.video.application.VideoQueryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class VideoController {

  private static final String VIDEOS = "/api/v1/media/videos";

  private final CreateVideoService createVideoService;
  private final CompleteUploadService completeUploadService;
  private final VideoQueryService videoQueryService;
  private final DeleteVideoService deleteVideoService;

  @PostMapping(VIDEOS)
  ResponseEntity<CreateVideoResponse> create(
      @RequestHeader(value = UserHeaders.USER_ID, required = false) @Nullable String userId,
      @Valid @RequestBody CreateVideoRequest request) {
    CreatedVideo created =
        createVideoService.create(
            UserHeaders.userId(userId),
            request.fileName(),
            request.sizeBytes(),
            request.contentType(),
            request.sessionId(),
            request.role(),
            null);
    VideoResponse video = VideoResponse.from(created.video());
    return ResponseEntity.created(URI.create(VIDEOS + "/" + video.id()))
        .body(new CreateVideoResponse(video, UploadInstruction.from(created.upload())));
  }

  @GetMapping(VIDEOS)
  VideoPageResponse list(
      @RequestHeader(value = UserHeaders.USER_ID, required = false) @Nullable String userId,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return VideoPageResponse.from(videoQueryService.list(UserHeaders.userId(userId), page, size));
  }

  @GetMapping(VIDEOS + "/{videoId}")
  VideoResponse get(
      @RequestHeader(value = UserHeaders.USER_ID, required = false) @Nullable String userId,
      @RequestHeader(value = UserHeaders.USER_ROLES, required = false) @Nullable String roles,
      @PathVariable UUID videoId) {
    return VideoResponse.from(
        videoQueryService.get(videoId, UserHeaders.userId(userId), UserHeaders.isAdmin(roles)));
  }

  @DeleteMapping(VIDEOS + "/{videoId}")
  ResponseEntity<Void> delete(
      @RequestHeader(value = UserHeaders.USER_ID, required = false) @Nullable String userId,
      @RequestHeader(value = UserHeaders.USER_ROLES, required = false) @Nullable String roles,
      @PathVariable UUID videoId) {
    deleteVideoService.delete(videoId, UserHeaders.userId(userId), UserHeaders.isAdmin(roles));
    return ResponseEntity.noContent().build();
  }

  @PostMapping(VIDEOS + "/{videoId}/complete")
  VideoResponse complete(
      @RequestHeader(value = UserHeaders.USER_ID, required = false) @Nullable String userId,
      @PathVariable UUID videoId,
      @Valid @RequestBody(required = false) @Nullable CompleteUploadRequest request) {
    List<UploadedPart> parts = request == null ? List.of() : request.uploadedParts();
    return VideoResponse.from(
        completeUploadService.complete(videoId, UserHeaders.userId(userId), parts));
  }
}
