package com.example.agent.runtime.structured.extractor;

import com.example.agent.capabilities.context.runtime.ContextRuntimeKeys;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.agent.runtime.structured.*;
import com.example.agent.runtime.structured.result.StructuredQuality;
import com.example.agent.runtime.structured.result.StructuredRefs;
import com.example.agent.runtime.structured.result.StructuredResult;
import com.example.agent.runtime.structured.structured.RecordsetData;
import com.example.agent.runtime.structured.structured.SqlData;
import com.example.agent.runtime.structured.structured.StructuredData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 通用结构化提取器，提供基础工具结果映射。
 *
 * <p>边界条件：
 * 1. 当 rawResult 为空时返回默认空对象；
 * 2. SQL 与 RECORDSET 场景输出强类型数据对象；
 * 3. 其余场景使用默认数据实现兜底，保证兼容性。
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class GenericStructuredExtractor implements StructuredExtractor {

    private static final Logger log = LoggerFactory.getLogger(GenericStructuredExtractor.class);

    @Override
    public boolean supports(String stepType, String toolName, Map<String, Object> rawResult) {
        return rawResult != null;
    }

    @Override
    public StructuredResult<? extends StructuredData> extract(String stepType,
                                                              String toolName,
                                                              Map<String, Object> rawResult,
                                                              String rawRef) {
        ResultKind kind = resolveKind(stepType, toolName, rawResult);
        StructuredResult<? extends StructuredData> result = buildTypedResult(kind, toolName, rawResult);
        result.setSchemaVersion(1);
        StructuredQuality quality = new StructuredQuality();
        quality.setConfidence(0.7D);
        quality.setWarnings(List.of());
        quality.setTruncated(false);
        result.setQuality(quality);
        StructuredRefs refs = new StructuredRefs();
        refs.setRawRef(rawRef);
        result.setRefs(refs);
        log.debug("结构化提取完成, kind={}, toolName={}, rawSize={}",
                kind,
                toolName,
                rawResult == null ? 0 : rawResult.size());
        return result;
    }

    private StructuredResult<? extends StructuredData> buildTypedResult(ResultKind kind,
                                                                        String toolName,
                                                                        Map<String, Object> rawResult) {
        if (ResultKind.SQL.equals(kind)) {
            return StructuredResult.of(kind, 1, buildSqlData(rawResult));
        }
        if (ResultKind.RECORDSET.equals(kind)) {
            return StructuredResult.of(kind, 1, buildRecordsetData(rawResult));
        }
        return StructuredResult.defaultResult(kind, 1, buildDefaultData(toolName, rawResult));
    }

    private SqlData buildSqlData(Map<String, Object> rawResult) {
        SqlData data = new SqlData();
        Object sql = rawResult.get("sql");
        if (sql != null) {
            data.setSql(String.valueOf(sql));
        }
        Object dialect = rawResult.get("dialect");
        if (dialect != null) {
            data.setDialect(String.valueOf(dialect));
        }
        return data;
    }

    private RecordsetData buildRecordsetData(Map<String, Object> rawResult) {
        RecordsetData data = new RecordsetData();
        data.setColumns(resolveColumns(rawResult));
        data.setRows(resolveRows(rawResult));
        return data;
    }

    private List<String> resolveColumns(Map<String, Object> rawResult) {
        Object columns = rawResult.get("columns");
        if (columns instanceof List<?> list) {
            List<String> normalized = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    normalized.add(String.valueOf(item));
                }
            }
            return normalized;
        }
        List<Map<String, Object>> rows = resolveRows(rawResult);
        if (!rows.isEmpty()) {
            return new ArrayList<>(rows.get(0).keySet());
        }
        return List.of();
    }

    private List<Map<String, Object>> resolveRows(Map<String, Object> rawResult) {
        Object rows = rawResult.get("rows");
        List<?> rowsList;
        if (rows instanceof List<?> list) {
            rowsList = list;
        } else {
            rows = rawResult.get("records");
            if (!(rows instanceof List<?> recordsList)) {
                return List.of();
            }
            rowsList = recordsList;
        }
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (Object item : rowsList) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> row = new LinkedHashMap<>();
                map.forEach((key, value) -> row.put(String.valueOf(key), value));
                normalized.add(row);
            }
        }
        return normalized;
    }

    private Map<String, Object> buildDefaultData(String toolName, Map<String, Object> rawResult) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("keys", rawResult.keySet().stream().map(String::valueOf).toList());
        data.put("size", rawResult.size());
        if (toolName != null && !toolName.isBlank()) {
            data.put("tool", toolName);
        }
        return data;
    }

    private ResultKind resolveKind(String stepType, String toolName, Map<String, Object> rawResult) {
        if ("RESEARCH".equalsIgnoreCase(stepType)
                || rawResult.containsKey(ContextRuntimeKeys.CITATIONS)) {
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
