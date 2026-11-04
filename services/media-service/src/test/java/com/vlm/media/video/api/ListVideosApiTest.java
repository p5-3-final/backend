package com.vlm.media.video.api;

import static com.vlm.media.support.MediaApi.asUser;
import static com.vlm.media.support.MediaApi.createVideo;
import static com.vlm.media.support.MediaApi.json;
import static com.vlm.media.support.MediaApi.listRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.vlm.media.normalization.application.NormalizationEventService;
import com.vlm.media.shared.domain.RejectReason;
import com.vlm.media.support.IntegrationTest;
import com.vlm.media.support.Normalizations;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class ListVideosApiTest {

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired NormalizationEventService normalization;

  private final UUID ownerId = UUID.randomUUID();

  @Test
  @DisplayName("[SPEC-003 R-14] 내 영상만 만든 시각 최신순으로 반환한다")
  void listsOwnVideosNewestFirst() throws Exception {
    List<String> created = createVideos(ownerId, 3);
    createVideo(mvc, UUID.randomUUID(), "other.mp4", 1000, "video/mp4");

    JsonNode body = json(mvc.perform(asUser(listRequest(), ownerId)).andExpect(status().isOk()));

    assertThat(ids(body)).containsExactly(created.get(2), created.get(1), created.get(0));
    assertThat(body.at("/page/totalElements").asLong()).isEqualTo(3);
    assertThat(body.at("/page/totalPages").asInt()).isEqualTo(1);
  }

  @Test
  @DisplayName("[SPEC-007 R-21] 목록에도 준비됨 영상의 길이·예상 시간과 거절된 영상의 사유를 담는다")
  void listIncludesNormalizationFields() throws Exception {
    List<String> created = createVideos(ownerId, 2);
    Normalizations.ready(normalization, jdbc, created.get(0));
    Normalizations.rejected(normalization, jdbc, created.get(1), RejectReason.TOO_SHORT);

    JsonNode body = json(mvc.perform(asUser(listRequest(), ownerId)).andExpect(status().isOk()));

    JsonNode rejected = body.at("/items/0");
    JsonNode ready = body.at("/items/1");
    assertThat(rejected.get("rejectReason").asText()).isEqualTo("TOO_SHORT");
    assertThat(rejected.get("durationSec").isNull()).isTrue();
    assertThat(ready.get("durationSec").asDouble()).isEqualTo(612.48);
    assertThat(ready.get("estimatedProcessingSec").asInt()).isEqualTo(613);
    assertThat(ready.get("rejectReason").isNull()).isTrue();
  }

  @Test
  @DisplayName("[SPEC-003 R-14] 삭제된 영상은 목록에서 뺀다")
  void excludesDeletedVideos() throws Exception {
    List<String> created = createVideos(ownerId, 2);
    jdbc.update("UPDATE videos SET status = 'DELETED' WHERE public_id = ?::uuid", created.get(0));

    JsonNode body = json(mvc.perform(asUser(listRequest(), ownerId)).andExpect(status().isOk()));

    assertThat(ids(body)).containsExactly(created.get(1));
    assertThat(body.at("/page/totalElements").asLong()).isEqualTo(1);
  }

  @Test
  @DisplayName("[SPEC-003 R-14] 페이지 크기를 정하지 않으면 20개씩 반환한다")
  void defaultPageSizeIs20() throws Exception {
    createVideos(ownerId, 21);

    mvc.perform(asUser(listRequest(), ownerId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(20))
        .andExpect(jsonPath("$.page.page").value(0))
        .andExpect(jsonPath("$.page.size").value(20))
        .andExpect(jsonPath("$.page.totalElements").value(21))
        .andExpect(jsonPath("$.page.totalPages").value(2));
  }

  @Test
  @DisplayName("[SPEC-003 R-14] 요청한 페이지와 크기만큼 반환한다")
  void returnsRequestedPage() throws Exception {
    List<String> created = createVideos(ownerId, 3);

    JsonNode body =
        json(
            mvc.perform(asUser(listRequest().param("page", "1").param("size", "2"), ownerId))
                .andExpect(status().isOk()));

    assertThat(ids(body)).containsExactly(created.get(0));
    assertThat(body.at("/page/totalPages").asInt()).isEqualTo(2);
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "101", "abc"})
  @DisplayName("[SPEC-003 R-14] 페이지 크기가 1~100이 아니면 400 MEDIA_INVALID_REQUEST를 반환한다")
  void rejectsInvalidSize(String size) throws Exception {
    mvc.perform(asUser(listRequest().param("size", size), ownerId))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MEDIA_INVALID_REQUEST"));
  }

  @Test
  @DisplayName("[SPEC-003 R-14] 페이지 번호가 음수면 400 MEDIA_INVALID_REQUEST를 반환한다")
  void rejectsNegativePage() throws Exception {
    mvc.perform(asUser(listRequest().param("page", "-1"), ownerId))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MEDIA_INVALID_REQUEST"));
  }

  private List<String> createVideos(UUID owner, int count) throws Exception {
    List<String> ids = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      ids.add(
          createVideo(mvc, owner, "v" + i + ".mp4", 1000, "video/mp4").at("/video/id").asText());
    }
    return ids;
  }

  private static List<String> ids(JsonNode body) {
    List<String> ids = new ArrayList<>();
    body.get("items").forEach(item -> ids.add(item.get("id").asText()));
    return ids;
  }
}
