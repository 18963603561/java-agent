package com.example.agent.runtime;

import com.example.agent.auth.TenantContext;
import com.example.agent.common.TaskRequest;
import com.example.agent.model.ModelInvocationService;
import com.example.agent.model.ModelRequest;
import com.example.agent.model.ModelResponse;
import com.example.agent.model.ModelScene;
import com.example.agent.model.PromptAssembler;
import com.example.agent.model.PromptBundle;
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
 * <p>用途：将步骤输出汇总为最终答复，并附加模型标识与置信度。
 * <p>输入：任务请求、问题、规划摘要与步骤输出。
 * <p>输出：结构化的最终结果映射。
 * <p>边界：模型响应为空时返回兜底输出。
 * <p>示例：
 * <pre>{@code
 * Map<String, Object> output = finalOutputService.finalizeOutput(request, query, summary, steps, ctx, wfId, seq);
 * }</pre>
 */
@Service
public class FinalOutputService {

    /**
     * 日志记录器。
     * <p>示例：记录上下文序列化失败信息。
     */
    private static final Logger log = LoggerFactory.getLogger(FinalOutputService.class);

    /**
     * 模型调用协调器。
     * <p>示例：调用模型生成最终答复。
     */
    private final ModelInvocationService modelInvocationService;
    /**
     * 提示词装配器。
     * <p>示例：将提示词转换为消息序列。
     */
    private final PromptAssembler promptAssembler;
    /**
     * 序列化工具。
     * <p>示例：将上下文转为 {@code JSON} 字符串。
     */
    private final ObjectMapper objectMapper;

    /**
     * 构造最终输出服务。
     *
     * <p>输入：模型调用协调器、提示词装配器与序列化工具。
     * <p>输出：初始化后的服务实例。
     * <p>示例：
     * <pre>{@code
     * new FinalOutputService(modelInvocationService, promptAssembler, objectMapper);
     * }</pre>
     *
     * @param modelInvocationService 模型调用协调器
     * @param promptAssembler 提示词装配器
     * @param objectMapper 序列化工具
     */
    public FinalOutputService(ModelInvocationService modelInvocationService,
                              PromptAssembler promptAssembler,
                              ObjectMapper objectMapper) {
        this.modelInvocationService = modelInvocationService;
        this.promptAssembler = promptAssembler;
        this.objectMapper = objectMapper;
    }

    /**
     * 生成最终输出。
     *
     * <p>输入：任务请求、问题、规划摘要与步骤输出。
     * <p>输出：结构化结果映射。
     * <p>边界：模型响应为空时返回兜底输出。
     * <p>示例：
     * <pre>{@code
     * Map<String, Object> output = finalizeOutput(request, query, summary, steps, ctx, wfId, seq);
     * }</pre>
     *
     * @param taskRequest 任务请求
     * @param query 原始问题
     * @param planSummary 规划摘要
     * @param stepOutputs 步骤输出列表
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param seqCounter 事件序列计数器
     * @return 最终输出
     */
    public Map<String, Object> finalizeOutput(TaskRequest taskRequest,
                                              String query,
                                              String planSummary,
                                              List<Map<String, Object>> stepOutputs,
                                              TenantContext tenantContext,
                                              String workflowId,
                                              AtomicLong seqCounter) {
        // 生成最终输出提示词。
        String prompt = buildFinalPrompt(query, planSummary, stepOutputs);
        ModelRequest request = new ModelRequest(prompt, ModelScene.REFLECT);
        // 应用提示词装配器，注入消息结构。
        applyPromptBundle(request, prompt, taskRequest);
        Map<String, Object> metadata = new HashMap<>();
        if (planSummary != null) {
            metadata.put("planSummary", planSummary);
        }
        // 调用模型生成最终输出。
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
        // 解析模型输出为结构化映射。
        Map<String, Object> parsed = parseFinalOutput(response.getContent());
        if (parsed == null || parsed.isEmpty()) {
            // 解析失败时回退为原始文本输出。
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("answer", response.getContent());
            fallback.put("modelId", response.getModelId());
            return fallback;
        }
        parsed.putIfAbsent("modelId", response.getModelId());
        return parsed;
    }

    /**
     * 兼容旧接口的输出生成方法。
     *
     * <p>输入：问题、规划摘要与步骤输出。
     * <p>输出：结构化结果映射。
     * <p>示例：
     * <pre>{@code
     * Map<String, Object> output = finalizeOutput(query, summary, steps, ctx, wfId, seq);
     * }</pre>
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
        return finalizeOutput(null, query, planSummary, stepOutputs, tenantContext, workflowId, seqCounter);
    }

    /**
     * 构建最终输出提示词。
     *
     * <p>输入：问题、规划摘要与步骤输出。
     * <p>输出：提示词字符串。
     * <p>边界：序列化失败时返回空上下文。
     * <p>示例：
     * <pre>{@code
     * String prompt = buildFinalPrompt(query, summary, steps);
     * }</pre>
     */
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

    /**
     * 解析最终输出的结构化内容。
     *
     * <p>输入：模型输出内容。
     * <p>输出：结构化映射对象。
     * <p>边界：解析失败时返回空映射。
     * <p>示例：
     * <pre>{@code
     * Map<String, Object> parsed = parseFinalOutput(content);
     * }</pre>
     */
    private Map<String, Object> parseFinalOutput(String content) {
        try {
            return objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ex) {
            return Map.of();
        }
    }

    /**
     * 应用提示词装配器，将提示内容转换为消息格式。
     *
     * <p>输入：模型请求、提示词与任务请求。
     * <p>输出：无。
     * <p>边界：装配器为空时直接返回。
     * <p>示例：
     * <pre>{@code
     * applyPromptBundle(request, prompt, taskRequest);
     * }</pre>
     */
    private void applyPromptBundle(ModelRequest request, String prompt, TaskRequest taskRequest) {
        if (promptAssembler == null || request == null) {
            return;
        }
        PromptBundle bundle = promptAssembler.build(prompt, taskRequest, null);
        if (bundle != null && bundle.getMessages() != null) {
            request.setMessages(bundle.getMessages());
        }
    }
}
