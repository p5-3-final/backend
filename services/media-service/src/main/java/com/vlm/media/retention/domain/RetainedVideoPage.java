package com.vlm.media.retention.domain;

import java.util.List;

/** 보존 중인 삭제 영상 한 페이지. {@code page}는 0부터 센다. */
public record RetainedVideoPage(
    List<RetainedVideo> items, int page, int size, long totalElements) {}
