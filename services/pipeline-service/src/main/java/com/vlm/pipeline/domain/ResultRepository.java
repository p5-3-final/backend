package com.vlm.pipeline.domain;

/** 잠금과 트랜잭션은 application이 소유한다. */
public interface ResultRepository {
  String apply(ExecutionFinished event);
}
