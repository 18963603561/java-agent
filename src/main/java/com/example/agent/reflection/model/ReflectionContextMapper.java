package com.example.agent.reflection.model;

import com.example.agent.runtime.contract.RuntimeOutputKeys;
import com.example.agent.runtime.model.StepSpec;
import com.example.agent.runtime.step.contract.StepExecutionOutput;
import com.example.agent.runtime.structured.StructuredExtractorRegistry;
import com.example.agent.runtime.structured.result.StructuredResult;
import com.example.agent.runtime.structured.structured.StructuredData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 反思上下文映射器。
 *
 * <p>用途：将步骤与输出对象映射为强类型反思上下文，集中管理摘要契约。</p>
 */
@Component
public class ReflectionContextMapper {

    /**
     * 默认摘要长度上限。
     */
    private static final int DEFAULT_SUMMARY_MAX_CHARS = 1000;

    /**
     * 结构化结果提取器注册表。
     */
    private final StructuredExtractorRegistry structuredExtractorRegistry;

    /**
     * 无参构造，允许在无结构化提取器场景下使用。
     */
    public ReflectionContextMapper() {
        // 设置结构化提取器为空，避免强制依赖结构化能力。
        this.structuredExtractorRegistry = null;
    }

    public ReflectionContextMapper(StructuredExtractorRegistry structuredExtractorRegistry) {
        this.structuredExtractorRegistry = structuredExtractorRegistry;
    }

    /**
     * 映射反思上下文。
     *
     * @param step 步骤定义
     * @param output 输出对象
     * @param attempt 当前尝试次数
     * @return 反思上下文
     */
    public ReflectionContext map(StepSpec step, StepExecutionOutput output, int attempt) {
        // 构建结构化结果映射，作为反思主路径依据。
        Map<String, Object> resultMap = resolveStructuredResultMap(step, output);
        // 构建反思摘要上下文映射，统一摘要与指纹结构。
        Map<String, Object> contextMap = buildRawContextMap(output, resultMap);
        // 解析输出摘要映射，确保类型安全。
        Map<String, Object> outputSummaryMap = asObjectMap(contextMap.get(RuntimeOutputKeys.OUTPUT_SUMMARY));
        // 解析输出指纹映射，确保类型安全。
        Map<String, Object> outputDigestMap = asObjectMap(contextMap.get(RuntimeOutputKeys.OUTPUT_DIGEST));

        // 构建输出摘要对象，提供摘要文本。
        ReflectionOutputSummary outputSummary = new ReflectionOutputSummary(readString(outputSummaryMap.get(RuntimeOutputKeys.SUMMARY_TEXT)));
        // 构建输出指纹对象，提供输出结构特征。
        ReflectionOutputDigest outputDigest = new ReflectionOutputDigest(
                readInteger(outputDigestMap.get(RuntimeOutputKeys.KEY_COUNT)),
                readStringList(outputDigestMap.get(RuntimeOutputKeys.KEYS)),
                readInteger(outputDigestMap.get(RuntimeOutputKeys.CHAR_COUNT)),
                readBoolean(outputDigestMap.get(RuntimeOutputKeys.TRUNCATED))
        );

        // 组装反思上下文并返回。
        return ReflectionContext.builder()
                .stepType(step != null ? step.getStepType() : null)
                .attempt(attempt)
                .outputSummary(outputSummary)
                .outputDigest(outputDigest)
                .result(resultMap)
                .build();
    }

    private Map<String, Object> buildRawContextMap(StepExecutionOutput output, Map<String, Object> resultMap) {
        // 读取语义摘要映射，为空时使用空映射兜底。
        Map<String, Object> summaryMap = output != null ? output.getSummary() : Map.of();
        // 构建反思摘要上下文，统一摘要结构。
        return buildReflectionSummaryContext(summaryMap, resultMap);
    }

