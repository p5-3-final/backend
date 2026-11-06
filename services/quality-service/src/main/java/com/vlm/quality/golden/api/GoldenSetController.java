package com.vlm.quality.golden.api;

import com.vlm.quality.golden.application.GoldenDraftService;
import com.vlm.quality.golden.application.GoldenFreezeService;
import com.vlm.quality.golden.application.GoldenSetService;
import com.vlm.quality.golden.application.Page;
import com.vlm.quality.golden.domain.GoldenDraftEntry;
import com.vlm.quality.golden.domain.GoldenSetVersion;
import com.vlm.quality.shared.api.PageMetas;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import vlm.contracts.quality.api.GoldenSetsApi;
import vlm.contracts.quality.model.AddGoldenSetEntryRequest;
import vlm.contracts.quality.model.CreateGoldenSetRequest;
import vlm.contracts.quality.model.GoldenSet;
import vlm.contracts.quality.model.GoldenSetEntry;
import vlm.contracts.quality.model.ListGoldenSetEntries200Response;
import vlm.contracts.quality.model.ListGoldenSetVersions200Response;
import vlm.contracts.quality.model.ListGoldenSets200Response;
import vlm.contracts.quality.model.ReviewLabel;

/** Golden Set API(계약 tag golden-sets, SPEC-029 R-11~R-15). */
@RestController
@RequiredArgsConstructor
class GoldenSetController implements GoldenSetsApi {

  private final GoldenSetService sets;
  private final GoldenDraftService drafts;
  private final GoldenFreezeService freezes;

  @Override
  public ResponseEntity<GoldenSet> createGoldenSet(
      UUID xUserId, String xUserRoles, CreateGoldenSetRequest request) {
    var created =
        sets.create(request.getName(), request.getSplit().getValue(), request.getDescription());
    return ResponseEntity.status(HttpStatus.CREATED).body(GoldenSetResponses.of(created));
  }

  @Override
  public ResponseEntity<ListGoldenSets200Response> listGoldenSets(
      UUID xUserId, String xUserRoles, Integer page, Integer size) {
    var found = sets.list(page, size);
    return ResponseEntity.ok(
        new ListGoldenSets200Response(
            found.items().stream().map(GoldenSetResponses::of).toList(),
            PageMetas.of(page, size, found.totalElements())));
  }

  @Override
  public ResponseEntity<GoldenSet> getGoldenSet(UUID xUserId, String xUserRoles, UUID setId) {
    return ResponseEntity.ok(GoldenSetResponses.of(sets.view(setId)));
  }

  @Override
  public ResponseEntity<GoldenSetEntry> addGoldenSetEntry(
      UUID xUserId, String xUserRoles, UUID setId, AddGoldenSetEntryRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(entry(drafts.add(setId, request.getReviewItemId())));
  }

  @Override
  public ResponseEntity<ListGoldenSetEntries200Response> listGoldenSetEntries(
      UUID xUserId, String xUserRoles, UUID setId, Integer page, Integer size) {
    Page<GoldenDraftEntry> found = drafts.list(setId, page, size);
    return ResponseEntity.ok(
        new ListGoldenSetEntries200Response(
            found.items().stream().map(GoldenSetController::entry).toList(),
            PageMetas.of(page, size, found.totalElements())));
  }

  @Override
  public ResponseEntity<Void> deleteGoldenSetEntry(
      UUID xUserId, String xUserRoles, UUID setId, UUID entryId) {
    drafts.remove(setId, entryId);
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<vlm.contracts.quality.model.GoldenSetVersion> createGoldenSetVersion(
      UUID xUserId, String xUserRoles, UUID setId) {
    return ResponseEntity.status(HttpStatus.CREATED).body(version(freezes.freeze(setId, xUserId)));
  }

  @Override
  public ResponseEntity<ListGoldenSetVersions200Response> listGoldenSetVersions(
      UUID xUserId, String xUserRoles, UUID setId, Integer page, Integer size) {
    Page<GoldenSetVersion> found = sets.listVersions(setId, page, size);
    return ResponseEntity.ok(
        new ListGoldenSetVersions200Response(
            found.items().stream().map(GoldenSetController::version).toList(),
            PageMetas.of(page, size, found.totalElements())));
  }

  private static GoldenSetEntry entry(GoldenDraftEntry entry) {
    var label = entry.label();
    return new GoldenSetEntry(
        entry.entryId(),
        entry.setId(),
        entry.reviewItemId(),
        entry.sourceId(),
        entry.eventId(),
        new ReviewLabel(
            ReviewLabel.VerdictEnum.fromValue(label.verdict()),
            label.eventPresent(),
            label.eventType() == null
                ? null
                : ReviewLabel.EventTypeEnum.fromValue(label.eventType()),
            label.startMs() == null ? null : Math.toIntExact(label.startMs()),
            label.endMs() == null ? null : Math.toIntExact(label.endMs()),
            label.severity() == null ? null : ReviewLabel.SeverityEnum.fromValue(label.severity()),
            label.note()),
        offset(entry.addedAt()));
  }

  private static vlm.contracts.quality.model.GoldenSetVersion version(GoldenSetVersion version) {
    return new vlm.contracts.quality.model.GoldenSetVersion(
        version.setId(),
        version.version(),
        version.entryCount(),
        version.manifestKey(),
        offset(version.frozenAt()),
        version.frozenBy());
  }

  static OffsetDateTime offset(Instant instant) {
    return instant.atOffset(ZoneOffset.UTC);
  }
}
