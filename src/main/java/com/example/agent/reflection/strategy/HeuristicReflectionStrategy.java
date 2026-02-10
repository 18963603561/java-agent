package com.example.agent.reflection.strategy;

import com.example.agent.reflection.ReflectionDecision;
import com.example.agent.reflection.ReflectionDecisionStatus;
import com.example.agent.reflection.ReflectionExecutionContext;
import com.example.agent.reflection.ReflectionFailureReason;
import com.example.agent.reflection.ReflectionProperties;
import com.example.agent.reflection.ReflectionReport;
import com.example.agent.reflection.ReflectionResult;
import com.example.agent.reflection.model.ReflectionContext;
import com.example.agent.streaming.observability.MetricsPublisher;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 规则反思策略。
 *
 * <p>用途：基于启发式评分规则评估步骤输出质量并给出重试建议。</p>
 */
@Component
public class HeuristicReflectionStrategy implements ReflectionStrategy {

    /**
     * 启发式策略执行顺序。
     */
    private static final int STRATEGY_ORDER = 200;

    private static final Logger log = LoggerFactory.getLogger(HeuristicReflectionStrategy.class);

    /**
     * 反思配置。
     */
    private final ReflectionProperties properties;

    /**
     * 指标发布器。
     */
    private final MetricsPublisher metricsPublisher;

    public HeuristicReflectionStrategy(ReflectionProperties properties,
                                       MetricsPublisher metricsPublisher) {
        this.properties = properties;
        this.metricsPublisher = metricsPublisher;
    }

    @Override
    public ReflectionDecision execute(ReflectionExecutionContext context) {
        ReflectionContext reflectionContext = context != null ? context.getReflectionContext() : null;
        int attempt = context != null ? context.getAttempt() : 0;
        HeuristicScoringContext scoringContext = HeuristicScoringContext.fromReflectionContext(reflectionContext);
        if (context != null
                && context.getStep() != null
                && context.getStep().getArguments() != null
                && Boolean.TRUE.equals(context.getStep().getArguments().get("critical"))) {
            scoringContext = scoringContext.withCritical(true);
        }

        EvaluationResult evaluationResult = evaluate(scoringContext);
        boolean retry = evaluationResult.score < properties.getConfidenceThreshold()
                && attempt < properties.getMaxRetries();
        if (retry) {
            metricsPublisher.increment("reflection.retry.count");
        }
        String tenantId = context != null && context.getTenantContext() != null
                ? context.getTenantContext().getTenantId()
                : null;
        log.info("反思完成(规则), tenantId={}, workflowId={}, stepType={}, attempt={}, score={}, retry={}",
                tenantId,
                context != null ? context.getWorkflowId() : null,
                scoringContext.getStepType(),
                attempt,
                evaluationResult.score,
                retry);
        ReflectionResult result = new ReflectionResult(retry,
                new ReflectionReport(evaluationResult.score, evaluationResult.notes));
        return ReflectionDecision.builder()
                .status(ReflectionDecisionStatus.COMPLETED)
                .reason(ReflectionFailureReason.NONE)
                .result(result)
                .fromLlm(false)
                .repairAttempted(false)
                .repairSuccess(false)
                .build();
    }

    @Override
    public int order() {
        return STRATEGY_ORDER;
    }

    @Override
    public boolean isEnabled(ReflectionProperties reflectionProperties) {
        return reflectionProperties != null && reflectionProperties.isFallbackEnabled();
    }

    private EvaluationResult evaluate(HeuristicScoringContext context) {
        List<String> notes = new ArrayList<>();
        double score = 1.0;

        String summaryText = context != null ? context.getSummaryText() : null;
        Integer charCount = context != null ? context.getCharCount() : null;
        List<String> keys = context != null && context.getKeys() != null ? context.getKeys() : List.of();

        if (!StringUtils.hasText(summaryText) && keys.isEmpty()) {
            score -= 0.6;
            notes.add("输出为空");
        }

        int outputLength = charCount != null ? charCount
                : (StringUtils.hasText(summaryText) ? summaryText.length() : 0);
        if (outputLength < properties.getMinOutputChars()) {
            score -= 0.2;
            notes.add("输出过短");
        }

        for (String requiredKey : properties.getRequiredKeys()) {
            boolean exists = keys.stream().anyMatch(key -> key != null && key.equals(requiredKey));
            if (!exists) {
                score -= 0.05;
                notes.add("缺少字段:" + requiredKey);
            }
        }

        if (StringUtils.hasText(summaryText)) {
            String outputText = summaryText.toLowerCase();
            for (String keyword : properties.getFailureKeywords()) {
                if (outputText.contains(keyword.toLowerCase())) {
                    score -= 0.2;
                    notes.add("检测到失败关键词:" + keyword);
                    break;
                }
            }
        }

        if (context != null && context.isCritical() && score < 0.8) {
            score -= 0.05;
            notes.add("关键步骤需更高质量");
        }

        if (score < 0) {
            score = 0;
        }
        if (score > 1) {
            score = 1;
        }

        if (notes.isEmpty()) {
            notes.add("输出质量满足要求");
        }
        return new EvaluationResult(score, String.join("，", notes));
    }

    private record EvaluationResult(double score, String notes) {
    }
}
