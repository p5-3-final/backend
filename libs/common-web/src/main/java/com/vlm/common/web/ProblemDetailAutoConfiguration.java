package com.vlm.common.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * 서블릿 기반 서비스에 {@link ProblemDetailExceptionHandler}를 등록한다.
 *
 * <p>입력 검증 오류 코드는 {@code vlm.problem.validation-error-code}로 서비스마다 지정한다.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ProblemDetailAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  ProblemDetailExceptionHandler problemDetailExceptionHandler(
      @Value("${vlm.problem.validation-error-code:COMMON_INVALID_REQUEST}")
          String validationErrorCode) {
    return new ProblemDetailExceptionHandler(validationErrorCode);
  }
}
