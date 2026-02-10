package com.example.agent.reflection;

import com.example.agent.security.auth.TenantContext;
import com.example.agent.runtime.model.StepSpec;
import com.example.agent.runtime.step.contract.StepExecutionOutput;
import com.example.agent.reflection.model.ReflectionContextMapper;
import com.example.agent.reflection.strategy.ReflectionStrategy;
import com.example.agent.reflection.strategy.ReflectionStrategySelector;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 反思服务，负责评估输出质量并给出重试建议。
 */
@Service
public class ReflectionService {

    private static final Logger log = LoggerFactory.getLogger(ReflectionService.class);

    private final ReflectionProperties properties;
    private final ReflectionContextMapper reflectionContextMapper;
    private final ReflectionStrategySelector strategySelector;

    public ReflectionService(ReflectionProperties properties,
                             ReflectionContextMapper reflectionContextMapper,
                             ReflectionStrategySelector strategySelector) {
        this.properties = properties;
        this.reflectionContextMapper = reflectionContextMapper;
        this.strategySelector = strategySelector;
    }

    /**
     * 进行反思评估，使用默认尝试次数。
     *
     * @param step 步骤请求
     * @param output 输出结果
     * @param tenantContext 租户上下文
     * @return 反思结果
     */
    public ReflectionResult reflect(StepSpec step, StepExecutionOutput output, TenantContext tenantContext) {
        return reflect(step, output, tenantContext, 1, null, null);
    }

    /**
     * 进行反思评估。
     *
     * @param step 步骤请求
     * @param output 输出结果
     * @param tenantContext 租户上下文
     * @param attempt 当前尝试次数
     * @return 反思结果
     */
    public ReflectionResult reflect(StepSpec step,
                                    StepExecutionOutput output,
                                    TenantContext tenantContext,
                                    int attempt) {
        return reflect(step, output, tenantContext, attempt, null, null);
    }

    /**
     * 带运行上下文的反思入口，用于发布 LLM 事件。
     *
     * @param step 步骤请求
     * @param output 输出结果
     * @param tenantContext 租户上下文
     * @param attempt 当前尝试次数
     * @param workflowId 工作流标识
     * @param seqCounter 事件序列计数器
     * @return 反思结果
     */
    public ReflectionResult reflect(StepSpec step,
                                    StepExecutionOutput output,
                                    TenantContext tenantContext,
                                    int attempt,
                                    String workflowId,
                                    AtomicLong seqCounter) {
        if (!properties.isEnabled()) {
            ReflectionReport report = new ReflectionReport(0.9, "反思未启用，跳过评估");
            return new ReflectionResult(false, report);
        }

        ReflectionExecutionContext context = ReflectionExecutionContext.builder()
                .step(step)
                .output(output)
                .tenantContext(tenantContext)
                .attempt(attempt)
                .workflowId(workflowId)
                .seqCounter(seqCounter)
                .reflectionContext(reflectionContextMapper.map(step, output, attempt))
                .build();

        ReflectionDecision llmFailureDecision = null;
        for (ReflectionStrategy strategy : strategySelector.resolveOrderedStrategies()) {
            ReflectionDecision decision = strategy.execute(context);
            if (decision == null) {
                log.warn("反思策略返回空决策, tenantId={}, workflowId={}, stepType={}, attempt={}, strategy={}",
                        tenantContext != null ? tenantContext.getTenantId() : null,
                        workflowId,
                        step != null ? step.getStepType() : null,
                        attempt,
                        strategy.getClass().getSimpleName());
                continue;
            }
            if (decision.getStatus() == ReflectionDecisionStatus.COMPLETED) {
                return decision.getResult();
            }
            if (decision.isFromLlm()) {
                llmFailureDecision = decision;
            }
        }

        if (!strategySelector.allowFallback()) {
            throw new IllegalStateException(resolveFallbackDisabledReason(llmFailureDecision));
        }
        throw new IllegalStateException("reflection_no_strategy_completed");
    }

    private String resolveFallbackDisabledReason(ReflectionDecision decision) {
        if (decision == null || decision.getReason() == null) {
            return "reflection_fallback_disabled";
        }
        if (decision.getReason() == ReflectionFailureReason.NONE) {
            return "reflection_fallback_disabled";
        }
        return "reflection_fallback_disabled:" + decision.getReason().name().toLowerCase();
    }
}
