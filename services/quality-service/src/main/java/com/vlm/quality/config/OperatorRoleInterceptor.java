package com.vlm.quality.config;

import com.vlm.common.error.BusinessException;
import com.vlm.common.web.UserHeaders;
import com.vlm.quality.shared.domain.QualityErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import org.jspecify.annotations.Nullable;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 공개 경로({@code /api/v1/quality/**})는 OPERATOR만 쓴다(SPEC-029 R-4).
 *
 * <p>역할은 api-gateway가 토큰에서 옮긴 {@code X-User-Roles}로 본다. 사용자 헤더가 없으면 게이트웨이를 거치지 않은 요청이라 401이다.
 */
class OperatorRoleInterceptor implements HandlerInterceptor {

  private static final String OPERATOR = "OPERATOR";

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    UserHeaders.userId(request.getHeader(UserHeaders.USER_ID));
    if (!isOperator(request.getHeader(UserHeaders.USER_ROLES))) {
      throw new BusinessException(QualityErrorCode.FORBIDDEN);
    }
    return true;
  }

  private static boolean isOperator(@Nullable String roles) {
    return roles != null
        && Arrays.stream(roles.split(",")).map(String::trim).anyMatch(OPERATOR::equals);
  }
}
