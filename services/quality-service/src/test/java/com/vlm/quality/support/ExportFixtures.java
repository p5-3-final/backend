package com.vlm.quality.support;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vlm.quality.export.application.FeatureExportWorker;
import com.vlm.quality.export.application.SourceCompleted;
import com.vlm.quality.export.application.SourceResultService;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** 결과 파일을 원본 버킷에 두고 source.completed를 받은 것처럼 기록한다(SPEC-030 R-13, R-14). */
@Component
public class ExportFixtures {

  private static final String SOURCE_RESULT = "aggregates/v1/source-result.schema.json";

  private final SourceResultService results;
  private final FeatureExportWorker worker;

  public ExportFixtures(SourceResultService results, FeatureExportWorker worker) {
    this.results = results;
    this.worker = worker;
  }

  /** 위험 상태 riskCount개가 든 결과 파일을 올리고 받은 것으로 기록한다. */
  public void completed(UUID sourceId, int riskCount) {
    UUID jobId = UUID.randomUUID();
    String key = Events.resultKey(sourceId, jobId);
    StorageFixtures.putSource(
        key, resultFile(sourceId, jobId, riskCount).toString().getBytes(StandardCharsets.UTF_8));
    results.record(new SourceCompleted(sourceId, jobId, 1, key));
  }

  /** 결과 파일 없이 받은 것으로만 기록한다. */
  public void completedWithoutFile(UUID sourceId) {
    UUID jobId = UUID.randomUUID();
    results.record(new SourceCompleted(sourceId, jobId, 1, Events.resultKey(sourceId, jobId)));
  }

  public static ObjectNode resultFile(UUID sourceId, UUID jobId, int riskCount) {
    ObjectNode result = ContractExamples.example(SOURCE_RESULT, 0);
    result.put("sourceId", sourceId.toString());
    result.put("jobId", jobId.toString());
    ArrayNode risks = (ArrayNode) result.get("risks");
    ObjectNode template = (ObjectNode) risks.get(0);
    risks.removeAll();
    for (int i = 0; i < riskCount; i++) {
      ObjectNode risk = template.deepCopy();
      risk.put("riskId", UUID.randomUUID().toString());
      risks.add(risk);
    }
    return result;
  }

  public void runWorker() {
    boolean worked = true;
    while (worked) {
      worked = worker.runOnce();
    }
  }
}
