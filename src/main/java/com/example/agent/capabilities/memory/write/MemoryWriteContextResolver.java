package com.example.agent.capabilities.memory.write;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.capabilities.memory.config.MemoryWriteProperties;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 记忆写入上下文解析器，负责解析写入开关与会话元数据。
 */
@Component
public class MemoryWriteContextResolver {

    public static final String CONTEXT_WRITE_ENABLED = "memoryWriteEnabled";

    private final MemoryWriteProperties properties;

    public MemoryWriteContextResolver(MemoryWriteProperties properties) {
        this.properties = properties;
    }

    /**
     * 解析写入上下文快照。
     *
     * @param request 任务请求
     * @return 写入上下文
     */
    public MemoryWriteContext resolve(TaskRequest request) {
        String workflowId = readString(request != null ? request.getContext() : null, "workflowId");
        String sessionId = request != null ? request.getSessionId() : null;
        boolean enabled = resolveEnabled(request);
        return new MemoryWriteContext(enabled, workflowId, sessionId);
    }

    /**
     * 解析写入开关。
     */
    private boolean resolveEnabled(TaskRequest request) {
        if (request == null || request.getContext() == null) {
            return properties.isEnabled();
        }
        Map<String, Object> context = request.getContext();
        Object value = context.get(CONTEXT_WRITE_ENABLED);
        if (value instanceof Boolean boolValue) {
            return boolValue;
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            return Boolean.parseBoolean(text.trim());
        }
        return properties.isEnabled();
    }

    /**
     * 从上下文读取字符串字段。
     */
    public String readString(Map<String, Object> context, String key) {
        if (context == null || key == null) {
            return null;
        }
        Object value = context.get(key);
        if (value instanceof String text && StringUtils.hasText(text)) {
            return text;
        }
        return null;
    }
}

