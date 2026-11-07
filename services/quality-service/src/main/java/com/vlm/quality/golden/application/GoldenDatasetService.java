package com.vlm.quality.golden.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vlm.common.error.BusinessException;
import com.vlm.common.id.NameBasedUuid;
import com.vlm.quality.golden.domain.DatasetItem;
import com.vlm.quality.golden.domain.DatasetItemRepository;
import com.vlm.quality.golden.domain.GoldenSet;
import com.vlm.quality.golden.domain.GoldenSetRepository;
import com.vlm.quality.shared.domain.QualityErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 가져온 데이터셋을 {@code <이름>-dev}·{@code <이름>-eval} Golden Set에 넣고 확정한다(SPEC-030 R-8, R-10).
 *
 * <p>세트 찾기·만들기, 출처 기록, 항목 upsert는 한 트랜잭션이고, 확정은 SPEC-029 확정 절차(진행 표시 → 매니페스트 → 버전 행)를 분할마다 따로 탄다.
 * 확정이 실패해도 항목은 남으므로 다시 가져오거나 운영자가 확정하면 이어진다.
 */
@Service
@RequiredArgsConstructor
public class GoldenDatasetService {

  /** 데이터셋 항목 ID 네임스페이스. 바꾸면 같은 영상이 다른 항목이 되므로 바꾸지 않는다. */
  static final UUID DATASET_ITEM_NAMESPACE =
      UUID.fromString("c41f7a2e-93b6-5d08-a1e4-7f2c5b9d3e60");

  private final GoldenSetRepository sets;
  private final DatasetItemRepository items;
  private final GoldenFreezeService freezes;
  private final TransactionTemplate transactions;
  private final ObjectMapper json;
  private final Clock clock;

  /** 데이터셋 이름·externalId로 정한 항목 ID(계약 datasetItems.itemId). */
  public static UUID itemIdOf(String datasetName, String externalId) {
    return NameBasedUuid.v5(DATASET_ITEM_NAMESPACE, datasetName + ":" + externalId);
  }

  /**
   * 항목을 넣고, 이번 항목이 있는 분할만 새 버전으로 확정한다.
   *
   * @throws BusinessException 같은 이름의 검토 라벨 세트가 있으면 {@link QualityErrorCode#GOLDEN_ORIGIN_MISMATCH}
   */
  public DatasetFreeze importAndFreeze(DatasetBatch batch) {
    Map<Split, GoldenSet> bySplit =
        transactions.execute(
            status -> {
              Instant now = clock.instant();
              Map<Split, GoldenSet> found = new EnumMap<>(Split.class);
              String origin = write(batch.origin());
              for (Split split : Split.values()) {
                GoldenSet set = ensureSet(batch.datasetName() + "-" + split.wire(), split, now);
                sets.setDatasetOrigin(set.id(), origin, now);
                items.upsert(set.id(), itemsOf(batch, split), now);
                found.put(split, set);
              }
              return found;
            });
    if (bySplit == null) {
      throw new IllegalStateException("dataset golden sets were not written");
    }
    GoldenSet dev = Objects.requireNonNull(bySplit.get(Split.DEV));
    GoldenSet eval = Objects.requireNonNull(bySplit.get(Split.EVAL));
    return new DatasetFreeze(
        dev.setId(),
        eval.setId(),
        freezeIfAny(dev, batch, Split.DEV),
        freezeIfAny(eval, batch, Split.EVAL));
  }

  private GoldenSet ensureSet(String name, Split split, Instant now) {
    sets.insertDatasetSetIfNameFree(UUID.randomUUID(), name, split.wire(), now);
    GoldenSet set = sets.findByName(name).orElseThrow(IllegalStateException::new);
    // 같은 이름의 검토 라벨 세트에 데이터셋 항목을 섞지 않는다(R-10).
    if (!set.isDataset() || !set.split().equals(split.wire())) {
      throw new BusinessException(QualityErrorCode.GOLDEN_ORIGIN_MISMATCH);
    }
    return set;
  }

  private @Nullable Integer freezeIfAny(GoldenSet set, DatasetBatch batch, Split split) {
    if (itemsOf(batch, split).isEmpty()) {
      return null;
    }
    return freezes.freeze(set.setId(), batch.operator()).version();
  }

  private static List<DatasetItem> itemsOf(DatasetBatch batch, Split split) {
    return batch.entries().stream()
        .filter(entry -> entry.split().equals(split.wire()))
        .map(
            entry ->
                new DatasetItem(
                    itemIdOf(batch.datasetName(), entry.externalId()),
                    entry.externalId(),
                    entry.sourceId(),
                    batch.importId(),
                    entry.labelsJson()))
        .toList();
  }

  private String write(DatasetBatch.DatasetOrigin origin) {
    try {
      return json.writeValueAsString(origin);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("cannot write dataset origin", e);
    }
  }

  private enum Split {
    DEV,
    EVAL;

    String wire() {
      return name().toLowerCase(Locale.ROOT);
    }
  }
}
