package com.vlm.quality.dataset.infra.client;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * media-service 내부 호출 설정(SPEC-030 R-4).
 *
 * @param baseUrl media-service 클러스터 내부 주소
 * @param connectTimeout 연결 제한 시간
 * @param readTimeout 응답 제한 시간. media가 서버 쪽 복사를 끝낼 시간이다. 이 값으로 {@code X-Request-Deadline}도 정한다
 * @param maxAttempts 처음 호출을 포함한 최대 시도 수(재시도 = 이 값 − 1)
 * @param retryWait 첫 재시도 대기. 다음은 두 배씩 늘린다
 * @param circuitMinimumCalls 서킷 실패율을 계산하기 시작하는 최소 호출 수
 * @param circuitOpenDuration 서킷이 열린 뒤 다시 시도해 보기까지 기다리는 시간
 */
@ConfigurationProperties("vlm.quality.media")
public record MediaClientProperties(
    String baseUrl,
    Duration connectTimeout,
    Duration readTimeout,
    int maxAttempts,
    Duration retryWait,
    int circuitMinimumCalls,
    Duration circuitOpenDuration) {}
