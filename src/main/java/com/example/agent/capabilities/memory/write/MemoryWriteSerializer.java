package com.example.agent.capabilities.memory.write;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.capabilities.memory.support.MemoryTextUtils;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.streaming.observability.MetricsPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 记忆写入序列化器，负责最终输出序列化与降级观测。
 */
@Component
public class MemoryWriteSerializer {

    private static final Logger log = LoggerFactory.getLogger(MemoryWriteSerializer.class);
    private static final String MEMORY_WRITE_SERIALIZE_FALLBACK_TOTAL =
            "memory_write_output_serialize_fallback_total";

    private final ObjectMapper objectMapper;
    private final MetricsPublisher metricsPublisher;
    private final MemoryWriteContextResolver contextResolver;

    public MemoryWriteSerializer(ObjectMapper objectMapper,
                                 MetricsPublisher metricsPublisher,
                                 MemoryWriteContextResolver contextResolver) {
        this.objectMapper = objectMapper;
        this.metricsPublisher = metricsPublisher;
        this.contextResolver = contextResolver;
    }

    /**
     * 序列化最终输出，失败时记录降级日志并返回兜底文本。
     */
    public String serializeOutput(Map<String, Object> output,
                                  TenantContext tenantContext,
                                  TaskRequest request,
                                  String taskId) {
        if (output == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(output);
        } catch (Exception ex) {
            String tenantId = tenantContext != null ? tenantContext.getTenantId() : null;
            String sessionId = request != null ? request.getSessionId() : null;
            String workflowId = request != null ? contextResolver.readString(request.getContext(), "workflowId") : null;
            log.warn("最终输出序列化失败，使用降级文本, tenantId={}, workflowId={}, sessionId={}, taskId={}, reason={}",
                    tenantId, workflowId, sessionId, taskId, ex.getMessage());
            if (metricsPublisher != null) {
                metricsPublisher.increment(MEMORY_WRITE_SERIALIZE_FALLBACK_TOTAL);
            }
            return String.valueOf(output);
        }
    }

    /**
     * 构建输出摘要。
     */
    public String buildOutputSummary(String planSummary, String outputText) {
        StringBuilder builder = new StringBuilder();
        if (StringUtils.hasText(planSummary)) {
            builder.append("planSummary: ").append(planSummary.trim());
        }
        if (StringUtils.hasText(outputText)) {
            if (builder.length() > 0) {
                builder.append(" | ");
            }
            builder.append("finalOutput: ").append(MemoryTextUtils.trimText(outputText, 200));
        }
        String summary = builder.toString().trim();
        return summary.isEmpty() ? null : summary;
    }
}
