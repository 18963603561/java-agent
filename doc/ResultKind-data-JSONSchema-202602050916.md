# ResultKind 的 data 字段 JSON Schema（完整草案）

**说明**
- 仅定义 `StructuredResult.data` 的结构，其他外壳字段不在本文范围。
- Schema 采用 `draft/2020-12` 风格，仅用于约束字段形态与稳定性。
- 所有 `enum` 为稳定语义域，新增字段应保持向后兼容。

## 统一约定（通用类型定义）
下列类型在各 `data` Schema 内复用。

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://example.com/schema/common.json",
  "title": "Common Types",
  "definitions": {
    "Score01": {
      "type": "number",
      "minimum": 0,
      "maximum": 1
    },
    "StringList": {
      "type": "array",
      "items": { "type": "string" }
    },
    "KeyValue": {
      "type": "object",
      "additionalProperties": { "type": ["string", "number", "boolean", "null"] }
    },
    "RiskLevel": {
      "type": "string",
      "enum": ["LOW", "MEDIUM", "HIGH", "CRITICAL"]
    },
    "TimeIso8601": {
      "type": "string",
      "format": "date-time"
    }
  }
}
```

## 1) TABLE_MATCHES
用于数据发现、字段命中、治理检索等语义。

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://example.com/schema/table-matches.json",
  "title": "TABLE_MATCHES.data",
  "type": "object",
  "additionalProperties": false,
  "required": ["items"],
  "properties": {
    "items": {
      "type": "array",
      "items": {
        "type": "object",
        "additionalProperties": false,
        "required": ["table", "column", "matchType"],
        "properties": {
          "table": { "type": "string" },
          "column": { "type": "string" },
          "database": { "type": "string" },
          "schema": { "type": "string" },
          "matchType": {
            "type": "string",
            "enum": ["NAME", "COMMENT", "SEMANTIC", "SAMPLE", "RULE"]
          },
          "score": { "$ref": "common.json#/definitions/Score01" },
          "reason": { "type": "string" },
          "dataType": { "type": "string" },
          "nullable": { "type": "boolean" }
        }
      }
    },
    "topTables": { "$ref": "common.json#/definitions/StringList" },
    "topColumns": { "$ref": "common.json#/definitions/StringList" },
    "requiredFieldsCovered": { "$ref": "common.json#/definitions/StringList" },
    "missingRequiredFields": { "$ref": "common.json#/definitions/StringList" },
    "matchCount": { "type": "integer", "minimum": 0 }
  }
}
```

## 2) SQL
用于 SQL 生成、修复与优化语义。

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://example.com/schema/sql.json",
  "title": "SQL.data",
  "type": "object",
  "additionalProperties": false,
  "required": ["sql", "dialect"],
  "properties": {
    "sql": { "type": "string" },
    "dialect": {
      "type": "string",
      "enum": ["MYSQL", "POSTGRES", "CLICKHOUSE", "SPARK", "FMDB", "ANSI", "OTHER"]
    },
    "tables": { "$ref": "common.json#/definitions/StringList" },
    "params": {
      "type": "array",
      "items": {
        "type": "object",
        "additionalProperties": false,
        "required": ["name", "type"],
        "properties": {
          "name": { "type": "string" },
          "type": { "type": "string" },
          "default": { "type": ["string", "number", "boolean", "null"] }
        }
      }
    },
    "risk": {
      "type": "object",
      "additionalProperties": false,
      "properties": {
        "level": { "$ref": "common.json#/definitions/RiskLevel" },
        "reasons": { "$ref": "common.json#/definitions/StringList" }
      }
    }
  }
}
```

## 3) RECORDSET
用于查询结果集合语义，限制为轻量结构。

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://example.com/schema/recordset.json",
  "title": "RECORDSET.data",
  "type": "object",
  "additionalProperties": false,
  "required": ["columns", "rows"],
  "properties": {
    "columns": {
      "type": "array",
      "items": {
        "type": "object",
        "additionalProperties": false,
        "required": ["name", "type"],
        "properties": {
          "name": { "type": "string" },
          "type": { "type": "string" },
          "label": { "type": "string" }
        }
      }
    },
    "rows": {
      "type": "array",
      "items": {
        "type": "array",
        "items": { "type": ["string", "number", "boolean", "null"] }
      }
    },
    "rowCount": { "type": "integer", "minimum": 0 },
    "truncated": { "type": "boolean" },
    "cursor": { "type": "string" }
  }
}
```

