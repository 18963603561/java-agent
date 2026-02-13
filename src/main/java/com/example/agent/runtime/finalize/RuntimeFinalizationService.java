package com.example.agent.runtime.finalize;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.capabilities.memory.write.MemoryWriteService;
import com.example.agent.planning.PlanResult;
import com.example.agent.runtime.control.RuntimeControlEventPublisher;
import com.example.agent.runtime.model.RuntimeResult;
import com.example.agent.runtime.model.StepResult;
import com.example.agent.runtime.output.FinalOutputService;
import com.example.agent.runtime.output.OutputKeys;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.streaming.domain.EventType;
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
 * 运行时收口服务。
 *
 * <p>用途：集中处理最终输出生成、运行结果组装与记忆写入。
 * <p>输入：有效请求、规划结果、步骤输出与链路上下文。
 * <p>输出：运行时结果对象。
 * <p>边界：记忆写入失败不影响主流程，最终输出生成失败按原语义抛出。
 */
@Service
public class RuntimeFinalizationService {

    private static final Logger log = LoggerFactory.getLogger(RuntimeFinalizationService.class);

    /**
     * 最终输出服务。
     */
    private final FinalOutputService finalOutputService;

    /**
     * 记忆写入服务。
     */
    private final MemoryWriteService memoryWriteService;

    /**
     * 运行时控制事件发布器。
     */
    private final RuntimeControlEventPublisher runtimeControlEventPublisher;

    public RuntimeFinalizationService(FinalOutputService finalOutputService,
                                      MemoryWriteService memoryWriteService,
                                      RuntimeControlEventPublisher runtimeControlEventPublisher) {
        this.finalOutputService = finalOutputService;
        this.memoryWriteService = memoryWriteService;
        this.runtimeControlEventPublisher = runtimeControlEventPublisher;
    }

    /**
     * 收口空规划场景的运行结果。
     *
     * <p>输入：规划结果、步骤输出与链路上下文。
     * <p>输出：最终运行结果。
     */
    public RuntimeResult finalizeWhenPlanEmpty(TaskRequest effectiveRequest,
                                               TenantContext tenantContext,
                                               String workflowId,
                                               String taskId,
                                               PlanResult plan,
                                               List<StepResult> stepOutputs) {
        RuntimeResult result = buildRuntimeResult(plan, stepOutputs, null);
        persistMemorySafely(effectiveRequest, result, tenantContext, workflowId, taskId);
        return result;
    }

    /**
     * 收口完整执行场景的运行结果。
     *
     * <p>输入：规划结果、步骤输出与链路上下文。
     * <p>输出：最终运行结果。
     */
    public RuntimeResult finalizeRun(TaskRequest effectiveRequest,
                                     TenantContext tenantContext,
                                     String workflowId,
                                     String taskId,
                                     AtomicLong seqCounter,
                                     PlanResult plan,
                                     List<StepResult> stepOutputs) {
        // 尝试从最后一步直接解析最终输出。
        Map<String, Object> finalOutput = resolveFinalOutputFromSteps(plan, stepOutputs, workflowId);
        // 判断是否命中直出结果，未命中则调用最终输出服务。
        if (finalOutput == null) {
            // 记录最终输出服务开始日志。
            log.info("最终输出服务开始, tenantId={}, workflowId={}, stepCount={}",
                    tenantContext != null ? tenantContext.getTenantId() : null,
                    workflowId,
                    stepOutputs != null ? stepOutputs.size() : 0);
            // 调用最终输出服务生成结果。
            finalOutput = finalOutputService.finalizeOutput(
                    effectiveRequest,
                    effectiveRequest != null ? effectiveRequest.getQuery() : null,
                    plan != null ? plan.getSummary() : null,
                    stepOutputs,
                    tenantContext,
                    workflowId,
                    seqCounter
            );
            // 记录最终输出服务结束日志。
            log.info("最终输出服务结束, tenantId={}, workflowId={}, outputKeys={}",
                    tenantContext != null ? tenantContext.getTenantId() : null,
                    workflowId,
                    finalOutput != null ? finalOutput.keySet() : List.of());
        }
        // 按输出协议封装最终输出。
        Map<String, Object> wrappedOutput = wrapFinalOutput(finalOutput, plan);
        // 组装运行结果对象。
        RuntimeResult result = buildRuntimeResult(plan, stepOutputs, wrappedOutput);
        // 发布最终输出事件。
        publishLlmOutputEvent(tenantContext, workflowId, seqCounter, plan, stepOutputs, wrappedOutput);
        // 保护性写入记忆，失败不影响主流程。
        persistMemorySafely(effectiveRequest, result, tenantContext, workflowId, taskId);
        return result;
    }

