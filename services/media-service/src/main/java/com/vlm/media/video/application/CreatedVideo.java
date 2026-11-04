package com.vlm.media.video.application;

import com.vlm.media.shared.domain.UploadStorage.UploadTarget;
import com.vlm.media.shared.domain.Video;

/** 만든 영상과 브라우저에 줄 업로드 방법. */
public record CreatedVideo(Video video, UploadTarget upload) {}
