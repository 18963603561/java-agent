# 提示词输入输出契约规范（基于 prompt-diff-202601310902.md）

生成时间: 2026-01-31 10:05
范围: `system`、`developer`、`planner`、`reflect`、`final`、`react`、`cot`、`research`、`debate`、`multi-agent`

## 全局硬性约束（所有场景通用）
1) 除 `system` 与 `developer` 外，必须仅输出 JSON 原文，不允许任何额外说明
2) 必须禁止输出 Markdown 代码块（禁止 ```）
3) 必须禁止输出多余文本、标题、前后缀或注释
4) 字段名必须与解析逻辑完全一致，大小写敏感
5) 数值范围必须符合约束（例如 score/confidence 为 0-1）

## 解析失败二次修复提示词模板（通用）
用途: 首次输出 JSON 解析失败时，发送给模型进行二次修复。

模板:
你刚才的输出不是合法 JSON。请严格只输出合法 JSON 对象，不要任何解释文字，不要 Markdown 代码块，不要多余字段。
必须满足当前场景的字段与类型要求。若缺失字段请补齐，若类型不匹配请修复。
仅输出 JSON 对象。

## 场景 1: `system`
### 输入契约
- 输入: 上下文快照与系统约束信息（由模板渲染）
- 输出: 字符串文本
- 约束: 只允许输出系统提示文本，不包含 JSON，不包含额外说明

### 输出结构
- 本场景不要求 JSON
- 输出必须为纯文本

### 失败与修复策略
- 若输出包含 JSON 或多余说明，直接丢弃并回退到默认系统提示
- 若输出为空，回退到默认系统提示

### 最小示例
你是智能体运行时执行器，必须遵守安全边界与多租户隔离。

## 场景 2: `developer`
### 输入契约
- 输入: 上下文快照与开发者约束信息（由模板渲染）
- 输出: 字符串文本
- 约束: 只允许输出开发者提示文本，不包含 JSON，不包含额外说明

### 输出结构
- 本场景不要求 JSON
- 输出必须为纯文本

### 失败与修复策略
- 若输出包含 JSON 或多余说明，直接丢弃并回退到默认开发者提示
- 若输出为空，回退到默认开发者提示

### 最小示例
输出必须结构化且可追溯，遇到不确定先检索再回答。风险等级:{riskLevel}

## 场景 3: `planner`
### 输入契约
- 输入: 规划上下文（PLAN_CONTEXT_JSON）
- 输出: 严格 JSON 对象
- 约束: 仅输出 JSON；字段必须包含 summary 与 steps

### 输出 JSON Schema
{
  "type": "object",
  "required": ["summary", "steps"],
  "properties": {
    "summary": {"type": "string"},
    "steps": {
      "type": "array",
      "items": {
        "type": "object",
        "required": ["type", "input"],
        "properties": {
          "type": {"type": "string"},
          "input": {"type": "object"},
          "tool": {"type": "string"},
          "dependsOn": {"type": "array"}
        },
        "additionalProperties": true
      }
    }
  },
  "additionalProperties": true
}

### 失败与修复策略
- 解析失败时使用通用修复模板
- 修复仍失败时回退为最小 JSON：summary 固定为 "fallback"，steps 为单步空输入

### 最小示例
{"summary":"plan","steps":[{"type":"TOOL","input":{}}]}

## 场景 4: `reflect`
### 输入契约
- 输入: 反思上下文（REFLECTION_CONTEXT_JSON）
- 输出: 严格 JSON 对象
- 约束: 仅输出 JSON；字段必须包含 score、retry、notes

### 输出 JSON Schema
{
  "type": "object",
  "required": ["score", "retry", "notes"],
  "properties": {
    "score": {"type": "number", "minimum": 0, "maximum": 1},
    "retry": {"type": "boolean"},
    "notes": {"type": "string"}
  },
  "additionalProperties": true
}

### 失败与修复策略
- 解析失败时使用通用修复模板
- 修复仍失败时回退为: score=0, retry=true, notes="parse_failed"

### 最小示例
{"score":0.5,"retry":false,"notes":"ok"}

## 场景 5: `final`
### 输入契约
- 输入: 最终输出上下文（FINAL_CONTEXT_JSON）
- 输出: 严格 JSON 对象
- 约束: 仅输出 JSON；字段必须包含 answer，可选 highlights 与 confidence

### 输出 JSON Schema
{
  "type": "object",
  "required": ["answer"],
  "properties": {
    "answer": {"type": "string"},
    "highlights": {"type": "string"},
    "confidence": {"type": "number", "minimum": 0, "maximum": 1}
  },
  "additionalProperties": true
}

### 失败与修复策略
- 解析失败时使用通用修复模板
- 修复仍失败时回退为: answer 置空字符串

### 最小示例
{"answer":"done","highlights":"key points","confidence":0.7}

## 场景 6: `react`
### 输入契约
- 输入: React 上下文（REACT_CONTEXT_JSON）
- 输出: 严格 JSON 对象
- 约束: 仅输出 JSON；字段必须完整

### 输出 JSON Schema
{
  "type": "object",
  "required": ["action", "tool", "arguments", "shouldStop", "stopReason", "finalAnswer"],
  "properties": {
    "action": {"type": "string", "enum": ["tool", "stop", "none"]},
    "tool": {"type": "string"},
    "arguments": {"type": "object"},
    "shouldStop": {"type": "boolean"},
    "stopReason": {"type": "string"},
    "finalAnswer": {"type": "string"}
  },
  "additionalProperties": true
}

### 失败与修复策略
- 解析失败时使用通用修复模板
- 修复仍失败时回退为: action="none", tool="", arguments={}, shouldStop=true, stopReason="parse_failed", finalAnswer=""

### 最小示例
{"action":"none","tool":"","arguments":{},"shouldStop":false,"stopReason":"","finalAnswer":""}

## 场景 7: `cot`
### 输入契约
- 输入: COT 上下文（COT_CONTEXT_JSON）
- 输出: 严格 JSON 对象
- 约束: 仅输出 JSON；字段必须完整

### 输出 JSON Schema
{
  "type": "object",
  "required": ["stepSummary", "shouldContinue", "finalAnswer", "confidence", "stopReason"],
  "properties": {
    "stepSummary": {"type": "string"},
    "shouldContinue": {"type": "boolean"},
    "finalAnswer": {"type": "string"},
    "confidence": {"type": "number", "minimum": 0, "maximum": 1},
    "stopReason": {"type": "string"}
  },
  "additionalProperties": true
}

### 失败与修复策略
- 解析失败时使用通用修复模板
- 修复仍失败时回退为: stepSummary="", shouldContinue=false, finalAnswer="", confidence=0, stopReason="parse_failed"

### 最小示例
{"stepSummary":"next","shouldContinue":true,"finalAnswer":"","confidence":0.4,"stopReason":""}

## 场景 8: `research`
### 输入契约
- 输入: 研究上下文（RESEARCH_CONTEXT_JSON）
- 输出: 严格 JSON 对象
- 约束: 仅输出 JSON；字段必须包含 citations 数组

### 输出 JSON Schema
{
  "type": "object",
  "required": ["citations"],
  "properties": {
    "citations": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "source": {"type": "string"},
          "snippet": {"type": "string"},
          "fetchedAt": {"type": "string"}
        },
        "additionalProperties": true
      }
    }
  },
  "additionalProperties": true
}

说明: 当前程序解析使用 `ResearchCitation`，其字段以 `source`、`snippet`、`fetchedAt` 为准；若模型返回未知字段允许存在，但必须保留 citations 列表。

### 失败与修复策略
- 解析失败时使用通用修复模板
- 修复仍失败时回退为: citations=[]

### 最小示例
{"citations":[{"source":"local","snippet":"sample","fetchedAt":"2026-01-31T10:00:00Z"}]}

## 场景 9: `debate`
### 输入契约
- 输入: 辩论上下文（DEBATE_CONTEXT_JSON）
- 输出: 严格 JSON 对象
- 约束: 仅输出 JSON；字段必须包含 conclusion

### 输出 JSON Schema
{
  "type": "object",
  "required": ["conclusion"],
  "properties": {
    "conclusion": {"type": "string"}
  },
  "additionalProperties": true
}

### 失败与修复策略
- 解析失败时使用通用修复模板
- 修复仍失败时回退为: conclusion=""

### 最小示例
{"conclusion":"ok"}

## 场景 10: `multi-agent`
### 输入契约
- 输入: 多智能体上下文（MULTI_AGENT_CONTEXT_JSON）
- 输出: 严格 JSON 对象
- 约束: 仅输出 JSON；字段必须包含 team 数组

### 输出 JSON Schema
{
  "type": "object",
  "required": ["team"],
  "properties": {
    "team": {
      "type": "array",
      "items": {
        "type": "object",
        "required": ["role", "responsibility"],
        "properties": {
          "role": {"type": "string"},
          "responsibility": {"type": "string"}
        },
        "additionalProperties": true
      }
    }
  },
  "additionalProperties": true
}

兼容建议: 若当前程序未解析 team 元素内的 role/responsibility，可先保留字段并在后续解析中升级兼容。

### 失败与修复策略
- 解析失败时使用通用修复模板
- 修复仍失败时回退为: team=[]

### 最小示例
{"team":[{"role":"planner","responsibility":"generate steps"}]}