    private Map<String, Object> buildReflectionSummaryContext(Map<String, Object> summaryMap,
                                                              Map<String, Object> resultMap) {
        // 初始化反思上下文容器。
        Map<String, Object> context = new HashMap<>();
        // 初始化输出摘要映射，统一摘要文本字段。
        Map<String, Object> outputSummary = new HashMap<>();
        // 构建输出指纹映射，用于反思评分。
        Map<String, Object> outputDigest = buildOutputDigest(summaryMap, resultMap);

        // 读取语义摘要文本，用于反思提示词输入。
        String summaryText = resolveSummaryText(summaryMap);
        // 若摘要为空则回退到指纹摘要。
        if (!StringUtils.hasText(summaryText)) {
            // 构建指纹摘要文本作为兜底。
            summaryText = buildDigestSummary(outputDigest);
            // 若指纹摘要为空则输出默认占位文本。
            if (!StringUtils.hasText(summaryText)) {
                summaryText = "(summary disabled)";
            }
        }
        // 写入摘要文本并按最大长度截断。
        outputSummary.put(RuntimeOutputKeys.SUMMARY_TEXT, truncateSummary(summaryText, DEFAULT_SUMMARY_MAX_CHARS));
        // 写入摘要字符数到指纹映射。
        outputDigest.put(RuntimeOutputKeys.CHAR_COUNT, summaryText != null ? summaryText.length() : 0);
        // 写入输出摘要到反思上下文。
        context.put(RuntimeOutputKeys.OUTPUT_SUMMARY, outputSummary);
        // 仅在指纹不为空时写入，避免空字段污染。
        if (!outputDigest.isEmpty()) {
            context.put(RuntimeOutputKeys.OUTPUT_DIGEST, outputDigest);
        }
        return context;
    }

    private Map<String, Object> resolveStructuredResultMap(StepSpec step, StepExecutionOutput output) {
        // 读取步骤输出映射，作为结构化提取输入。
        Map<String, Object> rawOutput = output != null ? output.getPayload() : null;
        // 判断结构化提取器是否为空，空时返回空映射以避免走原始输出。
        if (structuredExtractorRegistry == null) {
            // 返回空映射，避免原始输出进入反思主流程。
            return new HashMap<>();
        }
        // 构建结构化提取输入映射，保证结构稳定。
        Map<String, Object> resultInput = buildResultInput(rawOutput);
        // 读取步骤类型，作为结构化提取条件。
        String stepType = step != null ? step.getStepType() : null;
        // 读取工具名称，作为结构化提取条件。
        String toolName = output != null ? output.getToolName() : null;
        // 读取原始引用，用于结构化结果追踪。
        String rawRef = output != null ? output.getRawRef() : null;
        // 调用结构化提取器生成结构化结果。
        StructuredResult<? extends StructuredData> structured = structuredExtractorRegistry.extract(
                stepType,
                toolName,
                resultInput,
                rawRef
        );
        // 结构化结果为空时返回空映射，避免回退原始输出。
        if (structured == null) {
            // 返回空映射，避免原始输出进入反思主流程。
            return new HashMap<>();
        }
        // 转换结构化结果为映射并返回。
        return toResultMap(structured);
    }

    private Map<String, Object> buildResultInput(Map<String, Object> rawOutput) {
        // 初始化结果输入容器。
        Map<String, Object> resultMap = new HashMap<>();
        // 判断原始输出是否为空，空时直接返回空映射。
        if (rawOutput == null || rawOutput.isEmpty()) {
            // 返回空映射，避免后续空指针。
            return resultMap;
        }
        // 读取 result 字段，优先使用结构化结果容器。
        Object resultObj = rawOutput.get(RuntimeOutputKeys.RESULT);
        // 判断 result 字段是否为映射，映射时拷贝其键值。
        if (resultObj instanceof Map<?, ?> map) {
            // 拷贝结果映射，统一键类型。
            map.forEach((key, value) -> resultMap.put(String.valueOf(key), value));
        } else {
            // 读取 rawResult 字段，优先提取工具输出原始数据。
            Object rawResult = rawOutput.get(RuntimeOutputKeys.RAW_RESULT);
            // 判断 rawResult 是否为映射，映射时提取内层优先数据。
            if (rawResult instanceof Map<?, ?> rawMap) {
                // 读取 rawResult 内的 result 字段，可能包含业务数据。
                Object nestedResult = rawMap.get(RuntimeOutputKeys.RESULT);
                // 判断 nestedResult 是否为映射，映射时拷贝键值。
                if (nestedResult instanceof Map<?, ?> nestedMap) {
                    // 拷贝 nestedResult 映射，统一键类型。
                    nestedMap.forEach((key, value) -> resultMap.put(String.valueOf(key), value));
                } else {
                    // 拷贝 rawResult 映射，统一键类型。
                    rawMap.forEach((key, value) -> resultMap.put(String.valueOf(key), value));
                }
            } else {
                // 回退使用原始输出映射，保证结果可用。
                resultMap.putAll(rawOutput);
            }
        }
        return resultMap;
    }

