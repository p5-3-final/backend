package com.vlm.media.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 게이트웨이가 붙이는 사용자 헤더를 넣어 media-service API를 부르는 테스트 도우미. */
public final class MediaApi {

  public static final String USER_ID = "X-User-Id";
  public static final String USER_ROLES = "X-User-Roles";
  private static final ObjectMapper JSON = new ObjectMapper();

  private MediaApi() {}

  public static MockHttpServletRequestBuilder asUser(
      MockHttpServletRequestBuilder request, UUID userId) {
    return request.header(USER_ID, userId.toString()).header(USER_ROLES, "USER");
  }

  public static MockHttpServletRequestBuilder asAdmin(
      MockHttpServletRequestBuilder request, UUID userId) {
    return request.header(USER_ID, userId.toString()).header(USER_ROLES, "USER,ADMIN");
  }

  public static MockHttpServletRequestBuilder createVideoRequest(
      String fileName, long sizeBytes, String contentType) throws Exception {
    return post("/api/v1/media/videos")
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            JSON.writeValueAsString(
                Map.of("fileName", fileName, "sizeBytes", sizeBytes, "contentType", contentType)));
  }

  /** 업로드 자리를 받고 응답 본문을 돌려준다. */
  public static JsonNode createVideo(
      MockMvc mvc, UUID ownerId, String fileName, long sizeBytes, String contentType)
      throws Exception {
    ResultActions result =
        mvc.perform(asUser(createVideoRequest(fileName, sizeBytes, contentType), ownerId));
    return JSON.readTree(result.andReturn().getResponse().getContentAsString());
  }

  public static MockHttpServletRequestBuilder completeRequest(String videoId) {
    return post("/api/v1/media/videos/{id}/complete", videoId);
  }

  public static JsonNode json(ResultActions result) throws Exception {
    return JSON.readTree(result.andReturn().getResponse().getContentAsString());
  }
}
