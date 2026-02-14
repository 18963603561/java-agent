package com.example.agent.runtime.structured.extractor;

import com.example.agent.runtime.output.OutputKeys;
import com.example.agent.runtime.structured.ResultKind;
import com.example.agent.runtime.structured.result.StructuredRefs;
import com.example.agent.runtime.structured.result.StructuredResult;
import com.example.agent.runtime.structured.structured.RecordsetData;
import com.example.agent.runtime.structured.structured.StructuredData;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * MCP 文本内容结构化提取器。
 *
 * <p>用途：解析 MCP content 中的 JSON 文本，抽取为记录集结构。</p>
 * <p>边界：仅处理 content[text] 为 JSON 的场景，失败时交由后续提取器兜底。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class McpContentStructuredExtractor implements StructuredExtractor {

    /**
     * 日志记录器。
     */
    private static final Logger log = LoggerFactory.getLogger(McpContentStructuredExtractor.class);

    /**
     * JSON 解析器。
     */
    private final ObjectMapper objectMapper;

    /**
     * 构造函数。
     *
     * @param objectMapper JSON 解析器
     */
    public McpContentStructuredExtractor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String stepType, String toolName, Map<String, Object> rawResult) {
        // 判断原始结果是否为空，空时不支持解析。
        if (rawResult == null || rawResult.isEmpty()) {
            // 返回不支持解析结果。
            return false;
        }
        // 解析候选结果来源，优先使用 rawResult/result 字段。
        Map<String, Object> candidate = resolveCandidate(rawResult);
        // 判断候选结果是否为空，空时不支持解析。
        if (candidate == null || candidate.isEmpty()) {
            // 返回不支持解析结果。
            return false;
        }
        // 读取 MCP content 字段。
        Object content = candidate.get("content");
        // 判断 content 是否为非空列表，非列表或为空直接返回。
        if (!(content instanceof List<?> list) || list.isEmpty()) {
            // 返回不支持解析结果。
            return false;
        }
        // 循环查找包含 JSON 文本的 content 项。
        for (Object item : list) {
            // 判断元素是否为 Map，非 Map 直接跳过。
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            // 读取文本字段。
            Object text = map.get("text");
            // 判断文本是否为 JSON 字符串，符合则支持解析。
            if (text instanceof String value && looksLikeJson(value)) {
                // 命中 JSON 文本时返回支持解析。
                return true;
            }
        }
        // 未命中可解析内容时返回 false。
        // 返回不支持解析结果。
        return false;
    }

    @Override
    public StructuredResult<? extends StructuredData> extract(String stepType,
                                                              String toolName,
                                                              Map<String, Object> rawResult,
                                                              String rawRef) {
        // 解析候选结果来源，优先使用 rawResult/result 字段。
        Map<String, Object> candidate = resolveCandidate(rawResult);
        // 解析 MCP content 中的 JSON 文本。
        Map<String, Object> parsed = parseContentJson(candidate);
        // 解析记录集载荷，优先使用 result 内的 rows/columns。
        Map<String, Object> payload = resolveRecordsetPayload(parsed);
        // 判断记录集是否为空，空时交由后续提取器处理。
        if (payload == null || payload.isEmpty()) {
            // 返回空结果交由后续提取器兜底。
            return null;
        }
        // 构建记录集结构化数据对象。
        RecordsetData data = buildRecordsetData(payload);
        // 构建结构化结果对象。
        StructuredResult<RecordsetData> result = StructuredResult.of(ResultKind.RECORDSET, 1, data);
        // 绑定结构化引用信息。
        StructuredRefs refs = new StructuredRefs();
        // 绑定原始引用标识。
        refs.setRawRef(rawRef);
        // 写入结构化引用集合。
        result.setRefs(refs);
        // 记录结构化提取日志。
        log.debug("MCP 内容结构化提取完成, toolName={}, columns={}, rows={}",
                toolName,
                data.getColumns() == null ? 0 : data.getColumns().size(),
                data.getRows() == null ? 0 : data.getRows().size());
        // 返回结构化结果。
        return result;
    }

    /**
     * 解析候选结果来源。
     *
     * @param rawResult 原始结果
     * @return 候选结果
     */
    private Map<String, Object> resolveCandidate(Map<String, Object> rawResult) {
        // 判断原始结果是否为空，空时返回空映射。
        if (rawResult == null || rawResult.isEmpty()) {
            // 返回空映射作为候选结果。
            return Map.of();
        }
        // 读取 rawResult 字段。
        Object rawResultObj = rawResult.get(OutputKeys.RAW_RESULT);
        // 判断 rawResult 是否为 Map，命中时返回副本。
        if (rawResultObj instanceof Map<?, ?> rawMap) {
            // 返回 rawResult 的归一化副本。
            return toStringObjectMap(rawMap);
        }
        // 读取 result 字段。
        Object resultObj = rawResult.get(OutputKeys.RESULT);
        // 判断 result 是否为 Map，命中时返回副本。
        if (resultObj instanceof Map<?, ?> resultMap) {
            // 返回 result 的归一化副本。
            return toStringObjectMap(resultMap);
        }
        // 返回原始结果映射。
        return rawResult;
    }

    /**
     * 解析 MCP content 中的 JSON 文本。
     *
     * @param candidate 候选结果
     * @return JSON 映射
     */
    private Map<String, Object> parseContentJson(Map<String, Object> candidate) {
        // 判断候选结果是否为空，空时返回空映射。
        if (candidate == null || candidate.isEmpty()) {
            // 返回空映射作为解析结果。
            return Map.of();
        }
        // 读取 content 字段。
        Object content = candidate.get("content");
        // 判断 content 是否为非空列表，非列表或为空直接返回空映射。
        if (!(content instanceof List<?> list) || list.isEmpty()) {
            // 返回空映射作为解析结果。
            return Map.of();
        }
        // 设计意图：只解析首个可用 JSON 文本，避免多段内容带来的歧义。
        // 循环遍历 content 项，寻找 JSON 文本。
        for (Object item : list) {
            // 判断元素是否为 Map，非 Map 直接跳过。
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            // 读取文本字段。
            Object text = map.get("text");
            // 判断文本是否为 JSON 字符串，非 JSON 直接跳过。
            if (!(text instanceof String value) || !looksLikeJson(value)) {
                continue;
            }
            // 异常处理：JSON 解析失败时记录日志并继续尝试其他片段。
            try {
                // 调用 JSON 解析器读取 Map。
                return objectMapper.readValue(value, new TypeReference<>() {
                });
            } catch (Exception ex) {
                // 记录 JSON 解析失败日志，便于排查。
                log.debug("MCP 内容 JSON 解析失败, message={}", ex.getMessage());
            }
        }
        // 未命中可解析 JSON 时返回空映射。
        return Map.of();
    }

    /**
     * 解析记录集载荷。
     *
     * @param parsed JSON 映射
     * @return 记录集映射
     */
    private Map<String, Object> resolveRecordsetPayload(Map<String, Object> parsed) {
        // 判断解析结果是否为空，空时返回空映射。
        if (parsed == null || parsed.isEmpty()) {
            // 返回空映射作为解析结果。
            return Map.of();
        }
        // 读取 result 字段作为候选载荷。
        Object inner = parsed.get("result");
        // 判断 result 是否为 Map，命中时尝试返回。
        if (inner instanceof Map<?, ?> innerMap) {
            Map<String, Object> payload = toStringObjectMap(innerMap);
            // 判断 payload 是否包含 rows/records，命中时返回。
            if (hasRecordsetShape(payload)) {
                // 返回 result 中的记录集载荷。
                return payload;
            }
        }
        // 判断顶层是否包含 rows/records，命中时返回顶层映射。
        if (hasRecordsetShape(parsed)) {
            // 返回顶层记录集载荷。
            return parsed;
        }
        // 未命中记录集形态时返回空映射。
        return Map.of();
    }

    /**
     * 构建记录集数据对象。
     *
     * @param payload 记录集载荷
     * @return 记录集数据
     */
    private RecordsetData buildRecordsetData(Map<String, Object> payload) {
        // 构建记录集数据对象。
        RecordsetData data = new RecordsetData();
        // 解析列字段列表。
        List<String> columns = resolveColumns(payload);
        // 解析行数据列表。
        List<Map<String, Object>> rows = resolveRows(payload);
        // 写入列字段列表。
        data.setColumns(columns);
        // 写入行数据列表。
        data.setRows(rows);
        // 返回记录集数据对象。
        return data;
    }

    /**
     * 判断是否包含记录集形态字段。
     *
     * @param payload 载荷
     * @return 是否为记录集
     */
    private boolean hasRecordsetShape(Map<String, Object> payload) {
        // 判断载荷是否为空，空时直接返回 false。
        if (payload == null || payload.isEmpty()) {
            // 返回非记录集标识。
            return false;
        }
        // 判断是否包含 rows 或 records 字段。
        return payload.containsKey("rows") || payload.containsKey("records");
    }

    /**
     * 解析列字段。
     *
     * @param payload 记录集载荷
     * @return 列字段列表
     */
    private List<String> resolveColumns(Map<String, Object> payload) {
        // 读取 columns 字段。
        Object columns = payload.get("columns");
        // 判断 columns 是否为列表，命中时归一化输出。
        if (columns instanceof List<?> list) {
            // 初始化列名列表。
            List<String> normalized = new ArrayList<>();
            // 循环遍历列字段并转换为字符串。
            for (Object item : list) {
                // 判断列值是否为空，非空时写入列表。
                if (item != null) {
                    normalized.add(String.valueOf(item));
                }
            }
            // 返回归一化列名列表。
            return normalized;
        }
        // 解析行数据作为列名兜底。
        List<Map<String, Object>> rows = resolveRows(payload);
        // 判断行数据是否为空，非空时使用首行字段名。
        if (!rows.isEmpty()) {
            // 返回首行字段名作为列名。
            return new ArrayList<>(rows.get(0).keySet());
        }
        // 返回空列字段列表。
        return List.of();
    }

    /**
     * 解析行数据。
     *
     * @param payload 记录集载荷
     * @return 行数据列表
     */
    private List<Map<String, Object>> resolveRows(Map<String, Object> payload) {
        // 读取 rows 字段。
        Object rows = payload.get("rows");
        List<?> rowsList;
        // 判断 rows 是否为列表，非列表时尝试读取 records 字段。
        if (rows instanceof List<?> list) {
            rowsList = list;
        } else {
            // 读取 records 字段。
            rows = payload.get("records");
            // 判断 records 是否为列表，非列表时返回空列表。
            if (!(rows instanceof List<?> recordsList)) {
                // 返回空行列表作为兜底结果。
                return List.of();
            }
            rowsList = recordsList;
        }
        // 初始化行列表。
        List<Map<String, Object>> normalized = new ArrayList<>();
        // 循环遍历行数据并归一化为 Map。
        for (Object item : rowsList) {
            // 判断行数据是否为 Map，非 Map 时跳过。
            if (item instanceof Map<?, ?> map) {
                // 初始化行映射。
                Map<String, Object> row = new LinkedHashMap<>();
                // 循环写入字段和值。
                map.forEach((key, value) -> row.put(String.valueOf(key), value));
                normalized.add(row);
            }
        }
        // 返回归一化行数据列表。
        return normalized;
    }

    /**
     * 将 Map 归一化为 String/Object 映射。
     *
     * @param rawMap 原始映射
     * @return 归一化映射
     */
    private Map<String, Object> toStringObjectMap(Map<?, ?> rawMap) {
        // 初始化结果映射。
        Map<String, Object> result = new LinkedHashMap<>();
        // 判断原始映射是否为空，空时直接返回空映射。
        if (rawMap == null || rawMap.isEmpty()) {
            // 返回空映射作为归一化结果。
            return result;
        }
        // 循环写入原始映射键值。
        rawMap.forEach((key, value) -> {
            if (key != null) {
                result.put(String.valueOf(key), value);
            }
        });
        // 返回归一化映射。
        return result;
    }

    /**
     * 判断字符串是否为 JSON。
     *
     * @param text 文本
     * @return 是否为 JSON
     */
    private boolean looksLikeJson(String text) {
        // 判断文本是否为空，空时返回 false。
        if (text == null || text.isBlank()) {
            // 返回非 JSON 标识。
            return false;
        }
        // 读取去空白后的首字符。
        String trimmed = text.trim();
        // 判断首字符是否为 JSON 起始符号。
        return trimmed.startsWith("{") || trimmed.startsWith("[");
    }
}
