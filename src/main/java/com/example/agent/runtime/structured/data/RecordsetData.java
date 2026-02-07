package com.example.agent.runtime.structured.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 记录集语义数据对象。
 *
 * <p>用途：承载表格型查询结果，包含列信息与行数据。
 */
public class RecordsetData implements StructuredData {

    /**
     * 列定义列表。
     */
    private List<String> columns;

    /**
     * 行数据列表。
     */
    private List<Map<String, Object>> rows;

    public List<String> getColumns() {
        return columns;
    }

    public void setColumns(List<String> columns) {
        this.columns = columns;
    }

    public List<Map<String, Object>> getRows() {
        return rows;
    }

    public void setRows(List<Map<String, Object>> rows) {
        this.rows = rows;
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("columns", columns == null ? List.of() : List.copyOf(columns));
        values.put("rows", copyRows(rows));
        return values;
    }

    private List<Map<String, Object>> copyRows(List<Map<String, Object>> sourceRows) {
        if (sourceRows == null || sourceRows.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> copied = new ArrayList<>();
        for (Map<String, Object> row : sourceRows) {
            if (row == null || row.isEmpty()) {
                copied.add(Map.of());
                continue;
            }
            copied.add(new LinkedHashMap<>(row));
        }
        return copied;
    }
}

