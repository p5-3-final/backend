package com.vlm.quality.export.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vlm.quality.export.domain.ExportFiles;
import com.vlm.quality.export.domain.ExportScope;
import com.vlm.quality.export.domain.FeatureExport;
import com.vlm.quality.export.domain.FeatureExportRepository;
import com.vlm.quality.export.domain.FeatureRowMapper;
import com.vlm.quality.export.domain.ResultFiles;
import com.vlm.quality.export.domain.SourceResult;
import com.vlm.quality.export.domain.SourceResultRepository;
import com.vlm.quality.golden.application.GoldenSourceService;
import com.vlm.quality.golden.application.GoldenSources;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 내보내기 하나를 처리한다(SPEC-030 R-14).
 *
 * <p>중간 결과를 남기지 않으므로 Pod가 죽으면 다른 작업자가 처음부터 다시 쓴다. 같은 경로에 덮어쓰므로 결과는 같다.
 */
@Service
@RequiredArgsConstructor
public class FeatureExportWorker {

  private static final Logger log = LoggerFactory.getLogger(FeatureExportWorker.class);

  private final FeatureExportRepository exports;
  private final SourceResultRepository results;
  private final GoldenSourceService golden;
  private final ResultFiles resultFiles;
  private final ExportFiles exportFiles;
  private final ExportProperties properties;
  private final ObjectMapper json;
  private final Clock clock;

  /**
   * 맡을 내보내기가 있으면 하나를 처리한다.
   *
   * @return 처리했으면 true. 없으면 false
   */
  public boolean runOnce() {
    Instant now = clock.instant();
    Optional<FeatureExport> claimed = exports.claim(now, now.minus(properties.heartbeatTimeout()));
    if (claimed.isEmpty()) {
      return false;
    }
    FeatureExport job = claimed.get();
    try {
      run(job);
    } catch (RuntimeException e) {
      // 결과 파일 버킷·내보내기 버킷 오류. 다시 해도 같을 수 있어 실패로 끝내고 운영자가 다시 요청한다.
      exports.fail(job.id(), clock.instant());
      log.error("Feature export failed exportId={}", job.exportId(), e);
    }
    return true;
  }

  private void run(FeatureExport job) {
    Selection selection = select(job.scope());
    String fileKey = FeatureExport.fileKeyOf(job.exportId());
    Path file = createTempFile();
    try {
      Written written = write(job, selection, file);
      exportFiles.upload(fileKey, file);
      exports.complete(job.id(), fileKey, written.rows(), written.sources(), clock.instant());
      log.info(
          "Feature export completed exportId={} rows={} sources={}",
          job.exportId(),
          written.rows(),
          written.sources());
    } finally {
      removeQuietly(file);
    }
  }

  private Selection select(ExportScope scope) {
    UUID goldenSetId = scope.goldenSetId();
    if (goldenSetId != null) {
      GoldenSources sources = golden.latestSources(goldenSetId);
      return new Selection(results.findBySources(sources.sourceIds()), sources.split());
    }
    return new Selection(
        results.findReceivedBetween(
            Objects.requireNonNull(scope.from()), Objects.requireNonNull(scope.to())),
        null);
  }

  private Written write(FeatureExport job, Selection selection, Path file) {
    int rows = 0;
    int sources = 0;
    try (BufferedWriter out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
      for (SourceResult result : selection.results()) {
        Optional<JsonNode> document = readResult(result);
        exports.heartbeat(job.id(), clock.instant());
        if (document.isEmpty()) {
          continue;
        }
        sources++;
        for (ObjectNode row : FeatureRowMapper.rows(document.get(), selection.split())) {
          out.write(json.writeValueAsString(row));
          out.write('\n');
          rows++;
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException("cannot write feature export file", e);
    }
    return new Written(rows, sources);
  }

  // R-14: 결과 파일이 없거나 읽을 수 없는 영상은 건너뛴다. 영상 하나 때문에 내보내기 전체를 버리지 않는다.
  private Optional<JsonNode> readResult(SourceResult result) {
    Optional<byte[]> content = resultFiles.read(result.resultKey());
    if (content.isEmpty()) {
      log.warn("Skipped source without result file sourceId={}", result.sourceId());
      return Optional.empty();
    }
    try {
      return Optional.of(json.readTree(content.get()));
    } catch (IOException e) {
      log.warn("Skipped source with unreadable result file sourceId={}", result.sourceId());
      return Optional.empty();
    }
  }

  private static Path createTempFile() {
    try {
      return Files.createTempFile("feature-export-", ".jsonl");
    } catch (IOException e) {
      throw new UncheckedIOException("cannot create feature export file", e);
    }
  }

  private static void removeQuietly(Path file) {
    try {
      Files.deleteIfExists(file);
    } catch (IOException e) {
      log.warn("Cannot remove temporary export file", e);
    }
  }

  private record Selection(List<SourceResult> results, @Nullable String split) {}

  private record Written(int rows, int sources) {}
}
