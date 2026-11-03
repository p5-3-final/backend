package com.vlm.media.api;

import com.vlm.media.application.PlaybackService.Playback;
import java.time.Instant;

/** 계약 {@code VideoPlayback}. */
record VideoPlaybackResponse(String url, String downloadUrl, Instant expiresAt) {

  static VideoPlaybackResponse from(Playback playback) {
    return new VideoPlaybackResponse(playback.url(), playback.downloadUrl(), playback.expiresAt());
  }
}
