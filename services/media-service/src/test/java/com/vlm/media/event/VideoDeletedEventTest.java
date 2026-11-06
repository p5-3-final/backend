package com.vlm.media.event;

import static com.vlm.media.support.MediaApi.asAdmin;
import static com.vlm.media.support.MediaApi.asUser;
import static com.vlm.media.support.MediaApi.createVideo;
import static com.vlm.media.support.MediaApi.createVideoRequest;
import static com.vlm.media.support.MediaApi.deleteRequest;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vlm.media.retention.application.RetentionKeys;
import com.vlm.media.support.ContractSchemas;
import com.vlm.media.support.IntegrationTest;
import com.vlm.media.support.MediaApi;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class VideoDeletedEventTest {

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired RetentionKeys retentionKeys;
  private final ObjectMapper json = new ObjectMapper();

  private final UUID ownerId = UUID.randomUUID();

  @Test
  @DisplayName("[SPEC-003 R-19] 삭제로 바뀌면 video.deleted를 한 번 기록한다")
  void recordsVideoDeletedOnce() throws Exception {
    String videoId = newVideo();

    mvc.perform(asUser(deleteRequest(videoId), ownerId));
    mvc.perform(asUser(deleteRequest(videoId), ownerId));

    List<Map<String, Object>> rows =
        jdbc.queryForList(
            "SELECT event_type, aggregate_id::text AS aggregate_id FROM outbox_events");
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0))
        .containsEntry("event_type", "video.deleted")
        .containsEntry("aggregate_id", videoId);
  }

  @Test
  @DisplayName("[SPEC-003 R-21] video.deleted payload는 계약 스키마와 맞고 ADMIN이 지워도 소유자 ID를 담는다")
  void payloadMatchesContract() throws Exception {
    String videoId = newVideo();

    mvc.perform(asAdmin(deleteRequest(videoId), UUID.randomUUID()));

    JsonNode payload =
        json.readTree(jdbc.queryForObject("SELECT payload::text FROM outbox_events", String.class));
    assertThat(ContractSchemas.validateEvent("video.deleted.schema.json", payload)).isEmpty();
    assertThat(payload.get("eventType").asText()).isEqualTo("video.deleted");
    assertThat(payload.get("sourceId").asText()).isEqualTo(videoId);
    assertThat(payload.get("ownerId").asText()).isEqualTo(ownerId.toString());
  }

  @Test
  @DisplayName(
      "[SPEC-018 R-8] [SPEC-018 R-9] 동의한 영상을 지우면 video.deleted에 보존 키와 1년 뒤 만료 시각을 담고 최소 기록을 남긴다")
  void consentedVideoIsRetained() throws Exception {
    String videoId = consentedVideo();

    mvc.perform(asUser(deleteRequest(videoId), ownerId));

    JsonNode payload =
        json.readTree(jdbc.queryForObject("SELECT payload::text FROM outbox_events", String.class));
    assertThat(ContractSchemas.validateEvent("video.deleted.schema.json", payload)).isEmpty();
    Instant deletedAt = Instant.parse(payload.get("occurredAt").asText());
    Instant retainUntil = deletedAt.atZone(ZoneOffset.UTC).plusYears(1).toInstant();
    assertThat(payload.at("/retention/retentionKey").asText())
        .isEqualTo(retentionKeys.keyOf(UUID.fromString(videoId)));
    assertThat(Instant.parse(payload.at("/retention/retainUntil").asText())).isEqualTo(retainUntil);
    Map<String, Object> record =
        jdbc.queryForMap(
            "SELECT source_id::text AS source_id, owner_id::text AS owner_id, deleted_at,"
                + " retain_until FROM retained_videos");
    assertThat(record)
        .containsEntry("source_id", videoId)
        .containsEntry("owner_id", ownerId.toString());
    assertThat(((Timestamp) record.get("deleted_at")).toInstant()).isEqualTo(deletedAt);
    assertThat(((Timestamp) record.get("retain_until")).toInstant()).isEqualTo(retainUntil);
  }

  private String consentedVideo() throws Exception {
    return MediaApi.json(
            mvc.perform(
                asUser(
                    createVideoRequest(
                        "a.mp4", 1000, "video/mp4", Map.of("retentionConsent", true)),
                    ownerId)))
        .at("/video/id")
        .asText();
  }

  private String newVideo() throws Exception {
    return createVideo(mvc, ownerId, "a.mp4", 1000, "video/mp4").at("/video/id").asText();
  }
}
