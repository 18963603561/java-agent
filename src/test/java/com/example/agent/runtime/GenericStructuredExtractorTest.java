package com.example.agent.runtime;

import com.example.agent.runtime.structured.extractor.GenericStructuredExtractor;
import com.example.agent.runtime.structured.structured.RecordsetData;
import com.example.agent.runtime.structured.ResultKind;
import com.example.agent.runtime.structured.structured.SqlData;
import com.example.agent.runtime.structured.result.StructuredResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * GenericStructuredExtractor 强类型映射测试。
 */
class GenericStructuredExtractorTest {

    /**
     * SQL 场景应输出 SqlData。
     */
    @Test
    void extractReturnsSqlDataWhenResultContainsSql() {
        GenericStructuredExtractor extractor = new GenericStructuredExtractor();

        StructuredResult<?> result = extractor.extract("TOOL",
                "sql_tool",
                Map.of("sql", "select 1", "dialect", "postgres"),
                "raw-1");

        assertEquals(ResultKind.SQL, result.getKind());
        assertInstanceOf(SqlData.class, result.getData());
        SqlData data = (SqlData) result.getData();
        assertEquals("select 1", data.getSql());
        assertEquals("postgres", data.getDialect());
    }

    /**
     * RECORDSET 场景应输出 RecordsetData。
     */
    @Test
    void extractReturnsRecordsetDataWhenResultContainsRows() {
        GenericStructuredExtractor extractor = new GenericStructuredExtractor();

        StructuredResult<?> result = extractor.extract("TOOL",
                "query_tool",
                Map.of(
                        "rows", List.of(Map.of("id", 1, "name", "demo")),
                        "columns", List.of("id", "name")
                ),
                "raw-2");

        assertEquals(ResultKind.RECORDSET, result.getKind());
        assertInstanceOf(RecordsetData.class, result.getData());
        RecordsetData data = (RecordsetData) result.getData();
        assertEquals(List.of("id", "name"), data.getColumns());
        assertEquals(1, data.getRows().size());
        assertEquals("demo", data.getRows().get(0).get("name"));
    }
}

