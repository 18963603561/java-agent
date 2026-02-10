package com.example.agent.reasoning.cot;

import com.example.agent.reasoning.common.JsonPayloadNormalizer;
import com.example.agent.reasoning.common.ReasoningParseSupport;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * COT 决策解析器。
 *
 * <p>用途：将模型输出解析为步骤决策对象，并统一处理解析失败场景。
 */
@Component
public class CotDecisionParser {

    private static final Logger log = LoggerFactory.getLogger(CotDecisionParser.class);

    private final ObjectMapper objectMapper;
    private final JsonPayloadNormalizer jsonPayloadNormalizer;
    private final ReasoningParseSupport reasoningParseSupport;

    /**
     * 构造决策解析器。
     *
     * @param objectMapper JSON 序列化工具
     */
    public CotDecisionParser(ObjectMapper objectMapper,
                             JsonPayloadNormalizer jsonPayloadNormalizer,
                             ReasoningParseSupport reasoningParseSupport) {
        this.objectMapper = objectMapper;
        this.jsonPayloadNormalizer = jsonPayloadNormalizer;
        this.reasoningParseSupport = reasoningParseSupport;
    }

    /**
     * 解析模型输出为 COT 决策。
     *
     * @param content 模型输出
     * @return 解析后的决策对象
     */
    public CotDecision parse(String content) {
        if (!StringUtils.hasText(content)) {
            return CotDecision.invalid("empty_response");
        }
        try {
            String normalized = jsonPayloadNormalizer.normalize(content);
            if (!StringUtils.hasText(normalized)) {
                return CotDecision.invalid("empty_response");
            }
            Map<String, Object> root = objectMapper.readValue(normalized, new TypeReference<Map<String, Object>>() {
            });
            boolean shouldContinue = reasoningParseSupport.resolveBoolean(root, "shouldContinue", true);
            String stepSummary = reasoningParseSupport.resolveString(root, "stepSummary", "summary");
            String finalAnswer = reasoningParseSupport.resolveString(root, "finalAnswer", "answer");
            Double confidence = reasoningParseSupport.resolveDouble(root, "confidence");
            String stopReason = reasoningParseSupport.resolveString(root, "stopReason", null);
            if (!shouldContinue && !StringUtils.hasText(stopReason)) {
                stopReason = "completed";
            }
            return new CotDecision(shouldContinue, stepSummary, finalAnswer, confidence, stopReason, true);
        } catch (Exception ex) {
            log.warn("链式推理输出解析失败, reason={}", ex.getMessage());
            return CotDecision.invalid("invalid_response");
        }
    }

    /**
     * COT 决策对象。
     */
    public record CotDecision(boolean shouldContinue,
                              String stepSummary,
                              String finalAnswer,
                              Double confidence,
                              String stopReason,
                              boolean valid) {

        /**
         * 构造无效决策。
         */
        public static CotDecision invalid(String stopReason) {
            return new CotDecision(false, null, null, 0.3, stopReason, false);
        }
    }
}
