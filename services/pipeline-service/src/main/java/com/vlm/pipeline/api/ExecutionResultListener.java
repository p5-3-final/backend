package com.vlm.pipeline.api;

import com.vlm.pipeline.application.ExecutionResultService;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "pipeline.results.enabled", havingValue = "true")
public class ExecutionResultListener {
  private final ExecutionResultParser parser;
  private final ExecutionResultService results;

  @KafkaListener(
      id = "execution-results",
      topics = "chunk.execution.finished",
      groupId = "${pipeline.results.group-id:pipeline-execution-results-v1}",
      containerFactory = "executionResultsKafkaListenerContainerFactory")
  public void receive(ConsumerRecord<String, String> record) {
    results.receive(parser.parse(record.key(), record.value()));
  }
}
