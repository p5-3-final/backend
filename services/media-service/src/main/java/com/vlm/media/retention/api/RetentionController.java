package com.vlm.media.retention.api;

import com.vlm.common.web.UserHeaders;
import com.vlm.media.retention.application.RetentionService;
import com.vlm.media.shared.api.VideoResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class RetentionController {

  private static final String SETTINGS = "/api/v1/media/retention-settings";

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

  @PutMapping(SETTINGS)
  RetentionSettingsResponse updateSettings(
      @RequestHeader(value = UserHeaders.USER_ID, required = false) @Nullable String userId,
      @Valid @RequestBody RetentionSettingsRequest request) {
    return new RetentionSettingsResponse(
        retentionService.updateDefaultConsent(
            UserHeaders.userId(userId), request.defaultConsent()));
  }
}
