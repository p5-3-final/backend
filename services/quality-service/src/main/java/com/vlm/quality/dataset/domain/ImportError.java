package com.vlm.quality.dataset.domain;

import org.jspecify.annotations.Nullable;

/**
 * 가져오기 오류 한 건(계약 {@code DatasetImport.errors[]}).
 *
 * @param externalId 영상별 오류면 그 영상, 매니페스트 오류면 null
 * @param message 500자 이하로 자른다
 */
public record ImportError(@Nullable String externalId, String code, String message) {

  /** 계약 {@code errors[].message.maxLength}. */
  public static final int MAX_MESSAGE = 500;

  public ImportError {
    message = message.length() <= MAX_MESSAGE ? message : message.substring(0, MAX_MESSAGE);
  }
}
