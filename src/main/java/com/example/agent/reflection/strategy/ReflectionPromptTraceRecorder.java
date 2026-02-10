package com.example.agent.reflection.strategy;

import com.example.agent.capabilities.llm.client.ModelInvocationService;
import com.example.agent.capabilities.llm.prompt.PromptTrace;
import com.example.agent.reflection.ReflectionExecutionContext;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 反思提示词追踪记录器。
 * <p>用途：集中处理 parse/repair 追踪字段写入，保证追踪行为一致且便于后续扩展。</p>
 */
@Component
public class ReflectionPromptTraceRecorder {

    /**
     * 模型调用服务。
     */
    private final ModelInvocationService modelInvocationService;

    public ReflectionPromptTraceRecorder(ModelInvocationService modelInvocationService) {
        this.modelInvocationService = modelInvocationService;
    }

    /**
     * 记录反思提示词追踪信息。
     *
     * @param metadata 调用元数据
     * @param promptText 提示词文本
     * @param context 反思执行上下文
     * @param modelId 模型标识
     * @param parseSuccess 是否解析成功
     * @param parseErrorType 解析错误类型
     * @param repairAttempted 是否尝试修复
     * @param repairSuccess 是否修复成功
     */
    public void record(Map<String, Object> metadata,
                       String promptText,
                       ReflectionExecutionContext context,
                       String modelId,
                       boolean parseSuccess,
                       String parseErrorType,
                       boolean repairAttempted,
                       boolean repairSuccess) {
        PromptTrace trace = PromptTrace.fromMetadata(metadata);
        if (trace == null) {
            trace = PromptTrace.fromPrompt("reflect", promptText);
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
                context != null ? context.getTenantContext() : null,
                context != null ? context.getWorkflowId() : null,
                context != null ? context.getSeqCounter() : null,
                "reflect",
                modelId
        );
    }
}

