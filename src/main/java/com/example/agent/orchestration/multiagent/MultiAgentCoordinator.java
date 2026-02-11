package com.example.agent.orchestration.multiagent;

import com.example.agent.capabilities.llm.client.ModelInvocationService;
import com.example.agent.capabilities.llm.contract.LlmTaskContext;
import com.example.agent.capabilities.llm.contract.ModelRequest;
import com.example.agent.capabilities.llm.contract.ModelResponse;
import com.example.agent.capabilities.llm.contract.ModelScene;
import com.example.agent.capabilities.llm.prompt.PromptTrace;
import com.example.agent.capabilities.llm.tooling.ModelToolResolver;
import com.example.agent.orchestration.multiagent.model.MultiAgentExecutionResult;
import com.example.agent.orchestration.multiagent.usecase.MultiAgentExecutionUseCase;
import com.example.agent.runtime.model.StepSpec;
import com.example.agent.security.auth.TenantContext;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 多智能体协调入口。
 *
 * <p>用途：作为应用层入口，负责入参驱动、角色求解与用例调用。
 * 执行路径分发与结果拼装下沉到用例层，避免入口类承担过多职责。</p>
 */
@Service
public class MultiAgentCoordinator {

    private static final Logger log = LoggerFactory.getLogger(MultiAgentCoordinator.class);

    private final ModelInvocationService modelInvocationService;
    private final ModelToolResolver modelToolResolver;
    private final MultiAgentInputSummaryBuilder inputSummaryBuilder;
    private final MultiAgentPromptBuilder promptBuilder;
    private final MultiAgentRoleResolver roleResolver;
    private final MultiAgentEventPublisher multiAgentEventPublisher;
    private final MultiAgentExecutionUseCase multiAgentExecutionUseCase;

    public MultiAgentCoordinator(ModelInvocationService modelInvocationService,
                                 ModelToolResolver modelToolResolver,
                                 MultiAgentInputSummaryBuilder inputSummaryBuilder,
                                 MultiAgentPromptBuilder promptBuilder,
                                 MultiAgentRoleResolver roleResolver,
                                 MultiAgentEventPublisher multiAgentEventPublisher,
                                 MultiAgentExecutionUseCase multiAgentExecutionUseCase) {
        this.modelInvocationService = modelInvocationService;
        this.modelToolResolver = modelToolResolver;
        this.inputSummaryBuilder = inputSummaryBuilder;
        this.promptBuilder = promptBuilder;
        this.roleResolver = roleResolver;
        this.multiAgentEventPublisher = multiAgentEventPublisher;
        this.multiAgentExecutionUseCase = multiAgentExecutionUseCase;
    }

    /**
     * 协调多智能体执行。
     */
    public MultiAgentExecutionResult coordinateResult(StepSpec step,
                                                      TenantContext tenantContext,
                                                      String workflowId,
                                                      AtomicLong seqCounter) {
        Map<String, Object> inputSummary = inputSummaryBuilder.build(step);
        String prompt = promptBuilder.buildPrompt(inputSummary);
        ModelRequest request = new ModelRequest(prompt, ModelScene.PLANNER);
        promptBuilder.applyPromptBundle(request, prompt, inputSummary);
        modelToolResolver.applyTooling(request,
                LlmTaskContext.empty(),
                step != null ? step.toExecutionInput() : null);

        Map<String, Object> metadata = inputSummaryBuilder.buildMetadata(step);
        ModelResponse response = modelInvocationService.invoke(request,
                ModelScene.PLANNER,
                tenantContext,
                workflowId,
                seqCounter,
                "multi_agent",
                metadata);

        String rawContent = response != null ? response.getContent() : null;
        String rawRef = response != null ? response.getRawRef() : null;
        String modelId = response != null ? response.getModelId() : null;

        MultiAgentRoleResolver.RoleResolveResult attempt = roleResolver.resolve(rawContent,
                inputSummary,
                workflowId,
                step != null ? step.getStepType() : null);
        List<AgentRole> roles = attempt.roles();
        if (roles.isEmpty()) {
            log.warn("多智能体解析与修复均失败，使用回退角色, workflowId={}, stepType={}",
                    workflowId,
                    step != null ? step.getStepType() : null);
            roles = roleResolver.buildFallbackRoles();
        }

        recordPromptTrace(metadata,
                prompt,
                tenantContext,
                workflowId,
                seqCounter,
                modelId,
                !roles.isEmpty(),
                attempt.parseErrorType(),
                attempt.repairAttempted(),
                attempt.repairSuccess());
        multiAgentEventPublisher.publishTeamEvents(tenantContext, workflowId, seqCounter, roles);

        return multiAgentExecutionUseCase.execute(step,
                tenantContext,
                workflowId,
                seqCounter,
                roles,
                rawRef);
    }

    /**
     * 协调多智能体执行并返回边界层 Map 结果。
     */
    public Map<String, Object> coordinate(StepSpec step,
                                          TenantContext tenantContext,
                                          String workflowId,
                                          AtomicLong seqCounter) {
        return coordinateResult(step, tenantContext, workflowId, seqCounter).toMap();
    }

    /**
     * 记录提示词追踪信息。
     */
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
            trace = PromptTrace.fromPrompt("multiagent", promptText);
        }
        if (trace == null) {
            return;
        }
        trace.setParseSuccess(parseSuccess);
        trace.setParseErrorType(parseErrorType);
        trace.setRepairAttempted(repairAttempted);
        trace.setRepairSuccess(repairSuccess);
        modelInvocationService.recordPromptTrace(trace,
                tenantContext,
                workflowId,
                seqCounter,
                "multi_agent",
                modelId);
    }
}
