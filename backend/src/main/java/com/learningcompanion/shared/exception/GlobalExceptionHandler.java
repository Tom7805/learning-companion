package com.learningcompanion.shared.exception;

import com.learningcompanion.shared.web.RequestIdFilter;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    public static final String RETRY_AFTER_SECONDS = "retryAfterSeconds";

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiError> handleRateLimit(RateLimitExceededException ex) {
        return ResponseEntity.status(ex.code().status())
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, ex.retryAfter().toSeconds())))
                .body(body(ex.code(), ex.getMessage(), List.of(), null));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> handleBusiness(BusinessException ex) {
        List<ApiError.FieldError> fields = ex.field() == null
                ? List.of()
                : List.of(new ApiError.FieldError(ex.field(), ex.code().name(), ex.getMessage()));
        ResponseEntity.BodyBuilder response = ResponseEntity.status(ex.code().status());
        if (ex.details().get(RETRY_AFTER_SECONDS) instanceof Number seconds) {
            response.header(HttpHeaders.RETRY_AFTER, String.valueOf(seconds.longValue()));
        }
        return response.body(body(ex.code(), ex.getMessage(), fields, ex.details().isEmpty() ? null : ex.details()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleInvalid(MethodArgumentNotValidException ex) {
        List<ApiError.FieldError> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiError.FieldError(error.getField(), error.getCode(), error.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest()
                .body(body(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.defaultMessage(), fields, null));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, HttpMediaTypeNotSupportedException.class})
    public ResponseEntity<ApiError> handleUnreadable(Exception ex) {
        return ResponseEntity.badRequest()
                .body(body(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.defaultMessage(), List.of(), null));
    }

    @ExceptionHandler({NoResourceFoundException.class, HttpRequestMethodNotSupportedException.class})
    public ResponseEntity<ApiError> handleNotFound(Exception ex) {
        return ResponseEntity.status(ErrorCode.NOT_FOUND.status())
                .body(body(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.defaultMessage(), List.of(), null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        log.error("Unhandled error", ex);
        return ResponseEntity.internalServerError()
                .body(body(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage(), List.of(), null));
    }

    private ApiError body(ErrorCode code, String message, List<ApiError.FieldError> fields,
                          Map<String, Object> details) {
        return new ApiError(code.name(), message, code.status().value(), fields, details,
                MDC.get(RequestIdFilter.MDC_KEY), Instant.now(clock));
    }
}
