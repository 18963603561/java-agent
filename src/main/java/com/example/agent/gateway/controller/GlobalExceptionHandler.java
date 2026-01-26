package com.example.agent.gateway.controller;

import com.example.agent.auth.TenantContext;
import com.example.agent.common.ErrorCodeProvider;
import com.example.agent.common.ErrorResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolationException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.bind.support.WebExchangeBindException;
import reactor.core.publisher.Mono;

/**
 * 全局异常处理器，将异常统一映射为 ErrorResponse。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler implements WebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ObjectMapper objectMapper;

    public GlobalExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 统一处理异常并写回错误响应。
     *
     * @param exchange 请求上下文
     * @param ex 异常
     * @return 处理结果
     */
    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }

        HttpStatus status = resolveStatus(ex);
        String code = resolveCode(ex, status);
        TenantContext context = exchange.getAttribute(TenantContext.CONTEXT_KEY);
        String traceId = context != null ? context.getTraceId() : null;
        String requestId = context != null ? context.getRequestId() : null;

        log.error("Unhandled error, status={}, code={}, path={}", status, code,
                exchange.getRequest().getPath(), ex);

        ErrorResponse errorResponse = ErrorResponse.of(code, resolveMessage(ex),
                resolveDetails(ex, exchange), traceId, requestId);

        byte[] body = toJsonBytes(errorResponse);
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().setContentLength(body.length);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse()
                .bufferFactory().wrap(body)));
    }

    private HttpStatus resolveStatus(Throwable ex) {
        if (ex instanceof ResponseStatusException rse) {
            return HttpStatus.valueOf(rse.getStatusCode().value());
        }
        if (ex instanceof WebExchangeBindException
                || ex instanceof ServerWebInputException
                || ex instanceof ConstraintViolationException) {
            return HttpStatus.BAD_REQUEST;
        }
        if (ex instanceof IllegalArgumentException) {
            return HttpStatus.BAD_REQUEST;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private String resolveMessage(Throwable ex) {
        if (ex instanceof ResponseStatusException rse && rse.getReason() != null) {
            return rse.getReason();
        }
        if (ex instanceof WebExchangeBindException
                || ex instanceof ServerWebInputException
                || ex instanceof ConstraintViolationException) {
            return "参数校验失败";
        }
        return ex.getMessage();
    }

    private String resolveCode(Throwable ex, HttpStatus status) {
        if (ex instanceof ErrorCodeProvider provider) {
            return provider.getErrorCode();
        }
        return switch (status) {
            case BAD_REQUEST -> "INVALID_REQUEST";
            case UNAUTHORIZED -> "UNAUTHORIZED";
            case FORBIDDEN -> "FORBIDDEN";
            case NOT_FOUND -> "NOT_FOUND";
            case REQUEST_TIMEOUT -> "STREAM_TIMEOUT";
            case GONE -> "STREAM_GAP";
            default -> "INTERNAL_ERROR";
        };
    }

    private Map<String, Object> resolveDetails(Throwable ex, ServerWebExchange exchange) {
        Map<String, Object> details = new HashMap<>();
        details.put("path", exchange.getRequest().getPath().value());
        if (ex instanceof WebExchangeBindException bindException) {
            List<Map<String, String>> errors = bindException.getFieldErrors().stream()
                    .map(error -> Map.of(
                            "field", error.getField(),
                            "message", error.getDefaultMessage()))
                    .toList();
            details.put("errors", errors);
        } else if (ex instanceof ConstraintViolationException violationException) {
            List<Map<String, String>> errors = violationException.getConstraintViolations().stream()
                    .map(error -> Map.of(
                            "field", error.getPropertyPath().toString(),
                            "message", error.getMessage()))
                    .toList();
            details.put("errors", errors);
        }
        return details;
    }

    private byte[] toJsonBytes(Object value) {
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (JsonProcessingException e) {
            String fallback = "{\"code\":\"INTERNAL_ERROR\",\"message\":\"serialize_failed\"}";
            return fallback.getBytes(StandardCharsets.UTF_8);
        }
    }
}
