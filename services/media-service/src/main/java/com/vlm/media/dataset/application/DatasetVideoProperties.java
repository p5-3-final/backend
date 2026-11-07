package com.vlm.media.dataset.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 데이터셋 영상 등록 설정.
 *
 * @param defaultTimeout 호출자가 마감(X-Request-Deadline)을 주지 않았을 때 등록 한 건의 제한 시간. 서버 쪽 복사 시간이 대부분이다
 */
@ConfigurationProperties("vlm.media.dataset")
public record DatasetVideoProperties(Duration defaultTimeout) {}
