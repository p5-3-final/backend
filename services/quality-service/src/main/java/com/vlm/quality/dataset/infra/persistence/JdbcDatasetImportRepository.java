package com.vlm.quality.dataset.infra.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vlm.quality.dataset.domain.DatasetImport;
import com.vlm.quality.dataset.domain.DatasetImportRepository;
import com.vlm.quality.dataset.domain.ImportCounts;
import com.vlm.quality.dataset.domain.ImportError;
import com.vlm.quality.dataset.domain.ImportGoldenSets;
import com.vlm.quality.dataset.domain.ImportStatus;
import com.vlm.quality.dataset.domain.ImportVideo;
import com.vlm.quality.dataset.domain.NewImportVideo;
import com.vlm.quality.dataset.domain.Split;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class JdbcDatasetImportRepository implements DatasetImportRepository {

  // 계약 errors maxItems
  private static final int MAX_ERRORS = 100;
  // 영상 행을 나눠 넣는 크기. 1,000개 매니페스트도 몇 번의 왕복으로 끝난다(NFR-1).
  private static final int INSERT_BATCH = 500;
  private static final TypeReference<List<ImportError>> ERRORS = new TypeReference<>() {};

  private static final String SELECT =
      "SELECT id, import_id, dataset_name, manifest_key, eval_ratio, split_seed, status,"
          + " video_count, registered_count, failed_count, label_count, dev_count, eval_count,"
          + " dev_set_id, eval_set_id, dev_version, eval_version, errors::text AS errors,"
          + " requested_by, requested_at, finished_at, dataset_origin::text AS dataset_origin"
          + " FROM dataset_imports";
  private static final String VIDEO_SELECT =
      "SELECT id, external_id, path, content_type, split, labels::text AS labels, label_count,"
          + " status, source_id FROM dataset_import_videos";
  private static final String CLEAR_VIDEOS =
      "DELETE FROM dataset_import_videos WHERE import_id = :id";

  private final NamedParameterJdbcTemplate jdbc;
  private final ObjectMapper json;

  @Override
  public boolean insertIfAbsent(
      UUID importId,
      String manifestKey,
      double evalRatio,
      long splitSeed,
      UUID requestedBy,
      Instant now) {
    return jdbc.update(
            "INSERT INTO dataset_imports (import_id, manifest_key, eval_ratio, split_seed, status,"
                + " requested_by, requested_at, created_at, updated_at) VALUES (:importId, :key,"
                + " :ratio, :seed, 'PENDING', :by, :now, :now, :now)"
                + " ON CONFLICT (manifest_key) DO NOTHING",
            new MapSqlParameterSource()
                .addValue("importId", importId)
                .addValue("key", manifestKey)
                .addValue("ratio", evalRatio)
                .addValue("seed", splitSeed)
                .addValue("by", requestedBy)
                .addValue("now", Timestamp.from(now)))
        == 1;
  }

  @Override
  public Optional<DatasetImport> lockByManifestKey(String manifestKey) {
    return jdbc
        .query(
            SELECT + " WHERE manifest_key = :key FOR UPDATE",
            new MapSqlParameterSource("key", manifestKey),
            this::map)
        .stream()
        .findFirst();
  }

  @Override
  public void restart(long id, double evalRatio, long splitSeed, UUID requestedBy, Instant now) {
    var params =
        new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("ratio", evalRatio)
            .addValue("seed", splitSeed)
            .addValue("by", requestedBy)
            .addValue("now", Timestamp.from(now));
    jdbc.update(CLEAR_VIDEOS, params);
    jdbc.update(
        "UPDATE dataset_imports SET status = 'PENDING', dataset_name = NULL, dataset_origin = NULL,"
            + " eval_ratio = :ratio,"
            + " split_seed = :seed, video_count = 0, registered_count = 0, failed_count = 0,"
            + " label_count = 0, dev_count = 0, eval_count = 0, dev_set_id = NULL,"
            + " eval_set_id = NULL, dev_version = NULL, eval_version = NULL, errors = '[]',"
            + " requested_by = :by, requested_at = :now, heartbeat_at = NULL,"
            + " finished_at = NULL, updated_at = :now WHERE id = :id",
        params);
  }

  @Override
  public Optional<DatasetImport> findByImportId(UUID importId) {
    return jdbc
        .query(
            SELECT + " WHERE import_id = :importId",
            new MapSqlParameterSource("importId", importId),
            this::map)
        .stream()
        .findFirst();
  }

  @Override
  public List<DatasetImport> list(int page, int size) {
    return jdbc.query(
        SELECT + " ORDER BY requested_at DESC, id DESC LIMIT :limit OFFSET :offset",
        new MapSqlParameterSource().addValue("limit", size).addValue("offset", (long) page * size),
        this::map);
  }

  @Override
  public long count() {
    Long count =
        jdbc.queryForObject(
            "SELECT count(*) FROM dataset_imports", new MapSqlParameterSource(), Long.class);
    return count == null ? 0 : count;
  }

  @Override
  public Optional<DatasetImport> claim(Instant now, Instant staleBefore) {
    List<Long> claimed =
        jdbc.queryForList(
            "UPDATE dataset_imports SET heartbeat_at = :now, updated_at = :now WHERE id = ("
                + " SELECT id FROM dataset_imports WHERE status = 'PENDING' OR (status IN"
                + " ('VALIDATING', 'REGISTERING') AND (heartbeat_at IS NULL OR heartbeat_at <"
                + " :stale)) ORDER BY requested_at, id LIMIT 1 FOR UPDATE SKIP LOCKED)"
                + " RETURNING id",
            new MapSqlParameterSource()
                .addValue("now", Timestamp.from(now))
                .addValue("stale", Timestamp.from(staleBefore)),
            Long.class);
    if (claimed.isEmpty()) {
      return Optional.empty();
    }
    return jdbc
        .query(
            SELECT + " WHERE id = :id", new MapSqlParameterSource("id", claimed.get(0)), this::map)
        .stream()
        .findFirst();
  }

  @Override
  public void heartbeat(long id, Instant now) {
    jdbc.update(
        "UPDATE dataset_imports SET heartbeat_at = :now, updated_at = :now WHERE id = :id",
        new MapSqlParameterSource().addValue("id", id).addValue("now", Timestamp.from(now)));
  }

  @Override
  public void markValidating(long id, Instant now) {
    jdbc.update(
        "UPDATE dataset_imports SET status = 'VALIDATING', heartbeat_at = :now, updated_at = :now"
            + " WHERE id = :id",
        new MapSqlParameterSource().addValue("id", id).addValue("now", Timestamp.from(now)));
  }

  @Override
  public void saveValidated(
      long id,
      String datasetName,
      String originJson,
      List<NewImportVideo> videos,
      int labelCount,
      Instant now) {
    Timestamp at = Timestamp.from(now);
    jdbc.update(CLEAR_VIDEOS, new MapSqlParameterSource("id", id));
    for (int from = 0; from < videos.size(); from += INSERT_BATCH) {
      List<NewImportVideo> batch =
          videos.subList(from, Math.min(videos.size(), from + INSERT_BATCH));
      jdbc.batchUpdate(
          "INSERT INTO dataset_import_videos (import_id, external_id, path, content_type, split,"
              + " labels, label_count, status, created_at, updated_at) VALUES (:id, :externalId,"
              + " :path, :contentType, :split, CAST(:labels AS JSONB), :labelCount, 'PENDING',"
              + " :now, :now)",
          batch.stream()
              .map(
                  v ->
                      (SqlParameterSource)
                          new MapSqlParameterSource()
                              .addValue("id", id)
                              .addValue("externalId", v.externalId())
                              .addValue("path", v.path())
                              .addValue("contentType", v.contentType())
                              .addValue("split", v.split().wire())
                              .addValue("labels", v.labelsJson())
                              .addValue("labelCount", v.labelCount())
                              .addValue("now", at))
              .toArray(SqlParameterSource[]::new));
    }
    jdbc.update(
        "UPDATE dataset_imports SET status = 'REGISTERING', dataset_name = :name,"
            + " dataset_origin = CAST(:origin AS JSONB), video_count = :videos, label_count = :labels, heartbeat_at = :now,"
            + " updated_at = :now WHERE id = :id",
        new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("name", datasetName)
            .addValue("origin", originJson)
            .addValue("videos", videos.size())
            .addValue("labels", labelCount)
            .addValue("now", at));
  }

  @Override
  public void fail(long id, List<ImportError> errors, Instant now) {
    jdbc.update(
        "UPDATE dataset_imports SET status = 'FAILED', errors = CAST(:errors AS JSONB),"
            + " finished_at = :now, heartbeat_at = NULL, updated_at = :now WHERE id = :id",
        new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("errors", write(errors.subList(0, Math.min(errors.size(), MAX_ERRORS))))
            .addValue("now", Timestamp.from(now)));
  }

  @Override
  public List<ImportVideo> pendingVideos(long id) {
    return videos(id, ImportVideo.PENDING);
  }

  @Override
  public List<ImportVideo> registeredVideos(long id) {
    return videos(id, ImportVideo.REGISTERED);
  }

  private List<ImportVideo> videos(long id, String status) {
    return jdbc.query(
        VIDEO_SELECT + " WHERE import_id = :id AND status = :status ORDER BY id",
        new MapSqlParameterSource().addValue("id", id).addValue("status", status),
        VIDEO);
  }

  @Override
  public void markRegistered(long id, long videoRowId, UUID sourceId, Instant now) {
    var params =
        new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("video", videoRowId)
            .addValue("sourceId", sourceId)
            .addValue("now", Timestamp.from(now));
    jdbc.update(
        "UPDATE dataset_import_videos SET status = 'REGISTERED', source_id = :sourceId,"
            + " updated_at = :now WHERE id = :video",
        params);
    jdbc.update(
        "UPDATE dataset_imports SET registered_count = registered_count + 1, heartbeat_at = :now,"
            + " updated_at = :now WHERE id = :id",
        params);
  }

  @Override
  public void markFailed(long id, long videoRowId, ImportError error, Instant now) {
    var params =
        new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("video", videoRowId)
            .addValue("error", write(List.of(error)))
            .addValue("max", MAX_ERRORS)
            .addValue("now", Timestamp.from(now));
    jdbc.update(
        "UPDATE dataset_import_videos SET status = 'FAILED', updated_at = :now WHERE id = :video",
        params);
    // 오류는 처음 100건만 남기고 개수는 모두 센다(R-7).
    jdbc.update(
        "UPDATE dataset_imports SET failed_count = failed_count + 1, errors = CASE WHEN"
            + " jsonb_array_length(errors) < :max THEN errors || CAST(:error AS JSONB) ELSE errors"
            + " END, heartbeat_at = :now, updated_at = :now WHERE id = :id",
        params);
  }

  @Override
  public void complete(
      long id,
      ImportStatus status,
      ImportGoldenSets goldenSets,
      List<ImportError> extraErrors,
      Instant now) {
    // 이어받은 작업자가 같은 영상을 다시 셀 수 있어, 개수는 영상 행에서 다시 센다.
    jdbc.update(
        "UPDATE dataset_imports i SET status = :status,"
            + " registered_count = v.registered, failed_count = v.failed, dev_count = v.dev,"
            + " eval_count = v.eval, dev_set_id = :devSet, eval_set_id = :evalSet,"
            + " dev_version = :devVersion, eval_version = :evalVersion,"
            + " errors = (SELECT COALESCE(jsonb_agg(s.e ORDER BY s.n), '[]') FROM (SELECT t.e,"
            + " t.n FROM jsonb_array_elements(i.errors || CAST(:extra AS JSONB)) WITH ORDINALITY"
            + " AS t(e, n) ORDER BY t.n LIMIT :max) s),"
            + " finished_at = :now, heartbeat_at = NULL, updated_at = :now"
            + " FROM (SELECT count(*) FILTER (WHERE status = 'REGISTERED') AS registered,"
            + " count(*) FILTER (WHERE status = 'FAILED') AS failed,"
            + " count(*) FILTER (WHERE status = 'REGISTERED' AND split = 'dev') AS dev,"
            + " count(*) FILTER (WHERE status = 'REGISTERED' AND split = 'eval') AS eval"
            + " FROM dataset_import_videos WHERE import_id = :id) v WHERE i.id = :id",
        new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("status", status.name())
            .addValue("devSet", goldenSets.devSetId())
            .addValue("evalSet", goldenSets.evalSetId())
            .addValue("devVersion", goldenSets.devVersion())
            .addValue("evalVersion", goldenSets.evalVersion())
            .addValue("extra", write(extraErrors))
            .addValue("max", MAX_ERRORS)
            .addValue("now", Timestamp.from(now)));
  }

  private DatasetImport map(ResultSet rs, int row) throws SQLException {
    Timestamp finished = rs.getTimestamp("finished_at");
    return new DatasetImport(
        rs.getLong("id"),
        rs.getObject("import_id", UUID.class),
        rs.getString("dataset_name"),
        rs.getString("manifest_key"),
        rs.getDouble("eval_ratio"),
        rs.getLong("split_seed"),
        ImportStatus.valueOf(rs.getString("status")),
        new ImportCounts(
            rs.getInt("video_count"),
            rs.getInt("registered_count"),
            rs.getInt("failed_count"),
            rs.getInt("label_count"),
            rs.getInt("dev_count"),
            rs.getInt("eval_count")),
        rs.getObject("dev_set_id", UUID.class),
        rs.getObject("eval_set_id", UUID.class),
        rs.getObject("dev_version", Integer.class),
        rs.getObject("eval_version", Integer.class),
        read(rs.getString("errors")),
        rs.getObject("requested_by", UUID.class),
        rs.getTimestamp("requested_at").toInstant(),
        finished == null ? null : finished.toInstant(),
        rs.getString("dataset_origin"));
  }

  private static final RowMapper<ImportVideo> VIDEO =
      (rs, row) ->
          new ImportVideo(
              rs.getLong("id"),
              rs.getString("external_id"),
              rs.getString("path"),
              rs.getString("content_type"),
              Split.fromWire(rs.getString("split")),
              rs.getString("labels"),
              rs.getInt("label_count"),
              rs.getString("status"),
              rs.getObject("source_id", UUID.class));

  private String write(List<ImportError> errors) {
    try {
      return json.writeValueAsString(errors);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("cannot write import errors", e);
    }
  }

  private List<ImportError> read(String value) {
    try {
      return json.readValue(value, ERRORS);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("stored import errors are not JSON", e);
    }
  }
}
