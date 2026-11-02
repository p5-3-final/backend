package com.vlm.media.support;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/** 브라우저처럼 응답의 업로드 방법대로 저장소(SeaweedFS)에 파일을 올린다. */
public final class StorageUploads {

  private static final RestClient HTTP = RestClient.create();

  private StorageUploads() {}

  /** 멀티파트 업로드. 조각마다 PUT하고 응답의 ETag를 모아 완료 요청 본문의 parts로 돌려준다. */
  public static List<Map<String, Object>> putParts(JsonNode upload, byte[] content) {
    int partSize = upload.get("partSizeBytes").asInt();
    List<Map<String, Object>> parts = new ArrayList<>();
    for (JsonNode part : upload.get("parts")) {
      int number = part.get("partNumber").asInt();
      int from = (number - 1) * partSize;
      byte[] chunk = Arrays.copyOfRange(content, from, Math.min(from + partSize, content.length));
      String eTag =
          HTTP.put()
              .uri(URI.create(part.get("url").asText()))
              .body(chunk)
              .exchange((request, response) -> response.getHeaders().getETag());
      parts.add(Map.of("partNumber", number, "eTag", String.valueOf(eTag)));
    }
    return parts;
  }

  /** 단일 업로드(POST 폼). 폼 필드를 모두 넣고 마지막에 file을 넣는다. 저장소의 응답 상태를 돌려준다. */
  public static HttpStatusCode postForm(JsonNode upload, byte[] content) {
    MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
    for (Map.Entry<String, JsonNode> field : upload.get("fields").properties()) {
      form.add(field.getKey(), field.getValue().asText());
    }
    form.add(
        "file",
        new ByteArrayResource(content) {
          @Override
          public String getFilename() {
            return "upload.bin";
          }
        });
    return HTTP.post()
        .uri(upload.get("url").asText())
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .body(form)
        .exchange((request, response) -> response.getStatusCode());
  }
}
