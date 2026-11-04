package com.vlm.media.playback.api;

import com.vlm.media.playback.application.PlaybackService.Playback;
import java.time.Instant;

/** 계약 {@code VideoPlayback}. */
record VideoPlaybackResponse(String url, String downloadUrl, Instant expiresAt) {

  static VideoPlaybackResponse from(Playback playback) {
    return new VideoPlaybackResponse(playback.url(), playback.downloadUrl(), playback.expiresAt());
  }
}
