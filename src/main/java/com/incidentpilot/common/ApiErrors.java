package com.incidentpilot.common;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import com.incidentpilot.common.observability.RequestCorrelationFilter;
import com.incidentpilot.common.reliability.RequestRejectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 统一错误响应。对外只暴露稳定错误码与安全文案，堆栈只进服务端日志；
 * requestId 与访问日志、审计日志共用同一个关联 ID。
 */
@RestControllerAdvice
public class ApiErrors {
    private static final Logger log = LoggerFactory.getLogger(ApiErrors.class);
    private final Clock clock;

    public ApiErrors(Clock clock) { this.clock = clock; }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class})
    ResponseEntity<Error> badRequest(Exception error) {
        log.warn("Rejected request: {}", error.getClass().getSimpleName());
        return response(400, "INVALID_REQUEST", "请求参数无效，请检查必填字段、长度及 topK。");
    }

    @ExceptionHandler(RequestRejectedException.class)
    ResponseEntity<Error> rejected(RequestRejectedException error) {
        return response(429, error.code(), error.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Error> failed(Exception error) {
        log.error("Request failed", error);
        return response(503, "DEPENDENCY_OR_PROCESSING_FAILURE",
                "请求未完成，请稍后重试；若为摄取失败，可重复相同请求。");
    }

    private ResponseEntity<Error> response(int status, String code, String message) {
        String requestId = RequestCorrelationFilter.currentRequestId();
        return ResponseEntity.status(status)
                .body(new Error(code, message, requestId != null ? requestId : UUID.randomUUID().toString(),
                        clock.instant()));
    }

    public record Error(String code, String message, String requestId, Instant timestamp) { }
}
