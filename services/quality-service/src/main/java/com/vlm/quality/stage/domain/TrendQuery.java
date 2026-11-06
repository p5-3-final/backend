package com.vlm.quality.stage.domain;

import com.vlm.common.error.BusinessException;
import com.vlm.quality.shared.domain.QualityErrorCode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * 추이 조회 조건(SPEC-029 R-24).
 *
 * <p>버전 키와 값은 둘 다 있거나 둘 다 없어야 한다. 키만 있을 때 돌려줄 집계가 spec·계약에 없어 400으로 정했다(plan D-28).
 */
public record TrendQuery(
    String stageId,
    LocalDate from,
    LocalDate to,
    @Nullable String versionKey,
    @Nullable String versionValue) {

  /** from부터 포함해 최대 일수(계약 To "from부터 최대 90일"). */
  public static final int MAX_DAYS = 90;

  // 계약 VersionKey 목록(StageTiming.versions의 키)
  private static final Set<String> VERSION_KEYS =
      Set.of(
          "taxonomy",
          "detectorProfile",
          "embeddingModel",
          "modelId",
          "promptVersion",
          "analysisVersion");

  /**
   * 조건을 검사해 만든다.
   *
   * @throws BusinessException 규칙을 어기면 {@link QualityErrorCode#INVALID_REQUEST}
   */
  public static TrendQuery of(
      String stageId,
      LocalDate from,
      LocalDate to,
      @Nullable String versionKey,
      @Nullable String versionValue) {
    if (from.isAfter(to)) {
      throw invalid("from은 to보다 늦을 수 없습니다.");
    }
    if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
      throw invalid("기간은 90일을 넘을 수 없습니다.");
    }
    if ((versionKey == null) != (versionValue == null)) {
      throw invalid("versionKey와 versionValue는 함께 줘야 합니다.");
    }
    if (versionKey != null && !VERSION_KEYS.contains(versionKey)) {
      throw invalid("모르는 versionKey입니다.");
    }
    return new TrendQuery(stageId, from, to, versionKey, versionValue);
  }

  private static BusinessException invalid(String detail) {
    return new BusinessException(QualityErrorCode.INVALID_REQUEST, detail);
  }
}
