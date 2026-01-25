<!--
同步影响报告:
- 版本变更: 1.0.0 -> 1.1.0
- 修改的原则: 无
- 新增章节: 工程约束补充（`Shannon` 对齐约束、`Spring` 生态选型约束）
- 移除章节: 无
- 需要更新的模板:
  - ✅ `.specify/templates/plan-template.md`
  - ✅ `.specify/templates/spec-template.md`
  - ✅ `.specify/templates/tasks-template.md`
  - ⚠ `.specify/templates/commands/*.md`（目录不存在，无法校验）
- 后续待办: 无
-->

# Java Shannon Agent Orchestrator Core Constitution

## Core Principles

### 技术栈锁定
- 必须使用 `Java 17`、`Spring Boot 3`、`WebFlux`。
- 流式输出必须使用 `SSE`，不得改用其他协议或实现。
- 任何偏离技术栈的方案必须先修订宪章并给出迁移计划。
**理由**：统一技术栈降低维护成本，保证并发与流式能力一致。

### 统一输出契约
- 对外接口与内部工具调用必须使用统一的输出对象结构。
- 结果结构必须包含状态、数据与错误语义，字段定义必须集中管理。
- 输出结构变更必须提供兼容方案与版本说明。
**理由**：确保工具与智能体稳定集成，减少集成成本与回归风险。

### 日志与可观测性优先
- 日志框架必须使用 `slf4j`，并遵循项目统一格式。
- 外部接口调用（`HTTP`、`DB`、`MQ`、文件系统）必须记录日志与耗时。
- 核心业务关键路径必须记录开始、关键分支与结束的 `info` 日志。
- 异常捕获必须记录 `error` 日志，包含上下文信息与完整堆栈。
**理由**：保证排障、审计与运行可靠性。

### 多租户与鉴权扩展点预留
- 必须提供可插拔的多租户解析接口。
- 必须提供鉴权过滤器与上下文传递机制的扩展点。
- 任何功能不得绕过租户与鉴权上下文。
**理由**：支持企业级扩展，避免后续破坏性重构。

### 令牌预算跟踪一等公民
- 必须定义令牌预算计量点，覆盖核心调用链。
- 必须提供预算存储接口并隔离具体实现。
- 必须输出预算使用的可观测性指标与告警信号。
**理由**：保障成本可控与预算审计。

## 工程约束

- 所有 `Java` 注释必须使用中文，重要类、方法、关键属性、`bean` 及其属性
  必须补充注释，说明用途、输入输出、边界条件与注意事项。
- `Java` 类名、方法名、变量名、包名、配置 `key` 必须使用英文，并遵循既有风格。
- 文件编码必须为 `UTF-8`（无 `BOM`），换行必须为 `LF`。
- `Shannon` 架构与命名对齐约束：
  - 代码结构、模块划分、核心接口命名必须尽量对齐 `vendor/Shannon`
    的工程结构与术语体系，包括但不限于：`agent core`、`orchestrator`、
    `streaming api`、`event types`、`task history & timeline`、
    `memory system`、`scheduled tasks`、`authentication & multitenancy`、
    `token budget tracking`。
  - 规格说明中必须提供映射表：`Shannon` 源模块/目录/文件 -> `Java`
    模块/`package`/`interface`/`class`。
  - 对无法 `1:1` 对齐的模块或接口，必须在规格说明中说明差异原因（语言差异、
    框架差异、工程约束）、`Java` 侧替代设计与兼容边界。
  - 实现阶段不得临时新增抽象或隐式修改语义。
  - 核心概念命名优先复用 `Shannon` 术语，避免语义漂移。
- `Spring` 生态优先技术选型约束：
  - 应用基础框架统一使用 `Spring Boot 3.x`。
  - 只要 `Spring` 生态存在成熟组件，必须优先选用官方或事实标准实现，
    不得直接使用裸实现或重复造轮子。
  - 数据访问优先使用 `Spring JDBC`、`Spring Data`、`Spring Transaction`。
  - `Redis` 与缓存优先使用 `Spring Data Redis`。
  - `HTTP` 客户端优先使用 `Spring WebClient` 或 `RestClient`。
  - 配置管理优先使用 `Spring Boot Configuration Properties`。
  - 定时任务优先使用 `Spring Scheduling`、`Spring Integration`、
    `Quartz Spring Boot Starter`。
  - 事件机制优先使用 `Spring ApplicationEvent`、`Spring Messaging`、
    `Reactor`。
  - 指标与可观测性优先使用 `Micrometer` 与 `Spring Boot Actuator`。
  - `Spring` 生态无法满足需求时才允许引入非 `Spring` 组件，规格说明必须
    说明不可满足原因、替代组件选型理由、生命周期、运维、升级与兼容风险评估。
  - 新增依赖尽量通过 `Spring Boot Starter` 管理版本，避免大量显式版本锁定。

## 开发流程与质量门禁

- 需求、设计与任务必须使用 `specs/` 下的 `spec.md`、`plan.md`、`tasks.md`
  进行记录，并保持与实现一致。
- 对外接口、事件协议与输出结构变更必须同步更新契约与示例，并给出兼容方案。
- 关键路径变更必须提供可复现验证步骤，包含日志与指标检查。
- 违反宪章的例外必须在计划中记录原因与替代方案，并在评审中批准。

## Governance

- 本宪章高于其他规范，冲突时以本宪章为准。
- 修订流程：提出变更理由与影响评估，完成评审后同步更新宪章与模板，
  并记录版本与日期。
- 版本策略采用 `MAJOR.MINOR.PATCH`：
  - `MAJOR`：移除或重新定义原则、导致不兼容的治理变更。
  - `MINOR`：新增原则或新增重要约束与流程要求。
  - `PATCH`：措辞澄清、排版调整或非语义变更。
- 合规审查要求：所有需求、计划、评审与合并必须进行宪章检查，
  并在必要时记录例外与整改计划。

**Version**: 1.1.0 | **Ratified**: 2026-01-25 | **Last Amended**: 2026-01-25
