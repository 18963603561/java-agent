package com.example.agent.governance.evaluation;

import com.example.agent.security.auth.TenantContext;
import com.example.agent.streaming.domain.EventType;
import com.example.agent.streaming.domain.StreamEvent;
import com.example.agent.streaming.sse.EventStreamService;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 能力边界评估器，负责输出风险与策略建议。
 */
@Service
public class CapabilityBoundaryEvaluator {

    private static final Logger log = LoggerFactory.getLogger(CapabilityBoundaryEvaluator.class);
    private static final int MAX_PREVIEW_CHARS = 120;

    private final CapabilityEvaluationProperties properties;
    private final ApplicationEventPublisher eventPublisher;
    private final EventStreamService eventStreamService;

    public CapabilityBoundaryEvaluator(CapabilityEvaluationProperties properties,
                                       ApplicationEventPublisher eventPublisher,
                                       EventStreamService eventStreamService) {
        this.properties = properties;
        this.eventPublisher = eventPublisher;
        this.eventStreamService = eventStreamService;
    }

    /**
     * 是否启用评估。
     *
     * @return 是否启用
     */
    public boolean isEnabled() {
        return properties.isEnabled();
    }

    /**
     * 执行能力边界评估。
     *
     * @param input 评估输入
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param seqCounter 事件序列计数器
     * @return 评估结果
     */
    public CapabilityEvaluationResult evaluate(CapabilityEvaluationInput input,
                                               TenantContext tenantContext,
                                               String workflowId,
                                               AtomicLong seqCounter) {
        CapabilityEvaluationResult result = new CapabilityEvaluationResult();
        if (!properties.isEnabled()) {
            result.setSkipped(true);
            result.setRecommendedStrategy(properties.getDefaultStrategy());
            return result;
        }
        Map<String, Object> startedPayload = new HashMap<>();
        startedPayload.put("taskPreview", truncate(input != null ? input.getTaskDescription() : null));
        startedPayload.put("toolSummary", input != null ? input.getToolSummary() : null);
        publishEvent(tenantContext, workflowId, seqCounter, EventType.CAPABILITY_EVAL_STARTED, startedPayload);

        double complexityScore = input != null && input.getComplexityScore() != null
                ? input.getComplexityScore()
                : estimateComplexity(input != null ? input.getTaskDescription() : null);
        double riskScore = evaluateRiskScore(input, complexityScore);
        CapabilityRiskLevel riskLevel = resolveRiskLevel(riskScore);
        String recommendedStrategy = resolveStrategy(input, complexityScore, riskLevel);
        boolean shouldAskApproval = properties.isForceApprovalAboveRisk() && riskLevel == CapabilityRiskLevel.HIGH;
        boolean shouldDecompose = complexityScore >= properties.getComplexityThreshold()
                && riskLevel == CapabilityRiskLevel.HIGH;
        String stopEarlyReason = resolveStopEarlyReason(input, complexityScore, riskLevel);

        result.setComplexityScore(complexityScore);
        result.setRiskScore(riskScore);
        result.setRiskLevel(riskLevel);
        result.setRecommendedStrategy(recommendedStrategy);
        result.setShouldAskApproval(shouldAskApproval);
        result.setShouldDecompose(shouldDecompose);
        result.setStopEarlyReason(stopEarlyReason);

        Map<String, Object> completedPayload = new HashMap<>();
        completedPayload.put("complexityScore", complexityScore);
        completedPayload.put("riskScore", riskScore);
        completedPayload.put("riskLevel", riskLevel.name());
        completedPayload.put("recommendedStrategy", recommendedStrategy);
        completedPayload.put("shouldAskApproval", shouldAskApproval);
        completedPayload.put("shouldDecompose", shouldDecompose);
        if (StringUtils.hasText(stopEarlyReason)) {
            completedPayload.put("stopEarlyReason", stopEarlyReason);
        }
        publishEvent(tenantContext, workflowId, seqCounter, EventType.CAPABILITY_EVAL_COMPLETED, completedPayload);

        if (riskLevel == CapabilityRiskLevel.HIGH) {
            Map<String, Object> riskPayload = new HashMap<>();
            riskPayload.put("riskScore", riskScore);
            riskPayload.put("riskLevel", riskLevel.name());
            riskPayload.put("recommendedStrategy", recommendedStrategy);
            publishEvent(tenantContext, workflowId, seqCounter, EventType.CAPABILITY_EVAL_RISK_RAISED, riskPayload);
        }

        log.info("能力边界评估完成, riskLevel={}, complexityScore={}, recommendedStrategy={}",
                riskLevel, complexityScore, recommendedStrategy);
        return result;
    }

