package com.vlm.quality.review.application;

import static com.vlm.quality.support.QualityApi.operator;
import static org.assertj.core.api.Assertions.assertThat;

import com.vlm.quality.support.IntegrationTest;
import com.vlm.quality.support.ReviewFixtures;
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
class ConcurrentAssignTest {

  private static final int REQUESTS = 10;

  @Autowired MockMvc mvc;
  @Autowired ReviewFixtures fixtures;

  @Test
  @DisplayName("[SPEC-029 NFR-1] 같은 항목에 맡기 10건을 동시에 보내면 200은 1건, 409는 9건이다")
  void onlyOneAssignWins() throws Exception {
    UUID itemId = fixtures.reviewRequired(UUID.randomUUID());
    CountDownLatch start = new CountDownLatch(1);
    List<Future<Integer>> results = new ArrayList<>();

    try (ExecutorService pool = Executors.newFixedThreadPool(REQUESTS)) {
      for (int i = 0; i < REQUESTS; i++) {
        results.add(
            pool.submit(
                () -> {
                  start.await();
                  return mvc.perform(
                          operator(
                              HttpMethod.POST,
                              UUID.randomUUID(),
                              "/api/v1/quality/review-items/{id}/assignment",
                              itemId))
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

      assertThat(statuses).filteredOn(s -> s == 200).hasSize(1);
      assertThat(statuses).filteredOn(s -> s == 409).hasSize(REQUESTS - 1);
    }
  }
}
