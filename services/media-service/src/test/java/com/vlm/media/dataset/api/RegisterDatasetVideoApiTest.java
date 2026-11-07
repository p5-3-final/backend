package com.vlm.media.dataset.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vlm.media.TestcontainersConfiguration;
import com.vlm.media.support.ContractSchemas;
import com.vlm.media.support.DatasetVideos;
import com.vlm.media.support.IntegrationTest;
import com.vlm.media.support.MediaApi;
import com.vlm.media.support.StoragePuts;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import software.amazon.awssdk.services.s3.S3Client;

@IntegrationTest
class RegisterDatasetVideoApiTest {

  private static final byte[] CONTENT = new byte[1000];

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired S3Client s3;
  private final ObjectMapper json = new ObjectMapper();

  private final UUID importId = UUID.randomUUID();
  private final UUID ownerId = UUID.randomUUID();

  @Test
  @DisplayName(
      "[SPEC-030 R-5] 가져오기 버킷 파일을 원본 경로로 복사하고 결정적 videoId의 UPLOADED·DATASET 영상과 video.uploaded를 만든다")
  void registersDatasetVideo() throws Exception {
    String importKey = "imports/ds-a/videos/clip-0001.mp4";
    StoragePuts.putImport(s3, importKey, CONTENT);

    JsonNode created =
        MediaApi.json(
            mvc.perform(DatasetVideos.register(importId, "clip-0001", ownerId, importKey))
                .andExpect(status().isCreated()));

    UUID videoId = UUID.fromString(created.get("videoId").asText());
    assertThat(videoId.version()).isEqualTo(5);
    assertThat(created.get("status").asText()).isEqualTo("UPLOADED");
    assertThat(created.get("sizeBytes").asLong()).isEqualTo(CONTENT.length);
    assertThat(
            s3.getObjectAsBytes(
                    g ->
                        g.bucket(TestcontainersConfiguration.BUCKET)
                            .key("sources/" + videoId + "/original"))
                .asByteArray())
        .hasSize(CONTENT.length);
    Map<String, Object> row =
        jdbc.queryForMap(
            "SELECT owner_id, status, retention_grade, file_name FROM videos WHERE public_id = ?",
            videoId);
    assertThat(row)
        .containsEntry("owner_id", ownerId)
        .containsEntry("status", "UPLOADED")
        .containsEntry("retention_grade", "DATASET")
        .containsEntry("file_name", "clip-0001.mp4");
    JsonNode payload =
        json.readTree(
            jdbc.queryForObject(
                "SELECT payload::text FROM outbox_events WHERE event_type = 'video.uploaded'",
                String.class));
    assertThat(ContractSchemas.validateEvent("video.uploaded.schema.json", payload)).isEmpty();
    assertThat(payload.get("sourceId").asText()).isEqualTo(videoId.toString());
    assertThat(payload.get("ownerId").asText()).isEqualTo(ownerId.toString());
  }

