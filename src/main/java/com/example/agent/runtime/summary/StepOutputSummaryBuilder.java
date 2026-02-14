package com.example.agent.runtime.summary;

import com.example.agent.runtime.contract.RuntimeOutputFieldExtractor;
import com.example.agent.runtime.contract.RuntimeOutputKeys;
import com.example.agent.runtime.model.SemanticSummary;
import com.example.agent.runtime.model.SummarySourceRef;
import java.util.ArrayList;
import com.example.agent.runtime.summary.audit.SemanticSummarySampler;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 步骤输出摘要构建器。
 *
 * <p>用途：按策略生成步骤摘要结构，向下游提供稳定的 summary 视图。</p>
 * <p>输入：步骤摘要构建输入契约。</p>
 * <p>输出：摘要映射（text/highlights/openQuestions/risks/sourceRefs）。</p>
 * <p>边界：摘要开关关闭时返回空映射。</p>
 */
@Component
public class StepOutputSummaryBuilder {

    private static final Logger log = LoggerFactory.getLogger(StepOutputSummaryBuilder.class);

    /**
     * 摘要配置。
     */
    private final StepSummaryProperties properties;

    /**
     * 语义摘要服务。
     */
    private final SemanticSummaryService semanticSummaryService;

    /**
     * 语义摘要质量评分器。
     */
    private final SemanticSummaryQualityScorer qualityScorer;

    /**
     * 语义摘要抽检器。
     */
    private final SemanticSummarySampler summarySampler;

    /**
     * 语义摘要观测记录器。
     */
    private final SemanticSummaryMetricsRecorder metricsRecorder;

    /**
     * 摘要策略解析器。
     */
    private final SummaryStrategyResolver strategyResolver;

    public StepOutputSummaryBuilder(StepSummaryProperties properties,
                                    SemanticSummaryService semanticSummaryService,
                                    SemanticSummaryQualityScorer qualityScorer,
                                    SemanticSummarySampler summarySampler,
                                    SemanticSummaryMetricsRecorder metricsRecorder,
                                    SummaryStrategyResolver strategyResolver) {
        this.properties = properties;
        this.semanticSummaryService = semanticSummaryService;
        this.qualityScorer = qualityScorer;
        this.summarySampler = summarySampler;
        this.metricsRecorder = metricsRecorder;
        this.strategyResolver = strategyResolver;
    }

    /**
     * 判断摘要是否启用。
     *
     * @return 是否启用
     */
    public boolean isEnabled() {
        return properties != null && properties.isEnable();
    }

