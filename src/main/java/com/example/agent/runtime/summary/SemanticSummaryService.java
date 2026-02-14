package com.example.agent.runtime.summary;

import com.example.agent.runtime.contract.RuntimeOutputFieldExtractor;
import com.example.agent.runtime.contract.RuntimeOutputKeys;
import com.example.agent.runtime.model.SemanticSummary;
import com.example.agent.runtime.model.SummarySourceRef;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 语义摘要生成服务。
 *
 * <p>用途：将步骤输出转换为语义摘要，供反思、记忆与上下文压缩使用。</p>
 * <p>输入：摘要构建输入契约。</p>
 * <p>输出：语义摘要对象。</p>
 * <p>边界：输出为空时返回兜底摘要文本。</p>
 */
@Service
public class SemanticSummaryService {

    /**
     * 摘要配置。
     */
    private final StepSummaryProperties properties;

    /**
     * 摘要场景解析器。
     */
    private final SemanticSummaryPolicyResolver policyResolver;

    /**
     * 摘要预算解析器。
     */
    private final SemanticSummaryBudgetResolver budgetResolver;

    public SemanticSummaryService(StepSummaryProperties properties,
                                  SemanticSummaryPolicyResolver policyResolver,
                                  SemanticSummaryBudgetResolver budgetResolver) {
        this.properties = properties;
        this.policyResolver = policyResolver;
        this.budgetResolver = budgetResolver;
    }

    /**
     * 构建语义摘要。
     *
     * @param input 摘要输入
     * @return 语义摘要对象
     */
    public SemanticSummary buildSummary(StepSummaryBuildInput input) {
        StepSummaryBuildInput safeInput = input;
        // 输入为空时使用默认对象，避免空指针。
        if (safeInput == null) {
            // 构建默认输入对象以保证后续读取稳定。
            safeInput = StepSummaryBuildInput.builder().build();
        }

        // 解析输出映射，避免直接依赖外部对象类型。
        Map<String, Object> outputMap = resolveOutputMap(safeInput.getOutput());
        // 解析摘要场景，便于应用场景化策略。
        SemanticSummaryScenario scenario = resolveScenario(safeInput);
        // 解析摘要预算，控制摘要长度与列表规模。
        SemanticSummaryBudget budget = resolveBudget(safeInput, scenario);
        // 解析摘要文本，优先使用 answer/finalAnswer。
        String summaryText = resolveSummaryText(outputMap, safeInput.getError());
        // 解析高亮信息，作为摘要补充。
        List<String> highlights = resolveStringList(outputMap, RuntimeOutputKeys.SUMMARY_HIGHLIGHTS,
                budget != null ? budget.getMaxHighlights() : resolveMaxListItems());
        // 解析未解决问题，作为后续回归线索。
        List<String> openQuestions = resolveStringList(outputMap, RuntimeOutputKeys.SUMMARY_OPEN_QUESTIONS,
                budget != null ? budget.getMaxOpenQuestions() : resolveMaxListItems());
        // 解析风险提示，作为摘要补充信息。
        List<String> risks = resolveStringList(outputMap, RuntimeOutputKeys.SUMMARY_RISKS,
                budget != null ? budget.getMaxRisks() : resolveMaxListItems());
        // 解析来源引用，便于追踪原始数据。
        List<SummarySourceRef> sourceRefs = resolveSourceRefs(outputMap, safeInput,
                budget != null ? budget.getMaxSourceRefs() : resolveMaxListItems());

        boolean truncated = false;
        String finalText = summaryText;
        // 按预算截断摘要文本，控制长度。
        if (StringUtils.hasText(finalText)) {
            // 解析最大长度限制，避免负值。
            int maxChars = budget != null ? budget.getMaxChars() : resolveMaxSummaryChars();
            // 判断是否超长并截断，记录截断标记。
            // 设计意图：maxChars=0 表示不限制长度，不应清空摘要文本。
            if (maxChars > 0 && finalText.length() > maxChars) {
                finalText = finalText.substring(0, maxChars);
                truncated = true;
            }
        }
        // 判断摘要文本与列表均为空，空时写入兜底文本。
        if (!StringUtils.hasText(finalText) && isAllEmpty(highlights, openQuestions, risks)) {
            // 写入兜底摘要文本，避免下游空指针。
            finalText = "no_summary";
        }

        // 构建语义摘要对象，保证字段齐全。
        return new SemanticSummary(
                finalText,
                highlights,
                openQuestions,
                risks,
                sourceRefs,
                truncated
        );
    }