    /**
     * 发布最终输出事件。
     * <p>用途：在运行时收口阶段统一发布 {@link EventType#LLM_OUTPUT}，补齐最终答案事件闭环。</p>
     *
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param seqCounter 序列计数器
     * @param plan 规划结果
     * @param stepOutputs 步骤输出
     * @param finalOutput 最终输出
     */
    private void publishLlmOutputEvent(TenantContext tenantContext,
                                       String workflowId,
                                       AtomicLong seqCounter,
                                       PlanResult plan,
                                       List<StepResult> stepOutputs,
                                       Map<String, Object> finalOutput) {
        // 判断事件发布器是否为空，空时直接返回。
        if (runtimeControlEventPublisher == null) {
            // 返回空值，避免空指针。
            return;
        }
        // 判断上下文参数是否完整，不完整时跳过发布。
        if (tenantContext == null || workflowId == null || workflowId.isBlank() || seqCounter == null) {
            // 记录上下文不完整日志，跳过发布。
            log.warn("跳过LLM_OUTPUT事件发布，上下文不完整, tenantId={}, workflowId={}, hasSeqCounter={}",
                    tenantContext != null ? tenantContext.getTenantId() : null,
                    workflowId,
                    seqCounter != null);
            return;
        }
        // 判断最终输出是否为空，空时跳过发布。
        if (finalOutput == null || finalOutput.isEmpty()) {
            // 记录最终输出为空日志，跳过发布。
            log.debug("跳过LLM_OUTPUT事件发布，最终输出为空, tenantId={}, workflowId={}",
                    tenantContext.getTenantId(), workflowId);
            return;
        }

        // 构建事件负载容器。
        Map<String, Object> payload = new HashMap<>();
        // 解析最终响应文本并写入负载。
        payload.put("response", resolveFinalResponse(finalOutput));

        // 构建事件元数据容器。
        Map<String, Object> metadata = new HashMap<>();
        // 写入步骤数量字段。
        metadata.put("stepCount", stepOutputs != null ? stepOutputs.size() : 0);
        // 判断规划结果是否存在，存在时写入规划字段。
        if (plan != null) {
            // 判断规划标识是否为空，非空时写入字段。
            if (plan.getPlanId() != null) {
                metadata.put("planId", plan.getPlanId());
            }
            // 判断规划摘要是否为空，非空时写入字段。
            if (plan.getSummary() != null) {
                metadata.put("planSummary", plan.getSummary());
            }
        }
        // 读取协议元信息与结果映射，提取元数据字段。
        Map<String, Object> meta = readObjectMap(finalOutput.get(OutputKeys.META));
        Map<String, Object> result = readObjectMap(finalOutput.get(OutputKeys.RESULT));
        // 写入模型标识字段。
        appendMetadataField(metadata, "modelId", meta.get("modelId"));
        // 写入置信度字段。
        appendMetadataField(metadata, "confidence", result.get("confidence"));
        // 写入高亮字段。
        appendMetadataField(metadata, "highlights", result.get("highlights"));
        // 写入原始引用字段。
        appendMetadataField(metadata, "rawRef", meta.get(OutputKeys.RAW_REF));
        // 写入元数据到负载。
        payload.put("metadata", metadata);

        // 发布 LLM_OUTPUT 事件。
        runtimeControlEventPublisher.publish(
                tenantContext,
                workflowId,
                seqCounter,
                EventType.LLM_OUTPUT,
                payload
        );
        // 记录事件发布完成日志。
        log.info("发布LLM_OUTPUT事件, tenantId={}, workflowId={}, payloadKeys={}",
                tenantContext.getTenantId(), workflowId, payload.keySet());
    }

