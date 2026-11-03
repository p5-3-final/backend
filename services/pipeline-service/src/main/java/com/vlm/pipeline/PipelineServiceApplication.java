package com.vlm.pipeline;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@org.springframework.boot.context.properties.EnableConfigurationProperties(
    com.vlm.pipeline.config.NormalizedIntakeProperties.class)
@SpringBootApplication
public class PipelineServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(PipelineServiceApplication.class, args);
  }
}