    private Map<String, Object> resolveOutputMap(Object output) {
        // 输出为映射时直接转换为可读映射。
        if (output instanceof Map<?, ?> map && !map.isEmpty()) {
            // 初始化映射副本，隔离外部修改影响。
            Map<String, Object> copied = new LinkedHashMap<>();
            // 循环遍历映射条目，逐项写入副本。
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                // 写入条目键值，统一键为字符串。
                copied.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            // 返回复制后的映射结果。
            return copied;
        }
        // 输出为空时返回空映射。
        return Map.of();
    }

    private String resolveSummaryText(Map<String, Object> outputMap, Object error) {
        // 优先读取 answer 字段作为摘要文本。
        String text = readString(outputMap, "answer");
        // 若 answer 为空，回退到 finalAnswer。
        if (!StringUtils.hasText(text)) {
            text = readString(outputMap, "finalAnswer");
        }
        // 若仍为空，回退到 message。
        if (!StringUtils.hasText(text)) {
            text = readString(outputMap, "message");
        }
        // 若仍为空，回退到 summary 字段。
        if (!StringUtils.hasText(text)) {
            text = readString(outputMap, "summary");
        }
        // 若仍为空且存在错误对象，使用错误文本兜底。
        if (!StringUtils.hasText(text) && error != null) {
            text = String.valueOf(error);
        }
        return text;
    }

    private List<String> resolveStringList(Map<String, Object> outputMap, String key, int maxItems) {
        // 判断输出映射是否为空，空时返回空列表。
        if (outputMap == null || outputMap.isEmpty()) {
            // 返回空列表，避免空指针。
            return List.of();
        }
        // 读取目标字段对象。
        Object value = outputMap.get(key);
        // 初始化列表容器。
        List<String> items = new ArrayList<>();
        // 字符串字段直接加入列表。
        if (value instanceof String text) {
            // 判断字符串是否为空，非空时写入列表。
            if (StringUtils.hasText(text)) {
                // 写入字符串到列表。
                items.add(text);
            }
        }
        // 列表字段逐项写入。
        if (value instanceof List<?> list && !list.isEmpty()) {
            // 循环遍历列表元素，逐项转换为字符串。
            for (Object item : list) {
                // 判断元素是否为空，空时跳过。
                if (item == null) {
                    // 跳过空元素，继续处理下一项。
                    continue;
                }
                // 将元素转换为字符串。
                String text = String.valueOf(item);
                // 判断字符串是否为空，非空时写入列表。
                if (StringUtils.hasText(text)) {
                    // 写入字符串到列表。
                    items.add(text);
                }
            }
        }
        // 返回受限后的列表。
        return limitList(items, maxItems);
    }

    private List<SummarySourceRef> resolveSourceRefs(Map<String, Object> outputMap,
                                                     StepSummaryBuildInput input,
                                                     int maxItems) {
        // 初始化来源引用列表。
        List<SummarySourceRef> refs = new ArrayList<>();
        // 判断输出映射是否为空，空时直接返回空列表。
        if (outputMap == null || outputMap.isEmpty()) {
            // 返回空列表，避免空指针。
            return List.of();
        }
        // 解析原始引用并写入来源列表。
        String rawRef = RuntimeOutputFieldExtractor.resolveRawRef(outputMap);
        // 判断原始引用是否存在，存在时写入。
        if (StringUtils.hasText(rawRef)) {
            // 追加原始引用来源。
            appendSourceRef(refs, SummarySourceRef.rawRef(rawRef));
        }
        // 解析工具名称并写入来源列表。
        String toolName = input != null ? input.getToolName() : null;
        // 判断工具名称是否存在，存在时写入。
        if (StringUtils.hasText(toolName)) {
            // 追加工具来源引用。
            appendSourceRef(refs, SummarySourceRef.toolRef(toolName));
        }
        // 解析结果路径并写入来源列表。
        List<String> resultPaths = resolveResultPaths(outputMap);
        // 循环遍历结果路径，逐条写入来源列表。
        for (String path : resultPaths) {
            // 判断路径是否为空，空时跳过。
            if (!StringUtils.hasText(path)) {
                // 跳过空路径，继续处理。
                continue;
            }
            // 追加结果路径来源。
            appendSourceRef(refs, SummarySourceRef.resultPath(path));
        }
        // 返回受限后的来源引用列表。
        return limitSourceRefs(refs, maxItems);
    }

