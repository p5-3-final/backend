package com.vlm.quality.review.infra.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vlm.quality.review.domain.NewReviewItem;
import com.vlm.quality.review.domain.ReviewFilter;
import com.vlm.quality.review.domain.ReviewItem;
import com.vlm.quality.review.domain.ReviewItemPage;
import com.vlm.quality.review.domain.ReviewItemRepository;
import com.vlm.quality.review.domain.ReviewReason;
import com.vlm.quality.review.domain.ReviewStatus;
import com.vlm.quality.shared.domain.ReviewLabel;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class JdbcReviewItemRepository implements ReviewItemRepository {

  private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {};
  private static final String COLUMNS =
      "id, item_id, event_id, source_id, job_id, event_type, start_ms, end_ms, judgment_method,"
          + " taxonomy_version, reason, review_reasons, evidence_frame_keys, analysis, status,"
          + " assignee_id, assigned_at, label, labeled_at, created_at";
  // 목록 필터는 셋 다 선택이다. NULL 파라미터는 조건을 끈다.
  private static final String FILTER =
      " WHERE (CAST(:status AS VARCHAR) IS NULL OR status = :status)"
          + " AND (CAST(:reason AS VARCHAR) IS NULL OR reason = :reason)"
          + " AND (CAST(:eventType AS VARCHAR) IS NULL OR event_type = :eventType)";

  private final NamedParameterJdbcTemplate jdbc;
  private final ObjectMapper json;

  @Override
  public boolean insertIfAbsent(NewReviewItem item) {
    var params =
        new MapSqlParameterSource()
            .addValue("itemId", item.itemId())
            .addValue("eventId", item.eventId())
            .addValue("sourceId", item.sourceId())
            .addValue("jobId", item.jobId())
            .addValue("eventType", item.eventType())
            .addValue("startMs", item.startMs())
            .addValue("endMs", item.endMs())
            .addValue("judgmentMethod", item.judgmentMethod())
            .addValue("taxonomyVersion", item.taxonomyVersion())
            .addValue("reason", item.reason().wire())
            .addValue("reviewReasons", write(item.reviewReasons()))
            .addValue("evidenceFrameKeys", write(item.evidenceFrameKeys()))
            .addValue("analysis", item.analysisJson())
            .addValue("createdAt", Timestamp.from(item.createdAt()));
    // R-3: 같은 eventId는 내용이 달라도 처음 항목을 그대로 둔다.
    return jdbc.update(
            "INSERT INTO review_items (item_id, event_id, source_id, job_id, event_type, start_ms,"
                + " end_ms, judgment_method, taxonomy_version, reason, review_reasons,"
                + " evidence_frame_keys, analysis, status, created_at, updated_at)"
                + " VALUES (:itemId, :eventId, :sourceId, :jobId, :eventType, :startMs, :endMs,"
                + " :judgmentMethod, :taxonomyVersion, :reason, CAST(:reviewReasons AS JSONB),"
                + " CAST(:evidenceFrameKeys AS JSONB), CAST(:analysis AS JSONB), 'UNASSIGNED',"
                + " :createdAt, :createdAt)"
                + " ON CONFLICT (event_id) DO NOTHING",
            params)
        == 1;
  }

  @Override
  public Optional<ReviewItem> findByItemId(UUID itemId) {
    return jdbc
        .query(
            "SELECT " + COLUMNS + " FROM review_items WHERE item_id = :itemId",
            new MapSqlParameterSource("itemId", itemId),
            mapper())
        .stream()
        .findFirst();
  }

  @Override
  public ReviewItemPage list(ReviewFilter filter, int page, int size) {
    var params =
        new MapSqlParameterSource()
            .addValue("status", filter.status() == null ? null : filter.status().name())
            .addValue("reason", filter.reason() == null ? null : filter.reason().wire())
            .addValue("eventType", filter.eventType())
            .addValue("limit", size)
            .addValue("offset", (long) page * size);
    Long total =
        jdbc.queryForObject("SELECT count(*) FROM review_items" + FILTER, params, Long.class);
    List<ReviewItem> items =
        jdbc.query(
            "SELECT "
                + COLUMNS
                + " FROM review_items"
                + FILTER
                + " ORDER BY created_at, item_id LIMIT :limit OFFSET :offset",
            params,
            mapper());
    return new ReviewItemPage(items, total == null ? 0 : total);
  }

  @Override
  public int assignIfUnassigned(UUID itemId, UUID assignee, Instant now) {
    return jdbc.update(
        "UPDATE review_items SET status = 'ASSIGNED', assignee_id = :assignee, assigned_at = :now,"
            + " updated_at = :now WHERE item_id = :itemId AND status = 'UNASSIGNED'",
        transition(itemId, assignee, now));
  }

  @Override
  public int releaseIfAssignee(UUID itemId, UUID assignee, Instant now) {
    return jdbc.update(
        "UPDATE review_items SET status = 'UNASSIGNED', assignee_id = NULL, assigned_at = NULL,"
            + " updated_at = :now"
            + " WHERE item_id = :itemId AND status = 'ASSIGNED' AND assignee_id = :assignee",
        transition(itemId, assignee, now));
  }

  @Override
  public int labelIfAssignee(UUID itemId, UUID assignee, ReviewLabel label, Instant now) {
    return jdbc.update(
        "UPDATE review_items SET status = 'LABELED', label = CAST(:label AS JSONB),"
            + " labeled_at = :now, updated_at = :now"
            + " WHERE item_id = :itemId AND status IN ('ASSIGNED', 'LABELED')"
            + " AND assignee_id = :assignee",
        transition(itemId, assignee, now).addValue("label", write(label)));
  }

  @Override
  public int releaseAssignedBefore(Instant cutoff, Instant now) {
    return jdbc.update(
        "UPDATE review_items SET status = 'UNASSIGNED', assignee_id = NULL, assigned_at = NULL,"
            + " updated_at = :now WHERE status = 'ASSIGNED' AND assigned_at < :cutoff",
        new MapSqlParameterSource()
            .addValue("cutoff", Timestamp.from(cutoff))
            .addValue("now", Timestamp.from(now)));
  }

  @Override
  public int deleteBySource(UUID sourceId) {
    return jdbc.update(
        "DELETE FROM review_items WHERE source_id = :sourceId",
        new MapSqlParameterSource("sourceId", sourceId));
  }

  private static MapSqlParameterSource transition(UUID itemId, UUID assignee, Instant now) {
    return new MapSqlParameterSource()
        .addValue("itemId", itemId)
        .addValue("assignee", assignee)
        .addValue("now", Timestamp.from(now));
  }

  private RowMapper<ReviewItem> mapper() {
    return (rs, row) ->
        new ReviewItem(
            rs.getLong("id"),
            rs.getObject("item_id", UUID.class),
            rs.getObject("event_id", UUID.class),
            rs.getObject("source_id", UUID.class),
            rs.getObject("job_id", UUID.class),
            rs.getString("event_type"),
            rs.getLong("start_ms"),
            rs.getLong("end_ms"),
            rs.getString("judgment_method"),
            rs.getString("taxonomy_version"),
            ReviewReason.fromWire(rs.getString("reason"))
                .orElseThrow(() -> new IllegalStateException("unknown reason in review_items")),
            read(rs.getString("review_reasons"), STRINGS),
            read(rs.getString("evidence_frame_keys"), STRINGS),
            rs.getString("analysis"),
            ReviewStatus.valueOf(rs.getString("status")),
            rs.getObject("assignee_id", UUID.class),
            nullableInstant(rs, "assigned_at"),
            label(rs.getString("label")),
            nullableInstant(rs, "labeled_at"),
            rs.getTimestamp("created_at").toInstant());
  }

  private @Nullable ReviewLabel label(@Nullable String value) {
    return value == null ? null : read(value, new TypeReference<ReviewLabel>() {});
  }

  private static @Nullable Instant nullableInstant(ResultSet rs, String column)
      throws SQLException {
    Timestamp value = rs.getTimestamp(column);
    return value == null ? null : value.toInstant();
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
