package com.vlm.media.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SessionOrderTest {

  private static final Instant T0 = Instant.parse("2026-11-06T09:00:00Z");
  private static final UUID SESSION = UUID.randomUUID();

  @Test
  @DisplayName("[SPEC-017 R-7] 역할 순서, 같은 역할은 완료 시각 순, 미완료는 역할 끝에 만든 시각 순으로 놓는다")
  void ordersByRoleThenUploadThenCreation() {
    Video afterDone = video("after-done.mp4", SessionRole.AFTER, 0, 1);
    Video beforeLate = video("before-late.mp4", SessionRole.BEFORE, 1, 30);
    Video beforePending = video("before-pending.mp4", SessionRole.BEFORE, 2, null);
    Video beforeEarly = video("before-early.mp4", SessionRole.BEFORE, 3, 10);
    Video duringPendingOld = video("during-pending-old.mp4", SessionRole.DURING, 4, null);
    Video duringPendingNew = video("during-pending-new.mp4", SessionRole.DURING, 5, null);
    List<Video> videos =
        new ArrayList<>(
            List.of(
                afterDone,
                beforeLate,
                beforePending,
                beforeEarly,
                duringPendingNew,
                duringPendingOld));

    videos.sort(SessionOrder.COMPARATOR);

    assertThat(videos)
        .extracting(Video::getFileName)
        .containsExactly(
            "before-early.mp4",
            "before-late.mp4",
            "before-pending.mp4",
            "during-pending-old.mp4",
            "during-pending-new.mp4",
            "after-done.mp4");
  }

  // createdMinute: 만든 시각(분), uploadedMinute: 업로드 완료 시각(분). null이면 미완료
  private static Video video(
      String name, SessionRole role, int createdMinute, @Nullable Integer uploadedMinute) {
    Video video =
        Video.startUpload(
            UUID.randomUUID(),
            name,
            1,
            "video/mp4",
            T0.plus(Duration.ofMinutes(createdMinute)),
            Duration.ofHours(24));
    video.joinSession(SESSION, role);
    if (uploadedMinute != null) {
      video.completeUpload(T0.plus(Duration.ofMinutes(uploadedMinute)));
    }
    return video;
  }
}
