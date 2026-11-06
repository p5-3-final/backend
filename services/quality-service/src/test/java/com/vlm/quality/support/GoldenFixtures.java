package com.vlm.quality.support;

import static com.vlm.quality.support.QualityApi.operator;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;

/** Golden Set을 API로 만든다. */
@Component
public class GoldenFixtures {

  private final MockMvc mvc;
  private final ObjectMapper json;

  public GoldenFixtures(MockMvc mvc, ObjectMapper json) {
    this.mvc = mvc;
    this.json = json;
  }

  public UUID create(String name) throws Exception {
    String body =
        mvc.perform(
                operator(HttpMethod.POST, UUID.randomUUID(), "/api/v1/quality/golden-sets")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"" + name + "\",\"split\":\"eval\",\"description\":null}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(json.readTree(body).get("setId").asText());
  }

  public JsonNode addEntry(UUID setId, UUID reviewItemId) throws Exception {
    String body =
        mvc.perform(
                operator(
                        HttpMethod.POST,
                        UUID.randomUUID(),
                        "/api/v1/quality/golden-sets/{setId}/entries",
                        setId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reviewItemId\":\"" + reviewItemId + "\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body);
  }

  public JsonNode freeze(UUID setId) throws Exception {
    String body =
        mvc.perform(
                operator(
                    HttpMethod.POST,
                    UUID.randomUUID(),
                    "/api/v1/quality/golden-sets/{setId}/versions",
                    setId))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body);
  }
}