    private int resolveMaxSummaryChars() {
        // 配置为空时返回不限制长度。
        if (properties == null) {
            return 0;
        }
        // 返回摘要最大字符数，避免负值。
        return Math.max(0, properties.getMaxChars());
    }

    private int resolveMaxListItems() {
        // 配置为空时返回不限制数量。
        if (properties == null) {
            // 返回默认值，避免空指针。
            return 0;
        }
        // 返回摘要列表最大数量。
        return Math.max(0, properties.getMaxListItems());
    }

    /**
     * 解析摘要场景决策。
     *
     * @param input 摘要输入
     * @return 场景决策
     */
    SemanticSummaryScenarioDecision resolveScenarioDecision(StepSummaryBuildInput input) {
        // 判断场景解析器是否为空，空时返回默认场景决策。
        if (policyResolver == null) {
            // 返回默认场景决策，避免空指针。
            return SemanticSummaryScenarioDecision.defaultDecision();
        }
        // 调用场景解析器解析场景决策。
        return policyResolver.resolveScenarioDecision(input);
    }

    SemanticSummaryScenario resolveScenario(StepSummaryBuildInput input) {
        // 判断场景解析器是否为空，空时返回默认场景。
        if (policyResolver == null) {
            // 返回默认场景，避免空指针。
            return SemanticSummaryScenario.DEFAULT;
        }
        // 调用场景解析器解析场景。
        return policyResolver.resolveScenario(input);
    }

    private SemanticSummaryBudget resolveBudget(StepSummaryBuildInput input, SemanticSummaryScenario scenario) {
        // 判断预算解析器是否为空，空时返回空预算。
        if (budgetResolver == null) {
            // 返回空预算，使用默认配置。
            return null;
        }
        // 调用预算解析器解析预算。
        return budgetResolver.resolveBudget(input, scenario);
    }

    private boolean isAllEmpty(List<String> highlights,
                               List<String> openQuestions,
                               List<String> risks) {
        // 判断高亮列表是否为空。
        boolean highlightsEmpty = highlights == null || highlights.isEmpty();
        // 判断未解决问题列表是否为空。
        boolean openQuestionsEmpty = openQuestions == null || openQuestions.isEmpty();
        // 判断风险列表是否为空。
        boolean risksEmpty = risks == null || risks.isEmpty();
        // 返回全部为空的判断结果。
        return highlightsEmpty && openQuestionsEmpty && risksEmpty;
    }

    private List<String> resolveResultPaths(Map<String, Object> outputMap) {
        // 判断输出映射是否为空，空时返回空列表。
        if (outputMap == null || outputMap.isEmpty()) {
            // 返回空列表，避免空指针。
            return List.of();
        }
        // 读取结果字段对象。
        Object resultObj = outputMap.get(RuntimeOutputKeys.RESULT);
        // 判断结果字段是否为映射，非映射时返回空列表。
        if (!(resultObj instanceof Map<?, ?> resultMap) || resultMap.isEmpty()) {
            // 返回空列表，表示无结果路径。
            return List.of();
        }
        // 初始化路径列表。
        List<String> paths = new ArrayList<>();
        // 读取结果类型字段并写入路径。
        Object kind = resultMap.get("kind");
        if (kind != null) {
            // 写入结果类型路径。
            paths.add("result.kind");
        }
        // 读取数据字段并提取键路径。
        Object data = resultMap.get("data");
        if (data instanceof Map<?, ?> dataMap && !dataMap.isEmpty()) {
            // 循环遍历数据键集合，逐项写入路径。
            for (Object key : dataMap.keySet()) {
                // 判断键是否为空，空时跳过。
                if (key == null) {
                    // 跳过空键，继续遍历。
                    continue;
                }
                // 写入数据键路径。
                paths.add("result.data." + key);
            }
        }
        // 返回路径列表。
        return paths;
    }

