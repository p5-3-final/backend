package com.vlm.gateway.support;

import org.testcontainers.containers.GenericContainer;

/** 게이트웨이 테스트용 Redis. 테스트 컨텍스트가 여러 개여도 컨테이너 하나를 같이 쓴다. */
public final class TestRedis {

  static final int PORT = 6379;

  private static final GenericContainer<?> CONTAINER =
      new GenericContainer<>("redis:7.4-alpine").withExposedPorts(PORT);

  private TestRedis() {}

  public static GenericContainer<?> container() {
    if (!CONTAINER.isRunning()) {
      CONTAINER.start();
    }
    return CONTAINER;
  }
}
