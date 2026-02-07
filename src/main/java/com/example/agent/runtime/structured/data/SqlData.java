package com.example.agent.runtime.structured.data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SQL 语义数据对象。
 *
 * <p>用途：承载 SQL 类结构化结果，供执行器、审计链路和展示层消费。
 */
public class SqlData implements StructuredData {

    /**
     * SQL 文本。
     */
    private String sql;

    /**
     * SQL 方言。
     */
    private String dialect;

    public String getSql() {
        return sql;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }

    public String getDialect() {
        return dialect;
    }

    public void setDialect(String dialect) {
        this.dialect = dialect;
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("sql", sql);
        values.put("dialect", dialect);
        return values;
    }
}

