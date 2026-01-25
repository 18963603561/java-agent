# 基于 autopilot-m1.ps1 的自动化能力分析

## 结论
现有脚本链路已经具备自动向 `codex` 发起请求、自动执行测试并记录结果的能力，整体可用。需要满足依赖状态、任务元数据与提示模板编码的前置条件，才能稳定推进任务。

## 脚本关系与职责
- `scripts/autopilot-m1.ps1`  
  负责选择任务、校验依赖状态、生成提示词并调用 `codex`，随后触发 `scripts/run-m1.ps1` 执行测试，最终根据退出码决定是否继续。
- `scripts/run-m1.ps1`  
  负责按 `testCmd` 执行测试、写入 `run/logs/<taskId>.log`，并更新 `run/m1-status.json`。
- `scripts/m1-tasks.ps1`  
  维护任务元数据（`taskId`、`deps`、`testCmd`、`enabled`），供两条脚本链路使用。
- `scripts/prompts/implement-task.txt`  
  作为 `codex` 的提示模板，`autopilot-m1.ps1` 会将 `{TASK_ID}` 替换为当前任务。

## 关键流程
1. `autopilot-m1.ps1` 读取任务列表与 `run/m1-status.json`。
2. 校验依赖任务 `deps` 的 `exitCode==0`，否则直接终止。
3. 生成提示词并执行 `codex exec --full-auto -C <repoRoot> <prompt>`，日志写入 `run/logs/<taskId>.codex.log`。
4. 重新加载 `scripts/m1-tasks.ps1`，要求目标任务 `enabled==true` 且 `testCmd` 不是 `echo TODO`。
5. 调用 `scripts/run-m1.ps1 -OnlyTaskId <taskId>` 执行测试并写回状态。

## 可用性前置条件
- 需要可用的 `codex` 命令行环境。
- `run/m1-status.json` 必须包含依赖任务的成功状态。
- 目标任务在 `scripts/m1-tasks.ps1` 中已设置 `enabled=true` 且 `testCmd` 为真实可执行命令。
- 测试命令依赖的构建环境可用，例如 `mvn`。

## 主要问题与风险
- 提示模板编码不一致会导致提示词不可读，可用 `scripts/preflight-m1.ps1` 检测提示模板编码是否为 `UTF-8` 无 `BOM`。
- `autopilot-m1.ps1` 不会自动修复缺失的依赖状态，`run/m1-status.json` 不完整会导致任务直接终止。
- `scripts/run-m1.ps1` 通过 `cmd.exe /c` 执行 `testCmd`，复杂命令可能需要额外转义与引号处理。

## 建议落地动作
- 使用 `scripts/preflight-m1.ps1` 检测提示模板编码，必要时将 `scripts/prompts/implement-task.txt` 转换为 `UTF-8` 且无 `BOM`。
- 先使用 `scripts/run-m1.ps1` 跑通依赖任务，保证 `run/m1-status.json` 完整。
- 使用 `-StartTaskId` 与 `-MaxTasks` 控制推进范围，并结合 `-DryRun` 验证执行路径。

## 落地执行
- 已新增 `scripts/preflight-m1.ps1`，用于检查 `codex`、`mvn`、提示模板编码与依赖状态。
- 可用 `.\scripts\preflight-m1.ps1 -TaskId <任务ID>` 做任务前置校验。
