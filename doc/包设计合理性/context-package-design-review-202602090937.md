# context 包设计合理性复评

## 1. 评审范围
- 目标目录：`src/main/java/com/example/agent/capabilities/context`
- 覆盖子包：`assembly`、`builder/*`、`research`、`runtime`
- 评审维度：分层边界、职责内聚、扩展性、可观测性、坏味道与演进风险
- 评审时间：`2026-02-09 09:37`

## 2. 结构快照（本次扫描）
- Java 文件总数：`39`
- 目录分布：
  - `src/main/java/com/example/agent/capabilities/context`：`28`
  - `src/main/java/com/example/agent/capabilities/context/runtime`：`4`
  - `src/main/java/com/example/agent/capabilities/context/research`：`3`
  - `src/main/java/com/example/agent/capabilities/context/assembly`：`1`
  - `src/main/java/com/example/agent/capabilities/context/builder/*`：`3`
- 大文件（按行数）：
  - `src/main/java/com/example/agent/capabilities/context/DefaultContextBuilder.java`：`941` 行
  - `src/main/java/com/example/agent/capabilities/context/research/ResearchPipeline.java`：`452` 行
  - `src/main/java/com/example/agent/capabilities/context/EvidencePackService.java`：`435` 行

## 3. 综合评级
- 包设计合理性等级：**B+（8.2/10）**
- 代码坏味道等级：**B-（中等风险）**
- 结论摘要：
  - 第三阶段改造后，包内“类型化上下文访问”和“装配策略统一”方向正确，核心语义明显收敛。
  - 当前主要风险已从“语义错误风险”转为“规模与治理风险”，即个别核心类仍偏大、状态管理与可观测细节仍有改进空间。

## 4. 主要进步（相较前次评审）

### 4.1 上下文访问从散落键读向类型化门面收敛
- 证据：`DefaultContextBuilder` 已通过 `ContextRuntimeKeys` + `ContextRuntimeView` 读取关键字段，例如：
  - `src/main/java/com/example/agent/capabilities/context/DefaultContextBuilder.java:420`
  - `src/main/java/com/example/agent/capabilities/context/DefaultContextBuilder.java:467`
  - `src/main/java/com/example/agent/capabilities/context/DefaultContextBuilder.java:717`
- 评价：核心读取链路可维护性和一致性明显提升。

### 4.2 策略装配实现已单点化
- 证据：装配策略集中在 `PromptContextPolicyApplier`：
  - `src/main/java/com/example/agent/capabilities/context/assembly/PromptContextPolicyApplier.java:133`
- 评价：`DefaultContextAssembler` 与提示词解析链路的重复逻辑已实质减少。

### 4.3 死配置已清理
- 证据：仓内已无 `DeepResearchWorkflowProperties` 与 `deep-research` 关键词残留（本次全局检索为空）。
- 评价：配置认知负担下降，误配置风险下降。

## 5. 主要坏味道清单（按严重度）

### 5.1 高优先级

#### S1-01 `DefaultContextBuilder` 仍是大类，编排与细节粘连
- 证据：
  - 文件规模：`941` 行
  - 依赖规模：`45` 个 import
  - 入口方法：`src/main/java/com/example/agent/capabilities/context/DefaultContextBuilder.java:178`
- 风险：
  - 变更面仍偏大，预算、剪枝、压缩、快照与指标变更会互相牵连。
  - 新增能力时，回归成本继续上升。
- 建议：
  - 继续下沉阶段性编排步骤为 `Pipeline Step` 组件。
  - 将构建流程改成可组合链，`DefaultContextBuilder` 仅保留 orchestration。

#### S1-02 `EvidencePackService` 仍是进程级状态存储，缺少自动回收策略
- 证据：
  - 进程存储：`src/main/java/com/example/agent/capabilities/context/EvidencePackService.java:31`
  - 仅有手工删除：`src/main/java/com/example/agent/capabilities/context/EvidencePackService.java:146`
  - 空键回退 `unknown`：`src/main/java/com/example/agent/capabilities/context/EvidencePackService.java:455`
- 风险：
  - 长生命周期运行下存在容量增长风险。
  - `unknown:unknown` 可能造成意外聚合。
- 建议：
  - 增加基于时间和容量的淘汰策略。
  - 对空 `tenantId/workflowId` 改为拒绝入库或显式临时包策略。

### 5.2 中优先级

#### S2-01 `ContextAssembler` 接口参数仍偏多，语义可进一步收敛
- 证据：`assemble(...)` 仍有 `8` 个参数：
  - `src/main/java/com/example/agent/capabilities/context/ContextAssembler.java:26`
- 风险：
  - 调用端易出现参数错位与语义漂移。
- 建议：
  - 引入 `PromptAssemblyCommand`（或同类命令对象）封装参数与扩展字段。

#### S2-02 可观测性在不同链路间不对齐
- 证据：
  - `DefaultContextAssembler` 使用 `new PromptContextPolicyApplier(null)`：
    - `src/main/java/com/example/agent/capabilities/context/DefaultContextAssembler.java:46`
  - `ContextRuntimeView` 类型读取告警缺少 `tenantId/workflowId/taskId/stage`：
    - `src/main/java/com/example/agent/capabilities/context/runtime/ContextRuntimeView.java:216`
- 风险：
  - 指标/日志在不同入口链路颗粒度不一致，排障视角不统一。
- 建议：
  - 让 `DefaultContextAssembler` 接入真实 `MetricsPublisher`。
  - 在运行时视图中引入可选上下文标识对象，统一异常日志模板。

#### S2-03 键常量存在跨模块重复定义
- 证据：
  - `EvidencePackService` 自定义 `CONTEXT_EVIDENCE_PACK`：
    - `src/main/java/com/example/agent/capabilities/context/EvidencePackService.java:26`
  - 其它模块存在同义常量：
    - `src/main/java/com/example/agent/capabilities/tools/execution/ToolExecutor.java:61`
- 风险：
  - 协议键未来演进时可能出现局部变更、全局失配。
- 建议：
  - 全部收口到 `ContextRuntimeKeys.EVIDENCE_PACK`，删除重复常量。

### 5.3 低优先级

#### S3-01 `ResearchPipeline` 局部异常兜底仍偏静默
- 证据：
  - 提示词上下文序列化失败时直接回退 `{}`，未记录日志：
    - `src/main/java/com/example/agent/capabilities/context/research/ResearchPipeline.java:188`
- 风险：
  - 低概率故障在运行期不易被发现。
- 建议：
  - 对该分支补充 `warn` 日志并带 `workflowId/queryLength`。

#### S3-02 根包仍偏拥挤
- 证据：根目录下仍有 `28` 个类。
- 风险：
  - 新成员理解成本高，演进方向不够显式。
- 建议：
  - 按职责再收敛为 `model`、`evidence`、`builder`、`assembly`、`research` 五类主目录。

## 6. 复评结论
- 结论：该包已从“高语义风险”进入“中等结构优化期”。
- 当前最有价值的改造方向：
  - 继续拆分 `DefaultContextBuilder`。
  - 为 `EvidencePackService` 增加自动回收策略与严格键策略。
  - 统一 `context` 链路的日志上下文与策略分支指标。
- 若完成以上三项，预计可提升到 **A-（8.8/10）** 水平。

