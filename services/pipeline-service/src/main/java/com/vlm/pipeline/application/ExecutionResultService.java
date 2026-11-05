package com.vlm.pipeline.application;

import com.vlm.pipeline.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExecutionResultService {
  private final DispatchRepository dispatch;
  private final ResultRepository results;
  private final JobExecutionService executions;

  @Transactional
  public String receive(ExecutionFinished event) {
    dispatch.lock();
    String decision = results.apply(event);
    executions.refresh(event.jobId());
    return decision;
  }
}
