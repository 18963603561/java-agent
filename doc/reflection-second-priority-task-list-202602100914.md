# `reflection` 包第二优先级改造任务清单

## 1. 目标

- 目标一：将反思 Prompt 模板从代码中外置，并建立可管理的版本机制。
- 目标二：将反思输入契约从散落 `Map<String, Object>` 收敛为统一强类型对象。
- 目标三：为 `ReflectionProperties` 增加参数校验与启动期失败机制，避免非法配置进入运行态。

## 2. 范围

- 代码范围：`src/main/java/com/example/agent/reflection`
- 资源范围：`src/main/resources/prompts/reflection`
- 测试范围：`src/test/java/com/example/agent/reflection`

## 3. 任务清单（第二优先级）

### A 组：Prompt 模板外置 + 版本化

#### 任务 A1：建立 Prompt 模板目录与版本命名规范

- 任务编号：`REFL-P1-A1`
- 主要产出：
  - 新建目录 `src/main/resources/prompts/reflection`
  - 新建模板文件 `src/main/resources/prompts/reflection/review-v1.prompt`
- 设计要求：
  - 模板内仅保留指令与占位符（如 `{{contextJson}}`），不拼接业务逻辑。
  - 文件命名显式带版本号，便于灰度与回滚。
- 验收标准：`LlmReflectionStrategy` 不再内嵌大段三引号 Prompt 文本。

#### 任务 A2：实现 Prompt 解析与渲染组件

- 任务编号：`REFL-P1-A2`
- 主要产出：新增 `ReflectionPromptProvider` 与模板渲染能力。
- 建议文件：
  - `src/main/java/com/example/agent/reflection/prompt/ReflectionPromptProvider.java`
  - `src/main/java/com/example/agent/reflection/prompt/ReflectionPromptTemplateEngine.java`
- 设计要求：
  - `Provider` 负责按版本加载模板。
  - `TemplateEngine` 只做占位符替换和基础校验。
  - 组件需具备缓存能力，避免每次读取磁盘。
- 验收标准：渲染失败时有清晰异常信息与原因码，不允许静默降级。

#### 任务 A3：增加版本配置与默认策略

- 任务编号：`REFL-P1-A3`
- 主要产出：在 `ReflectionProperties` 增加 `promptVersion` 等配置项。
- 建议配置：
  - `agent.reflection.prompt-version=v1`
  - `agent.reflection.prompt-strict=true`
- 设计要求：
  - `strict=true`：模板缺失或渲染失败即启动失败。
  - `strict=false`：允许兜底到内置最小模板（建议仅测试环境使用）。
- 验收标准：配置可驱动版本切换，不改代码即可替换 Prompt 模板。

#### 任务 A4：补齐 Prompt 外置相关测试

- 任务编号：`REFL-P1-A4`
- 主要产出：新增模板加载、版本切换、渲染失败场景测试。
- 建议文件：
  - `src/test/java/com/example/agent/reflection/prompt/ReflectionPromptProviderTest.java`
  - `src/test/java/com/example/agent/reflection/ReflectionServiceTest.java`
- 验收标准：
  - 模板路径错误时符合预期（启动失败或异常）。
  - 版本切换后 Prompt 文本可观测变化。

### B 组：收敛反思输入契约

#### 任务 B1：定义统一 `ReflectionContext` 领域模型

- 任务编号：`REFL-P1-B1`
- 主要产出：新增 `ReflectionContext`、`ReflectionOutputSummary`、`ReflectionOutputDigest` 等强类型对象。
- 建议文件：
  - `src/main/java/com/example/agent/reflection/model/ReflectionContext.java`
  - `src/main/java/com/example/agent/reflection/model/ReflectionOutputSummary.java`
  - `src/main/java/com/example/agent/reflection/model/ReflectionOutputDigest.java`
- 设计要求：
  - 模型字段语义固定，禁止直接暴露动态 `Map` 给策略层。
  - 可序列化且具备构建器。
- 验收标准：策略层与解析层方法签名不再传递原始 `Map`。