    /**
     * 解析最终输出中的响应文本。
     */
    private Object resolveFinalResponse(Map<String, Object> finalOutput) {
        // 判断最终输出是否为空，空时直接返回空值。
        if (finalOutput == null || finalOutput.isEmpty()) {
            // 返回空值，避免空指针。
            return null;
        }
        // 读取协议 result 字段映射。
        Map<String, Object> result = readObjectMap(finalOutput.get(OutputKeys.RESULT));
        // 优先从 result.answer 读取最终答案。
        Object answer = result.get("answer");
        if (answer != null) {
            // 返回 answer 字段作为响应。
            return answer;
        }
        // 若 answer 为空则读取 result.finalAnswer。
        Object finalAnswer = result.get("finalAnswer");
        if (finalAnswer != null) {
            // 返回 finalAnswer 字段作为响应。
            return finalAnswer;
        }
        // 兜底从顶层字段读取答案。
        Object legacyAnswer = finalOutput.get("answer");
        if (legacyAnswer != null) {
            // 返回旧字段 answer 作为响应。
            return legacyAnswer;
        }
        // 返回完整最终输出作为兜底响应。
        return finalOutput;
    }

    /**
     * 追加元数据字段（仅在值不为空时写入）。
     */
    private void appendMetadataField(Map<String, Object> metadata,
                                     String key,
                                     Object value) {
        if (metadata == null || key == null || key.isBlank() || value == null) {
            return;
        }
        metadata.put(key, value);
    }

    /**
     * 保护性写入记忆，失败不影响主流程。
     */
    private void persistMemorySafely(TaskRequest request,
                                     RuntimeResult result,
                                     TenantContext tenantContext,
                                     String workflowId,
                                     String taskId) {
        try {
            memoryWriteService.saveTaskMemory(request, result, tenantContext, taskId);
        } catch (Exception ex) {
            log.error("记忆写入异常, tenantId={}, workflowId={}, taskId={}",
                    tenantContext != null ? tenantContext.getTenantId() : null,
                    workflowId,
                    taskId,
                    ex);
        }
    }

    /**
     * 基于最后一个大模型步骤尝试提取最终输出。
     */
    private Map<String, Object> resolveFinalOutputFromSteps(PlanResult plan,
                                                            List<StepResult> stepOutputs,
                                                            String workflowId) {
        if (plan == null || plan.getSteps() == null || plan.getSteps().isEmpty()) {
            return null;
        }
        var lastStep = plan.getSteps().get(plan.getSteps().size() - 1);
        if (lastStep == null || lastStep.getStepType() == null) {
            return null;
        }
        String stepType = lastStep.getStepType();
        if (!"LLM".equalsIgnoreCase(stepType) && !"ANSWER".equalsIgnoreCase(stepType)) {
            return null;
        }
        if (stepOutputs == null || stepOutputs.isEmpty()) {
            return null;
        }
        StepResult lastStepResult = stepOutputs.get(stepOutputs.size() - 1);
        if (lastStepResult == null) {
            return null;
        }
        Object output = extractFinalOutput(lastStepResult);
        if (output == null) {
            return null;
        }
        Map<String, Object> result = new HashMap<>();
        if (output instanceof Map<?, ?> map) {
            map.forEach((key, value) -> result.put(String.valueOf(key), value));
        } else {
            result.put("answer", output.toString());
        }
        if (!result.containsKey("answer") && result.get("finalAnswer") != null) {
            result.put("answer", result.get("finalAnswer"));
        }
        log.info("大模型步骤已产出最终结果, 工作流={}, 步骤类型={}, 输出字段={}",
                workflowId,
                stepType,
                result.keySet());
        return result;
    }

