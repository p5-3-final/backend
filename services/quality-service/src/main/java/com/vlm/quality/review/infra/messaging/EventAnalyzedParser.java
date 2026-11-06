package com.vlm.quality.review.infra.messaging;

import static com.vlm.quality.shared.infra.messaging.EventFields.nullableText;
import static com.vlm.quality.shared.infra.messaging.EventFields.sourceIdMatchingKey;
import static com.vlm.quality.shared.infra.messaging.EventFields.text;
import static com.vlm.quality.shared.infra.messaging.EventFields.texts;
import static com.vlm.quality.shared.infra.messaging.EventFields.uuid;
import static com.vlm.quality.shared.infra.messaging.EventFields.videoMs;

import com.fasterxml.jackson.databind.JsonNode;
import com.vlm.quality.review.application.AnalyzedEvent;
import com.vlm.quality.shared.infra.messaging.EventSchemas;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/** event.analyzed를 계약(events/event.analyzed.schema.json)으로 검증해 읽는다. */
@Component
@RequiredArgsConstructor
class EventAnalyzedParser {

  private static final String SCHEMA = "events/event.analyzed.schema.json";

  private final EventSchemas schemas;

  AnalyzedEvent parse(@Nullable String key, String value) {
    JsonNode event = schemas.readValid(SCHEMA, value);
    JsonNode analysis = event.get("analysis");
    return new AnalyzedEvent(
        uuid(event, "eventId"),
        sourceIdMatchingKey(event, key),
        uuid(event, "jobId"),
        text(event, "candidateEventType"),
        videoMs(event, "startMs"),
        videoMs(event, "endMs"),
        text(event, "judgmentMethod"),
        text(event, "taxonomyVersion"),
        event.get("reviewRequired").asBoolean(),
        texts(event, "reviewReasons"),
        text(analysis, "verificationStatus"),
        nullableText(event, "unverifiedReason"),
        texts(event, "evidenceFrameKeys"),
        analysis.toString());
  }
}
