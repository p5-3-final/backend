package com.vlm.media.config;

import com.vlm.common.error.BusinessException;
import com.vlm.media.shared.domain.MediaErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 내부 경로는 허용한 서비스만 부른다(SPEC-030 R-6).
 *
 * <p>api-gateway가 {@code /internal/**}을 라우팅하지 않고 NetworkPolicy가 클러스터 밖을 막는다. 이 검사는 클러스터 안의 다른 워크로드가
 * 잘못 부르는 것을 막는 마지막 단계다.
 */
@RequiredArgsConstructor
class InternalCallerInterceptor implements HandlerInterceptor {

  static final String CALLER = "X-Caller";

  private final InternalCallerProperties properties;

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    String caller = request.getHeader(CALLER);
    if (caller == null || !properties.allowedCallers().contains(caller)) {
      throw new BusinessException(MediaErrorCode.CALLER_NOT_ALLOWED);
    }
    return true;
  }
}