    /**
     * 构建摘要。
     *
     * @param input 摘要输入契约
     * @return 摘要结果
     */
    public Map<String, Object> build(StepSummaryBuildInput input) {
        StepSummaryBuildInput safeInput = input;
        // 输入为空时构造安全默认对象，避免空指针。
        if (safeInput == null) {
            // 构建默认输入，确保后续字段读取有默认值。
            safeInput = StepSummaryBuildInput.builder().build();
        }

        // 解析请求级覆盖配置。
        SummaryRequestOverrides overrides = resolveOverrides(safeInput);
        // 判断摘要是否启用，未启用直接返回空映射。
        boolean summaryEnabled = resolveSummaryEnabled(overrides);
        if (!summaryEnabled) {
            // 返回空映射，保持摘要链路幂等。
            return Collections.emptyMap();
        }

        // 解析摘要场景决策，供观测与日志使用。
        SemanticSummaryScenarioDecision scenarioDecision = resolveScenarioDecisionSafe(safeInput);
        // 读取摘要场景对象。
        SemanticSummaryScenario scenario = scenarioDecision.getScenario();
        // 读取场景命中来源。
        SemanticSummaryScenarioSource scenarioSource = scenarioDecision.getSource();
        // 解析摘要策略决策，供策略路由与日志使用。
        SummaryStrategyDecision strategyDecision = resolveStrategyDecisionSafe(safeInput, scenario);
        // 读取摘要策略。
        SummaryBuildStrategy strategy = strategyDecision.getStrategy();
        // 读取策略命中来源。
        SummaryStrategySource strategySource = strategyDecision.getSource();
        // 判断是否命中关闭策略，命中时直接返回空摘要。
        if (strategy == SummaryBuildStrategy.OFF) {
            // 返回空映射，保持与摘要开关关闭一致的语义。
            return Collections.emptyMap();
        }
        // 解析原始引用，便于日志追踪。
        String rawRef = resolveRawRef(safeInput);
        // 判断是否需要记录摘要日志。
        boolean logEnabled = metricsRecorder != null && metricsRecorder.shouldLog();
        // 判断是否需要记录开始日志，命中时输出开始日志。
        if (logEnabled) {
            // 记录摘要生成开始日志，包含关键定位字段。
            log.info("语义摘要生成开始, workflowId={}, stepId={}, stepType={}, rawRef={}, scenario={}, scenarioSource={}, strategy={}, strategySource={}",
                    null,
                    safeInput.getStepId(),
                    safeInput.getStepType(),
                    rawRef,
                    scenario.getCode(),
                    scenarioSource.getCode(),
                    strategy.getCode(),
                    strategySource.getCode());
        }
        // 记录摘要生成起始时间。
        long summaryStart = System.nanoTime();
        // 按策略路由生成摘要对象。
        SemanticSummary semanticSummary = buildByStrategy(strategy, safeInput);
        // 计算摘要生成耗时（毫秒）。
        long durationMs = (System.nanoTime() - summaryStart) / 1_000_000;
        // 判断观测记录器是否存在，存在时记录生成指标。
        if (metricsRecorder != null) {
            // 记录摘要生成观测指标（耗时/截断/空摘要）。
            metricsRecorder.recordGeneration(safeInput, scenario, semanticSummary, durationMs);
        }
        // 判断是否需要记录完成日志，命中时输出完成日志。
        if (logEnabled) {
            // 记录摘要生成完成日志，包含关键定位字段。
            log.info("语义摘要生成完成, workflowId={}, stepId={}, stepType={}, rawRef={}, scenario={}, scenarioSource={}, strategy={}, strategySource={}, truncated={}, durationMs={}",
                    null,
                    safeInput.getStepId(),
                    safeInput.getStepType(),
                    rawRef,
                    scenario.getCode(),
                    scenarioSource.getCode(),
                    strategy.getCode(),
                    strategySource.getCode(),
                    semanticSummary != null && semanticSummary.isTruncated(),
                    durationMs);
        }
        // 构建语义摘要映射，作为统一 summary 输出。
        Map<String, Object> summaryMap = buildSemanticSummary(semanticSummary);
        // 调用质量评分器计算摘要质量。
        SemanticSummaryQuality quality = qualityScorer != null ? qualityScorer.score(safeInput, semanticSummary) : null;
        // 将质量评分写入摘要映射。
        applyQuality(summaryMap, quality);
        // 判断观测记录器是否存在，存在时记录质量指标。
        if (metricsRecorder != null) {
            // 记录摘要质量观测指标。
            metricsRecorder.recordQuality(scenario, quality);
        }
        // 调用摘要抽检器进行抽样审计。
        if (summarySampler != null) {
            // 触发抽检逻辑并获取抽检原因。
            String sampleReason = summarySampler.sample(safeInput, summaryMap, quality);
            // 判断抽检原因是否有效，存在时记录抽检指标。
            if (StringUtils.hasText(sampleReason) && metricsRecorder != null) {
                // 记录摘要抽检命中指标。
                metricsRecorder.recordAuditSample(scenario, sampleReason);
            }
        }
        // 返回构建完成的摘要映射。
        return summaryMap;
    }

    private SummaryRequestOverrides resolveOverrides(StepSummaryBuildInput input) {
        // 调用覆盖配置解析器解析请求级覆盖。
        return SummaryRequestOverrides.fromInput(input);
    }

    private boolean resolveSummaryEnabled(SummaryRequestOverrides overrides) {
        // 判断覆盖配置是否存在且包含启用开关，存在时优先使用覆盖值。
        if (overrides != null && overrides.getEnabled() != null) {
            // 返回覆盖配置中的启用开关。
            return overrides.getEnabled();
        }
        // 返回全局配置的启用开关。
        return isEnabled();
    }

