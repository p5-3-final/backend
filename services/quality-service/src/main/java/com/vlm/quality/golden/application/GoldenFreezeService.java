package com.vlm.quality.golden.application;

import com.vlm.common.error.BusinessException;
import com.vlm.quality.golden.domain.FreezeEntry;
import com.vlm.quality.golden.domain.GoldenDraftRepository;
import com.vlm.quality.golden.domain.GoldenFrames;
import com.vlm.quality.golden.domain.GoldenSet;
import com.vlm.quality.golden.domain.GoldenSetRepository;
import com.vlm.quality.golden.domain.GoldenSetVersion;
import com.vlm.quality.golden.domain.GoldenVersionRepository;
import com.vlm.quality.shared.domain.GoldenStorage;
import com.vlm.quality.shared.domain.QualityErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 초안을 새 버전으로 확정한다(SPEC-029 R-14, R-15).
 *
 * <p>순서: 확정 진행 표시(짧은 트랜잭션) → 프레임 복사 → 매니페스트 쓰기 → 버전 행·최신 버전·표시 해제(한 트랜잭션). 저장소 복사는 길어 트랜잭션 밖에서 한다.
 * 실패하면 표시를 지워 버전 행이 없으니, 다음 확정이 같은 번호로 같은 경로에 다시 쓴다(Golden Set 버킷 Versioning이 이전 쓰기를 남긴다).
 */
@Service
@RequiredArgsConstructor
public class GoldenFreezeService {

  // 프레임 서버 쪽 복사 동시 수(plan 7절). 확정이 gateway 응답 제한(10s) 안에 끝나도록 병렬로 복사한다.
  private static final int COPY_CONCURRENCY = 8;

  private static final Logger log = LoggerFactory.getLogger(GoldenFreezeService.class);

  private final GoldenSetService setService;
  private final GoldenSetRepository sets;
  private final GoldenDraftRepository drafts;
  private final GoldenVersionRepository versions;
  private final GoldenStorage storage;
  private final GoldenManifestWriter manifests;
  private final GoldenProperties properties;
  private final TransactionTemplate transactions;
  private final Clock clock;

  public GoldenSetVersion freeze(UUID setId, UUID operator) {
    GoldenSet set = setService.get(setId);
    Instant started = clock.instant();
    if (sets.startFreeze(setId, started, started.minus(properties.freezeLockTimeout())) == 0) {
      throw new BusinessException(QualityErrorCode.GOLDEN_FREEZE_IN_PROGRESS);
    }
    try {
      return freezeMarked(set, operator);
    } catch (RuntimeException e) {
      sets.clearFreeze(setId, clock.instant());
      throw e;
    }
  }

  private GoldenSetVersion freezeMarked(GoldenSet set, UUID operator) {
    List<FreezeEntry> entries = drafts.forFreeze(set.id());
    if (entries.isEmpty()) {
      throw new BusinessException(QualityErrorCode.GOLDEN_EMPTY);
    }
    // 표시를 잡은 동안에는 다른 확정이 없으므로 최신 버전을 다시 읽어도 바뀌지 않는다.
    Integer latest = setService.get(set.setId()).latestVersion();
    int version = latest == null ? 1 : latest + 1;
    Map<UUID, List<String>> frameKeys = copyFrames(set.setId(), version, entries);
    Instant frozenAt = clock.instant();
    String manifestKey = GoldenFrames.manifestKey(set.setId(), version);
    storage.putJson(manifestKey, manifests.write(set, version, frozenAt, entries, frameKeys));
    var frozen =
        new GoldenSetVersion(set.setId(), version, entries.size(), manifestKey, frozenAt, operator);
    transactions.executeWithoutResult(
        status -> {
          versions.insert(set.id(), frozen);
          sets.finishFreeze(set.setId(), version, frozenAt);
        });
    log.info(
        "Froze golden set setId={} version={} entries={}", set.setId(), version, entries.size());
    return frozen;
  }

  private Map<UUID, List<String>> copyFrames(UUID setId, int version, List<FreezeEntry> entries) {
    Map<UUID, List<String>> copied = new HashMap<>();
    List<Future<?>> copies = new ArrayList<>();
    try (ExecutorService pool = Executors.newFixedThreadPool(COPY_CONCURRENCY)) {
      for (FreezeEntry entry : entries) {
        List<String> targets = new ArrayList<>();
        for (int i = 0; i < entry.evidenceFrameKeys().size(); i++) {
          String source = entry.evidenceFrameKeys().get(i);
          String target = GoldenFrames.frameKey(setId, version, entry.entryId(), i, source);
          targets.add(target);
          copies.add(pool.submit(() -> storage.copyFromSource(source, target)));
        }
        copied.put(entry.entryId(), List.copyOf(targets));
      }
      for (Future<?> copy : copies) {
        await(copy);
      }
    }
    return copied;
  }

  private static void await(Future<?> copy) {
    try {
      copy.get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("frame copy interrupted", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("frame copy failed", e.getCause());
    }
  }
}
