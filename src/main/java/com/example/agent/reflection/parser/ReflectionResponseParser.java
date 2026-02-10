package com.example.agent.reflection.parser;

import com.example.agent.capabilities.llm.repair.JsonOutputRepairService;
import com.example.agent.capabilities.llm.repair.JsonOutputSchema;
import com.example.agent.reflection.ReflectionExecutionContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 反思响应解析器。
 *
 * <p>用途：统一处理反思响应解析、修复和错误类型判定。</p>
 */
@Component
public class ReflectionResponseParser {

    private static final Logger log = LoggerFactory.getLogger(ReflectionResponseParser.class);

    /**
     * 反思上下文摘要最大长度。
     */
    private static final int SUMMARY_MAX_CHARS = 1000;

    /**
     * 对象序列化器。
     */
    private final ObjectMapper objectMapper;

    /**
     * JSON 修复服务。
     */
    private final JsonOutputRepairService jsonOutputRepairService;

    public ReflectionResponseParser(ObjectMapper objectMapper,
                                    JsonOutputRepairService jsonOutputRepairService) {
        this.objectMapper = objectMapper;
        this.jsonOutputRepairService = jsonOutputRepairService;
    }

    /**
     * 尝试解析原始内容。
     *
     * @param content 模型输出
     * @return 解析封装结果
     */
    public ReflectionParseOutcome parse(String content) {
        if (!StringUtils.hasText(content)) {
            return ReflectionParseOutcome.failure("empty_output");
        }
        Map<String, Object> root;
        try {
            root = objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ex) {
            log.warn("反思解析失败，JSON 不合法, reason={}", ex.getMessage());
            return ReflectionParseOutcome.failure("json_parse_error");
        }
        return parseRoot(root);
    }

    private ReflectionParseOutcome parseRoot(Map<String, Object> root) {
        Double score = null;
        if (root.get("score") instanceof Number number) {
            score = number.doubleValue();
        }
        if (score == null) {
            log.debug("反思解析失败，缺少 score 或 score 类型不正确");
            return ReflectionParseOutcome.failure("missing_field");
        }
        if (score < 0 || score > 1) {
            log.warn("反思解析失败，score 超出范围 [0,1], score={}", score);
            return ReflectionParseOutcome.failure("score_out_of_range");
        }
        boolean retry = root.get("retry") instanceof Boolean value && value;
        String notes = root.get("notes") instanceof String value ? value : "llm_reflection";
        return ReflectionParseOutcome.success(new ReflectionParsingResult(score, retry, notes));
    }

    /**
     * 尝试修复并解析模型输出。
     *
     * @param rawContent 原始内容
     * @param context 执行上下文
     * @return 解析结果，为空表示失败
     */
    public ReflectionParsingResult tryRepair(String rawContent, ReflectionExecutionContext context) {
        if (jsonOutputRepairService == null || !StringUtils.hasText(rawContent)) {
            return null;
        }
        String contextJson = buildRepairContextJson(context);
        String repaired = jsonOutputRepairService.repair(
                "reflection",
                rawContent,
                JsonOutputSchema.REFLECTION,
                contextJson,
                1
        );
        if (!StringUtils.hasText(repaired)) {
            return null;
        }
        ReflectionParseOutcome outcome = parse(repaired);
        if (outcome == null || !outcome.isSuccess()) {
            log.warn("反思修复解析失败, parseErrorType={}", outcome != null ? outcome.getErrorType() : null);
            return null;
        }
        return outcome.getParsingResult();
    }

    /**
     * 解析错误类型。
     *
     * @param rawContent 模型原始输出
     * @return 错误类型
     */
    public String resolveParseErrorType(String rawContent) {
        ReflectionParseOutcome outcome = parse(rawContent);
        if (outcome == null) {
            return "missing_field";
        }
        if (outcome.isSuccess()) {
            return null;
        }
        return outcome.getErrorType();
    }

    private String buildRepairContextJson(ReflectionExecutionContext context) {
        Map<String, Object> contextMap = new HashMap<>();
        contextMap.put("stepType", context != null && context.getStep() != null ? context.getStep().getStepType() : null);
        contextMap.put("attempt", context != null ? context.getAttempt() : null);
        contextMap.putAll(buildOutputSummaryContext(context));
        try {
            return objectMapper.writeValueAsString(contextMap);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private Map<String, Object> buildOutputSummaryContext(ReflectionExecutionContext context) {
        if (context == null || context.getOutput() == null) {
            return com.example.agent.runtime.summary.StepOutputSummaryView.empty().toReflectionContext(SUMMARY_MAX_CHARS);
        }
        return context.getOutput().toReflectionView().toReflectionContext(SUMMARY_MAX_CHARS);
    }
}
