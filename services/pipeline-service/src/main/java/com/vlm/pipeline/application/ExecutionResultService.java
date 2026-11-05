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

  @Transactional
  public String receive(ExecutionFinished event) {
    dispatch.lock();
    return results.apply(event);
  }
}
