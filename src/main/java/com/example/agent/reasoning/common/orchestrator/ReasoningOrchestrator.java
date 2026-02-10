package com.example.agent.reasoning.common.orchestrator;

import com.example.agent.reasoning.common.ReasoningRequest;
import com.example.agent.reasoning.common.ReasoningResult;
import com.example.agent.reasoning.common.ReasoningStrategyRegistry;
import com.example.agent.reasoning.common.config.ReasoningConfigResolver;
import com.example.agent.reasoning.common.selection.ReasoningDegradePolicy;
import com.example.agent.reasoning.common.telemetry.ReasoningMetricsPublisher;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 推理编排器。
 *
 * <p>用途：支持单策略与并行多策略执行，并通过仲裁器输出单一结果。
 */
@Component
public class ReasoningOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ReasoningOrchestrator.class);

    private final ReasoningStrategyRegistry strategyRegistry;
    private final ReasoningArbitrator reasoningArbitrator;
    private final Executor reasoningExecutor;
    private final ReasoningMetricsPublisher reasoningMetricsPublisher;
    private final ReasoningConfigResolver reasoningConfigResolver;
    private final ReasoningDegradePolicy reasoningDegradePolicy;

    /**
     * 构造推理编排器。
     *
     * @param strategyRegistry 策略注册表
     * @param reasoningArbitrator 结果仲裁器
     * @param reasoningExecutor 执行线程池
     * @param reasoningMetricsPublisher 指标发布器
     * @param reasoningConfigResolver 推理配置解析器
     * @param reasoningDegradePolicy 推理降级策略
     */
    public ReasoningOrchestrator(ReasoningStrategyRegistry strategyRegistry,
                                 ReasoningArbitrator reasoningArbitrator,
                                 @Qualifier("reasoningExecutor") Executor reasoningExecutor,
                                 ReasoningMetricsPublisher reasoningMetricsPublisher,
                                 ReasoningConfigResolver reasoningConfigResolver,
                                 ReasoningDegradePolicy reasoningDegradePolicy) {
        this.strategyRegistry = strategyRegistry;
        this.reasoningArbitrator = reasoningArbitrator;
        this.reasoningExecutor = reasoningExecutor;
        this.reasoningMetricsPublisher = reasoningMetricsPublisher;
        this.reasoningConfigResolver = reasoningConfigResolver;
        this.reasoningDegradePolicy = reasoningDegradePolicy;
    }

    /**
     * 执行推理计划。
     *
     * @param baseRequest 基础请求
     * @param plan 执行计划
     * @return 推理结果
     */
    public ReasoningResult execute(ReasoningRequest baseRequest, ReasoningExecutionPlan plan) {
        if (baseRequest == null) {
            throw new IllegalArgumentException("reasoning_request_missing");
        }
        List<String> candidateStrategies = normalizeCandidateStrategies(plan);
        String primaryStrategy = resolvePrimaryStrategy(plan, candidateStrategies, baseRequest);
        List<String> fallbackOrder = resolveFallbackOrder(candidateStrategies, primaryStrategy);

        boolean parallelEnabled = isParallelPlan(plan) && candidateStrategies.size() > 1;
        log.info("推理执行计划, workflowId={}, parallelEnabled={}, primaryStrategy={}, candidateStrategies={}",
                baseRequest.getWorkflowId(),
                parallelEnabled,
                primaryStrategy,
                candidateStrategies);

        if (!parallelEnabled) {
            return executeSequential(baseRequest, fallbackOrder);
        }

        List<CompletableFuture<ReasoningResult>> futures = new ArrayList<>();
        for (String strategy : candidateStrategies) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                long startedAt = System.currentTimeMillis();
                try {
                    ReasoningRequest request = withStrategy(baseRequest, strategy);
                    return strategyRegistry.execute(request);
                } finally {
                    reasoningMetricsPublisher.recordDuration(strategy, System.currentTimeMillis() - startedAt);
                }
            }, reasoningExecutor));
        }

        List<ReasoningResult> results = new ArrayList<>();
        long timeoutMillis = resolveTimeout(plan);
        for (int i = 0; i < futures.size(); i++) {
            CompletableFuture<ReasoningResult> future = futures.get(i);
            String strategy = candidateStrategies.get(i);
            try {
                ReasoningResult result = future.get(timeoutMillis, TimeUnit.MILLISECONDS);
                if (result != null) {
                    results.add(result);
                }
            } catch (Exception ex) {
                log.warn("并行推理子任务失败, strategy={}, workflowId={}, reason={}",
                        strategy,
                        baseRequest.getWorkflowId(),
                        ex.getMessage());
            }
        }

        if (results.isEmpty()) {
            log.warn("并行推理无可用结果，进入降级链路, workflowId={}, primaryStrategy={}",
                    baseRequest.getWorkflowId(),
                    primaryStrategy);
            return executeSequential(baseRequest, fallbackOrder);
        }
        ReasoningResult winner = reasoningArbitrator.selectBest(results);
        log.info("并行推理仲裁完成, workflowId={}, winnerStrategy={}, confidence={}",
                baseRequest.getWorkflowId(),
                winner.getStrategyType(),
                winner.getConfidence());
        return winner;
    }

    private boolean isParallelPlan(ReasoningExecutionPlan plan) {
        return plan != null
                && plan.isParallelEnabled()
                && plan.getCandidateStrategies() != null
                && !plan.getCandidateStrategies().isEmpty();
    }

    private List<String> normalizeCandidateStrategies(ReasoningExecutionPlan plan) {
        List<String> normalized = new ArrayList<>();
        if (plan == null || plan.getCandidateStrategies() == null) {
            return normalized;
        }
        for (String strategy : plan.getCandidateStrategies()) {
            if (!StringUtils.hasText(strategy)) {
                continue;
            }
            String normalizedStrategy = strategy.trim().toLowerCase();
            if (!normalized.contains(normalizedStrategy)) {
                normalized.add(normalizedStrategy);
            }
        }
        if (normalized.isEmpty() && StringUtils.hasText(plan.getPrimaryStrategy())) {
            normalized.add(plan.getPrimaryStrategy().trim().toLowerCase());
        }
        return normalized;
    }

    private long resolveTimeout(ReasoningExecutionPlan plan) {
        long defaultTimeout = reasoningConfigResolver.resolveParallelTimeoutMillis();
        if (plan == null) {
            return defaultTimeout;
        }
        long requested = plan.getTimeoutMillis();
        if (requested <= 0L) {
            return defaultTimeout;
        }
        return Math.max(500L, Math.min(requested, 60_000L));
    }

    private String resolvePrimaryStrategy(ReasoningExecutionPlan plan,
                                          List<String> candidateStrategies,
                                          ReasoningRequest baseRequest) {
        if (plan != null && StringUtils.hasText(plan.getPrimaryStrategy())) {
            return plan.getPrimaryStrategy().trim().toLowerCase();
        }
        if (candidateStrategies != null && !candidateStrategies.isEmpty()) {
            return candidateStrategies.get(0);
        }
        return baseRequest.getStrategyType();
    }

    private List<String> resolveFallbackOrder(List<String> candidateStrategies, String primaryStrategy) {
        List<String> order = new ArrayList<>();
        if (StringUtils.hasText(primaryStrategy)) {
            order.add(primaryStrategy.trim().toLowerCase());
        }
        if (candidateStrategies != null) {
            for (String strategy : candidateStrategies) {
                if (StringUtils.hasText(strategy) && !order.contains(strategy)) {
                    order.add(strategy);
                }
            }
        }
        for (String degraded : reasoningDegradePolicy.resolveFallbackOrder(primaryStrategy)) {
            if (StringUtils.hasText(degraded) && !order.contains(degraded)) {
                order.add(degraded);
            }
        }
        return order;
    }

    private ReasoningResult executeSequential(ReasoningRequest baseRequest, List<String> fallbackOrder) {
        RuntimeException lastException = null;
        String previousStrategy = null;
        for (String strategy : fallbackOrder) {
            long startedAt = System.currentTimeMillis();
            try {
                ReasoningResult result = executeSingle(baseRequest, strategy);
                reasoningMetricsPublisher.recordDuration(strategy, System.currentTimeMillis() - startedAt);
                if (StringUtils.hasText(previousStrategy) && !previousStrategy.equals(strategy)) {
                    log.info("推理策略降级生效, workflowId={}, fromStrategy={}, toStrategy={}",
                            baseRequest.getWorkflowId(), previousStrategy, strategy);
                }
                return result;
            } catch (RuntimeException ex) {
                reasoningMetricsPublisher.recordDuration(strategy, System.currentTimeMillis() - startedAt);
                log.warn("推理策略执行失败，尝试降级下一个策略, workflowId={}, strategy={}, reason={}",
                        baseRequest.getWorkflowId(), strategy, ex.getMessage(), ex);
                previousStrategy = strategy;
                lastException = ex;
            }
        }

        if (lastException != null) {
            throw new IllegalStateException("reasoning_all_strategies_failed", lastException);
        }
        return strategyRegistry.execute(baseRequest);
    }

    private ReasoningResult executeSingle(ReasoningRequest baseRequest, String strategyType) {
        if (!StringUtils.hasText(strategyType) || strategyType.equalsIgnoreCase(baseRequest.getStrategyType())) {
            return strategyRegistry.execute(baseRequest);
        }
        return strategyRegistry.execute(withStrategy(baseRequest, strategyType));
    }

    private ReasoningRequest withStrategy(ReasoningRequest baseRequest, String strategyType) {
        return new ReasoningRequest(
                strategyType,
                baseRequest.getPrompt(),
                baseRequest.getInput().attributes(),
                baseRequest.getTenantContext(),
                baseRequest.getWorkflowId(),
                baseRequest.getSeqCounter()
        );
    }
}
