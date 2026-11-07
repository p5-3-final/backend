package com.vlm.quality.dataset.application;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 가져오기 설정(SPEC-030).
 *
 * @param supportedTaxonomyVersions 받을 매니페스트 taxonomyVersion(R-2)
 * @param heartbeatTimeout 작업 표시가 이보다 오래되면 Pod가 죽은 것으로 보고 다른 작업자가 이어받는다
 */
@ConfigurationProperties("vlm.quality.dataset")
public record DatasetProperties(List<String> supportedTaxonomyVersions, Duration heartbeatTimeout) {

  public DatasetProperties {
    supportedTaxonomyVersions = List.copyOf(supportedTaxonomyVersions);
  }
}
