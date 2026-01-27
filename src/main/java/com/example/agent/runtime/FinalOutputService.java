package com.example.agent.runtime;

import com.example.agent.auth.TenantContext;
import com.example.agent.model.ModelInvocationService;
import com.example.agent.model.ModelRequest;
import com.example.agent.model.ModelResponse;
import com.example.agent.model.ModelScene;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 最终输出生成服务，负责整合步骤结果并调用模型总结。
 */
@Service
public class FinalOutputService {

    private static final Logger log = LoggerFactory.getLogger(FinalOutputService.class);

    private final ModelInvocationService modelInvocationService;
    private final ObjectMapper objectMapper;

    public FinalOutputService(ModelInvocationService modelInvocationService, ObjectMapper objectMapper) {
        this.modelInvocationService = modelInvocationService;
        this.objectMapper = objectMapper;
    }

    /**
     * 生成最终输出。
     *
     * @param query 原始问题
     * @param planSummary 规划摘要
     * @param stepOutputs 步骤输出列表
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param seqCounter 事件序列计数器
     * @return 最终输出
     */
    public Map<String, Object> finalizeOutput(String query,
                                              String planSummary,
                                              List<Map<String, Object>> stepOutputs,
                                              TenantContext tenantContext,
                                              String workflowId,
                                              AtomicLong seqCounter) {
        String prompt = buildFinalPrompt(query, planSummary, stepOutputs);
        ModelRequest request = new ModelRequest(prompt, ModelScene.REFLECT);
        Map<String, Object> metadata = new HashMap<>();
        if (planSummary != null) {
            metadata.put("planSummary", planSummary);
        }
        ModelResponse response = modelInvocationService.invoke(
                request,
                ModelScene.REFLECT,
                tenantContext,
                workflowId,
                seqCounter,
                "finalize",
                metadata
        );
        if (response == null || response.getContent() == null) {
            return Map.of("answer", "no_response");
        }
        Map<String, Object> parsed = parseFinalOutput(response.getContent());
        if (parsed == null || parsed.isEmpty()) {
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("answer", response.getContent());
            fallback.put("modelId", response.getModelId());
            return fallback;
        }
        parsed.putIfAbsent("modelId", response.getModelId());
        return parsed;
    }

    private String buildFinalPrompt(String query, String planSummary, List<Map<String, Object>> stepOutputs) {
        Map<String, Object> context = new HashMap<>();
        context.put("query", query);
        context.put("planSummary", planSummary);
        context.put("steps", stepOutputs);
        String contextJson;
        try {
            contextJson = objectMapper.writeValueAsString(context);
        } catch (Exception ex) {
            log.warn("最终输出上下文序列化失败, reason={}", ex.getMessage());
            contextJson = "{}";
        }
        return """
                你是执行结果总结器，请基于步骤输出给出最终答复。
                输出要求：仅输出 JSON，字段包含 answer，可选 highlights、confidence。
                FINAL_CONTEXT_JSON:%s
                """.formatted(contextJson);
    }

    private Map<String, Object> parseFinalOutput(String content) {
        try {
            return objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ex) {
            return Map.of();
        }
    }
}
