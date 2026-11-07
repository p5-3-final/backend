package com.vlm.quality.dataset.domain;

/** 검증한 매니페스트 영상과 분할(R-3). 등록 전이다. */
public record NewImportVideo(
    String externalId,
    String path,
    String contentType,
    Split split,
    String labelsJson,
    int labelCount) {}
