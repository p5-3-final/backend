package com.vlm.quality.golden.infra.persistence;

import com.vlm.quality.golden.domain.DatasetItem;
import com.vlm.quality.golden.domain.DatasetItemRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class JdbcDatasetItemRepository implements DatasetItemRepository {

  // 한 번에 보내는 행 수. 영상 1,000개 가져오기도 몇 번의 왕복으로 끝난다.
  private static final int BATCH = 500;

  private final NamedParameterJdbcTemplate jdbc;

  @Override
  public void upsert(long goldenSetRowId, List<DatasetItem> items, Instant now) {
    Timestamp at = Timestamp.from(now);
    for (int from = 0; from < items.size(); from += BATCH) {
      List<DatasetItem> batch = items.subList(from, Math.min(items.size(), from + BATCH));
      jdbc.batchUpdate(
          "INSERT INTO golden_dataset_items (item_id, golden_set_id, external_id, source_id,"
              + " import_id, labels, created_at, updated_at) VALUES (:itemId, :set, :externalId,"
              + " :sourceId, :importId, CAST(:labels AS JSONB), :now, :now)"
              + " ON CONFLICT (item_id) DO UPDATE SET golden_set_id = EXCLUDED.golden_set_id,"
              + " source_id = EXCLUDED.source_id, import_id = EXCLUDED.import_id,"
              + " labels = EXCLUDED.labels, updated_at = EXCLUDED.updated_at",
          batch.stream()
              .map(
                  item ->
                      (SqlParameterSource)
                          new MapSqlParameterSource()
                              .addValue("itemId", item.itemId())
                              .addValue("set", goldenSetRowId)
                              .addValue("externalId", item.externalId())
                              .addValue("sourceId", item.sourceId())
                              .addValue("importId", item.importId())
                              .addValue("labels", item.labelsJson())
                              .addValue("now", at))
              .toArray(SqlParameterSource[]::new));
    }
  }

  @Override
  public List<DatasetItem> forFreeze(long goldenSetRowId) {
    return jdbc.query(
        "SELECT item_id, external_id, source_id, import_id, labels::text AS labels"
            + " FROM golden_dataset_items WHERE golden_set_id = :set ORDER BY id",
        new MapSqlParameterSource("set", goldenSetRowId),
        (rs, row) ->
            new DatasetItem(
                rs.getObject("item_id", UUID.class),
                rs.getString("external_id"),
                rs.getObject("source_id", UUID.class),
                rs.getObject("import_id", UUID.class),
                rs.getString("labels")));
  }
}
