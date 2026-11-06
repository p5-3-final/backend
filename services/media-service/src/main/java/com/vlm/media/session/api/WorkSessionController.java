package com.vlm.media.session.api;

import com.vlm.common.web.UserHeaders;
import com.vlm.media.session.application.WorkSessionService;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class WorkSessionController {

  private static final String SESSIONS = "/api/v1/media/sessions";

  private final WorkSessionService sessionService;

  @PostMapping(SESSIONS)
  ResponseEntity<WorkSessionResponse> create(
      @RequestHeader(value = UserHeaders.USER_ID, required = false) @Nullable String userId,
      @Valid @RequestBody CreateWorkSessionRequest request) {
    WorkSessionResponse session =
        WorkSessionResponse.from(
            sessionService.create(
                UserHeaders.userId(userId), request.name(), request.site(), request.area()));
    return ResponseEntity.created(URI.create(SESSIONS + "/" + session.id())).body(session);
  }
}