## 4) ENTITY_LIST
用于实体识别与抽取结果语义。

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://example.com/schema/entity-list.json",
  "title": "ENTITY_LIST.data",
  "type": "object",
  "additionalProperties": false,
  "required": ["entities"],
  "properties": {
    "entities": {
      "type": "array",
      "items": {
        "type": "object",
        "additionalProperties": false,
        "required": ["type", "value"],
        "properties": {
          "type": { "type": "string" },
          "value": { "type": "string" },
          "normalized": { "type": "string" },
          "score": { "$ref": "common.json#/definitions/Score01" },
          "source": { "type": "string" }
        }
      }
    },
    "entityTypes": { "$ref": "common.json#/definitions/StringList" },
    "count": { "type": "integer", "minimum": 0 }
  }
}
```

## 5) DECISION
用于路由、选择、下一步建议语义。

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://example.com/schema/decision.json",
  "title": "DECISION.data",
  "type": "object",
  "additionalProperties": false,
  "required": ["decision"],
  "properties": {
    "decision": { "type": "string" },
    "options": { "$ref": "common.json#/definitions/StringList" },
    "reason": { "type": "string" },
    "confidence": { "$ref": "common.json#/definitions/Score01" },
    "nextAction": { "type": "string" },
    "parameters": { "$ref": "common.json#/definitions/KeyValue" }
  }
}
```

## 6) DIAGNOSIS
用于诊断、分析与建议语义。

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://example.com/schema/diagnosis.json",
  "title": "DIAGNOSIS.data",
  "type": "object",
  "additionalProperties": false,
  "required": ["summary"],
  "properties": {
    "summary": { "type": "string" },
    "findings": {
      "type": "array",
      "items": {
        "type": "object",
        "additionalProperties": false,
        "required": ["title"],
        "properties": {
          "title": { "type": "string" },
          "detail": { "type": "string" },
          "severity": { "$ref": "common.json#/definitions/RiskLevel" }
        }
      }
    },
    "recommendations": { "$ref": "common.json#/definitions/StringList" },
    "confidence": { "$ref": "common.json#/definitions/Score01" }
  }
}
```

## 7) DOCUMENT_CITATIONS
用于研究引用清单语义。

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://example.com/schema/document-citations.json",
  "title": "DOCUMENT_CITATIONS.data",
  "type": "object",
  "additionalProperties": false,
  "required": ["citations"],
  "properties": {
    "citations": {
      "type": "array",
      "items": {
        "type": "object",
        "additionalProperties": false,
        "required": ["source", "snippet"],
        "properties": {
          "source": { "type": "string" },
          "title": { "type": "string" },
          "snippet": { "type": "string" },
          "url": { "type": "string" },
          "fetchedAt": { "$ref": "common.json#/definitions/TimeIso8601" }
        }
      }
    },
    "count": { "type": "integer", "minimum": 0 }
  }
}
```

## 8) ERROR
用于抽取失败与错误兜底语义。

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://example.com/schema/error.json",
  "title": "ERROR.data",
  "type": "object",
  "additionalProperties": false,
  "required": ["category", "message"],
  "properties": {
    "category": {
      "type": "string",
      "enum": [
        "PARSE_ERROR",
        "TOOL_FAILED",
        "EMPTY_RESULT",
        "BUDGET_TRUNCATED",
        "MODEL_ERROR",
        "UNKNOWN"
      ]
    },
    "message": { "type": "string" },
    "actionableNext": { "$ref": "common.json#/definitions/StringList" },
    "retryable": { "type": "boolean" }
  }
}
```

**备注**
- 若需严格对齐现有工程字段，可按具体工具与模型扩展 `data` 字段。
- 生产环境建议将 `common.json` 拆为独立文件并在工程中统一维护。