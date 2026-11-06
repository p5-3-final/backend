package com.vlm.quality.golden.infra.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vlm.quality.golden.domain.FreezeEntry;
import com.vlm.quality.golden.domain.GoldenDraftEntry;
import com.vlm.quality.golden.domain.GoldenDraftRepository;
import com.vlm.quality.shared.domain.ReviewLabel;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 초안 항목 저장소. 응답·매니페스트에 필요한 검토 항목 값(영상·이벤트·판정 결과·근거 프레임)은 같은 DB의 review_items와 조인해 읽는다.
 *
 * <p>검토 항목의 이 값들은 만든 뒤 바뀌지 않으므로 초안에는 라벨만 복사해 둔다(R-12).
 */
@Repository
@RequiredArgsConstructor
class JdbcGoldenDraftRepository implements GoldenDraftRepository {

  private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {};

  private final NamedParameterJdbcTemplate jdbc;
  private final ObjectMapper json;

  @Override
  public boolean insertIfAbsent(
      UUID entryId, long goldenSetRowId, long reviewRowId, ReviewLabel label, Instant addedAt) {
    return jdbc.update(
            "INSERT INTO golden_draft_entries (entry_id, golden_set_id, review_item_id, label,"
                + " added_at, created_at, updated_at) VALUES (:entryId, :set, :review,"
                + " CAST(:label AS JSONB), :addedAt, :addedAt, :addedAt)"
                + " ON CONFLICT (golden_set_id, review_item_id) DO NOTHING",
            new MapSqlParameterSource()
                .addValue("entryId", entryId)
                .addValue("set", goldenSetRowId)
                .addValue("review", reviewRowId)
                .addValue("label", write(label))
                .addValue("addedAt", Timestamp.from(addedAt)))
        == 1;
  }

  @Override
  public List<GoldenDraftEntry> list(long goldenSetRowId, int page, int size) {
    return jdbc.query(
        "SELECT d.entry_id, g.set_id, r.item_id, r.source_id, r.event_id, d.label, d.added_at"
            + " FROM golden_draft_entries d JOIN golden_sets g ON g.id = d.golden_set_id"
            + " JOIN review_items r ON r.id = d.review_item_id WHERE d.golden_set_id = :set"
            + " ORDER BY d.added_at, d.id LIMIT :limit OFFSET :offset",
        new MapSqlParameterSource()
            .addValue("set", goldenSetRowId)
            .addValue("limit", size)
            .addValue("offset", (long) page * size),
        (rs, row) ->
            new GoldenDraftEntry(
                rs.getObject("entry_id", UUID.class),
                rs.getObject("set_id", UUID.class),
                rs.getObject("item_id", UUID.class),
                rs.getObject("source_id", UUID.class),
                rs.getObject("event_id", UUID.class),
                read(rs.getString("label"), new TypeReference<ReviewLabel>() {}),
                rs.getTimestamp("added_at").toInstant()));
  }

  @Override
  public long count(long goldenSetRowId) {
    Long count =
        jdbc.queryForObject(
            "SELECT count(*) FROM golden_draft_entries WHERE golden_set_id = :set",
            new MapSqlParameterSource("set", goldenSetRowId),
            Long.class);
    return count == null ? 0 : count;
  }

  @Override
  public int remove(long goldenSetRowId, UUID entryId) {
    return jdbc.update(
        "DELETE FROM golden_draft_entries WHERE golden_set_id = :set AND entry_id = :entryId",
        new MapSqlParameterSource().addValue("set", goldenSetRowId).addValue("entryId", entryId));
  }

  @Override
  public List<FreezeEntry> forFreeze(long goldenSetRowId) {
    return jdbc.query(
        "SELECT d.entry_id, r.source_id, r.event_id, r.event_type, r.judgment_method, r.start_ms,"
            + " r.end_ms, d.label, r.analysis, r.evidence_frame_keys, r.taxonomy_version"
            + " FROM golden_draft_entries d JOIN review_items r ON r.id = d.review_item_id"
            + " WHERE d.golden_set_id = :set ORDER BY d.added_at, d.id",
        new MapSqlParameterSource("set", goldenSetRowId),
        (rs, row) ->
            new FreezeEntry(
                rs.getObject("entry_id", UUID.class),
                rs.getObject("source_id", UUID.class),
                rs.getObject("event_id", UUID.class),
                rs.getString("event_type"),
                rs.getString("judgment_method"),
                rs.getLong("start_ms"),
                rs.getLong("end_ms"),
                read(rs.getString("label"), new TypeReference<ReviewLabel>() {}),
                rs.getString("analysis"),
                read(rs.getString("evidence_frame_keys"), STRINGS),
                rs.getString("taxonomy_version")));
  }

  private String write(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("cannot write JSON", e);
    }
  }

  private <T> T read(String value, TypeReference<T> type) {
    try {
      return json.readValue(value, type);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("cannot read JSON column", e);
    }
  }
}
