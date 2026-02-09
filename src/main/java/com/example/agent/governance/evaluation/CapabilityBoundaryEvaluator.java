package com.example.agent.governance.evaluation;

import com.example.agent.governance.evaluation.domain.BudgetPressureRiskRule;
import com.example.agent.governance.evaluation.domain.CapabilityRuleBinding;
import com.example.agent.governance.evaluation.domain.CapabilityRuleHit;
import com.example.agent.governance.evaluation.domain.CapabilityRuleRegistry;
import com.example.agent.governance.evaluation.domain.CapabilityRiskRule;
import com.example.agent.governance.evaluation.domain.CapabilityStrategyRule;
import com.example.agent.governance.evaluation.domain.ComplexityThresholdRiskRule;
import com.example.agent.governance.evaluation.domain.DebateKeywordStrategyRule;
import com.example.agent.governance.evaluation.domain.FailureTypesRiskRule;
import com.example.agent.governance.evaluation.domain.HighRiskThoughtTreeStrategyRule;
import com.example.agent.governance.evaluation.domain.MissingToolSummaryRiskRule;
import com.example.agent.governance.evaluation.domain.ResearchKeywordStrategyRule;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.streaming.domain.EventType;
import com.example.agent.streaming.domain.StreamEvent;
import com.example.agent.streaming.sse.EventStreamService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final CapabilityRuleRegistry capabilityRuleRegistry;

    @Autowired
    public CapabilityBoundaryEvaluator(CapabilityEvaluationProperties properties,
                                       ApplicationEventPublisher eventPublisher,
                                       EventStreamService eventStreamService,
                                       CapabilityRuleRegistry capabilityRuleRegistry) {
        this.properties = properties;
        this.eventPublisher = eventPublisher;
        this.eventStreamService = eventStreamService;
        this.capabilityRuleRegistry = capabilityRuleRegistry;
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

        String tenantId = tenantContext != null ? tenantContext.getTenantId() : null;
        String scene = resolveScene(input);
        capabilityRuleRegistry.logRegistrySummary(tenantId, scene, properties);

        double complexityScore = input != null && input.getComplexityScore() != null
                ? input.getComplexityScore()
                : estimateComplexity(input != null ? input.getTaskDescription() : null);
        List<CapabilityRuleHit> ruleHits = new ArrayList<>();
        double riskScore = evaluateRiskScore(input, complexityScore, tenantId, scene, ruleHits);
        CapabilityRiskLevel riskLevel = resolveRiskLevel(riskScore);
        String recommendedStrategy = resolveStrategy(input, complexityScore, riskLevel, tenantId, scene, ruleHits);
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
        result.setRuleHits(ruleHits);

        Map<String, Object> completedPayload = new HashMap<>();
        completedPayload.put("complexityScore", complexityScore);
        completedPayload.put("riskScore", riskScore);
        completedPayload.put("riskLevel", riskLevel.name());
        completedPayload.put("recommendedStrategy", recommendedStrategy);
        completedPayload.put("shouldAskApproval", shouldAskApproval);
        completedPayload.put("shouldDecompose", shouldDecompose);
        completedPayload.put("ruleHits", toRuleHitSummary(ruleHits));
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

        log.info("能力边界评估完成, tenantId={}, workflowId={}, riskLevel={}, complexityScore={}, recommendedStrategy={}, hitRules={}",
                tenantId,
                workflowId,
                riskLevel,
                complexityScore,
                recommendedStrategy,
                toRuleHitSummary(ruleHits));
        return result;
    }

    private double evaluateRiskScore(CapabilityEvaluationInput input,
                                     double complexityScore,
                                     String tenantId,
                                     String scene,
                                     List<CapabilityRuleHit> ruleHits) {
        double riskScore = clampScore(properties.getBaseRiskScore());
        List<CapabilityRuleBinding<CapabilityRiskRule>> activeRules = capabilityRuleRegistry.activeRiskRules(
                tenantId,
                scene,
                properties);
        for (CapabilityRuleBinding<CapabilityRiskRule> binding : activeRules) {
            if (binding == null || binding.getRule() == null || binding.getMetadata() == null) {
                continue;
            }
            CapabilityRiskRule rule = binding.getRule();
            double delta = Math.max(0, rule.score(input, complexityScore, properties));
            if (delta <= 0) {
                continue;
            }
            riskScore += delta;
            CapabilityRuleHit hit = CapabilityRuleHit.riskHit(binding.getMetadata(), delta, complexityScore);
            ruleHits.add(hit);
            log.debug("能力评估风险规则命中, ruleId={}, version={}, priority={}, delta={}, complexityScore={}",
                    binding.getMetadata().getRuleId(),
                    binding.getMetadata().getVersion(),
                    binding.getMetadata().getPriority(),
                    delta,
                    complexityScore);
        }
        return clampScore(riskScore);
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
                                   CapabilityRiskLevel riskLevel,
                                   String tenantId,
                                   String scene,
                                   List<CapabilityRuleHit> ruleHits) {
        List<CapabilityRuleBinding<CapabilityStrategyRule>> activeRules = capabilityRuleRegistry.activeStrategyRules(
                tenantId,
                scene,
                properties);
        for (CapabilityRuleBinding<CapabilityStrategyRule> binding : activeRules) {
            if (binding == null || binding.getRule() == null || binding.getMetadata() == null) {
                continue;
            }
            CapabilityStrategyRule rule = binding.getRule();
            String strategy = rule.resolve(input, complexityScore, riskLevel, properties);
            if (StringUtils.hasText(strategy)) {
                CapabilityRuleHit hit = CapabilityRuleHit.strategyHit(
                        binding.getMetadata(),
                        strategy,
                        riskLevel != null ? riskLevel.name() : null,
                        complexityScore);
                ruleHits.add(hit);
                log.debug("能力评估策略规则命中, ruleId={}, version={}, priority={}, strategy={}",
                        binding.getMetadata().getRuleId(),
                        binding.getMetadata().getVersion(),
                        binding.getMetadata().getPriority(),
                        strategy);
                return strategy;
            }
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

    private String resolveScene(CapabilityEvaluationInput input) {
        if (input == null || !StringUtils.hasText(input.getScene())) {
            return "default";
        }
        return input.getScene().trim();
    }

    private List<String> toRuleHitSummary(List<CapabilityRuleHit> ruleHits) {
        if (ruleHits == null || ruleHits.isEmpty()) {
            return List.of();
        }
        return ruleHits.stream()
                .filter(hit -> hit != null)
                .map(hit -> hit.getRuleType() + ":" + hit.getRuleId() + "@" + hit.getVersion() + "(" + hit.getSummary() + ")")
                .toList();
    }

    private double clampScore(double score) {
        if (score < 0) {
            return 0;
        }
        if (score > 1) {
            return 1;
        }
        return score;
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
