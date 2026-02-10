package com.example.agent.reflection.strategy;

import com.example.agent.capabilities.llm.client.ModelInvocationService;
import com.example.agent.reflection.ReflectionDecision;
import com.example.agent.reflection.ReflectionExecutionContext;
import com.example.agent.reflection.ReflectionFailureReason;
import com.example.agent.reflection.ReflectionProperties;
import com.example.agent.reflection.strategy.ReflectionLlmDecisionResolver.ReflectionLlmResolutionResult;
import com.example.agent.streaming.observability.MetricsPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 模型反思策略。
 *
 * <p>用途：调用 LLM 执行反思评估，处理解析修复与提示追踪。</p>
 */
@Component
public class LlmReflectionStrategy implements ReflectionStrategy {

    /**
     * LLM 策略执行顺序。
     */
    private static final int STRATEGY_ORDER = 100;

    private static final Logger log = LoggerFactory.getLogger(LlmReflectionStrategy.class);

    /**
     * 指标发布器。
     */
    private final MetricsPublisher metricsPublisher;

    /**
     * 反思配置。
     */
    private final ReflectionProperties reflectionProperties;

    /**
     * 模型调用服务。
     */
    private final ModelInvocationService modelInvocationService;

    /**
     * 反思 LLM 调用执行器。
     */
    private final ReflectionLlmInvocationExecutor invocationExecutor;

    /**
     * 反思判定器。
     */
    private final ReflectionLlmDecisionResolver decisionResolver;

    /**
     * 提示词追踪记录器。
     */
    private final ReflectionPromptTraceRecorder promptTraceRecorder;

    public LlmReflectionStrategy(MetricsPublisher metricsPublisher,
                                 ReflectionProperties reflectionProperties,
                                 ModelInvocationService modelInvocationService,
                                 ReflectionLlmInvocationExecutor invocationExecutor,
                                 ReflectionLlmDecisionResolver decisionResolver,
                                 ReflectionPromptTraceRecorder promptTraceRecorder) {
        this.metricsPublisher = metricsPublisher;
        this.reflectionProperties = reflectionProperties;
        this.modelInvocationService = modelInvocationService;
        this.invocationExecutor = invocationExecutor;
        this.decisionResolver = decisionResolver;
        this.promptTraceRecorder = promptTraceRecorder;
    }

    @Override
    public ReflectionDecision execute(ReflectionExecutionContext context) {
        String tenantId = context != null && context.getTenantContext() != null
                ? context.getTenantContext().getTenantId()
                : null;
        String workflowId = context != null ? context.getWorkflowId() : null;
        String stepType = context != null && context.getStep() != null
                ? context.getStep().getStepType()
                : null;
        int attempt = context != null ? context.getAttempt() : 0;
        log.info("反思开始(LLM), tenantId={}, workflowId={}, stepType={}, attempt={}, scene=reflect",
                tenantId, workflowId, stepType, attempt);
        try {
            ReflectionLlmInvocation invocation = invocationExecutor.invoke(context);
            if (invocation == null || !invocation.hasResponseContent()) {
                log.warn("反思模型返回为空, tenantId={}, workflowId={}, stepType={}, attempt={}, reasonCode={}",
                        tenantId, workflowId, stepType, attempt, ReflectionFailureReason.LLM_EMPTY_RESPONSE);
                return ReflectionDecision.fallbackRequired(
                        ReflectionFailureReason.LLM_EMPTY_RESPONSE,
                        "empty_output",
                        false,
                        false
                );
            }

            ReflectionLlmResolutionResult resolution = decisionResolver.resolve(
                    invocation.response().getContent(),
                    context,
                    tenantId,
                    workflowId,
                    stepType,
                    attempt
            );
            if (resolution == null || resolution.decision() == null) {
                return ReflectionDecision.fallbackRequired(
                        ReflectionFailureReason.LLM_REPAIR_FAILED,
                        "parse_result_missing",
                        true,
                        false
                );
            }
            promptTraceRecorder.record(
                    invocation.metadata(),
                    invocation.prompt(),
                    context,
                    invocation.modelId(),
                    resolution.parseSuccess(),
                    resolution.parseErrorType(),
                    resolution.repairAttempted(),
                    resolution.repairSuccess()
            );
            if (!resolution.parseSuccess()) {
                return resolution.decision();
            }
            log.info("反思完成(LLM), tenantId={}, workflowId={}, stepType={}, attempt={}, score={}, retry={}",
                    tenantId,
                    workflowId,
                    stepType,
                    attempt,
                    resolution.score(),
                    resolution.decision().getResult() != null && resolution.decision().getResult().retryRequested());
            return resolution.decision();
        } catch (Exception ex) {
            log.warn("反思调用异常, tenantId={}, workflowId={}, stepType={}, attempt={}, reasonCode={}, reason={}",
                    tenantId,
                    workflowId,
                    stepType,
                    attempt,
                    ReflectionFailureReason.LLM_INVOCATION_ERROR,
                    ex.getMessage(),
                    ex);
            return ReflectionDecision.fallbackRequired(
                    ReflectionFailureReason.LLM_INVOCATION_ERROR,
                    "llm_invocation_error",
                    false,
                    false
            );
        }
    }

    @Override
    public int order() {
        return STRATEGY_ORDER;
    }

    @Override
    public boolean isEnabled(ReflectionProperties reflectionProperties) {
        return reflectionProperties != null && reflectionProperties.isLlmEnabled();
    }
}
