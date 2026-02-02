# 任务接口请求与报错记录

## 环境说明
- 目标接口：`http://localhost:8080/api/v1/tasks`
- 接口鉴权：请求头 `X-API-Key`，示例值 `demo-key`
- 租户标识：请求头 `X-Tenant-Id`，示例值 `tenant-demo-1`
- 会话标识：`session-demo-1`

## 服务启动情况
- 启动前端口检查：检测到 8080 端口已在监听
- 启动动作：未执行启动命令
curl.exe -sS -X POST "http://localhost:8080/api/v1/tasks" -H "Content-Type: application/json" -H "Accept: application/json" -H "X-API-Key: demo-key" -H "X-Tenant-Id: tenant-demo-1" -d '{"query": "我是谁", "idempotencyKey": "idem-task-who-1", "toolChoice": {"mode": "NONE"}, "sessionId": "session-demo-1"}'

curl.exe -sS -X POST "http://localhost:8080/api/v1/tasks" -H "Content-Type: application/json" -H "Accept: application/json" -H "X-API-Key: demo-key" -H "X-Tenant-Id: tenant-demo-1" -d '{"query": "帮我查询一下bob用户信息", "idempotencyKey": "idem-task-bob-1", "toolChoice": {"mode": "REQUIRED"}, "sessionId": "session-demo-1"}'

curl.exe -sS -X POST "http://localhost:8080/api/v1/tasks" -H "Content-Type: application/json" -H "Accept: application/json" -H "X-API-Key: demo-key" -H "X-Tenant-Id: tenant-demo-1" -d '{"query": "帮我查询一下h用户信息", "idempotencyKey": "idem-task-h-1", "toolChoice": {"mode": "SPECIFIED", "toolName": "search-user-by-name"}, "sessionId": "session-demo-1"}'

curl.exe -sS -X POST "http://localhost:8080/api/v1/tasks" -H "Content-Type: application/json" -H "Accept: application/json" -H "X-API-Key: demo-key" -H "X-Tenant-Id: tenant-demo-1" -d '{"query": "告诉我你是谁", "idempotencyKey": "idem-task-who-2", "toolChoice": {"mode": "AUTO"}, "sessionId": "session-demo-1"}'

## 请求命令与响应
### 请求一：不带工具，条件为“我是谁”
请求体：
```json
{"query": "我是谁", "idempotencyKey": "idem-task-who-1", "toolChoice": {"mode": "NONE"}, "sessionId": "session-demo-1"}
```
请求命令：
```bash
curl.exe -sS -X POST "http://localhost:8080/api/v1/tasks" -H "Content-Type: application/json" -H "Accept: application/json" -H "X-API-Key: demo-key" -H "X-Tenant-Id: tenant-demo-1" -d '{"query": "我是谁", "idempotencyKey": "idem-task-who-1", "toolChoice": {"mode": "NONE"}, "sessionId": "session-demo-1"}'
```
退出码：0
响应输出：
```json
{"code":"OK","message":"success","data":{"taskId":"d66c4f56-66cc-4438-9a5b-577e94522cba","workflowId":"c6687156-243c-46e4-beba-084dd3a7ced3","status":"SUBMITTED"},"traceId":null,"requestId":null}
```

### 请求二：带工具，条件为“帮我查询一下bob用户信息”
请求体：
```json
{"query": "帮我查询一下bob用户信息", "idempotencyKey": "idem-task-bob-1", "toolChoice": {"mode": "REQUIRED"}, "sessionId": "session-demo-1"}
```
请求命令：
```bash
curl.exe -sS -X POST "http://localhost:8080/api/v1/tasks" -H "Content-Type: application/json" -H "Accept: application/json" -H "X-API-Key: demo-key" -H "X-Tenant-Id: tenant-demo-1" -d '{"query": "帮我查询一下bob用户信息", "idempotencyKey": "idem-task-bob-1", "toolChoice": {"mode": "REQUIRED"}, "sessionId": "session-demo-1"}'
```
退出码：0
响应输出：
```json
{"code":"OK","message":"success","data":{"taskId":"d6241a73-1e30-45da-bd64-d00e2dd727dc","workflowId":"54148e88-c04d-4430-9ecb-c0157703c516","status":"SUBMITTED"},"traceId":null,"requestId":null}
```

### 请求三：指定工具，条件为“帮我查询一下h用户信息”
请求体：
```json
{"query": "帮我查询一下h用户信息", "idempotencyKey": "idem-task-h-1", "toolChoice": {"mode": "SPECIFIED", "toolName": "search-user-by-name"}, "sessionId": "session-demo-1"}
```
请求命令：
```bash
curl.exe -sS -X POST "http://localhost:8080/api/v1/tasks" -H "Content-Type: application/json" -H "Accept: application/json" -H "X-API-Key: demo-key" -H "X-Tenant-Id: tenant-demo-1" -d '{"query": "帮我查询一下h用户信息", "idempotencyKey": "idem-task-h-1", "toolChoice": {"mode": "SPECIFIED", "toolName": "search-user-by-name"}, "sessionId": "session-demo-1"}'
```
退出码：0
响应输出：
```json
{"code":"OK","message":"success","data":{"taskId":"5e2f3804-5af3-4268-bf9c-49ef259d6a12","workflowId":"0e9b688e-e8cc-4c5e-a55f-19798e66e17d","status":"SUBMITTED"},"traceId":null,"requestId":null}
```

### 请求四：查询条件为“告诉我你是谁”
请求体：
```json
{"query": "告诉我你是谁", "idempotencyKey": "idem-task-who-2", "toolChoice": {"mode": "AUTO"}, "sessionId": "session-demo-1"}
```
请求命令：
```bash
curl.exe -sS -X POST "http://localhost:8080/api/v1/tasks" -H "Content-Type: application/json" -H "Accept: application/json" -H "X-API-Key: demo-key" -H "X-Tenant-Id: tenant-demo-1" -d '{"query": "告诉我你是谁", "idempotencyKey": "idem-task-who-2", "toolChoice": {"mode": "AUTO"}, "sessionId": "session-demo-1"}'
```
退出码：0
响应输出：
```json
{"code":"OK","message":"success","data":{"taskId":"ac257227-f2fb-4cb6-8d43-80b877e86bbc","workflowId":"18850c0d-90f9-4ce6-93a7-447656d0ac94","status":"SUBMITTED"},"traceId":null,"requestId":null}
```

## 报错记录
本次请求未出现错误输出。
