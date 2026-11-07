package com.vlm.quality.golden.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vlm.common.error.BusinessException;
import com.vlm.quality.golden.domain.GoldenFrames;
import com.vlm.quality.golden.domain.GoldenSet;
import com.vlm.quality.golden.domain.GoldenSetRepository;
import com.vlm.quality.shared.domain.GoldenStorage;
import com.vlm.quality.shared.domain.QualityErrorCode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 다른 기능(export)에 Golden Set 확정 버전의 영상 목록을 준다(SPEC-030 R-14). */
@Service
@RequiredArgsConstructor
public class GoldenSourceService {

  private final GoldenSetRepository sets;
  private final GoldenStorage storage;
  private final ObjectMapper json;

  /**
   * 최신 확정 버전 매니페스트의 영상. 확정 버전이 없으면 영상이 없다.
   *
   * <p>초안은 확정 뒤에도 바뀌므로 DB가 아니라 확정 매니페스트(ADR-0011 불변)를 읽는다.
   *
   * @throws BusinessException 없으면 {@link QualityErrorCode#GOLDEN_SET_NOT_FOUND}
   */
  public GoldenSources latestSources(UUID setId) {
    GoldenSet set =
        sets.findBySetId(setId)
            .orElseThrow(() -> new BusinessException(QualityErrorCode.GOLDEN_SET_NOT_FOUND));
    Integer version = set.latestVersion();
    if (version == null) {
      return new GoldenSources(set.split(), List.of());
    }
    JsonNode manifest = parse(storage.read(GoldenFrames.manifestKey(setId, version)));
    Set<UUID> sources = new LinkedHashSet<>();
    for (String field : List.of("entries", "datasetItems")) {
      for (JsonNode item : manifest.path(field)) {
        sources.add(UUID.fromString(item.path("sourceId").asText()));
      }
    }
    return new GoldenSources(set.split(), List.copyOf(sources));
  }

  /**
   * Golden Set이 있는지 확인한다.
   *
   * @throws BusinessException 없으면 {@link QualityErrorCode#GOLDEN_SET_NOT_FOUND}
   */
  public void requireExists(UUID setId) {
    if (sets.findBySetId(setId).isEmpty()) {
      throw new BusinessException(QualityErrorCode.GOLDEN_SET_NOT_FOUND);
    }
  }

  private JsonNode parse(byte[] manifest) {
    try {
      return json.readTree(manifest);
    } catch (IOException e) {
      throw new UncheckedIOException("frozen golden manifest is not JSON", e);
    }
  }
}
