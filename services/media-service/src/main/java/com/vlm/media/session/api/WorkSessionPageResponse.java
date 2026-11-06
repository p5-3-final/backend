package com.vlm.media.session.api;

import com.vlm.media.shared.api.PageMeta;
import com.vlm.media.shared.domain.WorkSessionPage;
import java.util.List;

/** 계약 {@code WorkSessionPage}. */
record WorkSessionPageResponse(List<WorkSessionResponse> items, PageMeta page) {

  static WorkSessionPageResponse from(WorkSessionPage result) {
    return new WorkSessionPageResponse(
        result.items().stream().map(WorkSessionResponse::from).toList(),
        new PageMeta(result.page(), result.size(), result.totalElements(), result.totalPages()));
  }
}
