package com.vlm.quality.support;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;

/** 계약 예시를 바꿔 만든 테스트용 이벤트. */
public final class Events {

  private Events() {}

  /** reviewRequired·확정 판정인 event.analyzed. eventId·sourceId를 바꾼다. */
  public static ObjectNode analyzed(UUID eventId, UUID sourceId) {
    ObjectNode event = ContractExamples.example("events/event.analyzed.schema.json", 0);
    event.put("eventId", eventId.toString());
    event.put("sourceId", sourceId.toString());
    ArrayNode frames = event.putArray("evidenceFrameKeys");
    String jobId = event.get("jobId").asText();
    String chunkId = event.get("chunkId").asText();
    String executionId = event.get("executionId").asText();
    for (int i = 0; i < 2; i++) {
      frames.add(frameKey(sourceId, jobId, chunkId, executionId, i));
    }
    return event;
  }

  public static ObjectNode reviewRequired(UUID eventId, UUID sourceId) {
    ObjectNode event = analyzed(eventId, sourceId);
    event.put("reviewRequired", true);
    event.putArray("reviewReasons").add("judgment_disagreement");
    return event;
  }

  /** 판정하지 않은(unverified) event.analyzed. */
  public static ObjectNode unverified(UUID eventId, UUID sourceId, String unverifiedReason) {
    ObjectNode event = analyzed(eventId, sourceId);
    ((ObjectNode) event.get("analysis")).put("verificationStatus", "unverified");
    event.put("unverifiedReason", unverifiedReason);
    return event;
  }

  public static ObjectNode videoDeleted(UUID sourceId) {
    ObjectNode event = ContractExamples.example("events/video.deleted.schema.json", 0);
    event.put("eventId", UUID.randomUUID().toString());
    event.put("sourceId", sourceId.toString());
    return event;
  }

  public static String frameKey(
      UUID sourceId, String jobId, String chunkId, String executionId, int index) {
    return "sources/"
        + sourceId
        + "/jobs/"
        + jobId
        + "/chunks/"
        + chunkId
        + "/executions/"
        + executionId
        + "/blurred/f"
        + index
        + ".jpg";
  }
}
