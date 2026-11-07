package com.vlm.quality.dataset.domain;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 검증을 통과한 매니페스트 영상 하나.
 *
 * @param id 내부 행 ID
 * @param path 매니페스트 폴더 기준 상대 경로
 * @param labelsJson 매니페스트 라벨 배열 그대로(JSON)
 * @param status PENDING·REGISTERED·FAILED
 * @param sourceId 등록됐으면 media videoId
 */
public record ImportVideo(
    long id,
    String externalId,
    String path,
    String contentType,
    Split split,
    String labelsJson,
    int labelCount,
    String status,
    @Nullable UUID sourceId) {

  public static final String PENDING = "PENDING";
  public static final String REGISTERED = "REGISTERED";
  public static final String FAILED = "FAILED";
}
