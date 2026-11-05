package com.vlm.media.playback.api;

import com.vlm.common.web.UserHeaders;
import com.vlm.media.playback.application.PlaybackService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class PlaybackController {

  private final PlaybackService playbackService;

  @GetMapping("/api/v1/media/videos/{videoId}/playback")
  VideoPlaybackResponse playback(
      @RequestHeader(value = UserHeaders.USER_ID, required = false) @Nullable String userId,
      @RequestHeader(value = UserHeaders.USER_ROLES, required = false) @Nullable String roles,
      @PathVariable UUID videoId) {
    return VideoPlaybackResponse.from(
        playbackService.issue(videoId, UserHeaders.userId(userId), UserHeaders.isAdmin(roles)));
  }
}