    private SemanticSummaryScenarioDecision resolveScenarioDecisionSafe(StepSummaryBuildInput input) {
        // 判断摘要服务是否可用，不可用时返回默认场景决策。
        if (semanticSummaryService == null) {
            // 返回默认场景决策，避免空指针。
            return SemanticSummaryScenarioDecision.defaultDecision();
        }
        // 调用摘要服务解析场景决策。
        return semanticSummaryService.resolveScenarioDecision(input);
    }

    private SummaryStrategyDecision resolveStrategyDecisionSafe(StepSummaryBuildInput input,
                                                                SemanticSummaryScenario scenario) {
        // 判断策略解析器是否可用，不可用时返回默认策略。
        if (strategyResolver == null) {
            // 返回默认策略决策，避免空指针。
            return SummaryStrategyDecision.defaultDecision();
        }
        // 调用策略解析器解析策略决策。
        return strategyResolver.resolve(input, scenario);
    }

    private SemanticSummary buildByStrategy(SummaryBuildStrategy strategy, StepSummaryBuildInput input) {
        // 判断语义摘要服务是否可用，不可用时返回空摘要。
        if (semanticSummaryService == null) {
            // 返回空摘要，避免空指针。
            return null;
        }
        // 判断策略是否为空，空时回退语义策略。
        if (strategy == null || strategy == SummaryBuildStrategy.SEMANTIC) {
            // 调用语义摘要服务生成语义摘要。
            return semanticSummaryService.buildSummary(input);
        }
        // 判断是否命中模板策略，命中时构建模板摘要。
        if (strategy == SummaryBuildStrategy.TEMPLATE) {
            // 调用模板摘要构建流程。
            return buildTemplateSummary(input);
        }
        // 判断是否命中模型策略，命中时回退语义摘要。
        if (strategy == SummaryBuildStrategy.MODEL) {
            // 调用语义摘要服务作为稳态回退输出。
            return semanticSummaryService.buildSummary(input);
        }
        // 返回语义摘要作为兜底，避免未知策略导致异常。
        return semanticSummaryService.buildSummary(input);
    }

    private SemanticSummary buildTemplateSummary(StepSummaryBuildInput input) {
        // 调用语义摘要服务构建基础摘要。
        SemanticSummary baseSummary = semanticSummaryService.buildSummary(input);
        // 判断基础摘要是否为空，空时直接返回。
        if (baseSummary == null) {
            // 返回空摘要，避免空指针。
            return null;
        }
        // 解析模板文本。
        String templateText = resolveTemplateText(baseSummary, input);
        // 解析模板高亮列表。
        List<String> templateHighlights = resolveTemplateHighlights(baseSummary, input);
        // 返回模板化后的摘要对象，保留来源与风险信息。
        return new SemanticSummary(templateText,
                templateHighlights,
                baseSummary.getOpenQuestions(),
                baseSummary.getRisks(),
                baseSummary.getSourceRefs(),
                baseSummary.isTruncated());
    }

    private String resolveTemplateText(SemanticSummary baseSummary, StepSummaryBuildInput input) {
        // 读取基础摘要文本。
        String baseText = baseSummary.getText();
        // 构建模板前缀。
        String templatePrefix = resolveTemplatePrefix(input);
        // 判断前缀与基础文本是否同时存在，命中时拼接输出。
        if (StringUtils.hasText(templatePrefix) && StringUtils.hasText(baseText)) {
            // 返回模板拼接文本。
            return templatePrefix + "：" + baseText;
        }
        // 判断仅前缀存在，存在时返回前缀文本。
        if (StringUtils.hasText(templatePrefix)) {
            // 返回模板前缀文本。
            return templatePrefix;
        }
        // 返回基础摘要文本。
        return baseText;
    }

