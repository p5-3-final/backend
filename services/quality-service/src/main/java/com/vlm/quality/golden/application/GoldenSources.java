package com.vlm.quality.golden.application;

import java.util.List;
import java.util.UUID;

/**
 * Golden Set 최신 확정 버전에 든 영상.
 *
 * @param split 세트 분할(dev·eval)
 * @param sourceIds 중복 없이, 매니페스트에 처음 나온 순서
 */
public record GoldenSources(String split, List<UUID> sourceIds) {

  public GoldenSources {
    sourceIds = List.copyOf(sourceIds);
  }
}
