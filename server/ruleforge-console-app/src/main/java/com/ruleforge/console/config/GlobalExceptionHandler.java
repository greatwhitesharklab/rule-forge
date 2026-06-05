package com.ruleforge.console.config;

import com.ruleforge.exception.RuleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * V5.9.0 全局异常 handler
 *
 * 背景: 之前各 controller 返 4xx/5xx 时,Spring 默认走 {@code DefaultErrorAttributes}
 *   返 {@code {"timestamp":..,"status":..,"error":..,"path":..}} JSON。
 *   前端 {@code formPost().handleError()} 走 {@code response.text()} 拿到的就是这段
 *   JSON,bootbox alert 直接展示给用户:
 *     服务端错误: {"timestamp":"2026-06-04T...","status":404,"error":"Not Found","path":"..."}
 *   体验差。
 *
 * 修复: 这里拦截 {@link ResponseStatusException} 和 {@link RuleException},
 *   把 {@code getReason()} 返成纯文本 body。前端 alert 就能展示 "file not found: <path>"
 *   这种友好消息,而不是 JSON。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> handleResponseStatus(ResponseStatusException ex) {
        String body = ex.getReason() != null ? ex.getReason() : ex.getMessage();
        if (body == null || body.isEmpty()) {
            body = ex.getStatusCode().toString();
        }
        return ResponseEntity.status(ex.getStatusCode())
                .header("Content-Type", "text/plain;charset=UTF-8")
                .body(body);
    }

    @ExceptionHandler(RuleException.class)
    public ResponseEntity<String> handleRuleException(RuleException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .header("Content-Type", "text/plain;charset=UTF-8")
                .body(ex.getMessage() != null ? ex.getMessage() : "RuleException");
    }

    /**
     * 兜底:任何未声明的异常都走这里,返 500 + 纯文本 message,
     *   避免 Spring 默认 {@code DefaultErrorAttributes} 返 {"timestamp":..,"error":..}
     *   的 JSON 包装,被前端 bootbox 直接展示给用户。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleGenericException(Exception ex) {
        log.error("Unhandled exception in API: {}", ex.getMessage(), ex);
        String body = ex.getMessage() != null && !ex.getMessage().isEmpty()
                ? ex.getMessage()
                : ex.getClass().getSimpleName();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .header("Content-Type", "text/plain;charset=UTF-8")
                .body(body);
    }
}
