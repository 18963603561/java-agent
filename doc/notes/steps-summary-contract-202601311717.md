# 对外 steps 字段摘要化校验与契约更新

## 校验结论
- 任务结果 `steps` 已为摘要化输出：`AgentRuntime.recordStepOutput(...)` 写入的 `output` 仅包含摘要层字段。
- 时间线接口 `timeline/steps` 返回 `StepRecord`，其 `output` 仍保留原始输出，同时追加摘要字段。

## 契约调整
- `TaskResponse.result` 指向 `RuntimeResult`，并明确 `steps[].output` 使用摘要结构 `StepOutputSummary`。
- `StepRecord.output` 增加摘要字段说明，保留原始输出的兼容性描述。

## 影响范围
- 仅更新接口契约与说明，不改变现有对外返回数据结构。
