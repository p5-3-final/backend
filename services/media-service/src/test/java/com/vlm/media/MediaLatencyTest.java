package com.vlm.media;

import static com.vlm.media.support.MediaApi.asUser;
import static com.vlm.media.support.MediaApi.completeRequest;
import static com.vlm.media.support.MediaApi.consentRequest;
import static com.vlm.media.support.MediaApi.createSession;
import static com.vlm.media.support.MediaApi.createSessionRequest;
import static com.vlm.media.support.MediaApi.createVideoRequest;
import static com.vlm.media.support.MediaApi.deleteRequest;
import static com.vlm.media.support.MediaApi.getRequest;
import static com.vlm.media.support.MediaApi.json;
import static com.vlm.media.support.MediaApi.listRequest;
import static com.vlm.media.support.MediaApi.playbackRequest;
import static com.vlm.media.support.MediaApi.retainedVideosRequest;
import static com.vlm.media.support.MediaApi.revokeRequest;
import static com.vlm.media.support.MediaApi.sessionRequest;
import static com.vlm.media.support.MediaApi.sessionsRequest;
import static com.vlm.media.support.MediaApi.updateRetentionSettingsRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.vlm.media.support.IntegrationTest;
import com.vlm.media.support.StorageUploads;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class MediaLatencyTest {

  // 요청 종류마다 재는 횟수. 처음 몇 번은 JIT·연결 준비로 느려서 따로 버린다.
  private static final int WARM_UP = 5;
  private static final int SAMPLES = 50;
  private static final Duration P95_LIMIT = Duration.ofMillis(300);

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;

  private final UUID ownerId = UUID.randomUUID();

  @Test
  @DisplayName("[SPEC-003 NFR-4] 업로드 자리·완료·목록·상세 요청의 p95 응답 시간이 300ms 이하다")
  void p95IsWithinLimit() throws Exception {
    List<Duration> create = new ArrayList<>();
    List<Duration> complete = new ArrayList<>();
    List<Duration> list = new ArrayList<>();
    List<Duration> get = new ArrayList<>();

    for (int i = 0; i < WARM_UP + SAMPLES; i++) {
      boolean measured = i >= WARM_UP;
      long start = System.nanoTime();
      JsonNode created =
          json(
              mvc.perform(asUser(createVideoRequest("a.mp4", 1000, "video/mp4"), ownerId))
                  .andExpect(status().isCreated()));
      record(create, start, measured);
      String videoId = created.at("/video/id").asText();
      StorageUploads.postForm(created.get("upload"), new byte[1000]);

      start = System.nanoTime();
      mvc.perform(asUser(completeRequest(videoId), ownerId)).andExpect(status().isOk());
      record(complete, start, measured);

      start = System.nanoTime();
      mvc.perform(asUser(listRequest(), ownerId)).andExpect(status().isOk());
      record(list, start, measured);

      start = System.nanoTime();
      mvc.perform(asUser(getRequest(videoId), ownerId)).andExpect(status().isOk());
      record(get, start, measured);
    }

    assertThat(p95(create)).as("create p95").isLessThanOrEqualTo(P95_LIMIT);
    assertThat(p95(complete)).as("complete p95").isLessThanOrEqualTo(P95_LIMIT);
    assertThat(p95(list)).as("list p95").isLessThanOrEqualTo(P95_LIMIT);
    assertThat(p95(get)).as("get p95").isLessThanOrEqualTo(P95_LIMIT);
  }

  @Test
  @DisplayName("[SPEC-006 NFR-2] 재생 주소 발급 요청의 p95 응답 시간이 300ms 이하다")
  void playbackP95IsWithinLimit() throws Exception {
    JsonNode created =
        json(
            mvc.perform(asUser(createVideoRequest("a.mp4", 1000, "video/mp4"), ownerId))
                .andExpect(status().isCreated()));
    String videoId = created.at("/video/id").asText();
    StorageUploads.postForm(created.get("upload"), new byte[1000]);
    mvc.perform(asUser(completeRequest(videoId), ownerId)).andExpect(status().isOk());
    List<Duration> playback = new ArrayList<>();

    for (int i = 0; i < WARM_UP + SAMPLES; i++) {
      long start = System.nanoTime();
      mvc.perform(asUser(playbackRequest(videoId), ownerId)).andExpect(status().isOk());
      record(playback, start, i >= WARM_UP);
    }

    assertThat(p95(playback)).as("playback p95").isLessThanOrEqualTo(P95_LIMIT);
  }

  @Test
  @DisplayName("[SPEC-017 NFR-1] 세션 만들기·목록·상세(영상 100개) 요청의 p95 응답 시간이 300ms 이하다")
  void sessionP95IsWithinLimit() throws Exception {
    String sessionId = createSession(mvc, ownerId, "세션");
    jdbc.update(
        """
        INSERT INTO videos (public_id, owner_id, file_name, size_bytes, content_type, status,
            storage_key, expires_at, uploaded_at, session_id, session_role)
        SELECT gen_random_uuid(), ?, 'v.mp4', 1, 'video/mp4', 'UPLOADED', 'k', now(), now(), ?,
            'DURING'
        FROM generate_series(1, 100)
        """,
        ownerId,
        UUID.fromString(sessionId));
    List<Duration> create = new ArrayList<>();
    List<Duration> list = new ArrayList<>();
    List<Duration> detail = new ArrayList<>();

    for (int i = 0; i < WARM_UP + SAMPLES; i++) {
      boolean measured = i >= WARM_UP;
      long start = System.nanoTime();
      mvc.perform(asUser(createSessionRequest(Map.of("name", "세션 " + i)), ownerId))
          .andExpect(status().isCreated());
      record(create, start, measured);

      start = System.nanoTime();
      mvc.perform(asUser(sessionsRequest(), ownerId)).andExpect(status().isOk());
      record(list, start, measured);

      start = System.nanoTime();
      mvc.perform(asUser(sessionRequest(sessionId), ownerId)).andExpect(status().isOk());
      record(detail, start, measured);
    }

    assertThat(p95(create)).as("session create p95").isLessThanOrEqualTo(P95_LIMIT);
    assertThat(p95(list)).as("session list p95").isLessThanOrEqualTo(P95_LIMIT);
    assertThat(p95(detail)).as("session detail p95").isLessThanOrEqualTo(P95_LIMIT);
  }

  @Test
  @DisplayName("[SPEC-018 NFR-2] 보존 설정·동의 변경·보존 목록·영상별 철회 요청의 p95 응답 시간이 300ms 이하다")
  void retentionP95IsWithinLimit() throws Exception {
    String videoId =
        json(mvc.perform(asUser(createVideoRequest("a.mp4", 1000, "video/mp4"), ownerId)))
            .at("/video/id")
            .asText();
    List<Duration> settings = new ArrayList<>();
    List<Duration> consent = new ArrayList<>();
    List<Duration> retained = new ArrayList<>();
    List<Duration> revoke = new ArrayList<>();

    for (int i = 0; i < WARM_UP + SAMPLES; i++) {
      boolean measured = i >= WARM_UP;
      long start = System.nanoTime();
      mvc.perform(asUser(updateRetentionSettingsRequest(i % 2 == 0), ownerId))
          .andExpect(status().isOk());
      record(settings, start, measured);

      start = System.nanoTime();
      mvc.perform(asUser(consentRequest(videoId, i % 2 == 0), ownerId)).andExpect(status().isOk());
      record(consent, start, measured);

      String retainedId =
          json(mvc.perform(
                  asUser(
                      createVideoRequest(
                          "r.mp4", 1000, "video/mp4", Map.of("retentionConsent", true)),
                      ownerId)))
              .at("/video/id")
              .asText();
      mvc.perform(asUser(deleteRequest(retainedId), ownerId)).andExpect(status().isNoContent());

      start = System.nanoTime();
      mvc.perform(asUser(retainedVideosRequest(), ownerId)).andExpect(status().isOk());
      record(retained, start, measured);

      start = System.nanoTime();
      mvc.perform(asUser(revokeRequest(retainedId), ownerId)).andExpect(status().isNoContent());
      record(revoke, start, measured);
    }

    assertThat(p95(settings)).as("settings p95").isLessThanOrEqualTo(P95_LIMIT);
    assertThat(p95(consent)).as("consent p95").isLessThanOrEqualTo(P95_LIMIT);
    assertThat(p95(retained)).as("retained list p95").isLessThanOrEqualTo(P95_LIMIT);
    assertThat(p95(revoke)).as("revoke p95").isLessThanOrEqualTo(P95_LIMIT);
  }

  private static void record(List<Duration> samples, long startNanos, boolean measured) {
    if (measured) {
      samples.add(Duration.ofNanos(System.nanoTime() - startNanos));
    }
  }

  private static Duration p95(List<Duration> samples) {
    List<Duration> sorted = samples.stream().sorted(Comparator.naturalOrder()).toList();
    return sorted.get((int) Math.ceil(sorted.size() * 0.95) - 1);
  }
}
