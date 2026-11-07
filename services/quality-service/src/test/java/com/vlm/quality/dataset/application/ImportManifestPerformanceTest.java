package com.vlm.quality.dataset.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vlm.quality.dataset.application.ImportManifestReader.Valid;
import com.vlm.quality.support.DatasetFixtures;
import com.vlm.quality.support.IntegrationTest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class ImportManifestPerformanceTest {

  private static final int VIDEOS = 1_000;
  private static final int LABELS_PER_VIDEO = 100;

  @Autowired ImportManifestReader manifests;

  @Test
  @DisplayName("[SPEC-030 NFR-1] 영상 1,000개·라벨 100,000개 매니페스트를 10초 안에 검증한다")
  void validatesLargeManifest() {
    byte[] manifest = largeManifest();
    // 스키마 검증기 첫 로드(JIT·스키마 컴파일)는 요청마다 반복되지 않으므로 뺀다.
    manifests.read(
        DatasetFixtures.manifest("warm-up", "clip-0").toString().getBytes(StandardCharsets.UTF_8));

    long started = System.nanoTime();
    ImportManifestReader.Result result = manifests.read(manifest);
    Duration took = Duration.ofNanos(System.nanoTime() - started);

    assertThat(result).isInstanceOf(Valid.class);
    Valid valid = (Valid) result;
    assertThat(valid.videos()).hasSize(VIDEOS);
    assertThat(valid.labelCount()).isEqualTo(VIDEOS * LABELS_PER_VIDEO);
    assertThat(took).isLessThanOrEqualTo(Duration.ofSeconds(10));
  }

  private static byte[] largeManifest() {
    String[] ids = new String[VIDEOS];
    for (int i = 0; i < VIDEOS; i++) {
      ids[i] = "clip-" + i;
    }
    ObjectNode manifest = DatasetFixtures.manifest("large", ids);
    for (var video : manifest.get("videos")) {
      ArrayNode labels = (ArrayNode) video.get("labels");
      ObjectNode interval = (ObjectNode) labels.get(0);
      labels.removeAll();
      for (int i = 0; i < LABELS_PER_VIDEO; i++) {
        ObjectNode label = interval.deepCopy();
        label.put("startMs", i * 1000L);
        label.put("endMs", i * 1000L + 500);
        labels.add(label);
      }
    }
    return manifest.toString().getBytes(StandardCharsets.UTF_8);
  }
}
