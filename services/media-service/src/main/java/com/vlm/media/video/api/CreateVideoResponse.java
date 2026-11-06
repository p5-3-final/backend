package com.vlm.media.video.api;

import com.vlm.media.shared.api.VideoResponse;

/** 계약 {@code CreateVideoResponse}. */
record CreateVideoResponse(VideoResponse video, UploadInstruction upload) {}
