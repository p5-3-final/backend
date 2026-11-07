package com.vlm.media.dataset.application;

import com.vlm.media.shared.domain.Video;

/**
 * 등록 결과.
 *
 * @param created 이번 요청이 영상을 만들었으면 true, 이미 있던 영상이면 false(SPEC-030 R-6)
 */
public record RegisteredDatasetVideo(Video video, boolean created) {}
