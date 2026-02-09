package com.example.agent.capabilities.context.runtime;

import com.example.agent.streaming.observability.MetricsPublisher;
import java.util.Map;
import org.slf4j.Logger;

/**
 * 上下文运行时视图工厂。
 */
public final class ContextRuntimeViews {

    private ContextRuntimeViews() {
    }

    /**
     * 创建只读视图。
     */
    public static ContextRuntimeView readOnly(Map<String, Object> values,
                                              Logger log,
                                              MetricsPublisher metricsPublisher) {
        return new ContextRuntimeView(values, log, metricsPublisher);
    }

    /**
     * 创建只读视图（带读取诊断上下文）。
     */
    public static ContextRuntimeView readOnly(Map<String, Object> values,
                                              Logger log,
                                              MetricsPublisher metricsPublisher,
                                              ContextRuntimeReadContext readContext) {
        return new ContextRuntimeView(values, log, metricsPublisher, readContext);
    }

    /**
     * 创建可写视图。
     */
    public static MutableContextRuntimeView mutable(Map<String, Object> values,
                                                    Logger log,
                                                    MetricsPublisher metricsPublisher) {
        return new MutableContextRuntimeView(values, log, metricsPublisher);
    }

    /**
     * 创建可写视图（带读取诊断上下文）。
     */
    public static MutableContextRuntimeView mutable(Map<String, Object> values,
                                                    Logger log,
                                                    MetricsPublisher metricsPublisher,
                                                    ContextRuntimeReadContext readContext) {
        return new MutableContextRuntimeView(values, log, metricsPublisher, readContext);
    }
}
