package com.example.agent.runtime.structured;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 通用结构化提取器，提供基础工具结果映射。
 */
@Component
public class GenericStructuredExtractor implements StructuredExtractor {

    @Override
    public boolean supports(String stepType, String toolName, Map<String, Object> rawResult) {
        return rawResult != null;
    }

    @Override
    public StructuredResult extract(String stepType,
                                    String toolName,
                                    Map<String, Object> rawResult,
                                    String rawRef) {
        StructuredResult result = new StructuredResult();
        result.setKind(resolveKind(stepType, toolName, rawResult));
        result.setSchemaVersion(1);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("keys", rawResult.keySet().stream().map(String::valueOf).toList());
        data.put("size", rawResult.size());
        if (toolName != null && !toolName.isBlank()) {
            data.put("tool", toolName);
        }
        result.setData(data);
        StructuredQuality quality = new StructuredQuality();
        quality.setConfidence(0.7D);
        quality.setWarnings(List.of());
        quality.setTruncated(false);
        result.setQuality(quality);
        StructuredRefs refs = new StructuredRefs();
        refs.setRawRef(rawRef);
        result.setRefs(refs);
        return result;
    }

    private ResultKind resolveKind(String stepType, String toolName, Map<String, Object> rawResult) {
        if ("RESEARCH".equalsIgnoreCase(stepType)
                || rawResult.containsKey("citations")) {
            return ResultKind.DOCUMENT_CITATIONS;
        }
        if (rawResult.containsKey("sql")) {
            return ResultKind.SQL;
        }
        if (rawResult.containsKey("rows") || rawResult.containsKey("records")) {
            return ResultKind.RECORDSET;
        }
        if (rawResult.containsKey("decision") || rawResult.containsKey("nextAction")) {
            return ResultKind.DECISION;
        }
        return ResultKind.DIAGNOSIS;
    }
}