#### 任务 B2：实现上下文映射器

- 任务编号：`REFL-P1-B2`
- 主要产出：新增 `ReflectionContextMapper`，负责将 `StepSpec + StepExecutionOutput` 映射为 `ReflectionContext`。
- 建议文件：
  - `src/main/java/com/example/agent/reflection/model/ReflectionContextMapper.java`
- 设计要求：
  - 集中管理摘要截断、字段兜底、空值策略。
  - 屏蔽底层输出结构变化对上层策略的影响。
- 验收标准：`LlmReflectionStrategy` 与 `ReflectionResponseParser` 不再直接拼装上下文 `Map`。

#### 任务 B3：改造策略与解析组件签名

- 任务编号：`REFL-P1-B3`
- 主要产出：将以下组件输入改为强类型：
  - `LlmReflectionStrategy`
  - `ReflectionResponseParser`
  - `ReflectionExecutionContext`
- 设计要求：
  - 通过类型系统约束必要字段，减少运行期空判断。
  - 仅在边界层保留一次对象到 JSON 的序列化。
- 验收标准：`reflection` 包中与反思上下文相关的 `Map<String, Object>` 使用点显著收敛。

#### 任务 B4：补齐输入契约回归测试

- 任务编号：`REFL-P1-B4`
- 主要产出：新增针对新契约的单测与集成回归。
- 建议场景：
  - 空摘要、缺失摘要、仅 digest。
  - 超长摘要截断。
  - 非法字段类型输入。
- 验收标准：映射器与策略行为可预测，边界输入均有覆盖。

### C 组：`ReflectionProperties` 参数校验与启动失败机制

#### 任务 C1：为配置类添加 Bean Validation 约束

- 任务编号：`REFL-P1-C1`
- 主要产出：给 `ReflectionProperties` 增加 `@Validated` 与字段约束注解。
- 约束建议：
  - `maxRetries`：`@Min(1)`
  - `confidenceThreshold`：`@DecimalMin("0.0")` + `@DecimalMax("1.0")`
  - `minOutputChars`：`@Min(1)`
  - `requiredKeys`：`@NotNull` + `@NotEmpty`
  - `failureKeywords`：`@NotNull`
- 验收标准：非法配置在启动时直接失败，不进入运行期。

#### 任务 C2：增加配置一致性校验器

- 任务编号：`REFL-P1-C2`
- 主要产出：新增 `ReflectionPropertiesValidator`，执行跨字段校验。
- 建议规则：
  - `llmEnabled=false` 且 `fallbackEnabled=false` 时禁止启动。
  - `promptVersion` 与模板文件存在性一致。
  - `promptStrict=true` 时模板缺失禁止启动。
- 验收标准：跨字段冲突在应用启动阶段阻断。

#### 任务 C3：补齐配置校验测试

- 任务编号：`REFL-P1-C3`
- 主要产出：新增配置加载测试，验证失败即抛异常。
- 建议文件：
  - `src/test/java/com/example/agent/reflection/ReflectionPropertiesValidationTest.java`
- 验收标准：覆盖“字段越界、列表为空、跨字段冲突、模板不存在”四类场景。

## 4. 推荐实施顺序

- 第一阶段：`A1 -> A2 -> A3 -> A4`
- 第二阶段：`B1 -> B2 -> B3 -> B4`
- 第三阶段：`C1 -> C2 -> C3`

## 5. 每阶段完成定义

- 阶段 A 完成定义：Prompt 不再硬编码，版本可配置，模板失败行为可控。
- 阶段 B 完成定义：反思输入契约强类型化，`Map` 扩散被收敛到边界层。
- 阶段 C 完成定义：配置非法值与冲突配置在启动期即失败。

## 6. 验收与发布建议

- 每阶段完成后执行一次全量回归：`mvn test -DskipTests=false`。
- 发布前补充一次配置矩阵冒烟：`llm/fallback/promptStrict` 组合至少覆盖 6 组。
- 版本发布说明中记录 Prompt 版本号、配置变更项、兼容性结论。
