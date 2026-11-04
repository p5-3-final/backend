package com.vlm.media.video.api;

/** 계약 {@code CreateVideoResponse}. */
record CreateVideoResponse(VideoResponse video, UploadInstruction upload) {}
