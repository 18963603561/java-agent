package com.example.agent.budget;

import com.example.agent.auth.TenantContext;
import com.example.agent.domain.event.EventType;
import com.example.agent.domain.event.StreamEvent;
import com.example.agent.model.ModelDefinition;
import com.example.agent.model.ModelFallbackDecision;
import com.example.agent.model.ModelFallbackPolicy;
import com.example.agent.model.ModelRegistry;
import com.example.agent.observability.MetricsPublisher;
import com.example.agent.streaming.EventStreamService;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * 预算管理器，负责记录计量并触发阈值事件。
 */
@Service
public class TokenBudgetManager {

    private static final Logger log = LoggerFactory.getLogger(TokenBudgetManager.class);

    private final TokenUsageRepository repository;
    private final CostCalculator costCalculator;
    private final ModelRegistry modelRegistry;
    private final ModelFallbackPolicy fallbackPolicy;
    private final ApplicationEventPublisher eventPublisher;
    private final EventStreamService eventStreamService;
    private final MetricsPublisher metricsPublisher;

    @Value("${agent.budget.enabled:true}")
    private boolean enabled;

    @Value("${agent.budget.threshold-tokens:10000}")
    private int thresholdTokens;

    public TokenBudgetManager(TokenUsageRepository repository,
                              CostCalculator costCalculator,
                              ModelRegistry modelRegistry,
                              ModelFallbackPolicy fallbackPolicy,
                              ApplicationEventPublisher eventPublisher,
                              EventStreamService eventStreamService,
                              MetricsPublisher metricsPublisher) {
        this.repository = repository;
        this.costCalculator = costCalculator;
        this.modelRegistry = modelRegistry;
        this.fallbackPolicy = fallbackPolicy;
        this.eventPublisher = eventPublisher;
        this.eventStreamService = eventStreamService;
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
        TokenUsageRecord record = buildRecord(input, tenantContext);
        boolean saved = repository.saveIfAbsent(record);
        if (!saved) {
            log.info("预算去重命中, tenantId={}, usageId={}", tenantContext.getTenantId(), input.getUsageId());
            return record;
        }
        metricsPublisher.increment("budget.tokens.used");
        log.info("预算记录写入, tenantId={}, usageId={}, taskId={}",
                tenantContext.getTenantId(), input.getUsageId(), input.getTaskId());
        if (enabled) {
            TokenUsageSummary summary = summarize(input.getTaskId(), tenantContext);
            if (summary.getTotalTokens() >= thresholdTokens) {
                metricsPublisher.increment("budget.exceeded.count");
                publishThresholdEvent(tenantContext, input.getTaskId(), summary.getTotalTokens());
                ModelFallbackDecision decision = fallbackPolicy.evaluate(
                        tenantContext, input.getTaskId(), input.getModel(), "budget_threshold");
                if (decision != null) {
                    publishFallbackEvent(tenantContext, decision);
                }
            }
        }
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
        List<TokenUsageRecord> records = repository.findByTask(tenantContext.getTenantId(), taskId);
        int totalTokens = 0;
        double totalCost = 0;
        Map<String, Integer> byModel = new HashMap<>();
        Map<String, Double> byProvider = new HashMap<>();
        for (TokenUsageRecord record : records) {
            totalTokens += record.getTotalTokens();
            totalCost += record.getCostUsd();
            if (record.getModel() != null) {
                byModel.merge(record.getModel(), record.getTotalTokens(), Integer::sum);
            }
            if (record.getProvider() != null) {
                byProvider.merge(record.getProvider(), record.getCostUsd(), Double::sum);
            }
        }
        return new TokenUsageSummary(taskId, totalTokens, totalCost, byModel, byProvider);
    }

    /**
     * 获取预算阈值令牌数，用于预算分配参考。
     *
     * @return 预算阈值令牌数
     */
    public int getThresholdTokens() {
        return thresholdTokens;
    }

    private TokenUsageRecord buildRecord(TokenUsageInput input, TenantContext tenantContext) {
        TokenUsageRecord record = new TokenUsageRecord();
        record.setRecordId(UUID.randomUUID().toString());
        record.setUsageId(input.getUsageId());
        record.setTaskId(input.getTaskId());
        record.setAgentId(input.getAgentId());
        record.setModel(input.getModel());
        record.setProvider(input.getProvider());
        int inputTokens = input.getInputTokens() != null ? input.getInputTokens() : 0;
        int outputTokens = input.getOutputTokens() != null ? input.getOutputTokens() : 0;
        int totalTokens = input.getTotalTokens() != null ? input.getTotalTokens() : inputTokens + outputTokens;
        record.setInputTokens(inputTokens);
        record.setOutputTokens(outputTokens);
        record.setTotalTokens(totalTokens);
        record.setTenantId(tenantContext.getTenantId());
        record.setCreatedAt(Instant.now());
        double cost = input.getCostUsd() != null ? input.getCostUsd() : 0;
        if (cost == 0 && input.getModel() != null) {
            ModelDefinition model = modelRegistry.getModel(input.getModel());
            cost = costCalculator.calculate(model, inputTokens, outputTokens);
        }
        record.setCostUsd(cost);
        return record;
    }

    private void publishThresholdEvent(TenantContext tenantContext, String taskId, int totalTokens) {
        String streamId = taskId != null ? taskId : tenantContext.getTenantId();
        long seq = eventStreamService.nextSequence(tenantContext.getTenantId(), streamId);
        StreamEvent event = new StreamEvent();
        event.setEventId(streamId + ":" + seq);
        event.setSchemaVersion("v1");
        event.setWorkflowId(streamId);
        event.setType(EventType.BUDGET_THRESHOLD);
        event.setTimestamp(Instant.now());
        event.setSeq(seq);
        event.setStreamId(streamId);
        event.setTenantId(tenantContext.getTenantId());
        event.setPayload(Map.of("taskId", taskId, "totalTokens", totalTokens));
        eventPublisher.publishEvent(event);
    }

    private void publishFallbackEvent(TenantContext tenantContext, ModelFallbackDecision decision) {
        String streamId = decision.getTaskId() != null ? decision.getTaskId() : tenantContext.getTenantId();
        long seq = eventStreamService.nextSequence(tenantContext.getTenantId(), streamId);
        StreamEvent event = new StreamEvent();
        event.setEventId(streamId + ":" + seq);
        event.setSchemaVersion("v1");
        event.setWorkflowId(streamId);
        event.setType(EventType.MODEL_FALLBACK_APPLIED);
        event.setTimestamp(Instant.now());
        event.setSeq(seq);
        event.setStreamId(streamId);
        event.setTenantId(tenantContext.getTenantId());
        event.setPayload(Map.of(
                "taskId", decision.getTaskId(),
                "fromModel", decision.getFromModel(),
                "toModel", decision.getToModel(),
                "reason", decision.getReason()
        ));
        eventPublisher.publishEvent(event);
    }
}
