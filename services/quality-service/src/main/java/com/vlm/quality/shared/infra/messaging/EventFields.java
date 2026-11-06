package com.vlm.quality.shared.infra.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 계약으로 이미 검증한 메시지에서 값을 꺼낸다. 스키마가 형식을 보장하므로 여기서는 꺼내기만 한다. */
public final class EventFields {

  // 계약 응답(ReviewItem.startMs 등)이 integer라 생성 모델이 Integer다. 그보다 큰 영상 시각(약 24.8일)은 받지 않는다.
  private static final long MAX_RESPONSE_MS = Integer.MAX_VALUE;

  private EventFields() {}

  public static String text(JsonNode event, String field) {
    return event.get(field).asText();
  }

  public static @Nullable String nullableText(JsonNode event, String field) {
    JsonNode node = event.get(field);
    return node == null || node.isNull() ? null : node.asText();
  }

  public static UUID uuid(JsonNode event, String field) {
    return UUID.fromString(text(event, field));
  }

  public static Instant instant(JsonNode event, String field) {
    return Instant.parse(text(event, field));
  }

  /** 응답에 담을 수 있는 영상 시각(밀리초). */
  public static long videoMs(JsonNode event, String field) {
    long value = event.get(field).asLong();
    if (value > MAX_RESPONSE_MS) {
      throw new InvalidEventException(field + " exceeds the supported range");
    }
    return value;
  }

  public static List<String> texts(JsonNode event, String field) {
    List<String> values = new ArrayList<>();
    event.get(field).forEach(node -> values.add(node.asText()));
    return List.copyOf(values);
  }

  /** 모든 이벤트 토픽의 키는 sourceId다. 키가 다르면 순서 보장이 깨진 메시지다. */
  public static UUID sourceIdMatchingKey(JsonNode event, @Nullable String key) {
    UUID sourceId = uuid(event, "sourceId");
    if (!sourceId.toString().equals(key)) {
      throw new InvalidEventException("message key does not match sourceId");
    }
    return sourceId;
  }
}
