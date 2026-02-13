package com.example.agent.capabilities.memory.write;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.capabilities.memory.support.MemoryTextUtils;
import com.example.agent.runtime.output.OutputKeys;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.streaming.observability.MetricsPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
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
        // 判断输出是否为空，空时直接返回空值。
        if (output == null) {
            // 返回空值，避免后续处理空指针。
            return null;
        }
        // 构建内存落盘输出载荷，移除原始输出与无关字段。
        Map<String, Object> payload = buildMemoryPayload(output);
        try {
            // 调用序列化工具将输出载荷转为 JSON 字符串。
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            // 读取租户标识用于日志追踪。
            String tenantId = tenantContext != null ? tenantContext.getTenantId() : null;
            // 读取会话标识用于日志追踪。
            String sessionId = request != null ? request.getSessionId() : null;
            // 读取工作流标识用于日志追踪。
            String workflowId = request != null ? contextResolver.readString(request.getContext(), "workflowId") : null;
            // 记录序列化失败日志，提示降级路径。
            log.warn("最终输出序列化失败，使用降级文本, tenantId={}, workflowId={}, sessionId={}, taskId={}, reason={}",
                    tenantId, workflowId, sessionId, taskId, ex.getMessage());
            // 判断指标发布器是否存在，存在时记录降级计数。
            if (metricsPublisher != null) {
                // 记录序列化降级指标。
                metricsPublisher.increment(MEMORY_WRITE_SERIALIZE_FALLBACK_TOTAL);
            }
            // 返回降级文本，避免序列化失败中断主流程。
            return String.valueOf(payload);
        }
    }

    /**
     * 构建输出摘要。
     */
    public String buildOutputSummary(String planSummary,
                                     Map<String, Object> output,
                                     String outputText) {
        // 提取语义摘要文本，作为首选摘要来源。
        String summaryText = extractSummaryText(output);
        // 判断语义摘要是否存在，存在时直接返回。
        if (StringUtils.hasText(summaryText)) {
            // 返回语义摘要文本，作为记忆摘要。
            return summaryText.trim();
        }
        // 判断规划摘要是否存在，存在时作为摘要回退。
        if (StringUtils.hasText(planSummary)) {
            // 返回规划摘要文本，补充记忆摘要。
            return planSummary.trim();
        }
        // 判断输出文本是否存在，存在时裁剪后作为摘要回退。
        if (StringUtils.hasText(outputText)) {
            // 调用裁剪工具生成简短摘要。
            return MemoryTextUtils.trimText(outputText, 200);
        }
        // 返回空值，表示无法构建摘要。
        return null;
    }

    private Map<String, Object> buildMemoryPayload(Map<String, Object> output) {
        // 初始化输出载荷容器。
        Map<String, Object> payload = new HashMap<>();
        // 读取元信息映射。
        Map<String, Object> meta = readObjectMap(output.get(OutputKeys.META));
        // 判断元信息是否为空，非空时写入载荷。
        if (!meta.isEmpty()) {
            // 写入元信息字段。
            payload.put(OutputKeys.META, meta);
        }
        // 读取结构化结果映射。
        Map<String, Object> result = readObjectMap(output.get(OutputKeys.RESULT));
        // 判断结构化结果是否为空，非空时写入载荷。
        if (!result.isEmpty()) {
            // 写入结构化结果字段。
            payload.put(OutputKeys.RESULT, result);
        }
        // 读取决策字段。
        Object decision = output.get(OutputKeys.DECISION);
        // 判断决策字段是否为空，非空时写入载荷。
        if (decision != null) {
            // 写入决策字段。
            payload.put(OutputKeys.DECISION, decision);
        }
        // 读取摘要映射。
        Map<String, Object> summary = readObjectMap(output.get(OutputKeys.SUMMARY));
        // 判断摘要映射是否为空，非空时写入载荷。
        if (!summary.isEmpty()) {
            // 写入摘要字段。
            payload.put(OutputKeys.SUMMARY, summary);
        }
        // 返回过滤后的输出载荷。
        return payload;
    }

    private String extractSummaryText(Map<String, Object> output) {
        // 判断输出是否为空，空时直接返回空值。
        if (output == null || output.isEmpty()) {
            // 返回空值，避免空指针。
            return null;
        }
        // 读取摘要映射。
        Map<String, Object> summary = readObjectMap(output.get(OutputKeys.SUMMARY));
        // 读取语义摘要文本字段。
        String text = readString(summary.get(OutputKeys.SUMMARY_TEXT));
        // 返回语义摘要文本。
        return text;
    }

    private Map<String, Object> readObjectMap(Object value) {
        // 判断值是否为映射且非空，非映射时返回空映射。
        if (!(value instanceof Map<?, ?> source) || source.isEmpty()) {
            // 返回空映射，避免空指针。
            return new HashMap<>();
        }
        // 初始化结果映射容器。
        Map<String, Object> target = new HashMap<>();
        // 循环拷贝映射元素，统一键类型。
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            // 转换键为字符串。
            String key = String.valueOf(entry.getKey());
            // 写入键值对到目标映射。
            target.put(key, entry.getValue());
        }
        // 返回拷贝后的映射。
        return target;
    }

    private String readString(Object value) {
        // 判断值是否为空，空时直接返回空值。
        if (value == null) {
            // 返回空值，避免空指针。
            return null;
        }
        // 返回字符串化后的值。
        return String.valueOf(value);
    }
}
