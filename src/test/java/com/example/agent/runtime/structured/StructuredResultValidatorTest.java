package com.example.agent.runtime.structured;

import com.example.agent.runtime.structured.result.StructuredResult;
import com.example.agent.runtime.structured.structured.SqlData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructuredResultValidatorTest {

    @Test
    void validateSqlSchema() {
        // 构建结构化校验定义注册表。
        StructuredResultSchemaRegistry registry = new StructuredResultSchemaRegistry();
        // 构建结构化结果校验器。
        StructuredResultValidator validator = new StructuredResultValidator(registry);

        // 构建缺失 SQL 的数据对象。
        SqlData badData = new SqlData();
        // 构建结构化结果对象。
        StructuredResult<SqlData> badResult = StructuredResult.of(ResultKind.SQL, 1, badData);
        // 调用校验器执行校验。
        StructuredValidationResult badValidation = validator.validate(badResult);
        // 断言校验未通过。
        assertFalse(badValidation.isValid());
        // 断言错误列表包含 SQL 缺失提示。
        assertTrue(badValidation.getErrors().contains("sql_required"));

        // 构建包含 SQL 的数据对象。
        SqlData goodData = new SqlData();
        // 写入 SQL 字段。
        goodData.setSql("select 1");
        // 构建结构化结果对象。
        StructuredResult<SqlData> goodResult = StructuredResult.of(ResultKind.SQL, 1, goodData);
        // 调用校验器执行校验。
        StructuredValidationResult goodValidation = validator.validate(goodResult);
        // 断言校验通过。
        assertTrue(goodValidation.isValid());
    }
}
