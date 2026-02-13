package com.example.agent.reflection;

import com.example.agent.reflection.model.ReflectionContextMapper;
import com.example.agent.reflection.strategy.ReflectionStabilityEvaluation;
import com.example.agent.reflection.strategy.ReflectionStrategy;
import com.example.agent.reflection.strategy.ReflectionStrategySelector;
import com.example.agent.runtime.model.StepSpec;
import com.example.agent.runtime.step.contract.StepExecutionOutput;
import com.example.agent.security.auth.TenantContext;
import java.util.List;
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
        // 调用带尝试次数的反思入口。
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
        // 调用带运行上下文的反思入口。
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
        // 设计意图：先做稳定性评分，再进入策略链，避免不稳定输入触发误判。
        // 判断反思是否启用，未启用时直接返回兜底结果。
        if (!properties.isEnabled()) {
            // 构建未启用的反思报告。
            ReflectionReport report = new ReflectionReport(0.9D, "反思未启用，跳过评估");
            // 返回默认反思结果。
            return new ReflectionResult(false, report);
        }

        // 初始化反思执行上下文构建器。
        ReflectionExecutionContext.Builder builder = ReflectionExecutionContext.builder();
        // 写入步骤信息。
        builder.step(step);
        // 写入输出信息。
        builder.output(output);
        // 写入租户上下文。
        builder.tenantContext(tenantContext);
        // 写入尝试次数。
        builder.attempt(attempt);
        // 写入工作流标识。
        builder.workflowId(workflowId);
        // 写入事件序列计数器。
        builder.seqCounter(seqCounter);
        // 写入反思上下文映射。
        builder.reflectionContext(reflectionContextMapper.map(step, output, attempt));
        // 构建反思执行上下文。
        ReflectionExecutionContext context = builder.build();

        // 执行稳定性评估。
        ReflectionStabilityEvaluation stability = strategySelector.evaluateStability(context);
        // 判断稳定性是否达标，未达标时直接返回。
        if (stability != null && !stability.isStable()) {
            // 记录稳定性不足日志。
            log.info("反思稳定性不足，跳过策略执行, workflowId={}, stepType={}, attempt={}, score={}, warnings={}",
                    workflowId,
                    step != null ? step.getStepType() : null,
                    attempt,
                    stability.getScore(),
                    stability.getWarnings());
            // 构建稳定性不足报告。
            ReflectionReport report = new ReflectionReport(stability.getScore(), buildStabilityNotes(stability));
            // 返回稳定性不足结果。
            return new ReflectionResult(false, report);
        }

        ReflectionDecision llmFailureDecision = null;
        // 解析有序策略列表。
        List<ReflectionStrategy> strategies = strategySelector.resolveOrderedStrategies();
        // 循环遍历反思策略列表，按顺序执行。
        for (ReflectionStrategy strategy : strategies) {
            // 调用策略执行反思评估。
            ReflectionDecision decision = strategy.execute(context);
            // 判断策略返回是否为空，空时记录告警并继续。
            if (decision == null) {
                // 记录空决策告警日志。
                log.warn("反思策略返回空决策, tenantId={}, workflowId={}, stepType={}, attempt={}, strategy={}",
                        tenantContext != null ? tenantContext.getTenantId() : null,
                        workflowId,
                        step != null ? step.getStepType() : null,
                        attempt,
                        strategy.getClass().getSimpleName());
                continue;
            }
            // 判断策略是否完成反思，完成时返回结果。
            if (decision.getStatus() == ReflectionDecisionStatus.COMPLETED) {
                // 返回反思完成结果。
                return decision.getResult();
            }
            // 判断是否为模型反思决策，记录失败信息供兜底判断。
            if (decision.isFromLlm()) {
                llmFailureDecision = decision;
            }
        }

        // 判断是否允许兜底策略，未允许时抛出异常。
        if (!strategySelector.allowFallback()) {
            // 抛出兜底禁用异常并包含原因。
            throw new IllegalStateException(resolveFallbackDisabledReason(llmFailureDecision));
        }
        throw new IllegalStateException("reflection_no_strategy_completed");
    }

    private String buildStabilityNotes(ReflectionStabilityEvaluation stability) {
        // 判断评估结果是否为空，空时返回默认说明。
        if (stability == null) {
            return "稳定性评估缺失，已跳过反思";
        }
        // 初始化说明构建器。
        StringBuilder builder = new StringBuilder("稳定性评分过低");
        // 判断告警列表是否为空，非空时追加告警说明。
        if (stability.getWarnings() != null && !stability.getWarnings().isEmpty()) {
            // 追加原因前缀。
            builder.append("，原因=");
            // 拼接告警列表到说明文本。
            builder.append(String.join(",", stability.getWarnings()));
        }
        // 返回稳定性说明文本。
        return builder.toString();
    }

    private String resolveFallbackDisabledReason(ReflectionDecision decision) {
        // 判断决策是否为空，空时返回默认原因。
        if (decision == null || decision.getReason() == null) {
            return "reflection_fallback_disabled";
        }
        // 判断决策原因是否为空，空时返回默认原因。
        if (decision.getReason() == ReflectionFailureReason.NONE) {
            return "reflection_fallback_disabled";
        }
        // 拼接失败原因并返回。
        return "reflection_fallback_disabled:" + decision.getReason().name().toLowerCase();
    }
}
