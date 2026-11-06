package com.vlm.quality.support;

import java.util.UUID;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** 게이트웨이가 채우는 사용자 헤더를 붙인 운영자 요청. */
public final class QualityApi {

  private QualityApi() {}

  public static MockHttpServletRequestBuilder operator(
      HttpMethod method, UUID operatorId, String path, Object... vars) {
    return MockMvcRequestBuilders.request(method, path, vars)
        .header("X-User-Id", operatorId.toString())
        .header("X-User-Roles", "USER,OPERATOR");
  }
}