    private String resolveTemplatePrefix(StepSummaryBuildInput input) {
        // 判断输入是否为空，空时返回默认模板前缀。
        if (input == null) {
            // 返回默认模板前缀。
            return "步骤摘要";
        }
        // 读取步骤类型。
        String stepType = input.getStepType();
        // 读取步骤状态。
        String status = input.getStatus();
        // 读取工具名称。
        String toolName = input.getToolName();
        // 判断工具名称是否存在，存在时输出工具模板前缀。
        if (StringUtils.hasText(toolName)) {
            // 返回工具模板前缀文本。
            return "步骤类型=" + defaultText(stepType, "unknown") + ", 状态="
                    + defaultText(status, "unknown") + ", 工具=" + toolName;
        }
        // 返回通用模板前缀文本。
        return "步骤类型=" + defaultText(stepType, "unknown") + ", 状态="
                + defaultText(status, "unknown");
    }

    private List<String> resolveTemplateHighlights(SemanticSummary baseSummary, StepSummaryBuildInput input) {
        // 判断基础摘要高亮是否存在，存在时复制后返回。
        if (baseSummary.getHighlights() != null && !baseSummary.getHighlights().isEmpty()) {
            // 返回高亮副本，避免污染原始数据。
            return List.copyOf(baseSummary.getHighlights());
        }
        // 初始化模板高亮列表。
        List<String> highlights = new ArrayList<>();
        // 读取步骤状态。
        String status = input != null ? input.getStatus() : null;
        // 读取工具名称。
        String toolName = input != null ? input.getToolName() : null;
        // 判断状态是否存在，存在时写入状态高亮。
        if (StringUtils.hasText(status)) {
            // 写入状态高亮文本。
            highlights.add("status=" + status);
        }
        // 判断工具名称是否存在，存在时写入工具高亮。
        if (StringUtils.hasText(toolName)) {
            // 写入工具高亮文本。
            highlights.add("tool=" + toolName);
        }
        // 判断高亮是否为空，空时写入默认高亮。
        if (highlights.isEmpty()) {
            // 写入默认高亮文本。
            highlights.add("strategy=template");
        }
        // 返回模板高亮列表。
        return List.copyOf(highlights);
    }

    private String defaultText(String text, String fallback) {
        // 判断文本是否有效，有效时返回原文本。
        if (StringUtils.hasText(text)) {
            // 返回原文本。
            return text;
        }
        // 返回兜底文本。
        return fallback;
    }

    private String resolveRawRef(StepSummaryBuildInput input) {
        // 判断输入或输出是否为空，空时直接返回空引用。
        if (input == null || input.getOutput() == null) {
            return null;
        }
        // 判断输出是否为映射且非空，非映射时直接返回空引用。
        if (!(input.getOutput() instanceof Map<?, ?> map) || map.isEmpty()) {
            return null;
        }
        // 初始化可读输出映射容器。
        Map<String, Object> outputMap = new LinkedHashMap<>();
        // 循环遍历输出映射条目，逐项写入容器。
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            // 写入条目键值，统一键为字符串。
            outputMap.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        // 调用字段提取器解析原始引用。
        return RuntimeOutputFieldExtractor.resolveRawRef(outputMap);
    }

    private Map<String, Object> buildSemanticSummary(SemanticSummary summary) {
        Map<String, Object> summaryMap = new LinkedHashMap<>();
        // 摘要对象为空时直接返回空映射，避免空指针。
        if (summary == null) {
            // 返回空映射，保持摘要结构可控。
            return summaryMap;
        }
        // 写入摘要文本字段，作为语义摘要核心内容。
        putIfHasText(summaryMap, RuntimeOutputKeys.SUMMARY_TEXT, summary.getText());
        // 写入摘要高亮列表，便于快速回顾关键点。
        if (summary.getHighlights() != null && !summary.getHighlights().isEmpty()) {
            // 写入高亮列表，增强摘要信息量。
            summaryMap.put(RuntimeOutputKeys.SUMMARY_HIGHLIGHTS, summary.getHighlights());
        }
        // 写入未解决问题列表，提示待补充信息。
        if (summary.getOpenQuestions() != null && !summary.getOpenQuestions().isEmpty()) {
            // 写入未解决问题列表，便于后续回归。
            summaryMap.put(RuntimeOutputKeys.SUMMARY_OPEN_QUESTIONS, summary.getOpenQuestions());
        }
        // 写入风险提示列表，标识潜在风险点。
        if (summary.getRisks() != null && !summary.getRisks().isEmpty()) {
            // 写入风险列表，提示注意事项。
            summaryMap.put(RuntimeOutputKeys.SUMMARY_RISKS, summary.getRisks());
        }
        // 写入来源引用列表，保证可追踪性。
        if (summary.getSourceRefs() != null && !summary.getSourceRefs().isEmpty()) {
            // 写入来源引用列表，方便定位原始数据。
            summaryMap.put(RuntimeOutputKeys.SUMMARY_SOURCE_REFS, toSourceRefMaps(summary.getSourceRefs()));
        }
        // 写入截断标记，提示摘要是否完整。
        summaryMap.put(RuntimeOutputKeys.TRUNCATED, summary.isTruncated());
        // 返回语义摘要映射。
        return summaryMap;
    }

