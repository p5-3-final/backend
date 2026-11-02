package com.vlm.media.application;

import com.vlm.media.domain.UploadStorage.UploadTarget;
import com.vlm.media.domain.Video;

/** 만든 영상과 브라우저에 줄 업로드 방법. */
public record CreatedVideo(Video video, UploadTarget upload) {}