    private void appendSourceRef(List<SummarySourceRef> refs, SummarySourceRef ref) {
        // 判断列表或引用是否为空，空时直接返回。
        if (refs == null || ref == null) {
            // 返回空值，避免空指针。
            return;
        }
        // 判断引用类型是否为空，空时直接返回。
        if (ref.getType() == null) {
            // 返回空值，避免无类型引用写入。
            return;
        }
        // 判断引用值与路径是否均为空，空时直接返回。
        if (!StringUtils.hasText(ref.getValue()) && !StringUtils.hasText(ref.getPath())) {
            // 返回空值，避免无效引用写入。
            return;
        }
        // 判断是否已存在相同引用，存在时跳过。
        if (containsSourceRef(refs, ref)) {
            // 跳过重复引用，避免冗余。
            return;
        }
        // 写入引用到列表。
        refs.add(ref);
    }

    private boolean containsSourceRef(List<SummarySourceRef> refs, SummarySourceRef ref) {
        // 判断列表是否为空，空时直接返回否。
        if (refs == null || refs.isEmpty()) {
            // 返回 false，表示未命中。
            return false;
        }
        // 循环遍历引用列表，判断是否重复。
        for (SummarySourceRef existing : refs) {
            // 判断类型是否一致。
            if (existing != null && existing.getType() == ref.getType()) {
                // 判断值与路径是否一致。
                if (equalsText(existing.getValue(), ref.getValue())
                        && equalsText(existing.getPath(), ref.getPath())) {
                    // 返回 true，表示命中重复引用。
                    return true;
                }
            }
        }
        // 返回 false，表示未找到重复引用。
        return false;
    }

    private boolean equalsText(String left, String right) {
        // 将空白文本统一为空串。
        String leftText = left == null ? "" : left.trim();
        String rightText = right == null ? "" : right.trim();
        // 返回文本一致性判断结果。
        return leftText.equals(rightText);
    }

    private List<String> limitList(List<String> items, int maxItems) {
        // 判断列表是否为空，空时返回空列表。
        if (items == null || items.isEmpty()) {
            // 返回空列表，避免空指针。
            return List.of();
        }
        // 判断是否需要限制数量，未限制时直接返回原列表副本。
        if (maxItems <= 0 || items.size() <= maxItems) {
            // 返回列表副本，避免外部修改。
            return List.copyOf(items);
        }
        // 返回截取后的列表副本。
        return List.copyOf(items.subList(0, maxItems));
    }

    private List<SummarySourceRef> limitSourceRefs(List<SummarySourceRef> refs, int maxItems) {
        // 判断列表是否为空，空时返回空列表。
        if (refs == null || refs.isEmpty()) {
            // 返回空列表，避免空指针。
            return List.of();
        }
        // 判断是否需要限制数量，未限制时直接返回原列表副本。
        if (maxItems <= 0 || refs.size() <= maxItems) {
            // 返回列表副本，避免外部修改。
            return List.copyOf(refs);
        }
        // 返回截取后的列表副本。
        return List.copyOf(refs.subList(0, maxItems));
    }

    private String readString(Map<String, Object> map, String key) {
        // 校验输入映射与键名，避免空指针与空键。
        if (map == null || !StringUtils.hasText(key)) {
            return null;
        }
        // 读取目标字段值并转换为字符串。
        Object value = map.get(key);
        return value == null ? null : String.valueOf(value);
    }
}