    private void applyQuality(Map<String, Object> summaryMap, SemanticSummaryQuality quality) {
        // 判断摘要映射是否为空，空时直接返回。
        if (summaryMap == null || summaryMap.isEmpty()) {
            // 直接返回，避免对空摘要追加质量信息。
            return;
        }
        // 判断质量对象是否为空，空时直接返回。
        if (quality == null) {
            // 直接返回，避免写入空质量字段。
            return;
        }
        // 将质量对象转换为映射。
        Map<String, Object> qualityMap = quality.toMap();
        // 判断质量映射是否为空，非空时写入质量字段。
        if (qualityMap != null && !qualityMap.isEmpty()) {
            // 写入摘要质量映射，供下游消费。
            summaryMap.put(RuntimeOutputKeys.SUMMARY_QUALITY, qualityMap);
        }
        // 判断质量告警是否存在，存在时写入摘要告警字段。
        if (quality.getWarnings() != null && !quality.getWarnings().isEmpty()) {
            // 写入摘要告警列表，提示低质量原因。
            summaryMap.put(RuntimeOutputKeys.SUMMARY_WARNINGS, quality.getWarnings());
        }
    }

    private List<Map<String, Object>> toSourceRefMaps(List<SummarySourceRef> refs) {
        // 判断来源引用列表是否为空，空时返回空列表。
        if (refs == null || refs.isEmpty()) {
            // 返回空列表，避免空指针。
            return List.of();
        }
        // 初始化来源引用映射列表。
        List<Map<String, Object>> items = new java.util.ArrayList<>();
        // 循环遍历来源引用列表，逐条转换为映射。
        for (SummarySourceRef ref : refs) {
            // 判断引用是否为空，空时跳过。
            if (ref == null) {
                // 跳过空引用，继续处理下一条。
                continue;
            }
            // 初始化引用映射容器。
            Map<String, Object> item = new LinkedHashMap<>();
            // 判断引用类型是否为空，非空时写入类型字段。
            if (ref.getType() != null) {
                // 写入引用类型编码。
                item.put(RuntimeOutputKeys.SUMMARY_SOURCE_REF_TYPE, ref.getType().getCode());
            }
            // 判断引用值是否为空，非空时写入值字段。
            if (StringUtils.hasText(ref.getValue())) {
                // 写入引用值字段。
                item.put(RuntimeOutputKeys.SUMMARY_SOURCE_REF_VALUE, ref.getValue());
            }
            // 判断引用路径是否为空，非空时写入路径字段。
            if (StringUtils.hasText(ref.getPath())) {
                // 写入引用路径字段。
                item.put(RuntimeOutputKeys.SUMMARY_SOURCE_REF_PATH, ref.getPath());
            }
            // 判断映射是否为空，非空时写入列表。
            if (!item.isEmpty()) {
                // 写入引用映射到列表。
                items.add(item);
            }
        }
        // 返回转换后的来源引用映射列表。
        return items;
    }

    private void putIfHasText(Map<String, Object> target, String key, String value) {
        // 校验目标映射与键名有效性，避免写入空键。
        if (target == null || !StringUtils.hasText(key)) {
            return;
        }
        // 仅在值非空时写入，避免污染摘要结构。
        if (StringUtils.hasText(value)) {
            // 写入非空字段，保证摘要结构稳定。
            target.put(key, value);
        }
    }
}
