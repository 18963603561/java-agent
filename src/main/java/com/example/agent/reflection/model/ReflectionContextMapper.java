package com.example.agent.reflection.model;

import com.example.agent.runtime.contract.RuntimeOutputKeys;
import com.example.agent.runtime.model.StepSpec;
import com.example.agent.runtime.step.contract.StepExecutionOutput;
import com.example.agent.runtime.summary.StepOutputSummaryView;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

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
     * 映射反思上下文。
     *
     * @param step 步骤定义
     * @param output 输出对象
     * @param attempt 当前尝试次数
     * @return 反思上下文
     */
    public ReflectionContext map(StepSpec step, StepExecutionOutput output, int attempt) {
        Map<String, Object> contextMap = buildRawContextMap(output);
        Map<String, Object> outputSummaryMap = asObjectMap(contextMap.get(RuntimeOutputKeys.OUTPUT_SUMMARY));
        Map<String, Object> outputDigestMap = asObjectMap(contextMap.get(RuntimeOutputKeys.OUTPUT_DIGEST));

        ReflectionOutputSummary outputSummary = new ReflectionOutputSummary(readString(outputSummaryMap.get(RuntimeOutputKeys.SUMMARY)));
        ReflectionOutputDigest outputDigest = new ReflectionOutputDigest(
                readInteger(outputDigestMap.get(RuntimeOutputKeys.KEY_COUNT)),
                readStringList(outputDigestMap.get(RuntimeOutputKeys.KEYS)),
                readInteger(outputDigestMap.get(RuntimeOutputKeys.CHAR_COUNT)),
                readBoolean(outputDigestMap.get(RuntimeOutputKeys.TRUNCATED))
        );

        return ReflectionContext.builder()
                .stepType(step != null ? step.getStepType() : null)
                .attempt(attempt)
                .outputSummary(outputSummary)
                .outputDigest(outputDigest)
                .build();
    }

    private Map<String, Object> buildRawContextMap(StepExecutionOutput output) {
        StepOutputSummaryView view = output != null ? output.toReflectionView() : StepOutputSummaryView.empty();
        return view.toReflectionContext(DEFAULT_SUMMARY_MAX_CHARS);
    }

    private Map<String, Object> asObjectMap(Object value) {
        if (!(value instanceof Map<?, ?> source) || source.isEmpty()) {
            return Map.of();
        }
        java.util.HashMap<String, Object> copied = new java.util.HashMap<>();
        source.forEach((key, mapValue) -> copied.put(String.valueOf(key), mapValue));
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

