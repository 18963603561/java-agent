package com.example.agent.runtime;

import com.example.agent.auth.TenantContext;
import com.example.agent.common.TaskRequest;
import com.example.agent.model.ModelInvocationService;
import com.example.agent.model.ModelRequest;
import com.example.agent.model.ModelResponse;
import com.example.agent.model.ModelScene;
import com.example.agent.model.PromptAssembler;
import com.example.agent.model.PromptBundle;
import com.example.agent.repair.JsonOutputRepairService;
import com.example.agent.repair.JsonOutputSchema;
import com.example.agent.model.PromptTrace;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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
    private static final int DEFAULT_PROMPT_SUMMARY_MAX_CHARS = 800;
    private static final String SUMMARY_TRUNCATED_SUFFIX = "...(truncated)";

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
    private final JsonOutputRepairService jsonOutputRepairService;
    /**
     * 最终输出提示词摘要限制配置。
     */
    private final FinalOutputProperties finalOutputProperties;
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
                              ObjectMapper objectMapper,
                              JsonOutputRepairService jsonOutputRepairService,
                              FinalOutputProperties finalOutputProperties) {
        this.modelInvocationService = modelInvocationService;
        this.promptAssembler = promptAssembler;
        this.objectMapper = objectMapper;
        this.jsonOutputRepairService = jsonOutputRepairService;
        this.finalOutputProperties = finalOutputProperties;
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
        metadata.put("promptScene", "final");
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
        String rawContent = response.getContent();
        boolean repairAttempted = false;
        boolean repairSuccess = false;
        String parseErrorType = null;
        Map<String, Object> parsed = parseFinalOutput(rawContent);
        if (parsed == null || parsed.isEmpty()) {
            parseErrorType = resolveParseErrorType(rawContent);
            repairAttempted = true;
            Map<String, Object> repaired = tryRepairFinalOutput(rawContent, query, planSummary, stepOutputs);
            if (repaired != null && !repaired.isEmpty()) {
                parsed = repaired;
                repairSuccess = true;
            }
        }
        if (parsed == null || parsed.isEmpty()) {
            log.warn("最终输出修复失败, workflowId={}, modelId={}", workflowId, response.getModelId());
            recordPromptTrace(metadata, prompt, tenantContext, workflowId, seqCounter, response.getModelId(), false,
                    parseErrorType, repairAttempted, repairSuccess);
            // 解析失败时回退为原始文本输出。
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("answer", response.getContent());
            fallback.put("modelId", response.getModelId());
            return fallback;
        }
        if (!parsed.containsKey("answer")) {
            parseErrorType = "missing_field";
        }
        recordPromptTrace(metadata, prompt, tenantContext, workflowId, seqCounter, response.getModelId(), true,
                parseErrorType,
                repairAttempted, repairSuccess);
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
        context.put("steps", buildStepSummaries(stepOutputs));
        String contextJson;
        try {
            contextJson = objectMapper.writeValueAsString(context);
        // 异常捕获：记录上下文并按当前策略处理
        } catch (Exception ex) {
            log.warn("最终输出上下文序列化失败, reason={}", ex.getMessage());
            contextJson = "{}";
        }
        return """
                你是执行结果总结器（final answer writer）。
                你的任务：根据 FINAL_CONTEXT_JSON 中的 query（用户问题）与 steps（执行步骤输出）生成最终答复。
                
                输出必须是单个 JSON 对象，不允许任何额外文本，不允许 Markdown/代码块。
                字段约束：
                1) answer: string，必须输出。必须围绕 query 给出最终结论；如果 steps 没有提供可用结果，明确说明“未执行/无数据/缺少步骤输出”，并指出下一步需要什么。
                2) highlights: string，必须输出。用一句话概括关键证据（例如：命中数量、关键字段、失败原因、使用了哪些步骤/工具）。
                3) confidence: number，必须输出。依据 steps 证据充足度：有完整结果集可取 0.7~0.95；只有部分信息 0.3~0.6；steps 为空或无有效输出 0。
                
                禁止编造：不得凭空生成查询结果或用户列表；只能基于 steps 中的输出数据。
                
                最小示例 JSON：{"answer":"","highlights":"","confidence":0}
                FINAL_CONTEXT_JSON:%s
                """.formatted(contextJson);

    }

    private Map<String, Object> tryRepairFinalOutput(String rawContent,
                                                     String query,
                                                     String planSummary,
                                                     List<Map<String, Object>> stepOutputs) {
        if (jsonOutputRepairService == null || !StringUtils.hasText(rawContent)) {
            return Map.of();
        }
        Map<String, Object> context = new HashMap<>();
        context.put("query", query);
        context.put("planSummary", planSummary);
        context.put("steps", buildStepSummaries(stepOutputs));
        String contextJson;
        try {
            contextJson = objectMapper.writeValueAsString(context);
        // 异常捕获：记录上下文并按当前策略处理
        } catch (Exception ex) {
            contextJson = "{}";
        }
        String repaired = jsonOutputRepairService.repair("final", rawContent, JsonOutputSchema.FINAL, contextJson, 1);
        if (!StringUtils.hasText(repaired)) {
            return Map.of();
        }
        return parseFinalOutput(repaired);
    }

    /**
     * 生成仅包含摘要的步骤列表，避免提示词注入原始输出。
     */
    private List<Map<String, Object>> buildStepSummaries(List<Map<String, Object>> stepOutputs) {
        if (stepOutputs == null || stepOutputs.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> summaries = new ArrayList<>();
        for (Map<String, Object> step : stepOutputs) {
            if (step == null) {
                continue;
            }
            Map<String, Object> summary = new HashMap<>();
            summary.put("stepId", toText(step.get("stepId")));
            summary.put("type", toText(step.get("type")));
            StepSummaryData data = resolveStepSummaryData(step.get("summary"));
            summary.put("status", data.status);
            summary.put("summary", data.summary);
            summaries.add(summary);
        }
        return summaries;
    }

    /**
     * 从摘要中提取摘要与状态，优先使用 stepSummary.summary。
     */
    private StepSummaryData resolveStepSummaryData(Object summarySource) {
        StepSummaryData data = new StepSummaryData();
        if (summarySource instanceof Map<?, ?> map) {
            Map<?, ?> summaryMap = map;
            Object stepSummaryObj = summaryMap.get("stepSummary");
            if (stepSummaryObj instanceof Map<?, ?> stepSummary) {
                data.status = toText(stepSummary.get("status"));
                Object summaryValue = stepSummary.get("summary");
                if (summaryValue != null && StringUtils.hasText(summaryValue.toString())) {
                    data.summary = summaryValue.toString();
                } else if (!stepSummary.isEmpty()) {
                    data.summary = toJsonSafe(stepSummary);
                }
            }
            if (!StringUtils.hasText(data.status)) {
                Object outputSummaryObj = summaryMap.get("outputSummary");
                if (outputSummaryObj instanceof Map<?, ?> outputSummary) {
                    data.status = toText(outputSummary.get("status"));
                }
            }
            if (!StringUtils.hasText(data.summary)) {
                Object digestObj = summaryMap.get("outputDigest");
                if (digestObj instanceof Map<?, ?> digest) {
                    data.summary = buildDigestSummary(digest);
                }
            }
        }
        if (!StringUtils.hasText(data.summary)) {
            data.summary = "(summary disabled)";
        }
        data.summary = truncateSummary(data.summary);
        return data;
    }

    private String buildDigestSummary(Map<?, ?> digest) {
        if (digest == null || digest.isEmpty()) {
            return null;
        }
        StringBuilder builder = new StringBuilder("digest");
        appendDigestField(builder, "keyCount", digest.get("keyCount"));
        appendDigestField(builder, "charCount", digest.get("charCount"));
        appendDigestField(builder, "truncated", digest.get("truncated"));
        return builder.toString();
    }

    private void appendDigestField(StringBuilder builder, String field, Object value) {
        if (value == null) {
            return;
        }
        builder.append(' ').append(field).append('=').append(value);
    }

    private String toJsonSafe(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        // 异常捕获：记录上下文并按当前策略处理
        } catch (Exception ex) {
            return String.valueOf(value);
        }
    }

    private String truncateSummary(String text) {
        if (!StringUtils.hasText(text)) {
            return text;
        }
        int maxChars = resolvePromptSummaryMaxChars();
        if (maxChars <= 0) {
            return text;
        }
        if (text.length() <= maxChars) {
            return text;
        }
        if (maxChars <= SUMMARY_TRUNCATED_SUFFIX.length()) {
            return text.substring(0, maxChars);
        }
        int endIndex = maxChars - SUMMARY_TRUNCATED_SUFFIX.length();
        if (endIndex <= 0) {
            return text.substring(0, maxChars);
        }
        return text.substring(0, endIndex) + SUMMARY_TRUNCATED_SUFFIX;
    }

    private int resolvePromptSummaryMaxChars() {
        if (finalOutputProperties == null) {
            return DEFAULT_PROMPT_SUMMARY_MAX_CHARS;
        }
        return finalOutputProperties.getPromptSummaryMaxChars();
    }

    private String toText(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private void recordPromptTrace(Map<String, Object> metadata,
                                   String promptText,
                                   TenantContext tenantContext,
                                   String workflowId,
                                   AtomicLong seqCounter,
                                   String modelId,
                                   boolean parseSuccess,
                                   String parseErrorType,
                                   boolean repairAttempted,
                                   boolean repairSuccess) {
        PromptTrace trace = PromptTrace.fromMetadata(metadata);
        if (trace == null) {
            trace = PromptTrace.fromPrompt("final", promptText);
        }
        if (trace == null) {
            return;
        }
        trace.setParseSuccess(parseSuccess);
        trace.setParseErrorType(parseErrorType);
        trace.setRepairAttempted(repairAttempted);
        trace.setRepairSuccess(repairSuccess);
        modelInvocationService.recordPromptTrace(trace, tenantContext, workflowId, seqCounter, "final", modelId);
    }

    private String resolveParseErrorType(String rawContent) {
        if (!StringUtils.hasText(rawContent)) {
            return "empty_output";
        }
        return "json_parse_error";
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
        // 异常捕获：记录上下文并按当前策略处理
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

    private static final class StepSummaryData {
        private String status;
        private String summary;
    }
}