    /**
     * 从步骤结果中提取最终输出候选。
     */
    private Object extractFinalOutput(StepResult stepResult) {
        // 判断步骤结果是否为空，空时直接返回。
        if (stepResult == null) {
            // 返回空值，避免空指针。
            return null;
        }
        // 判断结构化结果是否存在且包含数据，存在时返回结构化数据。
        if (stepResult.getResult() != null && stepResult.getResult().getData() != null
                && !stepResult.getResult().dataAsMap().isEmpty()) {
            // 返回结构化结果数据映射。
            return stepResult.getResult().dataAsMap();
        }
        // 判断语义摘要是否存在，存在时回退为摘要文本。
        if (stepResult.getSummary() != null && StringUtils.hasText(stepResult.getSummary().getText())) {
            // 返回摘要文本作为最终输出兜底。
            return Map.of("answer", stepResult.getSummary().getText());
        }
        return null;
    }

    private Map<String, Object> wrapFinalOutput(Map<String, Object> finalOutput, PlanResult plan) {
        // 判断最终输出是否为空，空时直接返回空值。
        if (finalOutput == null || finalOutput.isEmpty()) {
            // 返回空值，避免空指针。
            return finalOutput;
        }
        // 判断是否已是协议结构，已是协议结构则直接返回。
        if (finalOutput.containsKey(OutputKeys.META) || finalOutput.containsKey(OutputKeys.RESULT)) {
            // 返回原始输出，避免重复封装。
            return finalOutput;
        }
        // 构建元信息映射。
        Map<String, Object> meta = buildMetaFromFinalOutput(finalOutput, plan);
        // 构建结果映射，移除元信息字段。
        Map<String, Object> result = buildResultFromFinalOutput(finalOutput);
        // 构建语义摘要映射。
        Map<String, Object> summary = buildSummaryFromResult(result);

        // 组装输出协议映射并返回。
        Map<String, Object> wrapped = new HashMap<>();
        // 写入元信息字段。
        wrapped.put(OutputKeys.META, meta);
        // 写入结果字段。
        wrapped.put(OutputKeys.RESULT, result);
        // 写入决策字段（当前为空）。
        wrapped.put(OutputKeys.DECISION, null);
        // 判断摘要是否为空，非空时写入摘要字段。
        if (summary != null && !summary.isEmpty()) {
            // 写入语义摘要字段。
            wrapped.put(OutputKeys.SUMMARY, summary);
        }
        // 返回封装后的最终输出。
        return wrapped;
    }

    private Map<String, Object> buildMetaFromFinalOutput(Map<String, Object> finalOutput, PlanResult plan) {
        // 初始化元信息容器。
        Map<String, Object> meta = new HashMap<>();
        // 判断规划结果是否存在，存在时写入规划字段。
        if (plan != null) {
            // 写入规划标识字段。
            meta.put("planId", plan.getPlanId());
            // 写入规划摘要字段。
            meta.put("planSummary", plan.getSummary());
        }
        // 读取模型标识并写入元信息。
        Object modelId = finalOutput.get("modelId");
        // 判断模型标识是否为空，非空时写入元信息。
        if (modelId != null) {
            // 写入模型标识字段。
            meta.put("modelId", modelId);
        }
        // 读取原始引用并写入元信息。
        Object rawRef = finalOutput.get(OutputKeys.RAW_REF);
        // 判断原始引用是否为空，非空时写入元信息。
        if (rawRef != null) {
            // 写入原始引用字段。
            meta.put(OutputKeys.RAW_REF, rawRef);
        }
        // 返回构建后的元信息映射。
        return meta;
    }

