package com.example.agent.streaming.observability;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.stereotype.Component;

/**
 * 链路追踪发布器，提供基本追踪能力。
 */
@Component
public class TracingPublisher {

    private final Tracer tracer;

    public TracingPublisher(Tracer tracer) {
        this.tracer = tracer;
    }

    /**
     * 获取当前 traceId。
     *
     * @return traceId
     */
    public String currentTraceId() {
        Span span = tracer.currentSpan();
        if (span == null) {
            return null;
        }
        return span.context().traceId();
    }
}
