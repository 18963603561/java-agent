package com.example.agent.runtime.step.executor;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.capabilities.context.evidence.EvidencePack;
import com.example.agent.capabilities.context.evidence.EvidencePackService;
import com.example.agent.capabilities.context.research.ResearchCitation;
import com.example.agent.capabilities.context.research.ResearchPipeline;
import com.example.agent.capabilities.context.research.ResearchRunResult;
import com.example.agent.capabilities.tools.hook.HookManager;
import com.example.agent.runtime.model.input.StepInputView;
import com.example.agent.runtime.step.contract.StepExecutionOutput;
import com.example.agent.runtime.step.contract.StepExecutionRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 研究步骤执行器。
 *
 * <p>用途：封装 {@code RESEARCH} 步骤执行逻辑，输出引用列表与统计信息。
 * <p>输入：任务请求、步骤定义与运行上下文。
 * <p>输出：研究结果映射。
 * <p>边界：异常由上层统一捕获并按恢复策略处理。
 */
@Component
public class ResearchStepExecutor implements StepTypeExecutor {

    private final ResearchPipeline researchPipeline;
    private final HookManager hookManager;
    private final EvidencePackService evidencePackService;

    public ResearchStepExecutor(ResearchPipeline researchPipeline,
                                HookManager hookManager,
                                EvidencePackService evidencePackService) {
        this.researchPipeline = researchPipeline;
        this.hookManager = hookManager;
        this.evidencePackService = evidencePackService;
    }

    @Override
    public boolean supports(String stepType) {
        return stepType != null && "RESEARCH".equalsIgnoreCase(stepType);
    }

    @Override
    public StepExecutionOutput execute(StepExecutionRequest request) {
        String query = resolveStepQuery(request.getTaskRequest(), request.getStep());
        ResearchRunResult researchResult = researchPipeline.runWithRawRef(
                query,
                request.getTenantContext(),
                request.getWorkflowId(),
                request.getSeqCounter()
        );
        List<ResearchCitation> citations = researchResult != null && researchResult.getCitations() != null
                ? researchResult.getCitations()
                : List.of();

        Map<String, Object> output = new HashMap<>();
        output.put("query", query);
        output.put("citations", citations);
        output.put("count", citations.size());
        output.put("workflowId", request.getWorkflowId());
        if (researchResult != null
                && researchResult.getRawRef() != null
                && !researchResult.getRawRef().isBlank()) {
            output.put("rawRef", researchResult.getRawRef());
            output.put("modelRawRef", researchResult.getRawRef());
        }
        if (request.getRecord() != null) {
            output.put("stepId", request.getRecord().getStepId());
        }
        if (hookManager != null) {
            hookManager.postResearch(request.getTenantContext(), request.getRecord(), output);
        }
        syncEvidencePackFromStore(request.getRuntimeContext() != null ? request.getRuntimeContext().asMap() : null,
                request.getTenantContext(),
                request.getWorkflowId());
        return StepExecutionOutput.fromPayload(output);
    }

    private String resolveStepQuery(TaskRequest request, com.example.agent.runtime.model.StepSpec step) {
        StepInputView stepInputView = StepInputView.from(step, null);
        Map<String, Object> stepInput = stepInputView.toExecutionMap();
        if (stepInput != null) {
            Object query = stepInput.get("query");
            if (query instanceof String value && !value.isBlank()) {
                return value;
            }
        }
        if (request != null && request.getQuery() != null) {
            return request.getQuery();
        }
        return "";
    }

    /**
     * 从证据包服务同步当前工作流证据到运行上下文。
     */
    private void syncEvidencePackFromStore(Map<String, Object> runtimeContext,
                                           com.example.agent.security.auth.TenantContext tenantContext,
                                           String workflowId) {
        if (runtimeContext == null || evidencePackService == null || tenantContext == null
                || workflowId == null || workflowId.isBlank()) {
            return;
        }
        EvidencePack pack = evidencePackService.getPack(tenantContext.getTenantId(), workflowId);
        if (pack != null) {
            runtimeContext.put("evidencePack", pack);
        }
    }
}
