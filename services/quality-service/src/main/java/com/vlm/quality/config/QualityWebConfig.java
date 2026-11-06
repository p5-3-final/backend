package com.vlm.quality.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 공개 경로는 운영자 역할(R-4), 내부 경로는 호출자 허용 목록(R-17)을 먼저 본다. */
@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
class QualityWebConfig implements WebMvcConfigurer {

  private final InternalCallerProperties internalCallers;

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(new OperatorRoleInterceptor()).addPathPatterns("/api/v1/quality/**");
    registry
        .addInterceptor(new InternalCallerInterceptor(internalCallers))
        .addPathPatterns("/internal/v1/quality/**");
  }
}
