package com.vlm.media.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 내부 데이터셋 영상 등록 요청(SPEC-030 R-5, R-6). */
public final class DatasetVideos {

  public static final String PATH = "/internal/v1/media/dataset-videos";
  public static final String CALLER = "X-Caller";

  private DatasetVideos() {}

  public static MockHttpServletRequestBuilder register(
      UUID importId, String externalId, UUID ownerId, String importKey) {
    return register(importId, externalId, ownerId, importKey, "video/mp4");
  }

  public static MockHttpServletRequestBuilder register(
      UUID importId, String externalId, UUID ownerId, String importKey, String contentType) {
    return post(PATH)
        .header(CALLER, "quality-service")
        .contentType(MediaType.APPLICATION_JSON)
        .content(body(importId, externalId, ownerId, importKey, contentType));
  }

  public static String body(UUID importId, String externalId, UUID ownerId, String importKey) {
    return body(importId, externalId, ownerId, importKey, "video/mp4");
  }

  public static String body(
      UUID importId, String externalId, UUID ownerId, String importKey, String contentType) {
    return "{\"importId\":\""
        + importId
        + "\",\"externalId\":\""
        + externalId
        + "\",\"ownerId\":\""
        + ownerId
        + "\",\"importKey\":\""
        + importKey
        + "\",\"contentType\":\""
        + contentType
        + "\"}";
  }
}
