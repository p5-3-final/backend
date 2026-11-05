package com.vlm.media.support;

import com.vlm.media.normalization.application.NormalizationEventService;
import com.vlm.media.normalization.application.NormalizationOutcome;
import com.vlm.media.shared.domain.NormalizationResult;
import com.vlm.media.shared.domain.RejectReason;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** API로 만든 영상을 정규화 결과 상태로 옮긴다. 업로드 완료는 저장소 확인이 필요해 DB로 바로 바꾼다. */
public final class Normalizations {

  /** 길이 612.48초. 기본 처리 계수 1.0이면 예상 처리 시간은 613초다. */
  public static final long DURATION_MS = 612_480;

  private Normalizations() {}

  public static void ready(NormalizationEventService service, JdbcTemplate jdbc, String id) {
    UUID sourceId = uploaded(jdbc, id);
    UUID normalization = UUID.randomUUID();
    String base = "sources/" + sourceId + "/normalizations/" + normalization + "/";
    service.onNormalized(
        new NormalizationOutcome.Ready(
            sourceId,
            new NormalizationResult(
                normalization,
                "normalize-v1",
                base + "normalized.mp4",
                base + "hls/index.m3u8",
                DURATION_MS)));
  }

  public static void rejected(
      NormalizationEventService service, JdbcTemplate jdbc, String id, RejectReason reason) {
    service.onNormalized(
        new NormalizationOutcome.Rejected(
            uploaded(jdbc, id), UUID.randomUUID(), "normalize-v1", reason));
  }

  private static UUID uploaded(JdbcTemplate jdbc, String id) {
    jdbc.update(
        "UPDATE videos SET status = 'UPLOADED', uploaded_at = now() WHERE public_id = ?::uuid", id);
    return UUID.fromString(id);
  }
}
