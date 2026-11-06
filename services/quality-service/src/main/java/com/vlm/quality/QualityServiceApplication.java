package com.vlm.quality;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class QualityServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(QualityServiceApplication.class, args);
  }
}
