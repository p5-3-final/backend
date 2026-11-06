package com.vlm.media.retention.api;

import com.vlm.common.web.UserHeaders;
import com.vlm.media.retention.application.RetentionService;
import com.vlm.media.shared.api.VideoResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class RetentionController {

  private static final String SETTINGS = "/api/v1/media/retention-settings";
  private static final String RETAINED = "/api/v1/media/retained-videos";

  private final RetentionService retentionService;

  @GetMapping(SETTINGS)
  RetentionSettingsResponse settings(
      @RequestHeader(value = UserHeaders.USER_ID, required = false) @Nullable String userId) {
    return new RetentionSettingsResponse(
        retentionService.defaultConsent(UserHeaders.userId(userId)));
  }

  @PutMapping("/api/v1/media/videos/{videoId}/retention-consent")
  VideoResponse changeConsent(
      @RequestHeader(value = UserHeaders.USER_ID, required = false) @Nullable String userId,
      @PathVariable UUID videoId,
      @Valid @RequestBody RetentionConsentRequest request) {
    return VideoResponse.from(
        retentionService.changeConsent(videoId, UserHeaders.userId(userId), request.consent()));
  }

  @GetMapping(RETAINED)
  RetainedVideoPageResponse listRetained(
      @RequestHeader(value = UserHeaders.USER_ID, required = false) @Nullable String userId,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return RetainedVideoPageResponse.from(
        retentionService.listRetained(UserHeaders.userId(userId), page, size));
  }

  @DeleteMapping(RETAINED + "/{videoId}")
  ResponseEntity<Void> revoke(
      @RequestHeader(value = UserHeaders.USER_ID, required = false) @Nullable String userId,
      @PathVariable UUID videoId) {
    retentionService.revoke(videoId, UserHeaders.userId(userId));
    return ResponseEntity.noContent().build();
  }

  @PutMapping(SETTINGS)
  RetentionSettingsResponse updateSettings(
      @RequestHeader(value = UserHeaders.USER_ID, required = false) @Nullable String userId,
      @Valid @RequestBody RetentionSettingsRequest request) {
    return new RetentionSettingsResponse(
        retentionService.updateDefaultConsent(
            UserHeaders.userId(userId), request.defaultConsent()));
  }
}
