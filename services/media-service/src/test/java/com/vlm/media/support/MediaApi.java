package com.vlm.media.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

  /** 세션·역할 같은 추가 항목을 넣은 업로드 자리 요청. */
  public static MockHttpServletRequestBuilder createVideoRequest(
      String fileName, long sizeBytes, String contentType, Map<String, ?> extra) throws Exception {
    Map<String, Object> body = new HashMap<>(extra);
    body.put("fileName", fileName);
    body.put("sizeBytes", sizeBytes);
    body.put("contentType", contentType);
    return post("/api/v1/media/videos")
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(body));
  }

  /** 세션에 넣은 영상의 업로드 자리를 받고 영상 ID를 돌려준다. */
  public static String createSessionVideo(MockMvc mvc, UUID ownerId, String sessionId, String role)
      throws Exception {
    ResultActions result =
        mvc.perform(
            asUser(
                createVideoRequest(
                    role + ".mp4", 1000, "video/mp4", Map.of("sessionId", sessionId, "role", role)),
                ownerId));
    return JSON.readTree(result.andReturn().getResponse().getContentAsString())
        .at("/video/id")
        .asText();
  }

  /** 업로드 자리를 받고 응답 본문을 돌려준다. */
  public static JsonNode createVideo(
      MockMvc mvc, UUID ownerId, String fileName, long sizeBytes, String contentType)
      throws Exception {
    ResultActions result =
        mvc.perform(asUser(createVideoRequest(fileName, sizeBytes, contentType), ownerId));
    return JSON.readTree(result.andReturn().getResponse().getContentAsString());
  }

  /** 세션 만들기 요청. 값이 null인 항목은 본문에서 뺀다. */
  public static MockHttpServletRequestBuilder createSessionRequest(Map<String, ?> body)
      throws Exception {
    return post("/api/v1/media/sessions")
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(body));
  }

  /** 세션을 만들고 세션 ID를 돌려준다. */
  public static String createSession(MockMvc mvc, UUID ownerId, String name) throws Exception {
    ResultActions result = mvc.perform(asUser(createSessionRequest(Map.of("name", name)), ownerId));
    return JSON.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asText();
  }

  public static MockHttpServletRequestBuilder sessionRequest(String sessionId) {
    return get("/api/v1/media/sessions/{id}", sessionId);
  }

  public static MockHttpServletRequestBuilder sessionsRequest() {
    return get("/api/v1/media/sessions");
  }

  public static MockHttpServletRequestBuilder retentionSettingsRequest() {
    return get("/api/v1/media/retention-settings");
  }

  public static MockHttpServletRequestBuilder updateRetentionSettingsRequest(boolean defaultConsent)
      throws Exception {
    return put("/api/v1/media/retention-settings")
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(Map.of("defaultConsent", defaultConsent)));
  }

  public static MockHttpServletRequestBuilder consentRequest(String videoId, boolean consent)
      throws Exception {
    return put("/api/v1/media/videos/{id}/retention-consent", videoId)
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(Map.of("consent", consent)));
  }

  public static MockHttpServletRequestBuilder retainedVideosRequest() {
    return get("/api/v1/media/retained-videos");
  }

  public static MockHttpServletRequestBuilder revokeRequest(String videoId) {
    return delete("/api/v1/media/retained-videos/{id}", videoId);
  }

  public static MockHttpServletRequestBuilder revokeAllRequest() {
    return delete("/api/v1/media/retained-videos");
  }

  public static MockHttpServletRequestBuilder listRequest() {
    return get("/api/v1/media/videos");
  }

  public static MockHttpServletRequestBuilder getRequest(String videoId) {
    return get("/api/v1/media/videos/{id}", videoId);
  }

  public static MockHttpServletRequestBuilder deleteRequest(String videoId) {
    return delete("/api/v1/media/videos/{id}", videoId);
  }

  public static MockHttpServletRequestBuilder playbackRequest(String videoId) {
    return get("/api/v1/media/videos/{id}/playback", videoId);
  }

  public static MockHttpServletRequestBuilder completeRequest(String videoId) {
    return post("/api/v1/media/videos/{id}/complete", videoId);
  }

  public static MockHttpServletRequestBuilder completeRequest(String videoId, Object parts)
      throws Exception {
    return completeRequest(videoId)
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(Map.of("parts", parts)));
  }

  /** 객체 노드의 필드 이름. */
  public static List<String> fieldNames(JsonNode node) {
    List<String> names = new ArrayList<>();
    node.fieldNames().forEachRemaining(names::add);
    return names;
  }

  public static JsonNode json(ResultActions result) throws Exception {
    return JSON.readTree(result.andReturn().getResponse().getContentAsString());
  }
}
