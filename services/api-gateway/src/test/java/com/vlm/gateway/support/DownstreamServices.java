package com.vlm.gateway.support;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;

/** 게이트웨이 테스트에서 하위 서비스를 대신하는 WireMock 서버. 모든 서비스 주소를 이 서버 하나로 향하게 한다. */
public final class DownstreamServices {

  private static final WireMockServer SERVER =
      new WireMockServer(WireMockConfiguration.options().dynamicPort());

  private DownstreamServices() {}

  public static WireMockServer server() {
    if (!SERVER.isRunning()) {
      SERVER.start();
    }
    return SERVER;
  }

  public static void register(DynamicPropertyRegistry registry) {
    String baseUrl = server().baseUrl();
    for (String name :
        new String[] {
          "AUTH_SERVICE_URI",
          "MEDIA_SERVICE_URI",
          "PIPELINE_SERVICE_URI",
          "ANALYSIS_SERVICE_URI",
          "SEARCH_SERVICE_URI",
          "NOTIFICATION_SERVICE_URI",
          "QUALITY_SERVICE_URI"
        }) {
      registry.add(name, () -> baseUrl);
    }
  }
}
