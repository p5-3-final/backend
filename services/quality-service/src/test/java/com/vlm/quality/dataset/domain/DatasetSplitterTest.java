package com.vlm.quality.dataset.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DatasetSplitterTest {

  @Test
  @DisplayName("[SPEC-030 R-3] sha256(splitSeed:externalId) 앞 8바이트를 [0, 1)로 바꾼 값이다")
  void hashesSeedAndExternalId() {
    // python: int.from_bytes(sha256(b"7:clip-0001").digest()[:8], "big") / 2**64
    assertThat(DatasetSplitter.position(7, "clip-0001"))
        .isCloseTo(0.10432171225865393, within(1e-12));
    assertThat(DatasetSplitter.position(7, "clip-0002"))
        .isCloseTo(0.5408450111987251, within(1e-12));
    assertThat(DatasetSplitter.position(0, "a")).isCloseTo(0.6170009362330673, within(1e-12));
  }

  @Test
  @DisplayName("[SPEC-030 R-3] 값이 evalRatio보다 작으면 eval, 아니면 dev다")
  void splitsByRatio() {
    assertThat(DatasetSplitter.split(7, "clip-0001", 0.2)).isEqualTo(Split.EVAL);
    assertThat(DatasetSplitter.split(7, "clip-0002", 0.2)).isEqualTo(Split.DEV);
    assertThat(DatasetSplitter.split(7, "clip-0002", 0.6)).isEqualTo(Split.EVAL);
  }

  @Test
  @DisplayName("[SPEC-030 R-3] evalRatio 0이면 모두 dev, 1이면 모두 eval이다")
  void handlesRatioBounds() {
    for (int i = 0; i < 200; i++) {
      String externalId = "clip-" + i;
      assertThat(DatasetSplitter.split(3, externalId, 0.0)).isEqualTo(Split.DEV);
      assertThat(DatasetSplitter.split(3, externalId, 1.0)).isEqualTo(Split.EVAL);
    }
  }
}
