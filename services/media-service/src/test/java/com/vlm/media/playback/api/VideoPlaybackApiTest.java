package com.vlm.media.playback.api;

import static com.vlm.media.support.MediaApi.asAdmin;
import static com.vlm.media.support.MediaApi.asUser;
import static com.vlm.media.support.MediaApi.completeRequest;
import static com.vlm.media.support.MediaApi.createVideo;
import static com.vlm.media.support.MediaApi.json;
import static com.vlm.media.support.MediaApi.playbackRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.vlm.media.support.IntegrationTest;
import com.vlm.media.support.StoragePuts;
import com.vlm.media.support.StorageUploads;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;
import software.amazon.awssdk.services.s3.S3Client;

@IntegrationTest
class VideoPlaybackApiTest {

  private static final RestClient HTTP = RestClient.create();

  @Autowired MockMvc mvc;
  @Autowired S3Client s3;
  @Autowired JdbcTemplate jdbc;

  private final UUID ownerId = UUID.randomUUID();

  @Test
  @DisplayName("[SPEC-006 R-1] 업로드가 끝난 영상이면 재생·내려받기 주소와 만료 시각을 반환하고 두 주소로 올린 파일을 읽는다")
  void issuesPlaybackUrls() throws Exception {
    byte[] content = bytes(1000);
    String id = uploadedVideo("parking.mp4", content, "video/mp4");

    JsonNode playback =
        json(mvc.perform(asUser(playbackRequest(id), ownerId)).andExpect(status().isOk()));

    assertThat(playback.get("expiresAt").asText()).isNotBlank();
    assertThat(read(playback.get("url").asText()).getBody()).isEqualTo(content);
    assertThat(read(playback.get("downloadUrl").asText()).getBody()).isEqualTo(content);
  }

  @Test
  @DisplayName("[SPEC-006 R-2] 두 주소를 발급 시점부터 1시간 동안만 유효하게 서명한다")
  void urlsExpireInOneHour() throws Exception {
    String id = uploadedVideo("a.mp4", bytes(10), "video/mp4");
    Instant before = Instant.now();

    JsonNode playback =
        json(mvc.perform(asUser(playbackRequest(id), ownerId)).andExpect(status().isOk()));

    assertThat(playback.get("url").asText()).contains("X-Amz-Expires=3600");
    assertThat(playback.get("downloadUrl").asText()).contains("X-Amz-Expires=3600");
    assertThat(Instant.parse(playback.get("expiresAt").asText()))
        .isBetween(before.plus(Duration.ofHours(1)), Instant.now().plus(Duration.ofHours(1)));
  }

  @Test
  @DisplayName("[SPEC-006 R-3] 재생 주소의 응답 형식은 저장된 파일 형식과 상관없이 업로드 때 신고한 형식이다")
  void playbackUsesDeclaredContentType() throws Exception {
    // 저장소에 형식 없이 들어간 파일(application/octet-stream)이어도 신고한 형식으로 내보내야 브라우저가 재생한다.
    String id =
        createVideo(mvc, ownerId, "lobby.mov", 10, "video/quicktime").at("/video/id").asText();
    StoragePuts.put(s3, "sources/" + id + "/original", bytes(10));
    mvc.perform(asUser(completeRequest(id), ownerId)).andExpect(status().isOk());

    JsonNode playback =
        json(mvc.perform(asUser(playbackRequest(id), ownerId)).andExpect(status().isOk()));

    assertThat(read(playback.get("url").asText()).getHeaders().getContentType())
        .hasToString("video/quicktime");
  }

  @Test
  @DisplayName("[SPEC-006 R-1] 내려받기 주소는 원래 파일 이름(한글 포함)으로 내려받게 한다")
  void downloadUsesOriginalFileName() throws Exception {
    String id = uploadedVideo("주차장 \"입구\".mp4", bytes(10), "video/mp4");

    JsonNode playback =
        json(mvc.perform(asUser(playbackRequest(id), ownerId)).andExpect(status().isOk()));

    ContentDisposition disposition =
        read(playback.get("downloadUrl").asText()).getHeaders().getContentDisposition();
    assertThat(disposition.isAttachment()).isTrue();
    assertThat(disposition.getFilename()).isEqualTo("주차장 \"입구\".mp4");
    assertThat(
            read(playback.get("url").asText()).getHeaders().getContentDisposition().isAttachment())
        .isFalse();
  }

