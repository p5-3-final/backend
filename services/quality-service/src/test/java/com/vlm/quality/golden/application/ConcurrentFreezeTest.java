package com.vlm.quality.golden.application;

import static com.vlm.quality.support.QualityApi.operator;
import static org.assertj.core.api.Assertions.assertThat;

import com.vlm.quality.support.GoldenFixtures;
import com.vlm.quality.support.IntegrationTest;
import com.vlm.quality.support.ReviewFixtures;
import com.vlm.quality.support.StorageFixtures;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class ConcurrentFreezeTest {

  @Autowired MockMvc mvc;
  @Autowired GoldenFixtures golden;
  @Autowired ReviewFixtures reviews;

  @Test
  @DisplayName("[SPEC-029 R-15] 같은 Golden Set 확정 2건을 동시에 보내면 201 1건, 409 1건이다")
  void onlyOneFreezeWins() throws Exception {
    UUID setId = golden.create("race-set");
    for (int i = 0; i < 5; i++) {
      UUID source = UUID.randomUUID();
      golden.addEntry(
          setId,
          reviews.labeled(
              source, List.of(StorageFixtures.putSourceFrame(source, 0)), ReviewFixtures.CORRECT));
    }
    CountDownLatch start = new CountDownLatch(1);
    List<Future<Integer>> results = new ArrayList<>();

    try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
      for (int i = 0; i < 2; i++) {
        results.add(
            pool.submit(
                () -> {
                  start.await();
                  return mvc.perform(
                          operator(
                              HttpMethod.POST,
                              UUID.randomUUID(),
                              "/api/v1/quality/golden-sets/{setId}/versions",
                              setId))
                      .andReturn()
                      .getResponse()
                      .getStatus();
                }));
      }
      start.countDown();
      List<Integer> statuses = new ArrayList<>();
      for (Future<Integer> result : results) {
        statuses.add(result.get());
      }

      assertThat(statuses).containsExactlyInAnyOrder(201, 409);
    }
  }
}