  @Test
  @DisplayName("[SPEC-030 R-6] 같은 (importId, externalId)로 다시 부르면 복사·발행 없이 같은 영상을 200으로 돌려준다")
  void returnsExistingVideo() throws Exception {
    String importKey = "imports/ds-a/videos/clip-0002.mp4";
    StoragePuts.putImport(s3, importKey, CONTENT);
    JsonNode first =
        MediaApi.json(
            mvc.perform(DatasetVideos.register(importId, "clip-0002", ownerId, importKey))
                .andExpect(status().isCreated()));
    // 가져오기 버킷에서 지워도 이미 등록한 영상은 다시 확인하지 않는다.
    s3.deleteObject(d -> d.bucket(TestcontainersConfiguration.IMPORT_BUCKET).key(importKey));

    mvc.perform(DatasetVideos.register(importId, "clip-0002", ownerId, importKey))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.videoId").value(first.get("videoId").asText()))
        .andExpect(jsonPath("$.sizeBytes").value(CONTENT.length));

    assertThat(jdbc.queryForObject("SELECT count(*) FROM videos", Integer.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events", Integer.class))
        .isEqualTo(1);
  }

  @Test
  @DisplayName("[SPEC-030 R-6] 가져오기 버킷에 파일이 없으면 404 MEDIA_IMPORT_OBJECT_NOT_FOUND이고 영상을 만들지 않는다")
  void rejectsMissingObject() throws Exception {
    mvc.perform(
            DatasetVideos.register(
                importId, "clip-9999", ownerId, "imports/ds-a/videos/clip-9999.mp4"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("MEDIA_IMPORT_OBJECT_NOT_FOUND"));

    assertThat(jdbc.queryForObject("SELECT count(*) FROM videos", Integer.class)).isZero();
  }

  @Test
  @DisplayName("[SPEC-030 R-6] X-Caller가 없거나 허용 목록 밖이면 403 MEDIA_CALLER_NOT_ALLOWED다")
  void rejectsUnknownCaller() throws Exception {
    String importKey = "imports/ds-a/videos/clip-0003.mp4";
    StoragePuts.putImport(s3, importKey, CONTENT);

    mvc.perform(
            post(DatasetVideos.PATH)
                .header(DatasetVideos.CALLER, "analysis-api")
                .contentType(MediaType.APPLICATION_JSON)
                .content(DatasetVideos.body(importId, "clip-0003", ownerId, importKey)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("MEDIA_CALLER_NOT_ALLOWED"));
    mvc.perform(
            post(DatasetVideos.PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(DatasetVideos.body(importId, "clip-0003", ownerId, importKey)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("MEDIA_CALLER_NOT_ALLOWED"));
  }

  @Test
  @DisplayName(
      "[SPEC-030 R-6] webm은 400 MEDIA_UNSUPPORTED_FORMAT, 빈 파일은 400 MEDIA_FILE_SIZE_INVALID이고 영상을 만들지 않는다")
  void rejectsUnsupportedFiles() throws Exception {
    String webm = "imports/ds-a/videos/clip-0004.webm";
    StoragePuts.putImport(s3, webm, CONTENT);
    String empty = "imports/ds-a/videos/clip-0005.mp4";
    StoragePuts.putImport(s3, empty, new byte[0]);

    mvc.perform(DatasetVideos.register(importId, "clip-0004", ownerId, webm, "video/webm"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MEDIA_UNSUPPORTED_FORMAT"));
    mvc.perform(DatasetVideos.register(importId, "clip-0005", ownerId, empty))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MEDIA_FILE_SIZE_INVALID"));

    assertThat(jdbc.queryForObject("SELECT count(*) FROM videos", Integer.class)).isZero();
  }

  @Test
  @DisplayName("[SPEC-030 R-6] 경로에 ..가 있거나 필드가 빠지면 400 MEDIA_INVALID_REQUEST다")
  void rejectsInvalidRequest() throws Exception {
    mvc.perform(
            DatasetVideos.register(
                importId, "clip-0006", ownerId, "imports/ds-a/videos/../secret.mp4"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MEDIA_INVALID_REQUEST"));
    mvc.perform(
            post(DatasetVideos.PATH)
                .header(DatasetVideos.CALLER, "quality-service")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"importId\":\"" + importId + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MEDIA_INVALID_REQUEST"));
  }

  @Test
  @DisplayName(
      "[SPEC-030 R-5] X-Request-Deadline이 이미 지났으면 504 MEDIA_DEADLINE_EXCEEDED이고 영상을 만들지 않는다(C-33)")
  void honorsRequestDeadline() throws Exception {
    String importKey = "imports/ds-a/videos/clip-0007.mp4";
    StoragePuts.putImport(s3, importKey, CONTENT);

    mvc.perform(
            DatasetVideos.register(importId, "clip-0007", ownerId, importKey)
                .header("X-Request-Deadline", System.currentTimeMillis() - 1_000))
        .andExpect(status().isGatewayTimeout())
        .andExpect(jsonPath("$.code").value("MEDIA_DEADLINE_EXCEEDED"));
    mvc.perform(
            DatasetVideos.register(importId, "clip-0007", ownerId, importKey)
                .header("X-Request-Deadline", System.currentTimeMillis() + 30_000))
        .andExpect(status().isCreated());

    assertThat(jdbc.queryForObject("SELECT count(*) FROM videos", Integer.class)).isEqualTo(1);
  }
}
