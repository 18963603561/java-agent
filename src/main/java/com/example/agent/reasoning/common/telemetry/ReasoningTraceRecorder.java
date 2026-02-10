package com.example.agent.reasoning.common.telemetry;

import com.example.agent.capabilities.llm.client.ModelInvocationService;
import com.example.agent.capabilities.llm.prompt.PromptTrace;
import com.example.agent.security.auth.TenantContext;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * 推理 Trace 记录器。
 *
 * <p>用途：统一记录推理策略的提示词解析结果与修复状态，避免策略实现重复拼装 Trace 代码。
 */
@Component
public class ReasoningTraceRecorder {

    private final ModelInvocationService modelInvocationService;

    /**
     * 构造推理 Trace 记录器。
     *
     * @param modelInvocationService 模型调用服务
     */
    public ReasoningTraceRecorder(ModelInvocationService modelInvocationService) {
        this.modelInvocationService = modelInvocationService;
    }

    /**
     * 记录推理策略 Trace。
     *
     * @param strategyType 推理策略类型
     * @param metadata 调用元数据
     * @param promptText 提示词文本
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param seqCounter 事件序列计数器
     * @param modelId 模型标识
     * @param parseSuccess 解析是否成功
     * @param parseErrorType 解析错误类型
     * @param repairAttempted 是否尝试修复
     * @param repairSuccess 修复是否成功
     */
    public void record(String strategyType,
                       Map<String, Object> metadata,
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
            trace = PromptTrace.fromPrompt(strategyType, promptText);
        }
        if (trace == null) {
            return;
        }
        trace.setParseSuccess(parseSuccess);
        trace.setParseErrorType(parseErrorType);
        trace.setRepairAttempted(repairAttempted);
        trace.setRepairSuccess(repairSuccess);
        modelInvocationService.recordPromptTrace(
                trace,
                tenantContext,
                workflowId,
                seqCounter,
                strategyType,
                modelId
        );
    }
}

