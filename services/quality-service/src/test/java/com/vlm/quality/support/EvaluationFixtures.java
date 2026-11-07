package com.vlm.quality.support;

import static com.vlm.quality.support.QualityApi.operator;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** 평가 실행 제출과 확정 버전이 있는 Golden Set 준비. */
@Component
public class EvaluationFixtures {

  public static final String SUBMIT = "/internal/v1/quality/evaluation-runs";

  private final MockMvc mvc;
  private final GoldenFixtures golden;
  private final ReviewFixtures reviews;

  public EvaluationFixtures(MockMvc mvc, GoldenFixtures golden, ReviewFixtures reviews) {
    this.mvc = mvc;
    this.golden = golden;
    this.reviews = reviews;
  }

  /** 버전 1을 확정한 Golden Set. */
  public UUID frozenSet(String name) throws Exception {
    UUID setId = golden.create(name);
    UUID source = UUID.randomUUID();
    golden.addEntry(
        setId,
        reviews.labeled(
            source, List.of(StorageFixtures.putSourceFrame(source, 0)), ReviewFixtures.CORRECT));
    golden.freeze(setId);
    return setId;
  }

  public static String run(UUID runId, UUID setId, int version, String judgePrecision) {
    return "{\"runId\":\""
        + runId
        + "\",\"goldenSetId\":\""
        + setId
        + "\",\"goldenSetVersion\":"
        + version
        + ",\"kind\":\"event\",\"versions\":{\"taxonomy\":\"v2\",\"detectorProfile\":null,"
        + "\"modelId\":\"gpt-6.1-terra\",\"promptVersion\":\"event_judge@0123456789ab\","
        + "\"embeddingModel\":null},\"metrics\":[{\"name\":\"judge_precision\","
        + "\"eventType\":\"ppe_missing\",\"value\":"
        + judgePrecision
        + ",\"higherIsBetter\":true}],\"startedAt\":\"2026-11-06T11:10:00Z\","
        + "\"finishedAt\":\"2026-11-06T11:20:00Z\",\"trigger\":\"ci\",\"commitSha\":\"a6e8967\"}";
  }

  public ResultActions submit(String body) throws Exception {
    return mvc.perform(
        MockMvcRequestBuilders.post(SUBMIT)
            .header("X-Caller", "quality-eval")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  /** 실행을 제출하고 기준 실행으로 정한다. */
  public UUID baseline(UUID setId, String judgePrecision) throws Exception {
    UUID runId = UUID.randomUUID();
    submit(run(runId, setId, 1, judgePrecision)).andExpect(status().isCreated());
    mvc.perform(
            operator(
                    HttpMethod.PUT,
                    UUID.randomUUID(),
                    "/api/v1/quality/golden-sets/{setId}/baseline",
                    setId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"runId\":\"" + runId + "\"}"))
        .andExpect(status().isOk());
    return runId;
  }
}
