package com.vlm.quality.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 공개 경로에 운영자 역할 검사를 건다. 내부 경로는 내부 호출자 검사가 따로 본다(R-17). */
@Configuration(proxyBeanMethods = false)
class QualityWebConfig implements WebMvcConfigurer {

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(new OperatorRoleInterceptor()).addPathPatterns("/api/v1/quality/**");
  }
}
