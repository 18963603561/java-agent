package com.example.agent.reflection.parser;

import com.example.agent.capabilities.llm.repair.JsonOutputRepairService;
import com.example.agent.capabilities.llm.repair.JsonOutputSchema;
import com.example.agent.reflection.ReflectionExecutionContext;
import com.example.agent.runtime.contract.RuntimeOutputKeys;
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
        // 当上下文或输出为空时返回空摘要上下文。
        if (context == null || context.getOutput() == null) {
            // 使用空映射构建反思摘要上下文。
            return buildSummaryContextFromMap(Map.of());
        }
        // 基于输出摘要映射构建反思上下文。
        return buildSummaryContextFromMap(context.getOutput().getSummary());
    }

    private Map<String, Object> buildSummaryContextFromMap(Map<String, Object> summaryMap) {
        // 初始化反思上下文容器。
        Map<String, Object> context = new HashMap<>();
        // 初始化输出摘要映射。
        Map<String, Object> outputSummary = new HashMap<>();
        // 初始化输出指纹映射。
        Map<String, Object> outputDigest = new HashMap<>();

        // 读取语义摘要文本，作为反思提示词输入。
        String summaryText = resolveSummaryText(summaryMap);
        // 摘要为空时使用默认占位文本。
        if (!StringUtils.hasText(summaryText)) {
            // 设置默认占位文本，避免空摘要。
            summaryText = "(summary disabled)";
        }
        // 写入摘要文本并按最大长度截断。
        outputSummary.put(RuntimeOutputKeys.SUMMARY_TEXT, truncateSummary(summaryText, SUMMARY_MAX_CHARS));
        // 写入摘要字符数，便于诊断。
        outputDigest.put(RuntimeOutputKeys.CHAR_COUNT, summaryText != null ? summaryText.length() : 0);
        // 读取截断标记并写入指纹。
        Boolean truncated = summaryMap != null && summaryMap.get(RuntimeOutputKeys.TRUNCATED) instanceof Boolean value
                ? value
                : null;
        // 截断标记存在时写入，避免空字段污染。
        if (truncated != null) {
            // 写入截断标记字段。
            outputDigest.put(RuntimeOutputKeys.TRUNCATED, truncated);
        }
        // 写入输出摘要到反思上下文。
        context.put(RuntimeOutputKeys.OUTPUT_SUMMARY, outputSummary);
        // 仅在指纹不为空时写入，避免空字段污染。
        if (!outputDigest.isEmpty()) {
            context.put(RuntimeOutputKeys.OUTPUT_DIGEST, outputDigest);
        }
        return context;
    }

    private String resolveSummaryText(Map<String, Object> summaryMap) {
        // 摘要映射为空时直接返回空值。
        if (summaryMap == null || summaryMap.isEmpty()) {
            // 返回空值，避免空指针。
            return null;
        }
        // 读取语义摘要文本字段。
        String text = readString(summaryMap.get(RuntimeOutputKeys.SUMMARY_TEXT));
        // 返回语义摘要文本。
        return text;
    }

    private Map<String, Object> asObjectMap(Object value) {
        // 非映射或空映射时直接返回空结果。
        if (!(value instanceof Map<?, ?> source) || source.isEmpty()) {
            // 返回可修改的空映射，避免后续写入抛出异常。
            return new HashMap<>();
        }
        // 复制映射，避免外部修改影响反思上下文。
        Map<String, Object> copied = new HashMap<>();
        // 逐项拷贝键值，保证类型一致性。
        source.forEach((key, mapValue) -> copied.put(String.valueOf(key), mapValue));
        // 返回拷贝后的映射结果。
        return copied;
    }

    private String readString(Object value) {
        // 统一转换为字符串，空值直接返回 null。
        return value == null ? null : String.valueOf(value);
    }

    private String buildDigestSummary(Map<String, Object> digest) {
        // 指纹为空时直接返回空值。
        if (digest == null || digest.isEmpty()) {
            return null;
        }
        // 初始化指纹摘要文本构建器。
        StringBuilder builder = new StringBuilder("digest:");
        // 追加键数量字段。
        appendDigestField(builder, RuntimeOutputKeys.KEY_COUNT, digest.get(RuntimeOutputKeys.KEY_COUNT));
        // 追加键列表字段。
        appendDigestField(builder, RuntimeOutputKeys.KEYS, digest.get(RuntimeOutputKeys.KEYS));
        // 读取字符数量字段。
        Object charCount = digest.get(RuntimeOutputKeys.CHAR_COUNT);
        // 字符数量存在时追加到摘要文本。
        if (charCount != null) {
            // 判断截断标记，决定字段展示格式。
            boolean truncated = Boolean.TRUE.equals(digest.get(RuntimeOutputKeys.TRUNCATED));
            // 拼接字段名，截断时显示上限符号。
            String field = truncated ? RuntimeOutputKeys.CHAR_COUNT + "<=" : RuntimeOutputKeys.CHAR_COUNT;
            // 追加字符数量字段。
            appendDigestField(builder, field, charCount);
        }
        // 追加截断标记字段。
        appendDigestField(builder, RuntimeOutputKeys.TRUNCATED, digest.get(RuntimeOutputKeys.TRUNCATED));
        return builder.toString();
    }

    private void appendDigestField(StringBuilder builder, String field, Object value) {
        // 参数为空时直接返回，避免空字段写入。
        if (builder == null || value == null) {
            return;
        }
        // 判断是否需要追加分隔符，保证格式可读。
        if (builder.length() > 0 && builder.charAt(builder.length() - 1) != ':') {
            builder.append(", ");
        } else {
            builder.append(' ');
        }
        // 追加字段键值对到摘要文本。
        builder.append(field).append('=').append(value);
    }

    private String truncateSummary(String text, int maxChars) {
        // 空文本或无需截断时直接返回原值。
        if (!StringUtils.hasText(text) || maxChars <= 0 || text.length() <= maxChars) {
            return text;
        }
        // 定义截断后缀，提示被截断。
        String suffix = "...(truncated)";
        // 截断长度不足时直接截取，避免负索引。
        if (maxChars <= suffix.length()) {
            return text.substring(0, maxChars);
        }
        // 计算可截取长度并追加后缀。
        int endIndex = maxChars - suffix.length();
        // 长度异常时回退为直接截取。
        if (endIndex <= 0) {
            return text.substring(0, maxChars);
        }
        return text.substring(0, endIndex) + suffix;
    }
}
