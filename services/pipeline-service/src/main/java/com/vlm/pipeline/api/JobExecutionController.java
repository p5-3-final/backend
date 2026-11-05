package com.vlm.pipeline.api;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vlm.common.web.UserHeaders;
import com.vlm.pipeline.application.JobExecutionService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class JobExecutionController {
  private final JobExecutionService service;

  @GetMapping("/api/v1/jobs/{jobId}/execution")
  public ObjectNode getJobExecution(
      @PathVariable UUID jobId,
      @RequestHeader(name = UserHeaders.USER_ID, required = false) @Nullable String userId) {
    return service.status(jobId, UserHeaders.userId(userId));
  }
}