    private Map<String, Object> buildResultFromFinalOutput(Map<String, Object> finalOutput) {
        // 初始化结果容器。
        Map<String, Object> result = new HashMap<>();
        // 遍历最终输出映射，过滤元信息字段。
        finalOutput.forEach((key, value) -> {
            // 判断是否为元信息字段，元信息字段直接跳过。
            if (!"modelId".equals(key) && !OutputKeys.RAW_REF.equals(key)) {
                // 写入结果字段到结果映射。
                result.put(String.valueOf(key), value);
            }
        });
        // 返回过滤后的结果映射。
        return result;
    }

    private Map<String, Object> buildSummaryFromResult(Map<String, Object> result) {
        // 初始化摘要容器。
        Map<String, Object> summary = new HashMap<>();
        // 判断结果映射是否为空，空时直接返回空摘要。
        if (result == null || result.isEmpty()) {
            // 返回空摘要，避免空指针。
            return summary;
        }
        // 读取摘要文本字段，优先使用 answer。
        String text = readString(result.get("answer"));
        // 判断摘要文本是否为空，空时回退到 finalAnswer。
        if (!StringUtils.hasText(text)) {
            text = readString(result.get("finalAnswer"));
        }
        // 摘要文本存在时写入摘要字段。
        if (StringUtils.hasText(text)) {
            // 写入语义摘要文本字段。
            summary.put(OutputKeys.SUMMARY_TEXT, text);
        }
        // 读取高亮信息并写入摘要字段。
        List<String> highlights = readStringList(result.get("highlights"));
        // 判断高亮列表是否为空，非空时写入摘要字段。
        if (!highlights.isEmpty()) {
            // 写入高亮列表字段。
            summary.put(OutputKeys.SUMMARY_HIGHLIGHTS, highlights);
        }
        // 写入截断标记，默认未截断。
        summary.put(OutputKeys.TRUNCATED, false);
        // 返回构建后的摘要映射。
        return summary;
    }

    private List<String> readStringList(Object value) {
        // 初始化列表容器。
        List<String> items = new ArrayList<>();
        // 判断值是否为字符串，字符串时作为单元素列表返回。
        if (value instanceof String text && StringUtils.hasText(text)) {
            // 写入单条高亮文本到列表。
            items.add(text);
            // 返回单元素列表结果。
            return items;
        }
        // 判断值是否为列表，列表时逐项转换。
        if (value instanceof List<?> list && !list.isEmpty()) {
            // 循环遍历列表元素，逐项转换。
            for (Object item : list) {
                // 判断元素是否为空，非空时写入列表。
                if (item != null) {
                    // 写入高亮元素到列表。
                    items.add(String.valueOf(item));
                }
            }
        }
        // 返回转换后的字符串列表。
        return items;
    }

    private Map<String, Object> readObjectMap(Object value) {
        // 判断值是否为非空映射，非映射时返回可写空容器。
        if (!(value instanceof Map<?, ?> source) || source.isEmpty()) {
            // 返回可写空映射，避免后续写入异常。
            return new HashMap<>();
        }
        // 初始化目标映射，用于拷贝并统一键类型。
        Map<String, Object> target = new HashMap<>();
        // 循环遍历原始映射，逐项拷贝到目标映射。
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            // 转换键为字符串，保证键类型一致。
            String key = String.valueOf(entry.getKey());
            // 写入键值，保持值原样写入。
            target.put(key, entry.getValue());
        }
        // 返回拷贝后的映射结果。
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

    /**
     * 组装运行时结果对象。
     */
    private RuntimeResult buildRuntimeResult(PlanResult plan,
                                             List<StepResult> stepOutputs,
                                             Map<String, Object> finalOutput) {
        RuntimeResult result = new RuntimeResult();
        if (plan != null) {
            result.setPlanId(plan.getPlanId());
            result.setPlanSummary(plan.getSummary());
        }
        result.setSteps(stepOutputs);
        result.setFinalOutput(finalOutput);
        return result;
    }
}
