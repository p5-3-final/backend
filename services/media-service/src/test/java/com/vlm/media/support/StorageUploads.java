package com.vlm.media.support;

import com.fasterxml.jackson.databind.JsonNode;
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
