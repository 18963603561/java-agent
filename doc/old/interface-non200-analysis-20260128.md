# 接口非200原因分析与修复报告-20260128

## 背景
- 发现问题：`doc/service-query-test-report-20260128.md` 早期版本显示 `12` 个接口返回非 `200`
- 目标：分析原因、确认是否缺少请求数据、尝试修复并给出结果

## 非200清单与原因
### 原因一：`GET` 请求携带请求体导致客户端报错
- 现象：请求返回 `HTTP 0`，响应为：

```
Cannot send a content-body with this verb-type.
```
中文解释：客户端在 `GET` 请求中携带了请求体，`Invoke-WebRequest` 直接抛错，服务端未接收到请求。
- 影响接口：

```
/api/v1/tasks (GET)
/api/v1/tasks/{taskId} (GET)
/api/v1/budget/summary (GET)
/api/v1/events (GET)
/api/v1/timeline (GET)
/api/v1/timeline/steps (GET)
/api/v1/schedules (GET)
```

- 是否缺少请求数据：否
- 修复动作：调用 `GET` 接口时不传 `Body`，仅使用查询参数

### 原因二：调度接口返回 `404`
- 现象：请求返回 `HTTP 404`，响应为空
- 影响接口：

```
/api/v1/schedules (PUT)
/api/v1/schedules/{scheduleId}/pause (POST)
/api/v1/schedules/{scheduleId}/resume (POST)
/api/v1/schedules/{scheduleId}/cancel (POST)
/api/v1/schedules/{scheduleId} (DELETE)
```

- 是否缺少请求数据：是，使用的 `schedule-demo-1` 在当时存储中不存在
- 修复动作：
  - 通过 `SQL` 预置 `schedule-demo-1`
  - 或先调用 `POST /api/v1/schedules` 获取 `scheduleId` 再执行后续操作

## 修复验证
- 已补齐前置数据并修正 `GET` 调用方式
- 重新执行全部接口测试，全部返回 `200`
- 最新结果已写入 `doc/service-query-test-report-20260128.md`
