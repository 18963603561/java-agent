package com.example.agent.runtime.structured;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 结构化提取器注册表，统一负责提取器选择与兜底。
 */
@Component
public class StructuredExtractorRegistry {

    private final List<StructuredExtractor> extractors;

    public StructuredExtractorRegistry(List<StructuredExtractor> extractors) {
        this.extractors = extractors == null ? List.of() : extractors;
    }

    /**
     * 提取结构化结果。
     */
    public StructuredResult extract(String stepType,
                                    String toolName,
                                    Map<String, Object> rawResult,
                                    String rawRef) {
        for (StructuredExtractor extractor : extractors) {
            if (extractor == null) {
                continue;
            }
            if (extractor.supports(stepType, toolName, rawResult)) {
                StructuredResult result = extractor.extract(stepType, toolName, rawResult, rawRef);
                if (result != null) {
                    return result;
                }
            }
        }
        return fallback(rawRef, rawResult);
    }

    private StructuredResult fallback(String rawRef, Map<String, Object> rawResult) {
        StructuredResult result = new StructuredResult();
        result.setKind(ResultKind.ERROR);
        result.setSchemaVersion(1);
        result.setData(Map.of(
                "category", "PARSE_ERROR",
                "message", "未匹配到可用的结构化提取器",
                "actionableNext", List.of("补充对应 ResultKind 的提取器")
        ));
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