  @Test
  @DisplayName("[SPEC-006 R-4] 업로드 중인 영상이면 409 MEDIA_NOT_PLAYABLE을 반환한다")
  void uploadingVideoIsNotPlayable() throws Exception {
    String id = createVideo(mvc, ownerId, "a.mp4", 10, "video/mp4").at("/video/id").asText();

    mvc.perform(asUser(playbackRequest(id), ownerId))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("MEDIA_NOT_PLAYABLE"));
  }

  @Test
  @DisplayName("[SPEC-006 R-4] 만료된 영상이면 409 MEDIA_NOT_PLAYABLE을 반환한다")
  void expiredVideoIsNotPlayable() throws Exception {
    String id = createVideo(mvc, ownerId, "a.mp4", 10, "video/mp4").at("/video/id").asText();
    setStatus(id, "EXPIRED");

    mvc.perform(asUser(playbackRequest(id), ownerId))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("MEDIA_NOT_PLAYABLE"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"NORMALIZING", "READY", "REJECTED"})
  @DisplayName("[SPEC-006 R-1] 업로드 완료 이후 단계의 영상도 재생 주소를 발급한다")
  void laterStagesArePlayable(String laterStatus) throws Exception {
    String id = uploadedVideo("a.mp4", bytes(10), "video/mp4");
    setStatus(id, laterStatus);

    mvc.perform(asUser(playbackRequest(id), ownerId)).andExpect(status().isOk());
  }

  @Test
  @DisplayName("[SPEC-006 R-5] 없는 영상이면 404 MEDIA_VIDEO_NOT_FOUND를 반환한다")
  void missingVideoIsNotFound() throws Exception {
    mvc.perform(asUser(playbackRequest(UUID.randomUUID().toString()), ownerId))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("MEDIA_VIDEO_NOT_FOUND"));
  }

  @Test
  @DisplayName("[SPEC-006 R-5] 삭제된 영상이면 404 MEDIA_VIDEO_NOT_FOUND를 반환한다")
  void deletedVideoIsNotFound() throws Exception {
    String id = uploadedVideo("a.mp4", bytes(10), "video/mp4");
    setStatus(id, "DELETED");

    mvc.perform(asUser(playbackRequest(id), ownerId))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("MEDIA_VIDEO_NOT_FOUND"));
  }

  @Test
  @DisplayName("[SPEC-006 R-5] 소유자도 ADMIN도 아니면 업로드 중인 영상이어도 404 MEDIA_VIDEO_NOT_FOUND를 반환한다")
  void otherUsersVideoIsNotFound() throws Exception {
    String id = createVideo(mvc, ownerId, "a.mp4", 10, "video/mp4").at("/video/id").asText();

    mvc.perform(asUser(playbackRequest(id), UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("MEDIA_VIDEO_NOT_FOUND"));
  }

  @Test
  @DisplayName("[SPEC-006 R-5] ADMIN은 다른 사용자의 영상도 재생 주소를 받는다")
  void adminCanPlayOthersVideo() throws Exception {
    String id = uploadedVideo("a.mp4", bytes(10), "video/mp4");

    mvc.perform(asAdmin(playbackRequest(id), UUID.randomUUID())).andExpect(status().isOk());
  }

  @Test
  @DisplayName("[SPEC-006 NFR-1] 재생 주소 발급 응답은 JSON이고 영상 바이트를 담지 않는다")
  void responseIsJsonNotBytes() throws Exception {
    String id = uploadedVideo("a.mp4", bytes(1000), "video/mp4");

    mvc.perform(asUser(playbackRequest(id), ownerId))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(
            result ->
                assertThat(result.getResponse().getContentAsByteArray()).hasSizeLessThan(1000));
  }

  private void setStatus(String id, String newStatus) {
    jdbc.update("UPDATE videos SET status = ? WHERE public_id = ?::uuid", newStatus, id);
  }

  private String uploadedVideo(String fileName, byte[] content, String contentType)
      throws Exception {
    JsonNode created = createVideo(mvc, ownerId, fileName, content.length, contentType);
    StorageUploads.postForm(created.get("upload"), content);
    String id = created.at("/video/id").asText();
    mvc.perform(asUser(completeRequest(id), ownerId)).andExpect(status().isOk());
    return id;
  }

  private static ResponseEntity<byte[]> read(String url) {
    return HTTP.get().uri(URI.create(url)).retrieve().toEntity(byte[].class);
  }

  private static byte[] bytes(int size) {
    byte[] bytes = new byte[size];
    for (int i = 0; i < size; i++) {
      bytes[i] = (byte) i;
    }
    return bytes;
  }
}
