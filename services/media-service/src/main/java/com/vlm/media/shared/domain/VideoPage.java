package com.vlm.media.shared.domain;

import java.util.List;

/** 영상 목록 한 페이지. {@code page}는 0부터 센다. */
public record VideoPage(List<Video> items, int page, int size, long totalElements) {}
