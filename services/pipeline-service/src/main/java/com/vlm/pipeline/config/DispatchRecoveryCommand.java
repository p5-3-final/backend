package com.vlm.pipeline.config;

import com.vlm.pipeline.PipelineServiceApplication;
import com.vlm.pipeline.application.DispatchRecoveryService;
import com.vlm.pipeline.domain.StopConfirmation;
import org.slf4j.LoggerFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

/** 별도 프로세스에서 한 번 실행한다. 메시지 수신·배정·발행·웹을 강제로 끈다. */
public final class DispatchRecoveryCommand {
  private DispatchRecoveryCommand() {}

  public static void main(String[] args) {
    var confirmation = StopConfirmation.parse(args);
    try (var context =
        new SpringApplicationBuilder(PipelineServiceApplication.class)
            .web(WebApplicationType.NONE)
            .run(
                "--spring.main.web-application-type=none",
                "--pipeline.intake.enabled=false",
                "--pipeline.results.enabled=false",
                "--pipeline.dispatch.enabled=false",
                "--vlm.outbox.scheduling-enabled=false")) {
      context
          .getBean(DispatchRecoveryService.class)
          .confirmStopped(
              confirmation.executionId(), confirmation.confirmationId(), confirmation.evidence());
      // 증거 참조 자체는 개인정보·비밀값을 담을 수 있어 로그에 쓰지 않는다.
      LoggerFactory.getLogger(DispatchRecoveryCommand.class)
          .info(
              "Dispatch slot released: executionId={}, confirmationId={}",
              confirmation.executionId(),
              confirmation.confirmationId());
    }
  }
}
