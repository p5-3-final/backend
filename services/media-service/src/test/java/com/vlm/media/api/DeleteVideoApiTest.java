package com.vlm.media.api;

import static com.vlm.media.support.MediaApi.asAdmin;
import static com.vlm.media.support.MediaApi.asUser;
import static com.vlm.media.support.MediaApi.createVideo;
import static com.vlm.media.support.MediaApi.deleteRequest;
import static com.vlm.media.support.MediaApi.getRequest;
import static com.vlm.media.support.MediaApi.listRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vlm.media.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class DeleteVideoApiTest {

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;

  private final UUID ownerId = UUID.randomUUID();

  @Test
  @DisplayName("[SPEC-003 R-18] 소유자가 삭제하면 204를 반환하고 목록과 상세에서 사라진다")
  void ownerDeletesVideo() throws Exception {
    String id = newVideo();

    mvc.perform(asUser(deleteRequest(id), ownerId)).andExpect(status().isNoContent());

    assertThat(statusOf(id)).isEqualTo("DELETED");
    mvc.perform(asUser(getRequest(id), ownerId)).andExpect(status().isNotFound());
    mvc.perform(asUser(listRequest(), ownerId))
        .andExpect(jsonPath("$.page.totalElements").value(0));
  }

  @Test
  @DisplayName("[SPEC-003 R-17] ADMIN은 다른 사용자의 영상도 삭제할 수 있다")
  void adminDeletesOthersVideo() throws Exception {
    String id = newVideo();

    mvc.perform(asAdmin(deleteRequest(id), UUID.randomUUID())).andExpect(status().isNoContent());

    assertThat(statusOf(id)).isEqualTo("DELETED");
  }

  @Test
  @DisplayName("[SPEC-003 R-16] 소유자도 ADMIN도 아니면 404 MEDIA_VIDEO_NOT_FOUND를 반환하고 지우지 않는다")
  void otherUserCannotDelete() throws Exception {
    String id = newVideo();

    mvc.perform(asUser(deleteRequest(id), UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("MEDIA_VIDEO_NOT_FOUND"));
    assertThat(statusOf(id)).isEqualTo("UPLOADING");
  }

  @Test
  @DisplayName("[SPEC-003 R-16] 이미 삭제된 영상이면 404 MEDIA_VIDEO_NOT_FOUND를 반환한다")
  void deletedVideoIsNotFound() throws Exception {
    String id = newVideo();
    mvc.perform(asUser(deleteRequest(id), ownerId));

    mvc.perform(asUser(deleteRequest(id), ownerId))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("MEDIA_VIDEO_NOT_FOUND"));
  }

  @Test
  @DisplayName("[SPEC-003 R-16] 없는 영상이면 404 MEDIA_VIDEO_NOT_FOUND를 반환한다")
  void missingVideoIsNotFound() throws Exception {
    mvc.perform(asUser(deleteRequest(UUID.randomUUID().toString()), ownerId))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("MEDIA_VIDEO_NOT_FOUND"));
  }

  private String newVideo() throws Exception {
    return createVideo(mvc, ownerId, "a.mp4", 1000, "video/mp4").at("/video/id").asText();
  }

  private String statusOf(String id) {
    return jdbc.queryForObject(
        "SELECT status FROM videos WHERE public_id = ?::uuid", String.class, id);
  }
}
