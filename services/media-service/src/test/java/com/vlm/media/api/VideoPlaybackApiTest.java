package com.vlm.media.api;

import static com.vlm.media.support.MediaApi.asUser;
import static com.vlm.media.support.MediaApi.completeRequest;
import static com.vlm.media.support.MediaApi.createVideo;
import static com.vlm.media.support.MediaApi.json;
import static com.vlm.media.support.MediaApi.playbackRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.vlm.media.support.IntegrationTest;
import com.vlm.media.support.StorageUploads;
import java.net.URI;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;

@IntegrationTest
class VideoPlaybackApiTest {

  private static final RestClient HTTP = RestClient.create();

  @Autowired MockMvc mvc;

  private final UUID ownerId = UUID.randomUUID();

  @Test
  @DisplayName("[SPEC-006 R-1] 업로드가 끝난 영상이면 재생·내려받기 주소와 만료 시각을 반환하고 두 주소로 올린 파일을 읽는다")
  void issuesPlaybackUrls() throws Exception {
    byte[] content = content(1000);
    String id = uploadedVideo("parking.mp4", content, "video/mp4");

    JsonNode playback =
        json(mvc.perform(asUser(playbackRequest(id), ownerId)).andExpect(status().isOk()));

    assertThat(playback.get("expiresAt").asText()).isNotBlank();
    assertThat(read(playback.get("url").asText()).getBody()).isEqualTo(content);
    assertThat(read(playback.get("downloadUrl").asText()).getBody()).isEqualTo(content);
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

  private static byte[] content(int size) {
    byte[] bytes = new byte[size];
    for (int i = 0; i < size; i++) {
      bytes[i] = (byte) i;
    }
    return bytes;
  }
}
