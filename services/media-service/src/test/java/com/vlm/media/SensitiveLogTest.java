package com.vlm.media;

import static com.vlm.media.support.MediaApi.asUser;
import static com.vlm.media.support.MediaApi.completeRequest;
import static com.vlm.media.support.MediaApi.createVideo;
import static com.vlm.media.support.MediaApi.deleteRequest;
import static com.vlm.media.support.MediaApi.json;
import static com.vlm.media.support.MediaApi.playbackRequest;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.vlm.media.application.StorageCleanupJob;
import com.vlm.media.support.IntegrationTest;
import com.vlm.media.support.StorageUploads;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponentsBuilder;

// 테스트 설정: 단일 업로드 기준 1MiB, 조각 5MiB (IntegrationTest)
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = "logging.level.com.vlm=DEBUG")
class SensitiveLogTest {

  private static final int PART = 5 * 1024 * 1024;
  // 로그에 파일 내용이 섞였는지 찾기 쉬운 표식
  private static final String CONTENT_MARKER = "VIDEO-CONTENT-7f3a9c";

  @Autowired MockMvc mvc;
  @Autowired StorageCleanupJob cleanupJob;

  private final UUID ownerId = UUID.randomUUID();

  @Test
  @DisplayName("[SPEC-003 NFR-5] 업로드부터 삭제까지 업로드 주소의 서명과 파일 내용을 로그에 남기지 않는다")
  void doesNotLogSignaturesOrContent(CapturedOutput output) throws Exception {
    List<String> secrets = new ArrayList<>();
    byte[] small = content(1000);
    JsonNode single = createVideo(mvc, ownerId, "a.mp4", small.length, "video/mp4");
    secrets.add(single.at("/upload/fields/policy").asText());
    secrets.add(single.at("/upload/fields/x-amz-signature").asText());
    StorageUploads.postForm(single.get("upload"), small);
    mvc.perform(asUser(completeRequest(id(single)), ownerId));

    byte[] big = content(PART + 1000);
    JsonNode multipart = createVideo(mvc, ownerId, "big.mp4", big.length, "video/mp4");
    multipart
        .at("/upload/parts")
        .forEach(part -> secrets.add(signatureOf(part.get("url").asText())));
    mvc.perform(
        asUser(
            completeRequest(id(multipart), StorageUploads.putParts(multipart.get("upload"), big)),
            ownerId));

    mvc.perform(asUser(deleteRequest(id(single)), ownerId));
    mvc.perform(asUser(deleteRequest(id(multipart)), ownerId));
    cleanupJob.cleanUp();

    assertThat(secrets).doesNotContain("").hasSize(4);
    for (String secret : secrets) {
      assertThat(output.getAll()).doesNotContain(secret);
    }
    assertThat(output.getAll()).doesNotContain(CONTENT_MARKER);
  }

  @Test
  @DisplayName("[SPEC-006 NFR-3] 재생·내려받기 주소의 서명을 로그에 남기지 않는다")
  void doesNotLogPlaybackSignatures(CapturedOutput output) throws Exception {
    JsonNode created = createVideo(mvc, ownerId, "a.mp4", 1000, "video/mp4");
    StorageUploads.postForm(created.get("upload"), content(1000));
    mvc.perform(asUser(completeRequest(id(created)), ownerId));

    JsonNode playback = json(mvc.perform(asUser(playbackRequest(id(created)), ownerId)));
    List<String> secrets =
        List.of(
            signatureOf(playback.get("url").asText()),
            signatureOf(playback.get("downloadUrl").asText()));

    assertThat(secrets).doesNotContain("", "null");
    for (String secret : secrets) {
      assertThat(output.getAll()).doesNotContain(secret);
    }
  }

  private static byte[] content(int size) {
    byte[] content = new byte[size];
    byte[] marker = CONTENT_MARKER.getBytes(StandardCharsets.US_ASCII);
    for (int i = 0; i + marker.length <= size; i += marker.length) {
      System.arraycopy(marker, 0, content, i, marker.length);
    }
    return Arrays.copyOf(content, size);
  }

  private static String signatureOf(String url) {
    return String.valueOf(
        UriComponentsBuilder.fromUriString(url)
            .build()
            .getQueryParams()
            .getFirst("X-Amz-Signature"));
  }

  private static String id(JsonNode created) {
    return created.get("video").get("id").asText();
  }
}
