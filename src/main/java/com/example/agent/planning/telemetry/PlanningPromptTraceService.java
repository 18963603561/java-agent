package com.example.agent.planning.telemetry;

import com.example.agent.capabilities.llm.client.ModelInvocationService;
import com.example.agent.capabilities.llm.prompt.PromptTrace;
import com.example.agent.planning.PlanningFieldKeys;
import com.example.agent.security.auth.TenantContext;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 规划提示词追踪服务。
 *
 * <p>用途：负责规划阶段 PromptTrace 组装与上报，降低遥测组件职责宽度。
 */
@Component
public class PlanningPromptTraceService {

    private static final Logger log = LoggerFactory.getLogger(PlanningPromptTraceService.class);

    private final ModelInvocationService modelInvocationService;

    /**
     * 构造追踪服务。
     *
     * @param modelInvocationService 模型调用服务
     */
    public PlanningPromptTraceService(ModelInvocationService modelInvocationService) {
        this.modelInvocationService = modelInvocationService;
    }

    /**
     * 上报规划提示词追踪。
     *
     * @param metadata 元数据
     * @param promptText 提示词文本
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param seqCounter 事件序号
     * @param modelId 模型标识
     * @param parseSuccess 是否解析成功
     * @param parseErrorType 解析错误类型
     * @param repairAttempted 是否尝试修复
     * @param repairSuccess 是否修复成功
     */
    public void recordPromptTrace(Map<String, Object> metadata,
                                  String promptText,
                                  TenantContext tenantContext,
                                  String workflowId,
                                  AtomicLong seqCounter,
                                  String modelId,
                                  boolean parseSuccess,
                                  String parseErrorType,
                                  boolean repairAttempted,
                                  boolean repairSuccess) {
        if (modelInvocationService == null) {
            log.warn("规划提示词追踪跳过, workflowId={}, reason=model_invocation_service_missing", workflowId);
            return;
        }
        PromptTrace trace = PromptTrace.fromMetadata(metadata);
        if (trace == null) {
            trace = PromptTrace.fromPrompt(PlanningFieldKeys.SCENE_PLANNER, promptText);
        }
        if (trace == null) {
            log.warn("规划提示词追踪跳过, workflowId={}, reason=trace_build_failed", workflowId);
            return;
        }
        trace.setParseSuccess(parseSuccess);
        trace.setParseErrorType(parseErrorType);
        trace.setRepairAttempted(repairAttempted);
        trace.setRepairSuccess(repairSuccess);
        log.debug("规划提示词追踪上报, workflowId={}, modelId={}, parseSuccess={}, parseErrorType={}",
                workflowId,
                modelId,
                parseSuccess,
                parseErrorType);
        modelInvocationService.recordPromptTrace(trace, tenantContext, workflowId, seqCounter, "plan", modelId);
    }
}
