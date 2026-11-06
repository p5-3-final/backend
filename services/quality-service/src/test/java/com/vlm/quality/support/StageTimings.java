package com.vlm.quality.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** stage.timing 메시지(계약 events/stage.timing.schema.json)를 만든다. */
public final class StageTimings {

  private static final ObjectMapper JSON = new ObjectMapper();

  private final ObjectNode batch = JSON.createObjectNode();
  private final ArrayNode timings;

  private StageTimings() {
    batch.put("batchId", UUID.randomUUID().toString());
    batch.put("service", "event-analyzer");
    batch.put("pod", "event-analyzer-0");
    batch.put("producedAt", Instant.now().toString());
    timings = batch.putArray("timings");
  }

  public static StageTimings batch() {
    return new StageTimings();
  }

  public StageTimings timing(
      UUID timingId,
      String stageId,
      String path,
      @Nullable UUID sourceId,
      Instant startedAt,
      double durationMs,
      String status,
      Map<String, String> versions) {
    ObjectNode timing = timings.addObject();
    timing.put("timingId", timingId.toString());
    timing.put("stageId", stageId);
    timing.put("path", path);
    ObjectNode subject = timing.putObject("subject");
    if (sourceId == null) {
      subject.putNull("sourceId");
    } else {
      subject.put("sourceId", sourceId.toString());
    }
    timing.put("attempt", 1);
    timing.put("startedAt", startedAt.toString());
    timing.put("endedAt", startedAt.plusMillis((long) Math.max(durationMs, 0)).toString());
    timing.put("durationMs", durationMs);
    timing.put("status", status);
    ObjectNode versionNode = timing.putObject("versions");
    versions.forEach(versionNode::put);
    timing.putNull("traceId");
    timing.putNull("spanId");
    timing.putArray("aiCallIds");
    return this;
  }

  public StageTimings timing(UUID sourceId, String stageId, Instant startedAt, double durationMs) {
    return timing(
        UUID.randomUUID(),
        stageId,
        "event-analyzer.handle/" + stageId,
        sourceId,
        startedAt,
        durationMs,
        "ok",
        Map.of());
  }

  /** 마지막 기록의 subject.candidateId를 정한다. */
  public StageTimings candidate(String candidateId) {
    ((ObjectNode) timings.get(timings.size() - 1).get("subject")).put("candidateId", candidateId);
    return this;
  }

  public String json() {
    return batch.toString();
  }

  /** 계약: 메시지 키는 측정 대상의 sourceId이고 없으면 batchId다. */
  public String key() {
    var source = batch.get("timings").get(0).get("subject").get("sourceId");
    return source == null || source.isNull() ? batch.get("batchId").asText() : source.asText();
  }
}
