package com.example.agent.runtime.structured;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.agent.runtime.structured.result.StructuredQuality;
import com.example.agent.runtime.structured.result.StructuredRefs;
import com.example.agent.runtime.structured.result.StructuredResult;
import com.example.agent.runtime.structured.structured.DefaultStructuredData;
import com.example.agent.runtime.structured.structured.StructuredData;
import com.example.agent.runtime.structured.extractor.StructuredExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 结构化提取器注册表，统一负责提取器选择与兜底。
 */
@Component
public class StructuredExtractorRegistry {

    private static final Logger log = LoggerFactory.getLogger(StructuredExtractorRegistry.class);

    private final List<StructuredExtractor> extractors;

    public StructuredExtractorRegistry(List<StructuredExtractor> extractors) {
        this.extractors = extractors == null ? List.of() : extractors;
    }

    /**
     * 提取结构化结果。
     *
     * @param stepType 步骤类型
     * @param toolName 工具名称
     * @param rawResult 原始结果
     * @param rawRef 原始引用
     * @return 结构化结果
     */
    public StructuredResult<? extends StructuredData> extract(String stepType,
                                                              String toolName,
                                                              Map<String, Object> rawResult,
                                                              String rawRef) {
        for (StructuredExtractor extractor : extractors) {
            if (extractor == null) {
                continue;
            }
            if (extractor.supports(stepType, toolName, rawResult)) {
                StructuredResult<? extends StructuredData> result = extractor.extract(stepType, toolName, rawResult, rawRef);
                if (result != null) {
                    log.debug("匹配结构化提取器成功, extractor={}, kind={}",
                            extractor.getClass().getSimpleName(),
                            result.getKind());
                    return result;
                }
            }
        }
        log.warn("未匹配到结构化提取器，使用兜底结果, stepType={}, toolName={}", stepType, toolName);
        return fallback(rawRef, rawResult);
    }

    private StructuredResult<DefaultStructuredData> fallback(String rawRef, Map<String, Object> rawResult) {
        Map<String, Object> fallbackData = new LinkedHashMap<>();
        fallbackData.put("category", "PARSE_ERROR");
        fallbackData.put("message", "未匹配到可用的结构化提取器");
        fallbackData.put("actionableNext", List.of("补充对应 ResultKind 的提取器"));
        fallbackData.put("rawSize", rawResult == null ? 0 : rawResult.size());
        StructuredResult<DefaultStructuredData> result = StructuredResult.defaultResult(ResultKind.ERROR, 1, fallbackData);
        StructuredRefs refs = new StructuredRefs();
        refs.setRawRef(rawRef);
        result.setRefs(refs);
        StructuredQuality quality = new StructuredQuality();
        quality.setConfidence(0.0D);
        quality.setWarnings(List.of("fallback_extractor_used"));
        quality.setTruncated(false);
        result.setQuality(quality);
        return result;
    }
}

