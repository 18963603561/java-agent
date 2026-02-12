package com.example.agent.capabilities.context.compression.infrastructure.rule;

import com.example.agent.capabilities.context.compression.contract.CompressionExecutionResult;
import com.example.agent.capabilities.context.compression.contract.ContextCompressionRequest;
import com.example.agent.capabilities.context.compression.application.port.CompressionExecutor;
import com.example.agent.capabilities.context.compression.domain.model.CompressionCommand;
import com.example.agent.capabilities.memory.MemoryStore;
import com.example.agent.capabilities.memory.model.CompressionRequest;
import com.example.agent.capabilities.memory.model.MemoryRecord;
import com.example.agent.streaming.observability.MetricsPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 规则压缩执行器适配器。
 *
 * <p>用途：封装基于 MemoryStore 的既有规则压缩实现，并输出统一执行结果。</p>
 */
@Component
public class RuleCompressionExecutorAdapter implements CompressionExecutor {

    private static final Logger log = LoggerFactory.getLogger(RuleCompressionExecutorAdapter.class);

    /**
     * 规则压缩模式标识。
     */
    public static final String MODE = "rule";

    /**
     * 记忆存储门面。
     */
    private final MemoryStore memoryStore;

    /**
     * 指标发布器。
     */
    private final MetricsPublisher metricsPublisher;

    public RuleCompressionExecutorAdapter(MemoryStore memoryStore,
                                          MetricsPublisher metricsPublisher) {
        this.memoryStore = memoryStore;
        this.metricsPublisher = metricsPublisher;
    }

    @Override
    public String mode() {
        return MODE;
    }

    @Override
    public CompressionExecutionResult execute(CompressionCommand command) {
        CompressionExecutionResult result = new CompressionExecutionResult();
        result.setSource(MODE);

        ContextCompressionRequest request = command != null ? command.getRequest() : null;
        if (request == null || !StringUtils.hasText(request.getSessionId())) {
            result.setFailureReason("INVALID_REQUEST");
            return result;
        }
        if (memoryStore == null) {
            result.setFailureReason("MEMORY_STORE_UNAVAILABLE");
            return result;
        }

        long startNs = System.nanoTime();
        try {
            // 调用外部存储压缩：以会话与工作流为关键入参触发规则压缩。
            MemoryRecord compressed = memoryStore.compress(buildCompressionRequest(request), command.getTenantContext());
            long durationMs = Math.max(0, (System.nanoTime() - startNs) / 1_000_000);
            // 记录耗时：用于后续压缩性能分析与容量规划。
            recordDuration(durationMs);

            result.setCompressed(compressed);
            result.setDurationMs(durationMs);
            result.setSuccess(compressed != null);

            // 失败处理：压缩返回空结果时记录失败原因并输出告警日志。
            if (compressed == null) {
                result.setFailureReason("EMPTY_RESULT");
                log.warn("规则压缩返回空结果, workflowId={}, sessionId={}",
                        request.getWorkflowId(), request.getSessionId());
            }
        } catch (RuntimeException exception) {
            long durationMs = Math.max(0, (System.nanoTime() - startNs) / 1_000_000);
            // 异常处理：记录耗时与异常原因，策略为失败返回并交由上游决定降级。
            recordDuration(durationMs);
            result.setDurationMs(durationMs);
            result.setFailureReason("EXECUTION_EXCEPTION");
            log.error("规则压缩执行异常, workflowId={}, sessionId={}",
                    request.getWorkflowId(), request.getSessionId(), exception);
        }
        return result;
    }

    /**
     * 构造压缩请求对象。
     */
    private CompressionRequest buildCompressionRequest(ContextCompressionRequest input) {
        CompressionRequest request = new CompressionRequest();
        request.setSessionId(input.getSessionId());
        request.setWorkflowId(input.getWorkflowId());
        return request;
    }

    /**
     * 记录压缩耗时指标。
     */
    private void recordDuration(long durationMs) {
        if (metricsPublisher != null) {
            metricsPublisher.recordSummary("context_compression_duration_ms", durationMs);
        }
    }
}


