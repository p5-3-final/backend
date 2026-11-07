package com.vlm.media.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 내부 경로는 호출자 허용 목록을 먼저 본다(SPEC-030 R-6). */
@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
class MediaWebConfig implements WebMvcConfigurer {

  private final InternalCallerProperties internalCallers;

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry
        .addInterceptor(new InternalCallerInterceptor(internalCallers))
        .addPathPatterns("/internal/**");
  }
}