    private Map<String, Object> toResultMap(StructuredResult<? extends StructuredData> structured) {
        // 初始化结构化结果映射。
        Map<String, Object> resultMap = new HashMap<>();
        // 结构化结果为空时直接返回空映射。
        if (structured == null) {
            // 返回空映射，避免空指针。
            return resultMap;
        }
        // 写入结果类型字段，标识语义类型。
        if (structured.getKind() != null) {
            // 写入 kind 字段，便于下游识别。
            resultMap.put("kind", structured.getKind().name());
        }
        // 写入结构版本字段，便于演进。
        if (structured.getSchemaVersion() != null) {
            // 写入 schemaVersion 字段，保证版本可追踪。
            resultMap.put("schemaVersion", structured.getSchemaVersion());
        }
        // 写入结构化数据映射，作为主结果数据。
        resultMap.put("data", structured.dataAsMap());
        return resultMap;
    }

    private Map<String, Object> buildOutputDigest(Map<String, Object> summaryMap,
                                                  Map<String, Object> resultMap) {
        // 初始化指纹映射容器。
        Map<String, Object> digest = new HashMap<>();
        // 判断结构化结果是否为空，空时直接返回空指纹。
        if (resultMap == null || resultMap.isEmpty()) {
            // 返回空指纹，避免无意义摘要。
            return digest;
        }
        // 计算结果键数量，作为指纹指标。
        int keyCount = resultMap.size();
        // 写入键数量字段。
        digest.put(RuntimeOutputKeys.KEY_COUNT, keyCount);
        // 结果映射非空时写入键列表。
        // 初始化键列表容器。
        List<String> keys = new ArrayList<>();
        // 循环遍历结果键集合，逐项写入。
        for (String key : resultMap.keySet()) {
            // 写入键名，保持顺序。
            keys.add(String.valueOf(key));
        }
        // 写入键列表字段。
        digest.put(RuntimeOutputKeys.KEYS, keys);
        // 读取截断标记并写入指纹。
        Boolean truncated = summaryMap != null && summaryMap.get(RuntimeOutputKeys.TRUNCATED) instanceof Boolean value
                ? value
                : null;
        // 截断标记存在时写入，避免空字段污染。
        if (truncated != null) {
            // 写入截断标记字段。
            digest.put(RuntimeOutputKeys.TRUNCATED, truncated);
        }
        return digest;
    }

    private String resolveSummaryText(Map<String, Object> summaryMap) {
        // 判断摘要映射是否为空，空时直接返回空值。
        if (summaryMap == null || summaryMap.isEmpty()) {
            // 返回空值，避免空指针。
            return null;
        }
        // 读取语义摘要文本字段。
        String text = readString(summaryMap.get(RuntimeOutputKeys.SUMMARY_TEXT));
        // 返回语义摘要文本。
        return text;
    }

    private String buildDigestSummary(Map<String, Object> digest) {
        // 摘要指纹为空时直接返回空值。
        if (digest == null || digest.isEmpty()) {
            return null;
        }
        // 初始化指纹摘要文本构建器。
        StringBuilder builder = new StringBuilder("digest:");
        // 追加键数量字段。
        appendDigestField(builder, RuntimeOutputKeys.KEY_COUNT, digest.get(RuntimeOutputKeys.KEY_COUNT));
        // 追加键列表字段。
        appendDigestField(builder, RuntimeOutputKeys.KEYS, digest.get(RuntimeOutputKeys.KEYS));
        // 读取字符数量字段用于追加。
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

    private Map<String, Object> asObjectMap(Object value) {
        // 判断是否为可用映射，非映射或空映射时返回可写空容器。
        if (!(value instanceof Map<?, ?> source) || source.isEmpty()) {
            // 返回可修改的空映射，避免后续写入抛出异常。
            return new java.util.HashMap<>();
        }
        // 初始化映射副本，隔离外部修改影响。
        java.util.HashMap<String, Object> copied = new java.util.HashMap<>();
        // 逐项拷贝键值，统一键类型为字符串。
        source.forEach((key, mapValue) -> copied.put(String.valueOf(key), mapValue));
        // 返回拷贝后的映射结果。
        return copied;
    }

    private String readString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Integer readInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? null : Integer.parseInt(String.valueOf(value));
        } catch (Exception ex) {
            return null;
        }
    }

    private Boolean readBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value == null ? null : Boolean.parseBoolean(String.valueOf(value));
    }

    private List<String> readStringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }
}
