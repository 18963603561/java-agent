# ai-agent-book 能力差距核对清单

**来源**: `vendor/ai-agent-book/zh`
**说明**: 每项仅使用三种状态：已覆盖 / 新增覆盖 / 明确 `out-of-scope`

## 第一部分：Agent 基础
- [ ] 第01章：Agent 的本质 | 状态: 已覆盖 | 规格落点: `specs/001-agent-core-spec/spec.md` §核心概念与边界
- [ ] 第02章：`ReAct` 循环 | 状态: 新增覆盖 | 规格落点: `specs/001-agent-core-spec/spec.md` §Agent Runtime 与决策循环

## 第二部分：工具与扩展
- [ ] 第03章：工具调用基础 | 状态: 已覆盖 | 规格落点: `specs/001-agent-core-spec/spec.md` §模块划分/工具调用
- [ ] 第04章：`MCP` 协议详解 | 状态: 新增覆盖 | 规格落点: `specs/001-agent-core-spec/spec.md` §MCP/Skills/Hooks + `specs/001-agent-core-spec/contracts/openapi.yaml`
- [ ] 第05章：`Skills` 技能系统 | 状态: 新增覆盖 | 规格落点: `specs/001-agent-core-spec/spec.md` §MCP/Skills/Hooks
- [ ] 第06章：`Hooks` 与事件系统 | 状态: 新增覆盖 | 规格落点: `specs/001-agent-core-spec/spec.md` §MCP/Skills/Hooks

## 第三部分：上下文与记忆
- [ ] 第07章：上下文窗口管理 | 状态: 明确 `out-of-scope` | 规格落点: `specs/001-agent-core-spec/spec.md` §边界约束
- [ ] 第08章：记忆架构 | 状态: 已覆盖 | 规格落点: `specs/001-agent-core-spec/spec.md` §记忆抽象与落盘策略
- [ ] 第09章：多轮对话设计 | 状态: 明确 `out-of-scope` | 规格落点: `specs/001-agent-core-spec/spec.md` §边界约束

## 第四部分：单 Agent 模式
- [ ] 第10章：`Planning` 模式 | 状态: 新增覆盖 | 规格落点: `specs/001-agent-core-spec/spec.md` §Planner/Reflection
- [ ] 第11章：`Reflection` 模式 | 状态: 新增覆盖 | 规格落点: `specs/001-agent-core-spec/spec.md` §Planner/Reflection
- [ ] 第12章：`Chain-of-Thought` | 状态: 明确 `out-of-scope` | 规格落点: `specs/001-agent-core-spec/spec.md` §边界约束

## 第五部分：多 Agent 编排
- [ ] 第13章：编排基础 | 状态: 新增覆盖 | 规格落点: `specs/001-agent-core-spec/spec.md` §Multi-agent 编排
- [ ] 第14章：`DAG` 工作流 | 状态: 新增覆盖 | 规格落点: `specs/001-agent-core-spec/spec.md` §Multi-agent 编排
- [ ] 第15章：`Supervisor` 模式 | 状态: 新增覆盖 | 规格落点: `specs/001-agent-core-spec/spec.md` §Multi-agent 编排
- [ ] 第16章：`Handoff` 机制 | 状态: 新增覆盖 | 规格落点: `specs/001-agent-core-spec/spec.md` §Multi-agent 编排

## 第六部分：高级推理
- [ ] 第17章：`Tree-of-Thoughts` | 状态: 新增覆盖 | 规格落点: `specs/001-agent-core-spec/spec.md` §Advanced Reasoning
- [ ] 第18章：`Debate` 模式 | 状态: 新增覆盖 | 规格落点: `specs/001-agent-core-spec/spec.md` §Advanced Reasoning
- [ ] 第19章：`Research Synthesis` | 状态: 新增覆盖 | 规格落点: `specs/001-agent-core-spec/spec.md` §Advanced Reasoning

## 第七部分：生产架构
- [ ] 第20章：三层架构设计 | 状态: 明确 `out-of-scope` | 规格落点: `specs/001-agent-core-spec/spec.md` §对齐差异与替代设计
- [ ] 第21章：Temporal 工作流 | 状态: 明确 `out-of-scope` | 规格落点: `specs/001-agent-core-spec/spec.md` §依赖与假设
- [ ] 第22章：可观测性 | 状态: 已覆盖 | 规格落点: `specs/001-agent-core-spec/spec.md` §观测指标

## 第八部分：企业级特性
- [ ] 第23章：Token 预算控制 | 状态: 已覆盖 | 规格落点: `specs/001-agent-core-spec/spec.md` §预算计量与存储
- [ ] 第24章：策略治理（`OPA`） | 状态: 新增覆盖 | 规格落点: `specs/001-agent-core-spec/spec.md` §Enterprise 安全
- [ ] 第25章：安全执行（`WASI` 沙箱） | 状态: 新增覆盖 | 规格落点: `specs/001-agent-core-spec/spec.md` §Enterprise 安全
- [ ] 第26章：多租户设计 | 状态: 已覆盖 | 规格落点: `specs/001-agent-core-spec/spec.md` §多租户与鉴权策略

## 第九部分：前沿实践
- [ ] 第27章：`Deep Research` | 状态: 新增覆盖 | 规格落点: `specs/001-agent-core-spec/spec.md` §Advanced Reasoning
- [ ] 第28章：`Computer Use` | 状态: 明确 `out-of-scope` | 规格落点: `specs/001-agent-core-spec/spec.md` §边界约束
- [ ] 第29章：`Agentic Coding` | 状态: 明确 `out-of-scope` | 规格落点: `specs/001-agent-core-spec/spec.md` §边界约束
- [ ] 第30章：`Background Agents` | 状态: 明确 `out-of-scope` | 规格落点: `specs/001-agent-core-spec/spec.md` §边界约束
- [ ] 第31章：分层模型策略 | 状态: 新增覆盖 | 规格落点: `specs/001-agent-core-spec/spec.md` §Enterprise 安全
