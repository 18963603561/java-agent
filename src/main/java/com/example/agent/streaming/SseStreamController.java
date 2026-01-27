package com.example.agent.streaming;

import com.example.agent.auth.AuthService;
import com.example.agent.auth.TenantContext;
import com.example.agent.auth.UserContext;
import com.example.agent.common.ErrorCodeException;
import com.example.agent.domain.event.StreamEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * SSE 订阅控制器。
 */
@RestController
public class SseStreamController {

    private static final Logger log = LoggerFactory.getLogger(SseStreamController.class);

    private final EventStreamService eventStreamService;
    private final AuthService authService;

    private Duration firstEventTimeout = Duration.ofSeconds(30);

    private Scheduler timeoutScheduler = Schedulers.parallel();

    public SseStreamController(EventStreamService eventStreamService, AuthService authService) {
        this.eventStreamService = eventStreamService;
        this.authService = authService;
    }

    /**
     * 配置首事件超时时间。
     *
     * @param timeoutSeconds 超时秒数
     */
    @Value("${agent.sse.timeoutSeconds:30}")
    public void setFirstEventTimeoutSeconds(long timeoutSeconds) {
        if (timeoutSeconds <= 0) {
            this.firstEventTimeout = null;
        } else {
            this.firstEventTimeout = Duration.ofSeconds(timeoutSeconds);
        }
    }

    /**
     * 订阅事件流。
     *
     * @param workflowId 工作流标识
     * @param types 事件类型过滤
     * @param lastEventId 断线续传游标
     * @param lastEventHeader Header 中的游标
     * @param apiKey API Key
     * @param exchange 请求上下文
     * @return SSE 流
     */
    @GetMapping(path = "/api/v1/stream/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<StreamEvent>> stream(
            @RequestParam("workflow_id") String workflowId,
            @RequestParam(value = "types", required = false) String types,
            @RequestParam(value = "last_event_id", required = false) String lastEventId,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventHeader,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            ServerWebExchange exchange) {
        UserContext userContext = authService.authenticate(apiKey, exchange);
        TenantContext tenantContext = getTenantContext(exchange);
        tenantContext.applyUserContext(userContext);
        String finalCursor = StringUtils.hasText(lastEventHeader) ? lastEventHeader : lastEventId;

        TaskStreamRequest request = new TaskStreamRequest();
        request.setWorkflowId(workflowId);
        request.setTypes(parseTypes(types));
        request.setLastEventId(finalCursor);
        request.setCursor(finalCursor);

        log.info("SSE subscribe start, tenantId={}, workflowId={}", tenantContext.getTenantId(), workflowId);
        Flux<StreamEvent> stream = eventStreamService.stream(request, tenantContext);
        Flux<StreamEvent> gated = firstEventTimeout == null ? stream : stream.publish(shared -> {
            AtomicBoolean timedOut = new AtomicBoolean(false);
            Mono<StreamEvent> first = shared.next()
                    .timeout(firstEventTimeout, timeoutScheduler)
                    .onErrorResume(TimeoutException.class, ex -> {
                        timedOut.set(true);
                        StreamEvent timeoutEvent = buildTimeoutEvent(tenantContext, workflowId);
                        eventStreamService.recordSyntheticEvent(timeoutEvent);
                        log.warn("SSE first event timeout, tenantId={}, workflowId={}, eventId={}",
                                tenantContext.getTenantId(), workflowId, timeoutEvent.getEventId());
                        return Mono.just(timeoutEvent);
                    });
            return first.flatMapMany(event -> timedOut.get()
                    ? Flux.just(event)
                    : Flux.just(event).concatWith(shared.skip(1)));
        });

        Flux<ServerSentEvent<StreamEvent>> eventFlux = gated
                .map(event -> ServerSentEvent.<StreamEvent>builder()
                        .id(event.getEventId())
                        .event(event.getType().name())
                        .data(event)
                        .build());
        Flux<ServerSentEvent<StreamEvent>> output = firstEventTimeout == null
                ? Flux.just(ServerSentEvent.<StreamEvent>builder().comment("ready").build()).concatWith(eventFlux)
                : eventFlux;
        return output.doFinally(signal -> log.info("SSE subscribe end, tenantId={}, workflowId={}, signal={}",
                tenantContext.getTenantId(), workflowId, signal));
    }

    private TenantContext getTenantContext(ServerWebExchange exchange) {
        TenantContext context = exchange.getAttribute(TenantContext.CONTEXT_KEY);
        if (context == null) {
            throw new ErrorCodeException(HttpStatus.BAD_REQUEST, "TENANT_MISSING", "租户标识缺失");
        }
        return context;
    }

    private List<String> parseTypes(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .forEach(result::add);
        return result;
    }

    private StreamEvent buildTimeoutEvent(TenantContext tenantContext, String workflowId) {
        long seq = eventStreamService.nextSequence(tenantContext.getTenantId(), workflowId);
        StreamEvent event = new StreamEvent();
        event.setEventId(workflowId + ":" + seq);
        event.setSchemaVersion("v1");
        event.setWorkflowId(workflowId);
        event.setType(com.example.agent.domain.event.EventType.ERROR_OCCURRED);
        event.setTimestamp(Instant.now());
        event.setSeq(seq);
        event.setStreamId(workflowId);
        event.setTenantId(tenantContext.getTenantId());
        event.setPayload(Map.of("error", "STREAM_TIMEOUT"));
        return event;
    }
}
