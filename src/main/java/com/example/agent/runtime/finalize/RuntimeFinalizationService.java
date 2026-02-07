package com.example.agent.runtime.finalize;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.capabilities.memory.MemoryWriteService;
import com.example.agent.planning.PlanResult;
import com.example.agent.runtime.model.RuntimeResult;
import com.example.agent.runtime.model.StepResult;
import com.example.agent.runtime.output.FinalOutputService;
import com.example.agent.security.auth.TenantContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import com.example.agent.runtime.structured.result.StructuredResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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

    public RuntimeFinalizationService(FinalOutputService finalOutputService, MemoryWriteService memoryWriteService) {
        this.finalOutputService = finalOutputService;
        this.memoryWriteService = memoryWriteService;
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
        Map<String, Object> finalOutput = resolveFinalOutputFromSteps(plan, stepOutputs, workflowId);
        if (finalOutput == null) {
            log.info("最终输出服务开始, tenantId={}, workflowId={}, stepCount={}",
                    tenantContext != null ? tenantContext.getTenantId() : null,
                    workflowId,
                    stepOutputs != null ? stepOutputs.size() : 0);
            finalOutput = finalOutputService.finalizeOutput(
                    effectiveRequest,
                    effectiveRequest != null ? effectiveRequest.getQuery() : null,
                    plan != null ? plan.getSummary() : null,
                    stepOutputs,
                    tenantContext,
                    workflowId,
                    seqCounter
            );
            log.info("最终输出服务结束, tenantId={}, workflowId={}, outputKeys={}",
                    tenantContext != null ? tenantContext.getTenantId() : null,
                    workflowId,
                    finalOutput != null ? finalOutput.keySet() : List.of());
        }
        RuntimeResult result = buildRuntimeResult(plan, stepOutputs, finalOutput);
        persistMemorySafely(effectiveRequest, result, tenantContext, workflowId, taskId);
        return result;
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
        if (stepResult == null) {
            return null;
        }
        if (stepResult.getRaw() != null && stepResult.getRaw().getData() != null
                && !stepResult.getRaw().getData().isEmpty()) {
            return stepResult.getRaw().getData();
        }
        if (stepResult.getStructured() != null && stepResult.getStructured().getData() != null
                && !stepResult.getStructured().dataAsMap().isEmpty()) {
            StructuredResult<?> structured = stepResult.getStructured();
            return structured.dataAsMap();
        }
        if (stepResult.getSummary() != null) {
            Map<String, Object> stepSummary = stepResult.getSummary().getStepSummary();
            if (stepSummary != null && stepSummary.get("summary") != null) {
                return Map.of("answer", String.valueOf(stepSummary.get("summary")));
            }
        }
        return null;
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
