package com.example.agent.budget.token.application;

import com.example.agent.budget.token.model.TokenUsageInput;
import com.example.agent.budget.token.model.TokenUsageRecord;
import com.example.agent.budget.token.model.TokenUsageSummary;
import com.example.agent.budget.token.event.BudgetEventPublisher;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.capabilities.llm.provider.ModelFallbackDecision;
import com.example.agent.capabilities.llm.provider.ModelFallbackPolicy;
import com.example.agent.streaming.observability.MetricsPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 预算管理器，负责记录计量并触发阈值事件。
 */
@Service
public class TokenBudgetManager {

    private static final Logger log = LoggerFactory.getLogger(TokenBudgetManager.class);

    private final TokenUsageRecordFactory recordFactory;
    private final TokenUsageRecorder tokenUsageRecorder;
    private final TokenUsageSummaryService summaryService;
    private final BudgetThresholdEvaluator thresholdEvaluator;
    private final ModelFallbackPolicy fallbackPolicy;
    private final BudgetEventPublisher budgetEventPublisher;
    private final MetricsPublisher metricsPublisher;

    @Value("${agent.budget.enabled:true}")
    private boolean enabled;

    public TokenBudgetManager(TokenUsageRecordFactory recordFactory,
                              TokenUsageRecorder tokenUsageRecorder,
                              TokenUsageSummaryService summaryService,
                              BudgetThresholdEvaluator thresholdEvaluator,
                              ModelFallbackPolicy fallbackPolicy,
                              BudgetEventPublisher budgetEventPublisher,
                              MetricsPublisher metricsPublisher) {
        this.recordFactory = recordFactory;
        this.tokenUsageRecorder = tokenUsageRecorder;
        this.summaryService = summaryService;
        this.thresholdEvaluator = thresholdEvaluator;
        this.fallbackPolicy = fallbackPolicy;
        this.budgetEventPublisher = budgetEventPublisher;
        this.metricsPublisher = metricsPublisher;
    }

    /**
     * 记录预算使用。
     *
     * @param input 计量输入
     * @param tenantContext 租户上下文
     * @return 计量记录
     */
    public TokenUsageRecord recordUsage(TokenUsageInput input, TenantContext tenantContext) {
        log.info("预算记录开始, tenantId={}, usageId={}, taskId={}",
                tenantContext != null ? tenantContext.getTenantId() : null,
                input != null ? input.getUsageId() : null,
                input != null ? input.getTaskId() : null);
        TokenUsageRecord record = recordFactory.create(input, tenantContext);
        boolean saved = tokenUsageRecorder.record(record);
        if (!saved) {
            log.info("预算去重命中, tenantId={}, usageId={}", tenantContext.getTenantId(), input.getUsageId());
            return record;
        }
        metricsPublisher.increment("budget.tokens.used");
        log.info("预算记录写入, tenantId={}, usageId={}, taskId={}",
                tenantContext.getTenantId(), input.getUsageId(), input.getTaskId());
        if (enabled) {
            TokenUsageSummary summary = summaryService.summarize(input.getTaskId(), tenantContext);
            if (thresholdEvaluator.isExceeded(summary)) {
                metricsPublisher.increment("budget.exceeded.count");
                log.info("预算阈值命中, tenantId={}, taskId={}, totalTokens={}",
                        tenantContext.getTenantId(), input.getTaskId(), summary.getTotalTokens());
                // 预算治理事件：阈值告警之外，补充背压动作事件。
                budgetEventPublisher.publishBackpressureEvent(
                        tenantContext,
                        input.getTaskId(),
                        summary.getTotalTokens(),
                        thresholdEvaluator.getThresholdTokens());
                budgetEventPublisher.publishThresholdEvent(tenantContext, input.getTaskId(), summary.getTotalTokens());
                ModelFallbackDecision decision = fallbackPolicy.evaluate(
                        tenantContext, input.getTaskId(), input.getModel(), "budget_threshold");
                if (decision != null) {
                    log.info("预算触发模型降级, tenantId={}, taskId={}, fromModel={}, toModel={}",
                            tenantContext.getTenantId(), decision.getTaskId(),
                            decision.getFromModel(), decision.getToModel());
                    budgetEventPublisher.publishFallbackEvent(tenantContext, decision);
                }
            }
        }
        log.info("预算记录结束, tenantId={}, usageId={}, taskId={}",
                tenantContext.getTenantId(), input.getUsageId(), input.getTaskId());
        return record;
    }

    /**
     * 汇总预算信息。
     *
     * @param taskId 任务标识
     * @param tenantContext 租户上下文
     * @return 汇总结果
     */
    public TokenUsageSummary summarize(String taskId, TenantContext tenantContext) {
        return summaryService.summarize(taskId, tenantContext);
    }

    /**
     * 获取预算阈值令牌数，用于预算分配参考。
     *
     * @return 预算阈值令牌数
     */
    public int getThresholdTokens() {
        return thresholdEvaluator.getThresholdTokens();
    }
}
