# 方案C（摘要与输出解耦）涉及文件改动分析

## 一、目标与结构调整
- 目标：`steps[i].output` 仅保留业务原始输出，摘要信息独立存放，避免摘要递归污染。
- 建议结构：
  - `steps[i].output`：原始输出
  - `steps[i].summary`：由 `StepOutputSummaryBuilder` 生成的摘要（包含 outputSummary/toolResultSummary/stepSummary/outputDigest/inputSummary/inputDigest/truncated）

## 二、核心改动点与受影响文件
### 1）运行时核心流转
- `src/main/java/com/example/agent/runtime/StepRuntimeService.java`
  - 取消“摘要合并进 output”的逻辑。
  - 改为生成摘要后写入 `record` 或直接返回给调用方保存为 `summary`。
- `src/main/java/com/example/agent/runtime/AgentRuntime.java`
  - `recordStepOutput` 改为同时记录 `output` 与 `summary`。
  - `updateRuntimeContext` 使用独立 `summary` 更新 `lastStepSummary`。
  - `enrichOutputSummaryForReflection` 不再修改 output，改为构造临时 summary 传给反思流程。
  - `buildStepOutputSummary` / `normalizeStepSummary` 需要改为读取 `summary` 而非 `output`。

### 2）摘要生成与存储结构
- `src/main/java/com/example/agent/runtime/StepOutputSummaryBuilder.java`
  - 摘要生成逻辑保留，但输出仅用于 `summary` 字段，不再合并进 output。
- `src/main/java/com/example/agent/runtime/StepRecord.java`
  - 增加 `summary` 字段（如 `Map<String, Object> summary`）用于持久化摘要。

### 3）持久化与数据结构
- `src/main/java/com/example/agent/runtime/StepRecordRepository.java`
  - 接口不变，但实现需支持 `summary` 存取。
- `src/main/java/com/example/agent/runtime/InMemoryStepRecordRepository.java`
  - 无额外改动逻辑，但需跟随 `StepRecord` 新字段。
- `src/main/java/com/example/agent/runtime/JdbcStepRecordRepository.java`
  - 写入/查询 SQL 增加 `summary` 列。
  - RowMapper 增加 `summary` 反序列化。
- `docker/init/001-init.sql`
  - 增加 `summary` 字段（jsonb）与注释。
  - 需要提供升级脚本（兼容已有库）。

### 4）摘要消费者（必须改为读 summary）
- `src/main/java/com/example/agent/reflection/ReflectionService.java`
  - `buildOutputSummaryContext` 不能再依赖 output 内的摘要字段，需改为接收 `summary` 或从外部传入。
- `src/main/java/com/example/agent/runtime/ReactLoopService.java`
  - `extractMap(output, ...)` 的来源改为 `summary`。
  - `resolveStatus/resolveTruncated` 需要读取 `summary` 中的 digest。
- `src/main/java/com/example/agent/runtime/FinalOutputService.java`
  - 解析 stepSummary/outputSummary/outputDigest 的来源改为 `summary`。

### 5）对外响应结构与兼容
- `src/main/java/com/example/agent/runtime/RuntimeResult.java`
  - 结构不变，但 `steps[i]` 内部数据会新增 `summary`，`output` 内容由摘要转为原始结果。
- `src/main/java/com/example/agent/runtime/StepTimelineResponse.java`
  - 若 `StepRecord` 新增 `summary` 字段，时间线响应会自动携带该字段，需要评估前端/调用方兼容。

## 三、兼容策略建议
- 阶段性“双读取”：
  - 若 `steps[i].summary` 缺失，允许从旧版 `output` 中回退读取摘要字段。
- 数据库兼容：
  - `summary` 允许为 `NULL`，读取时容错。

## 四、影响面总结
- **代码层面**：运行时核心、反思、总结、React 逻辑均需要改造读取路径。
- **数据层面**：持久化结构必须扩展；旧数据需要兼容。
- **接口层面**：`steps[i].output` 语义变化，需要通知调用方。