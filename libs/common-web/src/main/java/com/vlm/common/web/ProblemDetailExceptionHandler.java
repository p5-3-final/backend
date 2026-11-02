package com.vlm.common.web;

import com.vlm.common.error.BusinessException;
import com.vlm.common.error.ErrorCode;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 모든 MVC 서비스의 오류를 RFC 9457 ProblemDetail({@code application/problem+json})로 바꾼다.
 *
 * <p>응답에는 {@code code}(오류 코드)와, 추적 중이면 {@code traceId}를 확장 필드로 넣는다.
 */
@RestControllerAdvice
public class ProblemDetailExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ProblemDetailExceptionHandler.class);

  // Micrometer Tracing이 현재 요청의 추적 ID를 MDC에 이 키로 넣는다.
  private static final String TRACE_ID_KEY = "traceId";

  private final String validationErrorCode;

  /**
   * @param validationErrorCode 입력 검증 실패와 읽을 수 없는 본문에 쓸 서비스별 오류 코드(예: {@code AUTH_INVALID_REQUEST})
   */
  public ProblemDetailExceptionHandler(String validationErrorCode) {
    this.validationErrorCode = validationErrorCode;
  }

  @ExceptionHandler(BusinessException.class)
  ResponseEntity<ProblemDetail> handleBusiness(BusinessException exception) {
    ErrorCode errorCode = exception.getErrorCode();
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatusCode.valueOf(errorCode.status()), exception.getMessage());
    return respond(problem, errorCode.code());
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> handleUnexpected(Exception exception) {
    // 내부 메시지에 비밀값이나 구현 정보가 섞일 수 있어 응답에는 기본 메시지만 쓰고 원인은 로그로만 남긴다.
    log.error("Unhandled exception", exception);
    CommonErrorCode errorCode = CommonErrorCode.INTERNAL_ERROR;
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatusCode.valueOf(errorCode.status()), errorCode.defaultMessage());
    return respond(problem, errorCode.code());
  }

  @Override
  protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException exception,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    ProblemDetail problem = exception.getBody();
    problem.setDetail(CommonErrorCode.INVALID_REQUEST.defaultMessage());
    List<Map<String, String>> errors =
        exception.getBindingResult().getFieldErrors().stream()
            .map(
                error ->
                    Map.of(
                        "field",
                        error.getField(),
                        "message",
                        String.valueOf(error.getDefaultMessage())))
            .toList();
    problem.setProperty("errors", errors);
    problem.setProperty("code", validationErrorCode);
    return handleExceptionInternal(exception, problem, headers, status, request);
  }

  @Override
  protected @Nullable ResponseEntity<Object> handleHttpMessageNotReadable(
      HttpMessageNotReadableException exception,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(status, CommonErrorCode.INVALID_REQUEST.defaultMessage());
    problem.setProperty("code", validationErrorCode);
    return handleExceptionInternal(exception, problem, headers, status, request);
  }

  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception exception,
      @Nullable Object body,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    ProblemDetail problem =
        body instanceof ProblemDetail detail ? detail : ProblemDetail.forStatus(status);
    // 405, 415처럼 Spring이 직접 만드는 오류에도 code가 빠지지 않게 상태 이름으로 채운다.
    if (problem.getProperties() == null || !problem.getProperties().containsKey("code")) {
      HttpStatus resolved = HttpStatus.resolve(status.value());
      problem.setProperty(
          "code", "COMMON_" + (resolved != null ? resolved.name() : status.value()));
    }
    addTraceId(problem);
    return ResponseEntity.status(status).headers(headers).body(problem);
  }

  private ResponseEntity<ProblemDetail> respond(ProblemDetail problem, String code) {
    problem.setProperty("code", code);
    addTraceId(problem);
    return ResponseEntity.status(problem.getStatus()).body(problem);
  }

  private static void addTraceId(ProblemDetail problem) {
    String traceId = MDC.get(TRACE_ID_KEY);
    if (traceId != null) {
      problem.setProperty(TRACE_ID_KEY, traceId);
    }
  }
}
