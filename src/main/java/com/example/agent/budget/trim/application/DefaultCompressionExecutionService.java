package com.example.agent.budget.trim.application;

import com.example.agent.budget.trim.model.CompressionExecutionResult;
import com.example.agent.budget.trim.model.ContextCompressionRequest;
import com.example.agent.capabilities.memory.MemoryStore;
import com.example.agent.capabilities.memory.model.CompressionRequest;
import com.example.agent.capabilities.memory.model.MemoryRecord;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.streaming.observability.MetricsPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 默认压缩执行服务，封装记忆存储压缩调用与耗时指标采集。
 */
@Component
public class DefaultCompressionExecutionService implements CompressionExecutionService {

    private static final Logger log = LoggerFactory.getLogger(DefaultCompressionExecutionService.class);

    private final MemoryStore memoryStore;
    private final MetricsPublisher metricsPublisher;

    public DefaultCompressionExecutionService(MemoryStore memoryStore,
                                             MetricsPublisher metricsPublisher) {
        this.memoryStore = memoryStore;
        this.metricsPublisher = metricsPublisher;
    }

    @Override
    public CompressionExecutionResult execute(ContextCompressionRequest request, TenantContext tenantContext) {
        CompressionExecutionResult result = new CompressionExecutionResult();
        if (request == null || !StringUtils.hasText(request.getSessionId())) {
            return result;
        }
        if (memoryStore == null) {
            return result;
        }
        long startNs = System.nanoTime();
        log.info("上下文压缩开始, tenantId={}, workflowId={}, sessionId={}",
                resolveTenantId(tenantContext), request.getWorkflowId(), request.getSessionId());
        try {
            MemoryRecord compressed = memoryStore.compress(buildCompressionRequest(request), tenantContext);
            long durationMs = Math.max(0, (System.nanoTime() - startNs) / 1_000_000);
            metricsPublisher.recordSummary("context_compression_duration_ms", durationMs);

            result.setCompressed(compressed);
            result.setDurationMs(durationMs);
            result.setSuccess(compressed != null);

            if (compressed == null) {
                log.warn("上下文压缩失败, tenantId={}, workflowId={}, durationMs={}",
                        resolveTenantId(tenantContext), request.getWorkflowId(), durationMs);
            } else {
                log.info("上下文压缩执行完成, tenantId={}, workflowId={}, durationMs={}, memoryId={}",
                        resolveTenantId(tenantContext), request.getWorkflowId(), durationMs, compressed.getMemoryId());
            }
        } catch (RuntimeException exception) {
            long durationMs = Math.max(0, (System.nanoTime() - startNs) / 1_000_000);
            metricsPublisher.recordSummary("context_compression_duration_ms", durationMs);
            result.setDurationMs(durationMs);
            log.error("上下文压缩异常, tenantId={}, workflowId={}, durationMs={}",
                    resolveTenantId(tenantContext), request.getWorkflowId(), durationMs, exception);
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
     * 提取租户标识，避免日志中 NPE。
     */
    private String resolveTenantId(TenantContext tenantContext) {
        if (tenantContext == null) {
            return null;
        }
        return tenantContext.getTenantId();
    }
}
