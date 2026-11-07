package com.vlm.quality.dataset.domain;

import java.util.UUID;

/** media-service의 데이터셋 영상 등록(SPEC-030 R-4). 구현은 내부 API 호출이다. */
public interface DatasetVideoRegistry {

  /**
   * 영상 하나를 등록한다. 실패해도 예외를 던지지 않고 영상별 오류로 돌려준다(R-7).
   *
   * @return 등록됐으면 {@link Registered}, 아니면 {@link Rejected}
   */
  RegistrationResult register(VideoRegistration request);

  /** 등록 요청(계약 {@code RegisterDatasetVideoRequest}). */
  record VideoRegistration(
      UUID importId, String externalId, UUID ownerId, String importKey, String contentType) {}

  /** 등록 결과. */
  sealed interface RegistrationResult permits Registered, Rejected {}

  /** 등록됨. videoId가 항목의 sourceId다(R-4). */
  record Registered(UUID videoId) implements RegistrationResult {}

  /**
   * 등록 안 됨.
   *
   * @param code {@link ImportErrorCodes}의 값
   */
  record Rejected(String code, String message) implements RegistrationResult {}
}
