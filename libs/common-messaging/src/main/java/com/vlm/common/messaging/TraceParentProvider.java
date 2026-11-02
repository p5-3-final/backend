package com.vlm.common.messaging;

import java.util.Optional;

/** 현재 요청의 W3C {@code traceparent} 값을 돌려준다. 추적이 없으면 비어 있다. */
@FunctionalInterface
public interface TraceParentProvider {

  /** 추적이 꺼진 서비스에서 쓴다. */
  TraceParentProvider NONE = Optional::empty;

  Optional<String> current();
}
