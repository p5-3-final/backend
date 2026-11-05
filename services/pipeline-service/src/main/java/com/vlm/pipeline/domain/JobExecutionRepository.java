package com.vlm.pipeline.domain;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;

/** 실행 집계의 원장 투영. 분석 완료 조건과 분리한다. */
public interface JobExecutionRepository {
  Optional<ObjectNode> refresh(UUID jobId);

  Optional<ObjectNode> find(UUID jobId, UUID ownerId);

  boolean owned(UUID jobId, UUID ownerId);

  List<UUID> due(int batchSize);

  List<UUID> live();

  Optional<UUID> jobForExecution(UUID executionId);
}