    private double evaluateRiskScore(CapabilityEvaluationInput input, double complexityScore) {
        double riskScore = 0.2;
        if (complexityScore >= properties.getComplexityThreshold()) {
            riskScore += 0.4;
        }
        if (input == null || !StringUtils.hasText(input.getToolSummary())) {
            riskScore += 0.2;
        }
        if (input != null && input.getFailureTypes() != null && !input.getFailureTypes().isEmpty()) {
            riskScore += Math.min(0.2, input.getFailureTypes().size() * 0.05);
        }
        int budgetThreshold = input != null && input.getBudgetThresholdTokens() > 0
                ? input.getBudgetThresholdTokens()
                : properties.getBudgetThresholdTokens();
        if (budgetThreshold > 0 && complexityScore >= 0.7 && budgetThreshold < 3000) {
            riskScore += 0.1;
        }
        return Math.min(1.0, riskScore);
    }

    private CapabilityRiskLevel resolveRiskLevel(double riskScore) {
        if (riskScore >= properties.getRiskThreshold()) {
            return CapabilityRiskLevel.HIGH;
        }
        if (riskScore >= properties.getRiskThreshold() * 0.7) {
            return CapabilityRiskLevel.MEDIUM;
        }
        return CapabilityRiskLevel.LOW;
    }

    private String resolveStrategy(CapabilityEvaluationInput input,
                                   double complexityScore,
                                   CapabilityRiskLevel riskLevel) {
        String description = input != null ? input.getTaskDescription() : null;
        String lower = description == null ? "" : description.toLowerCase(Locale.ROOT);
        if (containsKeyword(lower, List.of("调研", "研究", "资料", "来源", "证据", "报告"))) {
            return "research";
        }
        if (containsKeyword(lower, List.of("辩论", "利弊", "对比", "比较", "观点"))) {
            return "debate";
        }
        if (riskLevel == CapabilityRiskLevel.HIGH || complexityScore >= properties.getComplexityThreshold()) {
            return "thought_tree";
        }
        return properties.getDefaultStrategy();
    }

    private String resolveStopEarlyReason(CapabilityEvaluationInput input,
                                          double complexityScore,
                                          CapabilityRiskLevel riskLevel) {
        int budgetThreshold = input != null && input.getBudgetThresholdTokens() > 0
                ? input.getBudgetThresholdTokens()
                : properties.getBudgetThresholdTokens();
        if (riskLevel == CapabilityRiskLevel.HIGH && complexityScore >= 0.8 && budgetThreshold > 0
                && budgetThreshold < 2000) {
            return "budget_low_for_complexity";
        }
        return null;
    }

    private boolean containsKeyword(String text, List<String> keywords) {
        if (!StringUtils.hasText(text) || keywords == null) {
            return false;
        }
        for (String keyword : keywords) {
            if (StringUtils.hasText(keyword) && text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private double estimateComplexity(String query) {
        if (!StringUtils.hasText(query)) {
            return 0.1;
        }
        String trimmed = query.trim();
        int length = trimmed.length();
        int clauses = trimmed.split("[，。?!?\\s]+").length;
        double lengthScore = Math.min(1.0, length / 200.0);
        double clauseScore = Math.min(0.5, clauses * 0.1);
        return Math.min(1.0, lengthScore + clauseScore);
    }

    private String truncate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= MAX_PREVIEW_CHARS) {
            return trimmed;
        }
        return trimmed.substring(0, MAX_PREVIEW_CHARS);
    }

    private void publishEvent(TenantContext tenantContext,
                              String workflowId,
                              AtomicLong seqCounter,
                              EventType type,
                              Map<String, Object> payload) {
        if (tenantContext == null || workflowId == null || type == null) {
            return;
        }
        long seq = seqCounter != null
                ? seqCounter.incrementAndGet()
                : eventStreamService.nextSequence(tenantContext.getTenantId(), workflowId);
        StreamEvent event = new StreamEvent();
        event.setEventId(workflowId + ":" + seq);
        event.setSchemaVersion("v1");
        event.setWorkflowId(workflowId);
        event.setType(type);
        event.setTimestamp(Instant.now());
        event.setSeq(seq);
        event.setStreamId(workflowId);
        event.setTenantId(tenantContext.getTenantId());
        event.setPayload(payload != null ? new HashMap<>(payload) : new HashMap<>());
        eventPublisher.publishEvent(event);
    }
}
