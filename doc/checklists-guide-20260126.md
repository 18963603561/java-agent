# 清单完成指引（001-agent-core-spec）

## 适用范围
- 适用清单：`specs/001-agent-core-spec/checklists/checklist.md`、`specs/001-agent-core-spec/checklists/quality.md`、`specs/001-agent-core-spec/checklists/requirements.md`

## 通用完成流程
1. 逐条阅读清单中的通过与不通过条件。
2. 按条目括号中的 `Spec`、`Plan`、`Task` 引用定位证据。
3. 如条目包含验证命令，直接执行并核对证据输出是否满足。
4. 满足条件：
   - 若允许修改清单文件，将 `- [ ]` 改为 `- [x]`，并在条目后补充证据摘要与日期。
   - 若清单文件冻结，在 `doc/notes/` 记录证据与结论。
5. 不满足条件：保持未勾选，并在 `doc/notes/` 记录缺口与补救建议。

## `checklist.md` 完成方式
- CHK001~CHK035：以规格与计划一致性审查为主。
  - 通过条目中的 `Spec §...`、`Plan §...`、`Task ...` 关键字定位对应章节。
  - 需要确认覆盖、一致、无遗漏时，要求跨 `spec.md`、`plan.md`、`tasks.md` 交叉验证。
- CHK036~CHK045：以性能与安全验证为主，需在实现完成后记录基线。
  - 先完成实现并保证 `mvn test` 通过。
  - 按 `spec.md` 的成功标准与阈值执行压测或批量调用，记录 `p95` 与成功率。
  - 建议在 `doc/notes/` 新建验收记录并附上命令、样本数、时间窗口、环境说明与结果。
- CHK046~CHK052：按 `quickstart.md` 的错误场景调用验证。
  - 使用对应 `curl` 命令触发错误码。
  - 校验 `HTTP` 状态码与 `ErrorResponse.code` 一致。
  - 检查日志包含 `tenantId`、`userId`、`traceId`、`requestId` 字段。

## `quality.md` 完成方式
- 每条已给出验证命令与证据输出。
- 逐条执行验证命令，核对输出是否满足证据要求。
- 满足条件后勾选 `[x]`，并在条目后追加证据摘要。

## `requirements.md` 完成方式
- 当前未通过项：
  - `No implementation details (languages, frameworks, APIs)`
  - `No implementation details leak into specification`
- 由于规格明确要求给出实现落点，除非修改规格删除实现细节，否则无法转为通过。
- 若规格冻结：
  - 保持未勾选。
  - 在 `doc/notes/` 记录已知例外与理由，并注明审批人或决策记录。

## 建议的证据记录模板
- 位置：`doc/notes/验收记录-YYYYMMDD.md`
- 内容要点：
  - 条目编号
  - 验证步骤与命令
  - 结果摘要与数据
  - 结论（通过或不通过）与时间