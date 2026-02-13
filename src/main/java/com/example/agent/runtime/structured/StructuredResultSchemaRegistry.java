package com.example.agent.runtime.structured;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 结构化结果校验定义注册表。
 *
 * <p>用途：为不同 {@link ResultKind} 提供稳定的结构约束类型与版本映射。</p>
 */
@Component
public class StructuredResultSchemaRegistry {

    /**
     * 结构化校验定义映射表。
     */
    private final Map<ResultKind, SchemaDefinition> schemas;

    public StructuredResultSchemaRegistry() {
        // 初始化结构化校验定义映射容器。
        Map<ResultKind, SchemaDefinition> schemaMap = new EnumMap<>(ResultKind.class);
        // 注册 SQL 结构化校验定义。
        schemaMap.put(ResultKind.SQL, new SchemaDefinition(ResultKind.SQL, 1, SchemaType.SQL));
        // 注册 RECORDSET 结构化校验定义。
        schemaMap.put(ResultKind.RECORDSET, new SchemaDefinition(ResultKind.RECORDSET, 1, SchemaType.RECORDSET));
        // 注册 TABLE_MATCHES 结构化校验定义。
        schemaMap.put(ResultKind.TABLE_MATCHES, new SchemaDefinition(ResultKind.TABLE_MATCHES, 1, SchemaType.DEFAULT));
        // 注册 ENTITY_LIST 结构化校验定义。
        schemaMap.put(ResultKind.ENTITY_LIST, new SchemaDefinition(ResultKind.ENTITY_LIST, 1, SchemaType.DEFAULT));
        // 注册 DECISION 结构化校验定义。
        schemaMap.put(ResultKind.DECISION, new SchemaDefinition(ResultKind.DECISION, 1, SchemaType.DEFAULT));
        // 注册 DIAGNOSIS 结构化校验定义。
        schemaMap.put(ResultKind.DIAGNOSIS, new SchemaDefinition(ResultKind.DIAGNOSIS, 1, SchemaType.DEFAULT));
        // 注册 DOCUMENT_CITATIONS 结构化校验定义。
        schemaMap.put(ResultKind.DOCUMENT_CITATIONS, new SchemaDefinition(ResultKind.DOCUMENT_CITATIONS, 1,
                SchemaType.DEFAULT));
        // 注册 ERROR 结构化校验定义。
        schemaMap.put(ResultKind.ERROR, new SchemaDefinition(ResultKind.ERROR, 1, SchemaType.ERROR));
        // 固化校验定义映射表，防止外部修改。
        this.schemas = Collections.unmodifiableMap(schemaMap);
    }

    /**
     * 根据类型与版本获取校验定义。
     *
     * @param kind 结果类型
     * @param schemaVersion 结构版本
     * @return 校验定义，不存在时返回空
     */
    public SchemaDefinition findSchema(ResultKind kind, Integer schemaVersion) {
        // 判断类型是否为空，空时直接返回空定义。
        if (kind == null) {
            // 返回空定义，表示无法匹配校验规则。
            return null;
        }
        // 从映射表获取对应的校验定义。
        SchemaDefinition definition = schemas.get(kind);
        // 判断校验定义是否存在，不存在时直接返回空。
        if (definition == null) {
            // 返回空定义，表示未注册该类型。
            return null;
        }
        // 判断版本是否匹配，存在版本但不一致时返回空。
        if (schemaVersion != null && schemaVersion.intValue() != definition.getSchemaVersion()) {
            // 返回空定义，提示版本不匹配。
            return null;
        }
        // 返回匹配的校验定义。
        return definition;
    }

    /**
     * 结构化校验类型枚举。
     */
    public enum SchemaType {
        SQL,
        RECORDSET,
        DEFAULT,
        ERROR
    }

    /**
     * 结构化校验定义。
     */
    public static class SchemaDefinition {

        /**
         * 结果类型。
         */
        private final ResultKind kind;

        /**
         * 结构版本。
         */
        private final int schemaVersion;

        /**
         * 校验类型。
         */
        private final SchemaType schemaType;

        public SchemaDefinition(ResultKind kind, int schemaVersion, SchemaType schemaType) {
            this.kind = kind;
            this.schemaVersion = schemaVersion;
            this.schemaType = schemaType;
        }

        public ResultKind getKind() {
            return kind;
        }

        public int getSchemaVersion() {
            return schemaVersion;
        }

        public SchemaType getSchemaType() {
            return schemaType;
        }
    }
}
