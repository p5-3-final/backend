package com.vlm.media;

import static com.vlm.media.support.MediaApi.asUser;
import static com.vlm.media.support.MediaApi.completeRequest;
import static com.vlm.media.support.MediaApi.createVideoRequest;
import static com.vlm.media.support.MediaApi.getRequest;
import static com.vlm.media.support.MediaApi.json;
import static com.vlm.media.support.MediaApi.listRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.vlm.media.support.IntegrationTest;
import com.vlm.media.support.StorageUploads;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class MediaLatencyTest {

  // 요청 종류마다 재는 횟수. 처음 몇 번은 JIT·연결 준비로 느려서 따로 버린다.
  private static final int WARM_UP = 5;
  private static final int SAMPLES = 50;
  private static final Duration P95_LIMIT = Duration.ofMillis(300);

  @Autowired MockMvc mvc;

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
