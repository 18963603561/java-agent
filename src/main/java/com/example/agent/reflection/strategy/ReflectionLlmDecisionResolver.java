package com.example.agent.reflection.strategy;

import com.example.agent.reflection.ReflectionDecision;
import com.example.agent.reflection.ReflectionExecutionContext;
import com.example.agent.reflection.ReflectionFailureReason;
import com.example.agent.reflection.ReflectionProperties;
import com.example.agent.reflection.ReflectionReport;
import com.example.agent.reflection.ReflectionResult;
import com.example.agent.reflection.parser.ReflectionParseOutcome;
import com.example.agent.reflection.parser.ReflectionParsingResult;
import com.example.agent.reflection.parser.ReflectionResponseParser;
import com.example.agent.streaming.observability.MetricsPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 反思 LLM 决策判定器。
 * <p>用途：统一处理模型输出解析、修复判定、重试计算与失败原因映射。</p>
 */
@Component
public class ReflectionLlmDecisionResolver {

    private static final Logger log = LoggerFactory.getLogger(ReflectionLlmDecisionResolver.class);

    /**
     * 反思解析器。
     */
    private final ReflectionResponseParser reflectionResponseParser;

    /**
     * 反思配置。
     */
    private final ReflectionProperties reflectionProperties;

    /**
     * 指标发布器。
     */
    private final MetricsPublisher metricsPublisher;

    public ReflectionLlmDecisionResolver(ReflectionResponseParser reflectionResponseParser,
                                         ReflectionProperties reflectionProperties,
                                         MetricsPublisher metricsPublisher) {
        this.reflectionResponseParser = reflectionResponseParser;
        this.reflectionProperties = reflectionProperties;
        this.metricsPublisher = metricsPublisher;
    }

    /**
     * 基于模型原始文本构建反思决策。
     *
     * @param rawContent 模型原始文本
     * @param context 反思执行上下文
     * @param tenantId 租户标识
     * @param workflowId 工作流标识
     * @param stepType 步骤类型
     * @param attempt 当前尝试次数
     * @return 决策结果
     */
    public ReflectionLlmResolutionResult resolve(String rawContent,
                                                 ReflectionExecutionContext context,
                                                 String tenantId,
                                                 String workflowId,
                                                 String stepType,
                                                 int attempt) {
        boolean repairAttempted = false;
        boolean repairSuccess = false;
        String parseErrorType = null;
        ReflectionFailureReason reason = ReflectionFailureReason.NONE;

        ReflectionParseOutcome parseOutcome = reflectionResponseParser.parse(rawContent);
        ReflectionParsingResult parsed = parseOutcome != null && parseOutcome.isSuccess()
                ? parseOutcome.getParsingResult()
                : null;

        if (parsed == null && parseOutcome != null) {
            parseErrorType = parseOutcome.getErrorType();
            if ("json_parse_error".equals(parseErrorType)) {
                reason = ReflectionFailureReason.LLM_PARSE_ERROR;
            }
            log.warn("反思解析失败, tenantId={}, workflowId={}, stepType={}, attempt={}, parseErrorType={}, reasonCode={}",
                    tenantId, workflowId, stepType, attempt, parseErrorType, reason);
        }

        if (parsed == null) {
            if (parseErrorType == null) {
                parseErrorType = reflectionResponseParser.resolveParseErrorType(rawContent);
            }
            repairAttempted = true;
            parsed = reflectionResponseParser.tryRepair(rawContent, context);
            if (parsed == null) {
                if (reason == ReflectionFailureReason.NONE) {
                    reason = ReflectionFailureReason.LLM_REPAIR_FAILED;
                }
            } else {
                reason = ReflectionFailureReason.NONE;
                repairSuccess = true;
            }
        }

        if (parsed == null) {
            log.warn("反思修复失败, tenantId={}, workflowId={}, stepType={}, attempt={}, reasonCode={}, parseErrorType={}",
                    tenantId, workflowId, stepType, attempt, reason, parseErrorType);
            ReflectionDecision decision = ReflectionDecision.fallbackRequired(reason, parseErrorType, repairAttempted, repairSuccess);
            return ReflectionLlmResolutionResult.failure(decision, parseErrorType, repairAttempted, repairSuccess);
        }

        boolean retry = parsed.isRetry() && context != null
                && context.getAttempt() < reflectionProperties.getMaxRetries();
        if (retry) {
            metricsPublisher.increment("reflection.retry.count");
        }
        ReflectionResult result = new ReflectionResult(retry,
                new ReflectionReport(parsed.getScore(), parsed.getNotes()));
        ReflectionDecision decision = ReflectionDecision.completed(result, true, repairAttempted, repairSuccess);
        return ReflectionLlmResolutionResult.success(decision, parsed.getScore(), parseErrorType, repairAttempted, repairSuccess);
    }

    /**
     * 判定结果对象。
     * <p>用途：对外暴露决策结果与追踪字段，供编排层统一记录 prompt trace。</p>
     */
    public record ReflectionLlmResolutionResult(ReflectionDecision decision,
                                                boolean parseSuccess,
                                                Double score,
                                                String parseErrorType,
                                                boolean repairAttempted,
                                                boolean repairSuccess) {

        /**
         * 构建成功结果。
         *
         * @param decision 反思决策
         * @param score 评分
         * @param parseErrorType 解析错误类型
         * @param repairAttempted 是否尝试修复
         * @param repairSuccess 是否修复成功
         * @return 判定结果
         */
        public static ReflectionLlmResolutionResult success(ReflectionDecision decision,
                                                            Double score,
                                                            String parseErrorType,
                                                            boolean repairAttempted,
                                                            boolean repairSuccess) {
            return new ReflectionLlmResolutionResult(
                    decision,
                    true,
                    score,
                    parseErrorType,
                    repairAttempted,
                    repairSuccess
            );
        }

        /**
         * 构建失败结果。
         *
         * @param decision 反思决策
         * @param parseErrorType 解析错误类型
         * @param repairAttempted 是否尝试修复
         * @param repairSuccess 是否修复成功
         * @return 判定结果
         */
        public static ReflectionLlmResolutionResult failure(ReflectionDecision decision,
                                                            String parseErrorType,
                                                            boolean repairAttempted,
                                                            boolean repairSuccess) {
            return new ReflectionLlmResolutionResult(
                    decision,
                    false,
                    null,
                    parseErrorType,
                    repairAttempted,
                    repairSuccess
            );
        }
    }
}

