# 任务提交接口测试用例集

## 说明
本文档用于验证任务提交接口的关键路径与异常路径，覆盖多优先级场景。
每个用例包含请求参数、配置片段、完整请求命令与测试流程。

### 用例
用例编号：
```
P0-01
```
优先级：
```
P0
```
用例描述：
同步模式基础成功流程，验证请求成功并返回最终结果。
请求地址：
```
http://localhost:8080/api/v1/tasks
```
请求方法：
```
POST
```
请求头：
```
X-API-Key: demo-key
X-Tenant-Id: tenant-demo-1
Content-Type: application/json
```
请求参数：
```json
{
  "query": "帮我查询一下包含h的用户信息，或帮我查询一下包含bob的信息",
  "executionMode": "SYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "",
  "sessionId": "session-demo-0131"
}
```
配置文件：
```
application.yml
```
配置片段：
```yaml
server:
  port: 8080
auth:
  api-keys:
    demo-key:
      user-id: demo-user
      roles:
        - ROLE_USER
tenant:
  whitelist-paths:
    - /actuator/health
    - /actuator/info
agent:
  summary:
    enable: false
    raw-output:
      enable: true
  idempotency:
    redis-enabled: true
    ttl-seconds: 86400
  task:
    sync:
      wait-timeout-ms: 30000
      max-wait-timeout-ms: 60000
      max-concurrency: 20
```
请求命令：
```bash
curl -X POST http://localhost:8080/api/v1/tasks \
     -H "X-API-Key: demo-key" \
     -H "X-Tenant-Id: tenant-demo-1" \
     -H "Content-Type: application/json" \
     -d '{
  "query": "帮我查询一下包含h的用户信息，或帮我查询一下包含bob的信息",
  "executionMode": "SYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "",
  "sessionId": "session-demo-0131"
}'
```
测试流程：
1. 按配置片段启动服务。
2. 发送同步请求。
3. 等待响应返回。
预期结果：
1. 响应码为 200。
2. 响应体包含任务状态与结果信息。

### 用例
用例编号：
```
P0-02
```
优先级：
```
P0
```
用例描述：
异步模式提交成功，验证返回受理结果。
请求地址：
```
http://localhost:8080/api/v1/tasks
```
请求方法：
```
POST
```
请求头：
```
X-API-Key: demo-key
X-Tenant-Id: tenant-demo-1
Content-Type: application/json
```
请求参数：
```json
{
  "query": "帮我查询一下包含h的用户信息，或帮我查询一下包含bob的信息",
  "executionMode": "ASYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "",
  "sessionId": "session-demo-0131"
}
```
配置文件：
```
application.yml
```
配置片段：
```yaml
server:
  port: 8080
auth:
  api-keys:
    demo-key:
      user-id: demo-user
      roles:
        - ROLE_USER
tenant:
  whitelist-paths:
    - /actuator/health
    - /actuator/info
agent:
  summary:
    enable: false
    raw-output:
      enable: true
  idempotency:
    redis-enabled: true
    ttl-seconds: 86400
  task:
    sync:
      wait-timeout-ms: 30000
      max-wait-timeout-ms: 60000
      max-concurrency: 20
```
请求命令：
```bash
curl -X POST http://localhost:8080/api/v1/tasks \
     -H "X-API-Key: demo-key" \
     -H "X-Tenant-Id: tenant-demo-1" \
     -H "Content-Type: application/json" \
     -d '{
  "query": "帮我查询一下包含h的用户信息，或帮我查询一下包含bob的信息",
  "executionMode": "ASYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "",
  "sessionId": "session-demo-0131"
}'
```
测试流程：
1. 按配置片段启动服务。
2. 发送异步请求。
3. 记录返回的任务标识。
预期结果：
1. 响应码为 202。
2. 响应体包含任务标识与工作流标识。

### 用例
用例编号：
```
P0-03
```
优先级：
```
P0
```
用例描述：
同步等待超时降级为受理结果，验证超时处理路径。
请求地址：
```
http://localhost:8080/api/v1/tasks
```
请求方法：
```
POST
```
请求头：
```
X-API-Key: demo-key
X-Tenant-Id: tenant-demo-1
Content-Type: application/json
```
请求参数：
```json
{
  "query": "请执行耗时检索以触发同步超时",
  "executionMode": "SYNC",
  "waitTimeoutMs": 1000,
  "idempotencyKey": "timeout-case",
  "sessionId": "session-timeout-01"
}
```
配置文件：
```
application.yml
```
配置片段：
```yaml
server:
  port: 8080
auth:
  api-keys:
    demo-key:
      user-id: demo-user
      roles:
        - ROLE_USER
tenant:
  whitelist-paths:
    - /actuator/health
    - /actuator/info
agent:
  summary:
    enable: false
    raw-output:
      enable: true
  idempotency:
    redis-enabled: true
    ttl-seconds: 86400
  task:
    sync:
      wait-timeout-ms: 1000
      max-wait-timeout-ms: 1000
      max-concurrency: 20
```
请求命令：
```bash
curl -X POST http://localhost:8080/api/v1/tasks \
     -H "X-API-Key: demo-key" \
     -H "X-Tenant-Id: tenant-demo-1" \
     -H "Content-Type: application/json" \
     -d '{
  "query": "请执行耗时检索以触发同步超时",
  "executionMode": "SYNC",
  "waitTimeoutMs": 1000,
  "idempotencyKey": "timeout-case",
  "sessionId": "session-timeout-01"
}'
```
测试流程：
1. 按配置片段启动服务并准备耗时场景。
2. 发送同步请求。
3. 确认在等待超时后返回。
预期结果：
1. 响应码为 202。
2. 响应体状态为运行中或受理状态。

### 用例
用例编号：
```
P0-04
```
优先级：
```
P0
```
用例描述：
缺少接口密钥头，验证鉴权失败。
请求地址：
```
http://localhost:8080/api/v1/tasks
```
请求方法：
```
POST
```
请求头：
```
X-Tenant-Id: tenant-demo-1
Content-Type: application/json
```
请求参数：
```json
{
  "query": "帮我查询一下包含h的用户信息，或帮我查询一下包含bob的信息",
  "executionMode": "SYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "",
  "sessionId": "session-demo-0131"
}
```
配置文件：
```
application.yml
```
配置片段：
```yaml
server:
  port: 8080
auth:
  api-keys:
    demo-key:
      user-id: demo-user
      roles:
        - ROLE_USER
tenant:
  whitelist-paths:
    - /actuator/health
    - /actuator/info
agent:
  summary:
    enable: false
    raw-output:
      enable: true
  idempotency:
    redis-enabled: true
    ttl-seconds: 86400
  task:
    sync:
      wait-timeout-ms: 30000
      max-wait-timeout-ms: 60000
      max-concurrency: 20
```
请求命令：
```bash
curl -X POST http://localhost:8080/api/v1/tasks \
     -H "X-Tenant-Id: tenant-demo-1" \
     -H "Content-Type: application/json" \
     -d '{
  "query": "帮我查询一下包含h的用户信息，或帮我查询一下包含bob的信息",
  "executionMode": "SYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "",
  "sessionId": "session-demo-0131"
}'
```
测试流程：
1. 按配置片段启动服务。
2. 不携带接口密钥头发送请求。
预期结果：
1. 响应码为 401。
2. 响应体包含鉴权失败信息。

### 用例
用例编号：
```
P0-05
```
优先级：
```
P0
```
用例描述：
接口密钥无效，验证鉴权失败。
请求地址：
```
http://localhost:8080/api/v1/tasks
```
请求方法：
```
POST
```
请求头：
```
X-API-Key: bad-key
X-Tenant-Id: tenant-demo-1
Content-Type: application/json
```
请求参数：
```json
{
  "query": "帮我查询一下包含h的用户信息，或帮我查询一下包含bob的信息",
  "executionMode": "SYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "",
  "sessionId": "session-demo-0131"
}
```
配置文件：
```
application.yml
```
配置片段：
```yaml
server:
  port: 8080
auth:
  api-keys:
    demo-key:
      user-id: demo-user
      roles:
        - ROLE_USER
tenant:
  whitelist-paths:
    - /actuator/health
    - /actuator/info
agent:
  summary:
    enable: false
    raw-output:
      enable: true
  idempotency:
    redis-enabled: true
    ttl-seconds: 86400
  task:
    sync:
      wait-timeout-ms: 30000
      max-wait-timeout-ms: 60000
      max-concurrency: 20
```
请求命令：
```bash
curl -X POST http://localhost:8080/api/v1/tasks \
     -H "X-API-Key: bad-key" \
     -H "X-Tenant-Id: tenant-demo-1" \
     -H "Content-Type: application/json" \
     -d '{
  "query": "帮我查询一下包含h的用户信息，或帮我查询一下包含bob的信息",
  "executionMode": "SYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "",
  "sessionId": "session-demo-0131"
}'
```
测试流程：
1. 按配置片段启动服务。
2. 携带无效接口密钥发送请求。
预期结果：
1. 响应码为 401。
2. 响应体包含鉴权失败信息。

### 用例
用例编号：
```
P0-06
```
优先级：
```
P0
```
用例描述：
缺少租户标识头，验证租户校验失败。
请求地址：
```
http://localhost:8080/api/v1/tasks
```
请求方法：
```
POST
```
请求头：
```
X-API-Key: demo-key
Content-Type: application/json
```
请求参数：
```json
{
  "query": "帮我查询一下包含h的用户信息，或帮我查询一下包含bob的信息",
  "executionMode": "SYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "",
  "sessionId": "session-demo-0131"
}
```
配置文件：
```
application.yml
```
配置片段：
```yaml
server:
  port: 8080
auth:
  api-keys:
    demo-key:
      user-id: demo-user
      roles:
        - ROLE_USER
tenant:
  whitelist-paths:
    - /actuator/health
    - /actuator/info
agent:
  summary:
    enable: false
    raw-output:
      enable: true
  idempotency:
    redis-enabled: true
    ttl-seconds: 86400
  task:
    sync:
      wait-timeout-ms: 30000
      max-wait-timeout-ms: 60000
      max-concurrency: 20
```
请求命令：
```bash
curl -X POST http://localhost:8080/api/v1/tasks \
     -H "X-API-Key: demo-key" \
     -H "Content-Type: application/json" \
     -d '{
  "query": "帮我查询一下包含h的用户信息，或帮我查询一下包含bob的信息",
  "executionMode": "SYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "",
  "sessionId": "session-demo-0131"
}'
```
测试流程：
1. 按配置片段启动服务。
2. 不携带租户标识头发送请求。
预期结果：
1. 响应码为 400。
2. 响应体包含租户缺失信息。

### 用例
用例编号：
```
P0-07
```
优先级：
```
P0
```
用例描述：
请求头内容类型错误，验证格式校验失败。
请求地址：
```
http://localhost:8080/api/v1/tasks
```
请求方法：
```
POST
```
请求头：
```
X-API-Key: demo-key
X-Tenant-Id: tenant-demo-1
Content-Type: text/plain
```
请求参数：
```json
{
  "query": "帮我查询一下包含h的用户信息，或帮我查询一下包含bob的信息",
  "executionMode": "SYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "",
  "sessionId": "session-demo-0131"
}
```
配置文件：
```
application.yml
```
配置片段：
```yaml
server:
  port: 8080
auth:
  api-keys:
    demo-key:
      user-id: demo-user
      roles:
        - ROLE_USER
tenant:
  whitelist-paths:
    - /actuator/health
    - /actuator/info
agent:
  summary:
    enable: false
    raw-output:
      enable: true
  idempotency:
    redis-enabled: true
    ttl-seconds: 86400
  task:
    sync:
      wait-timeout-ms: 30000
      max-wait-timeout-ms: 60000
      max-concurrency: 20
```
请求命令：
```bash
curl -X POST http://localhost:8080/api/v1/tasks \
     -H "X-API-Key: demo-key" \
     -H "X-Tenant-Id: tenant-demo-1" \
     -H "Content-Type: text/plain" \
     -d '{
  "query": "帮我查询一下包含h的用户信息，或帮我查询一下包含bob的信息",
  "executionMode": "SYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "",
  "sessionId": "session-demo-0131"
}'
```
测试流程：
1. 按配置片段启动服务。
2. 使用错误的内容类型发送请求。
预期结果：
1. 响应码为 415。
2. 响应体提示不支持的内容类型。

### 用例
用例编号：
```
P0-08
```
优先级：
```
P0
```
用例描述：
请求体为空，验证参数校验失败。
请求地址：
```
http://localhost:8080/api/v1/tasks
```
请求方法：
```
POST
```
请求头：
```
X-API-Key: demo-key
X-Tenant-Id: tenant-demo-1
Content-Type: application/json
```
请求参数：
```
无
```
配置文件：
```
application.yml
```
配置片段：
```yaml
server:
  port: 8080
auth:
  api-keys:
    demo-key:
      user-id: demo-user
      roles:
        - ROLE_USER
tenant:
  whitelist-paths:
    - /actuator/health
    - /actuator/info
agent:
  summary:
    enable: false
    raw-output:
      enable: true
  idempotency:
    redis-enabled: true
    ttl-seconds: 86400
  task:
    sync:
      wait-timeout-ms: 30000
      max-wait-timeout-ms: 60000
      max-concurrency: 20
```
请求命令：
```bash
curl -X POST http://localhost:8080/api/v1/tasks \
     -H "X-API-Key: demo-key" \
     -H "X-Tenant-Id: tenant-demo-1" \
     -H "Content-Type: application/json"
```
测试流程：
1. 按配置片段启动服务。
2. 发送无请求体的提交请求。
预期结果：
1. 响应码为 400。
2. 响应体提示请求体缺失或参数无效。

### 用例
用例编号：
```
P0-09
```
优先级：
```
P0
```
用例描述：
查询内容为空字符串，验证必填字段校验。
请求地址：
```
http://localhost:8080/api/v1/tasks
```
请求方法：
```
POST
```
请求头：
```
X-API-Key: demo-key
X-Tenant-Id: tenant-demo-1
Content-Type: application/json
```
请求参数：
```json
{
  "query": "",
  "executionMode": "SYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "",
  "sessionId": "session-demo-0131"
}
```
配置文件：
```
application.yml
```
配置片段：
```yaml
server:
  port: 8080
auth:
  api-keys:
    demo-key:
      user-id: demo-user
      roles:
        - ROLE_USER
tenant:
  whitelist-paths:
    - /actuator/health
    - /actuator/info
agent:
  summary:
    enable: false
    raw-output:
      enable: true
  idempotency:
    redis-enabled: true
    ttl-seconds: 86400
  task:
    sync:
      wait-timeout-ms: 30000
      max-wait-timeout-ms: 60000
      max-concurrency: 20
```
请求命令：
```bash
curl -X POST http://localhost:8080/api/v1/tasks \
     -H "X-API-Key: demo-key" \
     -H "X-Tenant-Id: tenant-demo-1" \
     -H "Content-Type: application/json" \
     -d '{
  "query": "",
  "executionMode": "SYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "",
  "sessionId": "session-demo-0131"
}'
```
测试流程：
1. 按配置片段启动服务。
2. 发送查询内容为空的请求。
预期结果：
1. 响应码为 400。
2. 响应体提示查询内容不能为空。

### 用例
用例编号：
```
P0-10
```
优先级：
```
P0
```
用例描述：
幂等键重复提交，验证任务复用。
请求地址：
```
http://localhost:8080/api/v1/tasks
```
请求方法：
```
POST
```
请求头：
```
X-API-Key: demo-key
X-Tenant-Id: tenant-demo-1
Content-Type: application/json
```
请求参数：
```json
{
  "query": "帮我查询一下包含h的用户信息，或帮我查询一下包含bob的信息",
  "executionMode": "ASYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "idem-001",
  "sessionId": "session-demo-0131"
}
```
配置文件：
```
application.yml
```
配置片段：
```yaml
server:
  port: 8080
auth:
  api-keys:
    demo-key:
      user-id: demo-user
      roles:
        - ROLE_USER
tenant:
  whitelist-paths:
    - /actuator/health
    - /actuator/info
agent:
  summary:
    enable: false
    raw-output:
      enable: true
  idempotency:
    redis-enabled: true
    ttl-seconds: 86400
  task:
    sync:
      wait-timeout-ms: 30000
      max-wait-timeout-ms: 60000
      max-concurrency: 20
```
请求命令：
```bash
curl -X POST http://localhost:8080/api/v1/tasks \
     -H "X-API-Key: demo-key" \
     -H "X-Tenant-Id: tenant-demo-1" \
     -H "Content-Type: application/json" \
     -d '{
  "query": "帮我查询一下包含h的用户信息，或帮我查询一下包含bob的信息",
  "executionMode": "ASYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "idem-001",
  "sessionId": "session-demo-0131"
}'
```
测试流程：
1. 按配置片段启动服务。
2. 发送第一次请求并记录任务标识。
3. 使用相同幂等键再次发送请求。
预期结果：
1. 两次响应码均为 202。
2. 第二次响应返回同一任务标识。

### 用例
用例编号：
```
P1-01
```
优先级：
```
P1
```
用例描述：
同步等待时间为负数时使用默认值。
请求地址：
```
http://localhost:8080/api/v1/tasks
```
请求方法：
```
POST
```
请求头：
```
X-API-Key: demo-key
X-Tenant-Id: tenant-demo-1
Content-Type: application/json
```
请求参数：
```json
{
  "query": "查询同步默认等待时间",
  "executionMode": "SYNC",
  "waitTimeoutMs": -1,
  "idempotencyKey": "",
  "sessionId": "session-sync-01"
}
```
配置文件：
```
application.yml
```
配置片段：
```yaml
server:
  port: 8080
auth:
  api-keys:
    demo-key:
      user-id: demo-user
      roles:
        - ROLE_USER
tenant:
  whitelist-paths:
    - /actuator/health
    - /actuator/info
agent:
  summary:
    enable: false
    raw-output:
      enable: true
  idempotency:
    redis-enabled: true
    ttl-seconds: 86400
  task:
    sync:
      wait-timeout-ms: 30000
      max-wait-timeout-ms: 60000
      max-concurrency: 20
```
请求命令：
```bash
curl -X POST http://localhost:8080/api/v1/tasks \
     -H "X-API-Key: demo-key" \
     -H "X-Tenant-Id: tenant-demo-1" \
     -H "Content-Type: application/json" \
     -d '{
  "query": "查询同步默认等待时间",
  "executionMode": "SYNC",
  "waitTimeoutMs": -1,
  "idempotencyKey": "",
  "sessionId": "session-sync-01"
}'
```
测试流程：
1. 按配置片段启动服务。
2. 发送同步请求并设置负数等待时间。
预期结果：
1. 响应码为 200 或 202。
2. 服务使用默认等待时间处理。

### 用例
用例编号：
```
P1-02
```
优先级：
```
P1
```
用例描述：
同步等待时间超过上限时被裁剪。
请求地址：
```
http://localhost:8080/api/v1/tasks
```
请求方法：
```
POST
```
请求头：
```
X-API-Key: demo-key
X-Tenant-Id: tenant-demo-1
Content-Type: application/json
```
请求参数：
```json
{
  "query": "查询超长等待时间裁剪",
  "executionMode": "SYNC",
  "waitTimeoutMs": 120000,
  "idempotencyKey": "",
  "sessionId": "session-sync-02"
}
```
配置文件：
```
application.yml
```
配置片段：
```yaml
server:
  port: 8080
auth:
  api-keys:
    demo-key:
      user-id: demo-user
      roles:
        - ROLE_USER
tenant:
  whitelist-paths:
    - /actuator/health
    - /actuator/info
agent:
  summary:
    enable: false
    raw-output:
      enable: true
  idempotency:
    redis-enabled: true
    ttl-seconds: 86400
  task:
    sync:
      wait-timeout-ms: 5000
      max-wait-timeout-ms: 5000
      max-concurrency: 20
```
请求命令：
```bash
curl -X POST http://localhost:8080/api/v1/tasks \
     -H "X-API-Key: demo-key" \
     -H "X-Tenant-Id: tenant-demo-1" \
     -H "Content-Type: application/json" \
     -d '{
  "query": "查询超长等待时间裁剪",
  "executionMode": "SYNC",
  "waitTimeoutMs": 120000,
  "idempotencyKey": "",
  "sessionId": "session-sync-02"
}'
```
测试流程：
1. 按配置片段启动服务。
2. 发送同步请求并设置超大等待时间。
预期结果：
1. 响应码为 200 或 202。
2. 等待时间不超过配置上限。

### 用例
用例编号：
```
P1-03
```
优先级：
```
P1
```
用例描述：
同步并发超过上限时降级为受理结果。
请求地址：
```
http://localhost:8080/api/v1/tasks
```
请求方法：
```
POST
```
请求头：
```
X-API-Key: demo-key
X-Tenant-Id: tenant-demo-1
Content-Type: application/json
```
请求参数：
```json
{
  "query": "查询并发限制验证",
  "executionMode": "SYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "",
  "sessionId": "session-sync-03"
}
```
配置文件：
```
application.yml
```
配置片段：
```yaml
server:
  port: 8080
auth:
  api-keys:
    demo-key:
      user-id: demo-user
      roles:
        - ROLE_USER
tenant:
  whitelist-paths:
    - /actuator/health
    - /actuator/info
agent:
  summary:
    enable: false
    raw-output:
      enable: true
  idempotency:
    redis-enabled: true
    ttl-seconds: 86400
  task:
    sync:
      wait-timeout-ms: 30000
      max-wait-timeout-ms: 60000
      max-concurrency: 1
```
请求命令：
```bash
curl -X POST http://localhost:8080/api/v1/tasks \
     -H "X-API-Key: demo-key" \
     -H "X-Tenant-Id: tenant-demo-1" \
     -H "Content-Type: application/json" \
     -d '{
  "query": "查询并发限制验证",
  "executionMode": "SYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "",
  "sessionId": "session-sync-03"
}'
```
测试流程：
1. 按配置片段启动服务并将同步并发上限设为 1。
2. 并行发送两次同步请求。
预期结果：
1. 其中一次响应码为 202。
2. 被降级的请求返回受理状态。

### 用例
用例编号：
```
P1-04
```
优先级：
```
P1
```
用例描述：
执行模式为非法值时校验失败。
请求地址：
```
http://localhost:8080/api/v1/tasks
```
请求方法：
```
POST
```
请求头：
```
X-API-Key: demo-key
X-Tenant-Id: tenant-demo-1
Content-Type: application/json
```
请求参数：
```json
{
  "query": "查询非法执行模式",
  "executionMode": "FAST",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "",
  "sessionId": "session-invalid-01"
}
```
配置文件：
```
application.yml
```
配置片段：
```yaml
server:
  port: 8080
auth:
  api-keys:
    demo-key:
      user-id: demo-user
      roles:
        - ROLE_USER
tenant:
  whitelist-paths:
    - /actuator/health
    - /actuator/info
agent:
  summary:
    enable: false
    raw-output:
      enable: true
  idempotency:
    redis-enabled: true
    ttl-seconds: 86400
  task:
    sync:
      wait-timeout-ms: 30000
      max-wait-timeout-ms: 60000
      max-concurrency: 20
```
请求命令：
```bash
curl -X POST http://localhost:8080/api/v1/tasks \
     -H "X-API-Key: demo-key" \
     -H "X-Tenant-Id: tenant-demo-1" \
     -H "Content-Type: application/json" \
     -d '{
  "query": "查询非法执行模式",
  "executionMode": "FAST",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "",
  "sessionId": "session-invalid-01"
}'
```
测试流程：
1. 按配置片段启动服务。
2. 发送执行模式为非法值的请求。
预期结果：
1. 响应码为 400。
2. 响应体提示枚举值不合法。

### 用例
用例编号：
```
P1-05
```
优先级：
```
P1
```
用例描述：
等待时间类型错误时校验失败。
请求地址：
```
http://localhost:8080/api/v1/tasks
```
请求方法：
```
POST
```
请求头：
```
X-API-Key: demo-key
X-Tenant-Id: tenant-demo-1
Content-Type: application/json
```
请求参数：
```json
{
  "query": "查询等待时间类型错误",
  "executionMode": "SYNC",
  "waitTimeoutMs": "abc",
  "idempotencyKey": "",
  "sessionId": "session-invalid-02"
}
```
配置文件：
```
application.yml
```
配置片段：
```yaml
server:
  port: 8080
auth:
  api-keys:
    demo-key:
      user-id: demo-user
      roles:
        - ROLE_USER
tenant:
  whitelist-paths:
    - /actuator/health
    - /actuator/info
agent:
  summary:
    enable: false
    raw-output:
      enable: true
  idempotency:
    redis-enabled: true
    ttl-seconds: 86400
  task:
    sync:
      wait-timeout-ms: 30000
      max-wait-timeout-ms: 60000
      max-concurrency: 20
```
请求命令：
```bash
curl -X POST http://localhost:8080/api/v1/tasks \
     -H "X-API-Key: demo-key" \
     -H "X-Tenant-Id: tenant-demo-1" \
     -H "Content-Type: application/json" \
     -d '{
  "query": "查询等待时间类型错误",
  "executionMode": "SYNC",
  "waitTimeoutMs": "abc",
  "idempotencyKey": "",
  "sessionId": "session-invalid-02"
}'
```
测试流程：
1. 按配置片段启动服务。
2. 发送等待时间为字符串的请求。
预期结果：
1. 响应码为 400。
2. 响应体提示参数类型错误。

### 用例
用例编号：
```
P1-06
```
优先级：
```
P1
```
用例描述：
租户范围不匹配时拒绝访问。
请求地址：
```
http://localhost:8080/api/v1/tasks
```
请求方法：
```
POST
```
请求头：
```
X-API-Key: demo-key
X-Tenant-Id: tenant-demo-1
Content-Type: application/json
```
请求参数：
```json
{
  "query": "帮我查询一下包含h的用户信息，或帮我查询一下包含bob的信息",
  "executionMode": "SYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "",
  "sessionId": "session-demo-0131"
}
```
配置文件：
```
application.yml
```
配置片段：
```yaml
server:
  port: 8080
auth:
  api-keys:
    demo-key:
      user-id: demo-user
      roles:
        - ROLE_USER
      tenant-scope: tenant-prod-1
tenant:
  whitelist-paths:
    - /actuator/health
    - /actuator/info
agent:
  summary:
    enable: false
    raw-output:
      enable: true
  idempotency:
    redis-enabled: true
    ttl-seconds: 86400
  task:
    sync:
      wait-timeout-ms: 30000
      max-wait-timeout-ms: 60000
      max-concurrency: 20
```
请求命令：
```bash
curl -X POST http://localhost:8080/api/v1/tasks \
     -H "X-API-Key: demo-key" \
     -H "X-Tenant-Id: tenant-demo-1" \
     -H "Content-Type: application/json" \
     -d '{
  "query": "帮我查询一下包含h的用户信息，或帮我查询一下包含bob的信息",
  "executionMode": "SYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "",
  "sessionId": "session-demo-0131"
}'
```
测试流程：
1. 按配置片段启动服务并设置租户范围为其他租户。
2. 使用不匹配的租户标识发送请求。
预期结果：
1. 响应码为 403。
2. 响应体提示租户范围不允许。

### 用例
用例编号：
```
P1-07
```
优先级：
```
P1
```
用例描述：
可信上游模式开启但缺少令牌且未携带接口密钥。
请求地址：
```
http://localhost:8080/api/v1/tasks
```
请求方法：
```
POST
```
请求头：
```
X-Tenant-Id: tenant-demo-1
Content-Type: application/json
```
请求参数：
```json
{
  "query": "帮我查询一下包含h的用户信息，或帮我查询一下包含bob的信息",
  "executionMode": "SYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "",
  "sessionId": "session-demo-0131"
}
```
配置文件：
```
application.yml
```
配置片段：
```yaml
server:
  port: 8080
auth:
  api-keys:
    demo-key:
      user-id: demo-user
      roles:
        - ROLE_USER
  trusted-upstream:
    enabled: true
    token: change-me
    token-header: X-Trusted-Token
tenant:
  whitelist-paths:
    - /actuator/health
    - /actuator/info
agent:
  summary:
    enable: false
    raw-output:
      enable: true
  idempotency:
    redis-enabled: true
    ttl-seconds: 86400
  task:
    sync:
      wait-timeout-ms: 30000
      max-wait-timeout-ms: 60000
      max-concurrency: 20
```
请求命令：
```bash
curl -X POST http://localhost:8080/api/v1/tasks \
     -H "X-Tenant-Id: tenant-demo-1" \
     -H "Content-Type: application/json" \
     -d '{
  "query": "帮我查询一下包含h的用户信息，或帮我查询一下包含bob的信息",
  "executionMode": "SYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "",
  "sessionId": "session-demo-0131"
}'
```
测试流程：
1. 按配置片段启动服务并启用可信上游。
2. 不携带接口密钥与可信令牌发送请求。
预期结果：
1. 响应码为 401。
2. 响应体提示鉴权失败。

### 用例
用例编号：
```
P1-08
```
优先级：
```
P1
```
用例描述：
上下文禁用工具调用，验证工具禁用路径。
请求地址：
```
http://localhost:8080/api/v1/tasks
```
请求方法：
```
POST
```
请求头：
```
X-API-Key: demo-key
X-Tenant-Id: tenant-demo-1
Content-Type: application/json
```
请求参数：
```json
{
  "query": "请不要使用工具，只回答是否存在用户",
  "executionMode": "SYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "",
  "sessionId": "session-disable-tools",
  "context": {
    "disableTools": true
  }
}
```
配置文件：
```
application.yml
```
配置片段：
```yaml
server:
  port: 8080
auth:
  api-keys:
    demo-key:
      user-id: demo-user
      roles:
        - ROLE_USER
tenant:
  whitelist-paths:
    - /actuator/health
    - /actuator/info
agent:
  summary:
    enable: false
    raw-output:
      enable: true
  idempotency:
    redis-enabled: true
    ttl-seconds: 86400
  task:
    sync:
      wait-timeout-ms: 30000
      max-wait-timeout-ms: 60000
      max-concurrency: 20
```
请求命令：
```bash
curl -X POST http://localhost:8080/api/v1/tasks \
     -H "X-API-Key: demo-key" \
     -H "X-Tenant-Id: tenant-demo-1" \
     -H "Content-Type: application/json" \
     -d '{
  "query": "请不要使用工具，只回答是否存在用户",
  "executionMode": "SYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "",
  "sessionId": "session-disable-tools",
  "context": {
    "disableTools": true
  }
}'
```
测试流程：
1. 按配置片段启动服务。
2. 发送带禁用工具上下文的请求。
预期结果：
1. 响应码为 200 或 202。
2. 响应体中无工具调用结果摘要。

### 用例
用例编号：
```
P1-09
```
优先级：
```
P1
```
用例描述：
工具选择模式为禁用，验证不触发工具调用。
请求地址：
```
http://localhost:8080/api/v1/tasks
```
请求方法：
```
POST
```
请求头：
```
X-API-Key: demo-key
X-Tenant-Id: tenant-demo-1
Content-Type: application/json
```
请求参数：
```json
{
  "query": "请直接回答，不要调用工具",
  "executionMode": "SYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "",
  "sessionId": "session-tool-none",
  "toolChoice": {
    "mode": "NONE"
  }
}
```
配置文件：
```
application.yml
```
配置片段：
```yaml
server:
  port: 8080
auth:
  api-keys:
    demo-key:
      user-id: demo-user
      roles:
        - ROLE_USER
tenant:
  whitelist-paths:
    - /actuator/health
    - /actuator/info
agent:
  summary:
    enable: false
    raw-output:
      enable: true
  idempotency:
    redis-enabled: true
    ttl-seconds: 86400
  task:
    sync:
      wait-timeout-ms: 30000
      max-wait-timeout-ms: 60000
      max-concurrency: 20
```
请求命令：
```bash
curl -X POST http://localhost:8080/api/v1/tasks \
     -H "X-API-Key: demo-key" \
     -H "X-Tenant-Id: tenant-demo-1" \
     -H "Content-Type: application/json" \
     -d '{
  "query": "请直接回答，不要调用工具",
  "executionMode": "SYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "",
  "sessionId": "session-tool-none",
  "toolChoice": {
    "mode": "NONE"
  }
}'
```
测试流程：
1. 按配置片段启动服务。
2. 发送工具选择模式为禁用的请求。
预期结果：
1. 响应码为 200 或 202。
2. 响应体中无工具调用信息。

### 用例
用例编号：
```
P1-10
```
优先级：
```
P1
```
用例描述：
幂等键包含前后空格时仍命中同一任务。
请求地址：
```
http://localhost:8080/api/v1/tasks
```
请求方法：
```
POST
```
请求头：
```
X-API-Key: demo-key
X-Tenant-Id: tenant-demo-1
Content-Type: application/json
```
请求参数：
```json
{
  "query": "查询幂等键空格处理",
  "executionMode": "ASYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "  idem-002  ",
  "sessionId": "session-idem-02"
}
```
配置文件：
```
application.yml
```
配置片段：
```yaml
server:
  port: 8080
auth:
  api-keys:
    demo-key:
      user-id: demo-user
      roles:
        - ROLE_USER
tenant:
  whitelist-paths:
    - /actuator/health
    - /actuator/info
agent:
  summary:
    enable: false
    raw-output:
      enable: true
  idempotency:
    redis-enabled: true
    ttl-seconds: 86400
  task:
    sync:
      wait-timeout-ms: 30000
      max-wait-timeout-ms: 60000
      max-concurrency: 20
```
请求命令：
```bash
curl -X POST http://localhost:8080/api/v1/tasks \
     -H "X-API-Key: demo-key" \
     -H "X-Tenant-Id: tenant-demo-1" \
     -H "Content-Type: application/json" \
     -d '{
  "query": "查询幂等键空格处理",
  "executionMode": "ASYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "  idem-002  ",
  "sessionId": "session-idem-02"
}'
```
测试流程：
1. 按配置片段启动服务。
2. 先使用带空格的幂等键提交请求。
3. 再使用去空格的幂等键重复提交。
预期结果：
1. 两次响应码均为 202。
2. 两次任务标识一致。

### 用例
用例编号：
```
P2-01
```
优先级：
```
P2
```
用例描述：
查询内容包含表情与全角标点，验证字符兼容性。
请求地址：
```
http://localhost:8080/api/v1/tasks
```
请求方法：
```
POST
```
请求头：
```
X-API-Key: demo-key
X-Tenant-Id: tenant-demo-1
Content-Type: application/json
```
请求参数：
```json
{
  "query": "帮我查询包含😀的用户，并统计数量。",
  "executionMode": "SYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "",
  "sessionId": "session-emoji"
}
```
配置文件：
```
application.yml
```
配置片段：
```yaml
server:
  port: 8080
auth:
  api-keys:
    demo-key:
      user-id: demo-user
      roles:
        - ROLE_USER
tenant:
  whitelist-paths:
    - /actuator/health
    - /actuator/info
agent:
  summary:
    enable: false
    raw-output:
      enable: true
  idempotency:
    redis-enabled: true
    ttl-seconds: 86400
  task:
    sync:
      wait-timeout-ms: 30000
      max-wait-timeout-ms: 60000
      max-concurrency: 20
```
请求命令：
```bash
curl -X POST http://localhost:8080/api/v1/tasks \
     -H "X-API-Key: demo-key" \
     -H "X-Tenant-Id: tenant-demo-1" \
     -H "Content-Type: application/json" \
     -d '{
  "query": "帮我查询包含😀的用户，并统计数量。",
  "executionMode": "SYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "",
  "sessionId": "session-emoji"
}'
```
测试流程：
1. 按配置片段启动服务。
2. 发送包含表情的请求。
预期结果：
1. 响应码为 200 或 202。
2. 服务未出现编码错误。

### 用例
用例编号：
```
P2-02
```
优先级：
```
P2
```
用例描述：
查询内容包含疑似注入语句，验证安全处理。
请求地址：
```
http://localhost:8080/api/v1/tasks
```
请求方法：
```
POST
```
请求头：
```
X-API-Key: demo-key
X-Tenant-Id: tenant-demo-1
Content-Type: application/json
```
请求参数：
```json
{
  "query": "查询用户名中包含h的用户；或1=1",
  "executionMode": "SYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "",
  "sessionId": "session-sql"
}
```
配置文件：
```
application.yml
```
配置片段：
```yaml
server:
  port: 8080
auth:
  api-keys:
    demo-key:
      user-id: demo-user
      roles:
        - ROLE_USER
tenant:
  whitelist-paths:
    - /actuator/health
    - /actuator/info
agent:
  summary:
    enable: false
    raw-output:
      enable: true
  idempotency:
    redis-enabled: true
    ttl-seconds: 86400
  task:
    sync:
      wait-timeout-ms: 30000
      max-wait-timeout-ms: 60000
      max-concurrency: 20
```
请求命令：
```bash
curl -X POST http://localhost:8080/api/v1/tasks \
     -H "X-API-Key: demo-key" \
     -H "X-Tenant-Id: tenant-demo-1" \
     -H "Content-Type: application/json" \
     -d '{
  "query": "查询用户名中包含h的用户；或1=1",
  "executionMode": "SYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "",
  "sessionId": "session-sql"
}'
```
测试流程：
1. 按配置片段启动服务。
2. 发送包含疑似注入语句的请求。
预期结果：
1. 响应码为 200 或 202。
2. 响应体中不出现执行异常栈信息。

### 用例
用例编号：
```
P2-03
```
优先级：
```
P2
```
用例描述：
查询内容包含脚本片段，验证输入安全处理。
请求地址：
```
http://localhost:8080/api/v1/tasks
```
请求方法：
```
POST
```
请求头：
```
X-API-Key: demo-key
X-Tenant-Id: tenant-demo-1
Content-Type: application/json
```
请求参数：
```json
{
  "query": "<script>alert('x')</script> 查询包含bob的用户",
  "executionMode": "SYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "",
  "sessionId": "session-xss"
}
```
配置文件：
```
application.yml
```
配置片段：
```yaml
server:
  port: 8080
auth:
  api-keys:
    demo-key:
      user-id: demo-user
      roles:
        - ROLE_USER
tenant:
  whitelist-paths:
    - /actuator/health
    - /actuator/info
agent:
  summary:
    enable: false
    raw-output:
      enable: true
  idempotency:
    redis-enabled: true
    ttl-seconds: 86400
  task:
    sync:
      wait-timeout-ms: 30000
      max-wait-timeout-ms: 60000
      max-concurrency: 20
```
请求命令：
```bash
curl -X POST http://localhost:8080/api/v1/tasks \
     -H "X-API-Key: demo-key" \
     -H "X-Tenant-Id: tenant-demo-1" \
     -H "Content-Type: application/json" \
     -d '{
  "query": "<script>alert('"'"'x'"'"')</script> 查询包含bob的用户",
  "executionMode": "SYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "",
  "sessionId": "session-xss"
}'
```
测试流程：
1. 按配置片段启动服务。
2. 发送包含脚本片段的请求。
预期结果：
1. 响应码为 200 或 202。
2. 响应体保持正常结构。

### 用例
用例编号：
```
P2-04
```
优先级：
```
P2
```
用例描述：
超长查询内容，验证长度边界。
请求地址：
```
http://localhost:8080/api/v1/tasks
```
请求方法：
```
POST
```
请求头：
```
X-API-Key: demo-key
X-Tenant-Id: tenant-demo-1
Content-Type: application/json
```
请求参数：
```json
{
  "query": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
  "executionMode": "ASYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "",
  "sessionId": "session-long-query"
}
```
配置文件：
```
application.yml
```
配置片段：
```yaml
server:
  port: 8080
auth:
  api-keys:
    demo-key:
      user-id: demo-user
      roles:
        - ROLE_USER
tenant:
  whitelist-paths:
    - /actuator/health
    - /actuator/info
agent:
  summary:
    enable: false
    raw-output:
      enable: true
  idempotency:
    redis-enabled: true
    ttl-seconds: 86400
  task:
    sync:
      wait-timeout-ms: 30000
      max-wait-timeout-ms: 60000
      max-concurrency: 20
```
请求命令：
```bash
curl -X POST http://localhost:8080/api/v1/tasks \
     -H "X-API-Key: demo-key" \
     -H "X-Tenant-Id: tenant-demo-1" \
     -H "Content-Type: application/json" \
     -d '{
  "query": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
  "executionMode": "ASYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "",
  "sessionId": "session-long-query"
}'
```
测试流程：
1. 按配置片段启动服务。
2. 发送超长查询内容的请求。
预期结果：
1. 响应码为 202 或 400。
2. 服务无异常退出。

### 用例
用例编号：
```
P2-05
```
优先级：
```
P2
```
用例描述：
上下文包含多层嵌套对象，验证兼容性。
请求地址：
```
http://localhost:8080/api/v1/tasks
```
请求方法：
```
POST
```
请求头：
```
X-API-Key: demo-key
X-Tenant-Id: tenant-demo-1
Content-Type: application/json
```
请求参数：
```json
{
  "query": "查询包含h的用户",
  "executionMode": "ASYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "",
  "sessionId": "session-context",
  "context": {
    "filters": {
      "name": {
        "like": "h"
      }
    },
    "meta": {
      "source": "manual",
      "tags": [
        "t1",
        "t2"
      ]
    }
  }
}
```
配置文件：
```
application.yml
```
配置片段：
```yaml
server:
  port: 8080
auth:
  api-keys:
    demo-key:
      user-id: demo-user
      roles:
        - ROLE_USER
tenant:
  whitelist-paths:
    - /actuator/health
    - /actuator/info
agent:
  summary:
    enable: false
    raw-output:
      enable: true
  idempotency:
    redis-enabled: true
    ttl-seconds: 86400
  task:
    sync:
      wait-timeout-ms: 30000
      max-wait-timeout-ms: 60000
      max-concurrency: 20
```
请求命令：
```bash
curl -X POST http://localhost:8080/api/v1/tasks \
     -H "X-API-Key: demo-key" \
     -H "X-Tenant-Id: tenant-demo-1" \
     -H "Content-Type: application/json" \
     -d '{
  "query": "查询包含h的用户",
  "executionMode": "ASYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "",
  "sessionId": "session-context",
  "context": {
    "filters": {
      "name": {
        "like": "h"
      }
    },
    "meta": {
      "source": "manual",
      "tags": [
        "t1",
        "t2"
      ]
    }
  }
}'
```
测试流程：
1. 按配置片段启动服务。
2. 发送包含嵌套上下文的请求。
预期结果：
1. 响应码为 202。
2. 任务可继续查询。

### 用例
用例编号：
```
P2-06
```
优先级：
```
P2
```
用例描述：
缺少会话标识时仍可提交任务。
请求地址：
```
http://localhost:8080/api/v1/tasks
```
请求方法：
```
POST
```
请求头：
```
X-API-Key: demo-key
X-Tenant-Id: tenant-demo-1
Content-Type: application/json
```
请求参数：
```json
{
  "query": "查询包含bob的用户",
  "executionMode": "ASYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": ""
}
```
配置文件：
```
application.yml
```
配置片段：
```yaml
server:
  port: 8080
auth:
  api-keys:
    demo-key:
      user-id: demo-user
      roles:
        - ROLE_USER
tenant:
  whitelist-paths:
    - /actuator/health
    - /actuator/info
agent:
  summary:
    enable: false
    raw-output:
      enable: true
  idempotency:
    redis-enabled: true
    ttl-seconds: 86400
  task:
    sync:
      wait-timeout-ms: 30000
      max-wait-timeout-ms: 60000
      max-concurrency: 20
```
请求命令：
```bash
curl -X POST http://localhost:8080/api/v1/tasks \
     -H "X-API-Key: demo-key" \
     -H "X-Tenant-Id: tenant-demo-1" \
     -H "Content-Type: application/json" \
     -d '{
  "query": "查询包含bob的用户",
  "executionMode": "ASYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": ""
}'
```
测试流程：
1. 按配置片段启动服务。
2. 发送不包含会话标识的请求。
预期结果：
1. 响应码为 202。
2. 任务可继续查询。

### 用例
用例编号：
```
P2-07
```
优先级：
```
P2
```
用例描述：
指定不存在的技能名称，验证容错处理。
请求地址：
```
http://localhost:8080/api/v1/tasks
```
请求方法：
```
POST
```
请求头：
```
X-API-Key: demo-key
X-Tenant-Id: tenant-demo-1
Content-Type: application/json
```
请求参数：
```json
{
  "query": "查询包含h的用户",
  "executionMode": "ASYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "",
  "sessionId": "session-skill",
  "skillName": "unknown-skill"
}
```
配置文件：
```
application.yml
```
配置片段：
```yaml
server:
  port: 8080
auth:
  api-keys:
    demo-key:
      user-id: demo-user
      roles:
        - ROLE_USER
tenant:
  whitelist-paths:
    - /actuator/health
    - /actuator/info
agent:
  summary:
    enable: false
    raw-output:
      enable: true
  idempotency:
    redis-enabled: true
    ttl-seconds: 86400
  task:
    sync:
      wait-timeout-ms: 30000
      max-wait-timeout-ms: 60000
      max-concurrency: 20
```
请求命令：
```bash
curl -X POST http://localhost:8080/api/v1/tasks \
     -H "X-API-Key: demo-key" \
     -H "X-Tenant-Id: tenant-demo-1" \
     -H "Content-Type: application/json" \
     -d '{
  "query": "查询包含h的用户",
  "executionMode": "ASYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "",
  "sessionId": "session-skill",
  "skillName": "unknown-skill"
}'
```
测试流程：
1. 按配置片段启动服务。
2. 发送包含不存在技能名称的请求。
预期结果：
1. 响应码为 202。
2. 任务仍被受理。

### 用例
用例编号：
```
P2-08
```
优先级：
```
P2
```
用例描述：
指定工具选择为指定工具，验证工具选择字段兼容性。
请求地址：
```
http://localhost:8080/api/v1/tasks
```
请求方法：
```
POST
```
请求头：
```
X-API-Key: demo-key
X-Tenant-Id: tenant-demo-1
Content-Type: application/json
```
请求参数：
```json
{
  "query": "查询包含h的用户",
  "executionMode": "ASYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "",
  "sessionId": "session-tool-spec",
  "toolChoice": {
    "mode": "SPECIFIED",
    "toolName": "demo_tool"
  }
}
```
配置文件：
```
application.yml
```
配置片段：
```yaml
server:
  port: 8080
auth:
  api-keys:
    demo-key:
      user-id: demo-user
      roles:
        - ROLE_USER
tenant:
  whitelist-paths:
    - /actuator/health
    - /actuator/info
agent:
  summary:
    enable: false
    raw-output:
      enable: true
  idempotency:
    redis-enabled: true
    ttl-seconds: 86400
  task:
    sync:
      wait-timeout-ms: 30000
      max-wait-timeout-ms: 60000
      max-concurrency: 20
```
请求命令：
```bash
curl -X POST http://localhost:8080/api/v1/tasks \
     -H "X-API-Key: demo-key" \
     -H "X-Tenant-Id: tenant-demo-1" \
     -H "Content-Type: application/json" \
     -d '{
  "query": "查询包含h的用户",
  "executionMode": "ASYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "",
  "sessionId": "session-tool-spec",
  "toolChoice": {
    "mode": "SPECIFIED",
    "toolName": "demo_tool"
  }
}'
```
测试流程：
1. 按配置片段启动服务。
2. 发送带工具选择字段的请求。
预期结果：
1. 响应码为 202。
2. 任务被受理且无参数解析错误。

### 用例
用例编号：
```
P2-09
```
优先级：
```
P2
```
用例描述：
幂等键包含中文字符，验证兼容性。
请求地址：
```
http://localhost:8080/api/v1/tasks
```
请求方法：
```
POST
```
请求头：
```
X-API-Key: demo-key
X-Tenant-Id: tenant-demo-1
Content-Type: application/json
```
请求参数：
```json
{
  "query": "查询包含bob的用户",
  "executionMode": "ASYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "幂等键一号",
  "sessionId": "session-idem-cn"
}
```
配置文件：
```
application.yml
```
配置片段：
```yaml
server:
  port: 8080
auth:
  api-keys:
    demo-key:
      user-id: demo-user
      roles:
        - ROLE_USER
tenant:
  whitelist-paths:
    - /actuator/health
    - /actuator/info
agent:
  summary:
    enable: false
    raw-output:
      enable: true
  idempotency:
    redis-enabled: true
    ttl-seconds: 86400
  task:
    sync:
      wait-timeout-ms: 30000
      max-wait-timeout-ms: 60000
      max-concurrency: 20
```
请求命令：
```bash
curl -X POST http://localhost:8080/api/v1/tasks \
     -H "X-API-Key: demo-key" \
     -H "X-Tenant-Id: tenant-demo-1" \
     -H "Content-Type: application/json" \
     -d '{
  "query": "查询包含bob的用户",
  "executionMode": "ASYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "幂等键一号",
  "sessionId": "session-idem-cn"
}'
```
测试流程：
1. 按配置片段启动服务。
2. 使用包含中文的幂等键提交请求。
预期结果：
1. 响应码为 202。
2. 任务被受理且幂等键可保存。

### 用例
用例编号：
```
P2-10
```
优先级：
```
P2
```
用例描述：
异步模式携带等待时间，验证等待时间被忽略。
请求地址：
```
http://localhost:8080/api/v1/tasks
```
请求方法：
```
POST
```
请求头：
```
X-API-Key: demo-key
X-Tenant-Id: tenant-demo-1
Content-Type: application/json
```
请求参数：
```json
{
  "query": "查询包含h的用户",
  "executionMode": "ASYNC",
  "waitTimeoutMs": 1,
  "idempotencyKey": "",
  "sessionId": "session-async-wait"
}
```
配置文件：
```
application.yml
```
配置片段：
```yaml
server:
  port: 8080
auth:
  api-keys:
    demo-key:
      user-id: demo-user
      roles:
        - ROLE_USER
tenant:
  whitelist-paths:
    - /actuator/health
    - /actuator/info
agent:
  summary:
    enable: false
    raw-output:
      enable: true
  idempotency:
    redis-enabled: true
    ttl-seconds: 86400
  task:
    sync:
      wait-timeout-ms: 30000
      max-wait-timeout-ms: 60000
      max-concurrency: 20
```
请求命令：
```bash
curl -X POST http://localhost:8080/api/v1/tasks \
     -H "X-API-Key: demo-key" \
     -H "X-Tenant-Id: tenant-demo-1" \
     -H "Content-Type: application/json" \
     -d '{
  "query": "查询包含h的用户",
  "executionMode": "ASYNC",
  "waitTimeoutMs": 1,
  "idempotencyKey": "",
  "sessionId": "session-async-wait"
}'
```
测试流程：
1. 按配置片段启动服务。
2. 发送异步请求并携带等待时间。
预期结果：
1. 响应码为 202。
2. 响应不受等待时间影响。

### 用例
用例编号：
```
P3-01
```
优先级：
```
P3
```
用例描述：
请求体字段顺序乱序，验证反序列化兼容性。
请求地址：
```
http://localhost:8080/api/v1/tasks
```
请求方法：
```
POST
```
请求头：
```
X-API-Key: demo-key
X-Tenant-Id: tenant-demo-1
Content-Type: application/json
```
请求参数：
```json
{
  "sessionId": "session-order",
  "idempotencyKey": "",
  "waitTimeoutMs": 30000,
  "executionMode": "ASYNC",
  "query": "查询包含h的用户"
}
```
配置文件：
```
application.yml
```
配置片段：
```yaml
server:
  port: 8080
auth:
  api-keys:
    demo-key:
      user-id: demo-user
      roles:
        - ROLE_USER
tenant:
  whitelist-paths:
    - /actuator/health
    - /actuator/info
agent:
  summary:
    enable: false
    raw-output:
      enable: true
  idempotency:
    redis-enabled: true
    ttl-seconds: 86400
  task:
    sync:
      wait-timeout-ms: 30000
      max-wait-timeout-ms: 60000
      max-concurrency: 20
```
请求命令：
```bash
curl -X POST http://localhost:8080/api/v1/tasks \
     -H "X-API-Key: demo-key" \
     -H "X-Tenant-Id: tenant-demo-1" \
     -H "Content-Type: application/json" \
     -d '{
  "sessionId": "session-order",
  "idempotencyKey": "",
  "waitTimeoutMs": 30000,
  "executionMode": "ASYNC",
  "query": "查询包含h的用户"
}'
```
测试流程：
1. 按配置片段启动服务。
2. 发送乱序字段的请求。
预期结果：
1. 响应码为 202。
2. 任务被正常受理。

### 用例
用例编号：
```
P3-02
```
优先级：
```
P3
```
用例描述：
仅包含查询字段的最小请求。
请求地址：
```
http://localhost:8080/api/v1/tasks
```
请求方法：
```
POST
```
请求头：
```
X-API-Key: demo-key
X-Tenant-Id: tenant-demo-1
Content-Type: application/json
```
请求参数：
```json
{
  "query": "查询包含bob的用户"
}
```
配置文件：
```
application.yml
```
配置片段：
```yaml
server:
  port: 8080
auth:
  api-keys:
    demo-key:
      user-id: demo-user
      roles:
        - ROLE_USER
tenant:
  whitelist-paths:
    - /actuator/health
    - /actuator/info
agent:
  summary:
    enable: false
    raw-output:
      enable: true
  idempotency:
    redis-enabled: true
    ttl-seconds: 86400
  task:
    sync:
      wait-timeout-ms: 30000
      max-wait-timeout-ms: 60000
      max-concurrency: 20
```
请求命令：
```bash
curl -X POST http://localhost:8080/api/v1/tasks \
     -H "X-API-Key: demo-key" \
     -H "X-Tenant-Id: tenant-demo-1" \
     -H "Content-Type: application/json" \
     -d '{
  "query": "查询包含bob的用户"
}'
```
测试流程：
1. 按配置片段启动服务。
2. 发送仅包含查询字段的请求。
预期结果：
1. 响应码为 202。
2. 执行模式默认为异步。

### 用例
用例编号：
```
P3-03
```
优先级：
```
P3
```
用例描述：
会话标识超长字符串，验证存储与兼容性。
请求地址：
```
http://localhost:8080/api/v1/tasks
```
请求方法：
```
POST
```
请求头：
```
X-API-Key: demo-key
X-Tenant-Id: tenant-demo-1
Content-Type: application/json
```
请求参数：
```json
{
  "query": "查询包含h的用户",
  "executionMode": "ASYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "",
  "sessionId": "ssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssss"
}
```
配置文件：
```
application.yml
```
配置片段：
```yaml
server:
  port: 8080
auth:
  api-keys:
    demo-key:
      user-id: demo-user
      roles:
        - ROLE_USER
tenant:
  whitelist-paths:
    - /actuator/health
    - /actuator/info
agent:
  summary:
    enable: false
    raw-output:
      enable: true
  idempotency:
    redis-enabled: true
    ttl-seconds: 86400
  task:
    sync:
      wait-timeout-ms: 30000
      max-wait-timeout-ms: 60000
      max-concurrency: 20
```
请求命令：
```bash
curl -X POST http://localhost:8080/api/v1/tasks \
     -H "X-API-Key: demo-key" \
     -H "X-Tenant-Id: tenant-demo-1" \
     -H "Content-Type: application/json" \
     -d '{
  "query": "查询包含h的用户",
  "executionMode": "ASYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "",
  "sessionId": "ssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssss"
}'
```
测试流程：
1. 按配置片段启动服务。
2. 发送包含超长会话标识的请求。
预期结果：
1. 响应码为 202。
2. 服务保持稳定。

### 用例
用例编号：
```
P3-04
```
优先级：
```
P3
```
用例描述：
上下文包含未知字段，验证忽略策略。
请求地址：
```
http://localhost:8080/api/v1/tasks
```
请求方法：
```
POST
```
请求头：
```
X-API-Key: demo-key
X-Tenant-Id: tenant-demo-1
Content-Type: application/json
```
请求参数：
```json
{
  "query": "查询包含h的用户",
  "executionMode": "ASYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "",
  "sessionId": "session-extra-context",
  "context": {
    "unknown": "value",
    "flag": true
  }
}
```
配置文件：
```
application.yml
```
配置片段：
```yaml
server:
  port: 8080
auth:
  api-keys:
    demo-key:
      user-id: demo-user
      roles:
        - ROLE_USER
tenant:
  whitelist-paths:
    - /actuator/health
    - /actuator/info
agent:
  summary:
    enable: false
    raw-output:
      enable: true
  idempotency:
    redis-enabled: true
    ttl-seconds: 86400
  task:
    sync:
      wait-timeout-ms: 30000
      max-wait-timeout-ms: 60000
      max-concurrency: 20
```
请求命令：
```bash
curl -X POST http://localhost:8080/api/v1/tasks \
     -H "X-API-Key: demo-key" \
     -H "X-Tenant-Id: tenant-demo-1" \
     -H "Content-Type: application/json" \
     -d '{
  "query": "查询包含h的用户",
  "executionMode": "ASYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "",
  "sessionId": "session-extra-context",
  "context": {
    "unknown": "value",
    "flag": true
  }
}'
```
测试流程：
1. 按配置片段启动服务。
2. 发送包含未知上下文字段的请求。
预期结果：
1. 响应码为 202。
2. 任务可继续查询。

### 用例
用例编号：
```
P3-05
```
优先级：
```
P3
```
用例描述：
工具选择对象为空，验证默认处理。
请求地址：
```
http://localhost:8080/api/v1/tasks
```
请求方法：
```
POST
```
请求头：
```
X-API-Key: demo-key
X-Tenant-Id: tenant-demo-1
Content-Type: application/json
```
请求参数：
```json
{
  "query": "查询包含bob的用户",
  "executionMode": "ASYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "",
  "sessionId": "session-tool-empty",
  "toolChoice": {}
}
```
配置文件：
```
application.yml
```
配置片段：
```yaml
server:
  port: 8080
auth:
  api-keys:
    demo-key:
      user-id: demo-user
      roles:
        - ROLE_USER
tenant:
  whitelist-paths:
    - /actuator/health
    - /actuator/info
agent:
  summary:
    enable: false
    raw-output:
      enable: true
  idempotency:
    redis-enabled: true
    ttl-seconds: 86400
  task:
    sync:
      wait-timeout-ms: 30000
      max-wait-timeout-ms: 60000
      max-concurrency: 20
```
请求命令：
```bash
curl -X POST http://localhost:8080/api/v1/tasks \
     -H "X-API-Key: demo-key" \
     -H "X-Tenant-Id: tenant-demo-1" \
     -H "Content-Type: application/json" \
     -d '{
  "query": "查询包含bob的用户",
  "executionMode": "ASYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "",
  "sessionId": "session-tool-empty",
  "toolChoice": {}
}'
```
测试流程：
1. 按配置片段启动服务。
2. 发送工具选择对象为空的请求。
预期结果：
1. 响应码为 202。
2. 任务被正常受理。

### 用例
用例编号：
```
P3-06
```
优先级：
```
P3
```
用例描述：
幂等键为空字符串，验证视为未提供。
请求地址：
```
http://localhost:8080/api/v1/tasks
```
请求方法：
```
POST
```
请求头：
```
X-API-Key: demo-key
X-Tenant-Id: tenant-demo-1
Content-Type: application/json
```
请求参数：
```json
{
  "query": "查询包含bob的用户",
  "executionMode": "ASYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "   ",
  "sessionId": "session-idem-empty"
}
```
配置文件：
```
application.yml
```
配置片段：
```yaml
server:
  port: 8080
auth:
  api-keys:
    demo-key:
      user-id: demo-user
      roles:
        - ROLE_USER
tenant:
  whitelist-paths:
    - /actuator/health
    - /actuator/info
agent:
  summary:
    enable: false
    raw-output:
      enable: true
  idempotency:
    redis-enabled: true
    ttl-seconds: 86400
  task:
    sync:
      wait-timeout-ms: 30000
      max-wait-timeout-ms: 60000
      max-concurrency: 20
```
请求命令：
```bash
curl -X POST http://localhost:8080/api/v1/tasks \
     -H "X-API-Key: demo-key" \
     -H "X-Tenant-Id: tenant-demo-1" \
     -H "Content-Type: application/json" \
     -d '{
  "query": "查询包含bob的用户",
  "executionMode": "ASYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "   ",
  "sessionId": "session-idem-empty"
}'
```
测试流程：
1. 按配置片段启动服务。
2. 发送幂等键为空字符串的请求。
3. 再次发送同样请求。
预期结果：
1. 两次响应均为 202。
2. 两次任务标识不同。

### 用例
用例编号：
```
P3-07
```
优先级：
```
P3
```
用例描述：
同步等待时间为零时使用默认值。
请求地址：
```
http://localhost:8080/api/v1/tasks
```
请求方法：
```
POST
```
请求头：
```
X-API-Key: demo-key
X-Tenant-Id: tenant-demo-1
Content-Type: application/json
```
请求参数：
```json
{
  "query": "查询等待时间为零",
  "executionMode": "SYNC",
  "waitTimeoutMs": 0,
  "idempotencyKey": "",
  "sessionId": "session-zero-wait"
}
```
配置文件：
```
application.yml
```
配置片段：
```yaml
server:
  port: 8080
auth:
  api-keys:
    demo-key:
      user-id: demo-user
      roles:
        - ROLE_USER
tenant:
  whitelist-paths:
    - /actuator/health
    - /actuator/info
agent:
  summary:
    enable: false
    raw-output:
      enable: true
  idempotency:
    redis-enabled: true
    ttl-seconds: 86400
  task:
    sync:
      wait-timeout-ms: 30000
      max-wait-timeout-ms: 60000
      max-concurrency: 20
```
请求命令：
```bash
curl -X POST http://localhost:8080/api/v1/tasks \
     -H "X-API-Key: demo-key" \
     -H "X-Tenant-Id: tenant-demo-1" \
     -H "Content-Type: application/json" \
     -d '{
  "query": "查询等待时间为零",
  "executionMode": "SYNC",
  "waitTimeoutMs": 0,
  "idempotencyKey": "",
  "sessionId": "session-zero-wait"
}'
```
测试流程：
1. 按配置片段启动服务。
2. 发送同步请求并设置等待时间为零。
预期结果：
1. 响应码为 200 或 202。
2. 等待时间使用默认值。

### 用例
用例编号：
```
P3-08
```
优先级：
```
P3
```
用例描述：
执行模式缺失时默认异步。
请求地址：
```
http://localhost:8080/api/v1/tasks
```
请求方法：
```
POST
```
请求头：
```
X-API-Key: demo-key
X-Tenant-Id: tenant-demo-1
Content-Type: application/json
```
请求参数：
```json
{
  "query": "查询默认执行模式",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "",
  "sessionId": "session-default-mode"
}
```
配置文件：
```
application.yml
```
配置片段：
```yaml
server:
  port: 8080
auth:
  api-keys:
    demo-key:
      user-id: demo-user
      roles:
        - ROLE_USER
tenant:
  whitelist-paths:
    - /actuator/health
    - /actuator/info
agent:
  summary:
    enable: false
    raw-output:
      enable: true
  idempotency:
    redis-enabled: true
    ttl-seconds: 86400
  task:
    sync:
      wait-timeout-ms: 30000
      max-wait-timeout-ms: 60000
      max-concurrency: 20
```
请求命令：
```bash
curl -X POST http://localhost:8080/api/v1/tasks \
     -H "X-API-Key: demo-key" \
     -H "X-Tenant-Id: tenant-demo-1" \
     -H "Content-Type: application/json" \
     -d '{
  "query": "查询默认执行模式",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "",
  "sessionId": "session-default-mode"
}'
```
测试流程：
1. 按配置片段启动服务。
2. 发送不包含执行模式的请求。
预期结果：
1. 响应码为 202。
2. 执行模式按默认异步处理。

### 用例
用例编号：
```
P3-09
```
优先级：
```
P3
```
用例描述：
请求头增加追踪标识字段，验证兼容性。
请求地址：
```
http://localhost:8080/api/v1/tasks
```
请求方法：
```
POST
```
请求头：
```
X-API-Key: demo-key
X-Tenant-Id: tenant-demo-1
Content-Type: application/json
X-Request-Id: req-001
```
请求参数：
```json
{
  "query": "帮我查询一下包含h的用户信息，或帮我查询一下包含bob的信息",
  "executionMode": "SYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "",
  "sessionId": "session-demo-0131"
}
```
配置文件：
```
application.yml
```
配置片段：
```yaml
server:
  port: 8080
auth:
  api-keys:
    demo-key:
      user-id: demo-user
      roles:
        - ROLE_USER
tenant:
  whitelist-paths:
    - /actuator/health
    - /actuator/info
agent:
  summary:
    enable: false
    raw-output:
      enable: true
  idempotency:
    redis-enabled: true
    ttl-seconds: 86400
  task:
    sync:
      wait-timeout-ms: 30000
      max-wait-timeout-ms: 60000
      max-concurrency: 20
```
请求命令：
```bash
curl -X POST http://localhost:8080/api/v1/tasks \
     -H "X-API-Key: demo-key" \
     -H "X-Tenant-Id: tenant-demo-1" \
     -H "Content-Type: application/json" \
     -H "X-Request-Id: req-001" \
     -d '{
  "query": "帮我查询一下包含h的用户信息，或帮我查询一下包含bob的信息",
  "executionMode": "SYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "",
  "sessionId": "session-demo-0131"
}'
```
测试流程：
1. 按配置片段启动服务。
2. 发送包含额外追踪头的请求。
预期结果：
1. 响应码为 200 或 202。
2. 服务正常处理请求。

### 用例
用例编号：
```
P3-10
```
优先级：
```
P3
```
用例描述：
内容类型包含编码参数，验证兼容性。
请求地址：
```
http://localhost:8080/api/v1/tasks
```
请求方法：
```
POST
```
请求头：
```
X-API-Key: demo-key
X-Tenant-Id: tenant-demo-1
Content-Type: application/json; charset=utf-8
```
请求参数：
```json
{
  "query": "帮我查询一下包含h的用户信息，或帮我查询一下包含bob的信息",
  "executionMode": "SYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "",
  "sessionId": "session-demo-0131"
}
```
配置文件：
```
application.yml
```
配置片段：
```yaml
server:
  port: 8080
auth:
  api-keys:
    demo-key:
      user-id: demo-user
      roles:
        - ROLE_USER
tenant:
  whitelist-paths:
    - /actuator/health
    - /actuator/info
agent:
  summary:
    enable: false
    raw-output:
      enable: true
  idempotency:
    redis-enabled: true
    ttl-seconds: 86400
  task:
    sync:
      wait-timeout-ms: 30000
      max-wait-timeout-ms: 60000
      max-concurrency: 20
```
请求命令：
```bash
curl -X POST http://localhost:8080/api/v1/tasks \
     -H "X-API-Key: demo-key" \
     -H "X-Tenant-Id: tenant-demo-1" \
     -H "Content-Type: application/json; charset=utf-8" \
     -d '{
  "query": "帮我查询一下包含h的用户信息，或帮我查询一下包含bob的信息",
  "executionMode": "SYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "",
  "sessionId": "session-demo-0131"
}'
```
测试流程：
1. 按配置片段启动服务。
2. 发送带编码参数的内容类型请求。
预期结果：
1. 响应码为 200 或 202。
2. 请求被正常解析。

### 用例
用例编号：
```
P4-01
```
优先级：
```
P4
```
用例描述：
请求头顺序变化不影响处理。
请求地址：
```
http://localhost:8080/api/v1/tasks
```
请求方法：
```
POST
```
请求头：
```
Content-Type: application/json
X-Tenant-Id: tenant-demo-1
X-API-Key: demo-key
```
请求参数：
```json
{
  "query": "帮我查询一下包含h的用户信息，或帮我查询一下包含bob的信息",
  "executionMode": "SYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "",
  "sessionId": "session-demo-0131"
}
```
配置文件：
```
application.yml
```
配置片段：
```yaml
server:
  port: 8080
auth:
  api-keys:
    demo-key:
      user-id: demo-user
      roles:
        - ROLE_USER
tenant:
  whitelist-paths:
    - /actuator/health
    - /actuator/info
agent:
  summary:
    enable: false
    raw-output:
      enable: true
  idempotency:
    redis-enabled: true
    ttl-seconds: 86400
  task:
    sync:
      wait-timeout-ms: 30000
      max-wait-timeout-ms: 60000
      max-concurrency: 20
```
请求命令：
```bash
curl -X POST http://localhost:8080/api/v1/tasks \
     -H "Content-Type: application/json" \
     -H "X-Tenant-Id: tenant-demo-1" \
     -H "X-API-Key: demo-key" \
     -d '{
  "query": "帮我查询一下包含h的用户信息，或帮我查询一下包含bob的信息",
  "executionMode": "SYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "",
  "sessionId": "session-demo-0131"
}'
```
测试流程：
1. 按配置片段启动服务。
2. 使用不同顺序的请求头发送请求。
预期结果：
1. 响应码为 200 或 202。
2. 请求被正常处理。

### 用例
用例编号：
```
P4-02
```
优先级：
```
P4
```
用例描述：
请求头使用小写形式，验证兼容性。
请求地址：
```
http://localhost:8080/api/v1/tasks
```
请求方法：
```
POST
```
请求头：
```
x-api-key: demo-key
x-tenant-id: tenant-demo-1
content-type: application/json
```
请求参数：
```json
{
  "query": "帮我查询一下包含h的用户信息，或帮我查询一下包含bob的信息",
  "executionMode": "SYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "",
  "sessionId": "session-demo-0131"
}
```
配置文件：
```
application.yml
```
配置片段：
```yaml
server:
  port: 8080
auth:
  api-keys:
    demo-key:
      user-id: demo-user
      roles:
        - ROLE_USER
tenant:
  whitelist-paths:
    - /actuator/health
    - /actuator/info
agent:
  summary:
    enable: false
    raw-output:
      enable: true
  idempotency:
    redis-enabled: true
    ttl-seconds: 86400
  task:
    sync:
      wait-timeout-ms: 30000
      max-wait-timeout-ms: 60000
      max-concurrency: 20
```
请求命令：
```bash
curl -X POST http://localhost:8080/api/v1/tasks \
     -H "x-api-key: demo-key" \
     -H "x-tenant-id: tenant-demo-1" \
     -H "content-type: application/json" \
     -d '{
  "query": "帮我查询一下包含h的用户信息，或帮我查询一下包含bob的信息",
  "executionMode": "SYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "",
  "sessionId": "session-demo-0131"
}'
```
测试流程：
1. 按配置片段启动服务。
2. 使用小写请求头发送请求。
预期结果：
1. 响应码为 200 或 202。
2. 请求被正常解析。

### 用例
用例编号：
```
P4-03
```
优先级：
```
P4
```
用例描述：
增加接受头字段，验证兼容性。
请求地址：
```
http://localhost:8080/api/v1/tasks
```
请求方法：
```
POST
```
请求头：
```
X-API-Key: demo-key
X-Tenant-Id: tenant-demo-1
Content-Type: application/json
Accept: application/json
```
请求参数：
```json
{
  "query": "帮我查询一下包含h的用户信息，或帮我查询一下包含bob的信息",
  "executionMode": "SYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "",
  "sessionId": "session-demo-0131"
}
```
配置文件：
```
application.yml
```
配置片段：
```yaml
server:
  port: 8080
auth:
  api-keys:
    demo-key:
      user-id: demo-user
      roles:
        - ROLE_USER
tenant:
  whitelist-paths:
    - /actuator/health
    - /actuator/info
agent:
  summary:
    enable: false
    raw-output:
      enable: true
  idempotency:
    redis-enabled: true
    ttl-seconds: 86400
  task:
    sync:
      wait-timeout-ms: 30000
      max-wait-timeout-ms: 60000
      max-concurrency: 20
```
请求命令：
```bash
curl -X POST http://localhost:8080/api/v1/tasks \
     -H "X-API-Key: demo-key" \
     -H "X-Tenant-Id: tenant-demo-1" \
     -H "Content-Type: application/json" \
     -H "Accept: application/json" \
     -d '{
  "query": "帮我查询一下包含h的用户信息，或帮我查询一下包含bob的信息",
  "executionMode": "SYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "",
  "sessionId": "session-demo-0131"
}'
```
测试流程：
1. 按配置片段启动服务。
2. 发送包含接受头的请求。
预期结果：
1. 响应码为 200 或 202。
2. 响应体结构正常。

### 用例
用例编号：
```
P4-04
```
优先级：
```
P4
```
用例描述：
增加追踪标识头字段，验证兼容性。
请求地址：
```
http://localhost:8080/api/v1/tasks
```
请求方法：
```
POST
```
请求头：
```
X-API-Key: demo-key
X-Tenant-Id: tenant-demo-1
Content-Type: application/json
X-Trace-Id: trace-001
```
请求参数：
```json
{
  "query": "帮我查询一下包含h的用户信息，或帮我查询一下包含bob的信息",
  "executionMode": "SYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "",
  "sessionId": "session-demo-0131"
}
```
配置文件：
```
application.yml
```
配置片段：
```yaml
server:
  port: 8080
auth:
  api-keys:
    demo-key:
      user-id: demo-user
      roles:
        - ROLE_USER
tenant:
  whitelist-paths:
    - /actuator/health
    - /actuator/info
agent:
  summary:
    enable: false
    raw-output:
      enable: true
  idempotency:
    redis-enabled: true
    ttl-seconds: 86400
  task:
    sync:
      wait-timeout-ms: 30000
      max-wait-timeout-ms: 60000
      max-concurrency: 20
```
请求命令：
```bash
curl -X POST http://localhost:8080/api/v1/tasks \
     -H "X-API-Key: demo-key" \
     -H "X-Tenant-Id: tenant-demo-1" \
     -H "Content-Type: application/json" \
     -H "X-Trace-Id: trace-001" \
     -d '{
  "query": "帮我查询一下包含h的用户信息，或帮我查询一下包含bob的信息",
  "executionMode": "SYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "",
  "sessionId": "session-demo-0131"
}'
```
测试流程：
1. 按配置片段启动服务。
2. 发送包含追踪标识的请求。
预期结果：
1. 响应码为 200 或 202。
2. 服务正常返回。

### 用例
用例编号：
```
P4-05
```
优先级：
```
P4
```
用例描述：
幂等键仅包含空白字符，视为未提供。
请求地址：
```
http://localhost:8080/api/v1/tasks
```
请求方法：
```
POST
```
请求头：
```
X-API-Key: demo-key
X-Tenant-Id: tenant-demo-1
Content-Type: application/json
```
请求参数：
```json
{
  "query": "查询包含h的用户",
  "executionMode": "ASYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "   ",
  "sessionId": "session-idem-space"
}
```
配置文件：
```
application.yml
```
配置片段：
```yaml
server:
  port: 8080
auth:
  api-keys:
    demo-key:
      user-id: demo-user
      roles:
        - ROLE_USER
tenant:
  whitelist-paths:
    - /actuator/health
    - /actuator/info
agent:
  summary:
    enable: false
    raw-output:
      enable: true
  idempotency:
    redis-enabled: true
    ttl-seconds: 86400
  task:
    sync:
      wait-timeout-ms: 30000
      max-wait-timeout-ms: 60000
      max-concurrency: 20
```
请求命令：
```bash
curl -X POST http://localhost:8080/api/v1/tasks \
     -H "X-API-Key: demo-key" \
     -H "X-Tenant-Id: tenant-demo-1" \
     -H "Content-Type: application/json" \
     -d '{
  "query": "查询包含h的用户",
  "executionMode": "ASYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "   ",
  "sessionId": "session-idem-space"
}'
```
测试流程：
1. 按配置片段启动服务。
2. 连续提交两次相同请求。
预期结果：
1. 两次响应码均为 202。
2. 两次任务标识不同。

### 用例
用例编号：
```
P4-06
```
优先级：
```
P4
```
用例描述：
查询内容含首尾空格，验证兼容性。
请求地址：
```
http://localhost:8080/api/v1/tasks
```
请求方法：
```
POST
```
请求头：
```
X-API-Key: demo-key
X-Tenant-Id: tenant-demo-1
Content-Type: application/json
```
请求参数：
```json
{
  "query": "   查询包含bob的用户   ",
  "executionMode": "ASYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "",
  "sessionId": "session-query-space"
}
```
配置文件：
```
application.yml
```
配置片段：
```yaml
server:
  port: 8080
auth:
  api-keys:
    demo-key:
      user-id: demo-user
      roles:
        - ROLE_USER
tenant:
  whitelist-paths:
    - /actuator/health
    - /actuator/info
agent:
  summary:
    enable: false
    raw-output:
      enable: true
  idempotency:
    redis-enabled: true
    ttl-seconds: 86400
  task:
    sync:
      wait-timeout-ms: 30000
      max-wait-timeout-ms: 60000
      max-concurrency: 20
```
请求命令：
```bash
curl -X POST http://localhost:8080/api/v1/tasks \
     -H "X-API-Key: demo-key" \
     -H "X-Tenant-Id: tenant-demo-1" \
     -H "Content-Type: application/json" \
     -d '{
  "query": "   查询包含bob的用户   ",
  "executionMode": "ASYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "",
  "sessionId": "session-query-space"
}'
```
测试流程：
1. 按配置片段启动服务。
2. 发送含首尾空格的查询内容。
预期结果：
1. 响应码为 202。
2. 服务正常受理。

### 用例
用例编号：
```
P4-07
```
优先级：
```
P4
```
用例描述：
上下文为空对象，验证兼容性。
请求地址：
```
http://localhost:8080/api/v1/tasks
```
请求方法：
```
POST
```
请求头：
```
X-API-Key: demo-key
X-Tenant-Id: tenant-demo-1
Content-Type: application/json
```
请求参数：
```json
{
  "query": "查询包含h的用户",
  "executionMode": "ASYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "",
  "sessionId": "session-empty-context",
  "context": {}
}
```
配置文件：
```
application.yml
```
配置片段：
```yaml
server:
  port: 8080
auth:
  api-keys:
    demo-key:
      user-id: demo-user
      roles:
        - ROLE_USER
tenant:
  whitelist-paths:
    - /actuator/health
    - /actuator/info
agent:
  summary:
    enable: false
    raw-output:
      enable: true
  idempotency:
    redis-enabled: true
    ttl-seconds: 86400
  task:
    sync:
      wait-timeout-ms: 30000
      max-wait-timeout-ms: 60000
      max-concurrency: 20
```
请求命令：
```bash
curl -X POST http://localhost:8080/api/v1/tasks \
     -H "X-API-Key: demo-key" \
     -H "X-Tenant-Id: tenant-demo-1" \
     -H "Content-Type: application/json" \
     -d '{
  "query": "查询包含h的用户",
  "executionMode": "ASYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "",
  "sessionId": "session-empty-context",
  "context": {}
}'
```
测试流程：
1. 按配置片段启动服务。
2. 发送包含空上下文的请求。
预期结果：
1. 响应码为 202。
2. 任务被正常受理。

### 用例
用例编号：
```
P4-08
```
优先级：
```
P4
```
用例描述：
异步模式携带极小等待时间，验证不受影响。
请求地址：
```
http://localhost:8080/api/v1/tasks
```
请求方法：
```
POST
```
请求头：
```
X-API-Key: demo-key
X-Tenant-Id: tenant-demo-1
Content-Type: application/json
```
请求参数：
```json
{
  "query": "查询包含h的用户",
  "executionMode": "ASYNC",
  "waitTimeoutMs": 1,
  "idempotencyKey": "",
  "sessionId": "session-async-mini-wait"
}
```
配置文件：
```
application.yml
```
配置片段：
```yaml
server:
  port: 8080
auth:
  api-keys:
    demo-key:
      user-id: demo-user
      roles:
        - ROLE_USER
tenant:
  whitelist-paths:
    - /actuator/health
    - /actuator/info
agent:
  summary:
    enable: false
    raw-output:
      enable: true
  idempotency:
    redis-enabled: true
    ttl-seconds: 86400
  task:
    sync:
      wait-timeout-ms: 30000
      max-wait-timeout-ms: 60000
      max-concurrency: 20
```
请求命令：
```bash
curl -X POST http://localhost:8080/api/v1/tasks \
     -H "X-API-Key: demo-key" \
     -H "X-Tenant-Id: tenant-demo-1" \
     -H "Content-Type: application/json" \
     -d '{
  "query": "查询包含h的用户",
  "executionMode": "ASYNC",
  "waitTimeoutMs": 1,
  "idempotencyKey": "",
  "sessionId": "session-async-mini-wait"
}'
```
测试流程：
1. 按配置片段启动服务。
2. 发送异步请求并携带极小等待时间。
预期结果：
1. 响应码为 202。
2. 等待时间不影响受理结果。

### 用例
用例编号：
```
P4-09
```
优先级：
```
P4
```
用例描述：
会话标识为空字符串，验证兼容性。
请求地址：
```
http://localhost:8080/api/v1/tasks
```
请求方法：
```
POST
```
请求头：
```
X-API-Key: demo-key
X-Tenant-Id: tenant-demo-1
Content-Type: application/json
```
请求参数：
```json
{
  "query": "查询包含bob的用户",
  "executionMode": "ASYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "",
  "sessionId": ""
}
```
配置文件：
```
application.yml
```
配置片段：
```yaml
server:
  port: 8080
auth:
  api-keys:
    demo-key:
      user-id: demo-user
      roles:
        - ROLE_USER
tenant:
  whitelist-paths:
    - /actuator/health
    - /actuator/info
agent:
  summary:
    enable: false
    raw-output:
      enable: true
  idempotency:
    redis-enabled: true
    ttl-seconds: 86400
  task:
    sync:
      wait-timeout-ms: 30000
      max-wait-timeout-ms: 60000
      max-concurrency: 20
```
请求命令：
```bash
curl -X POST http://localhost:8080/api/v1/tasks \
     -H "X-API-Key: demo-key" \
     -H "X-Tenant-Id: tenant-demo-1" \
     -H "Content-Type: application/json" \
     -d '{
  "query": "查询包含bob的用户",
  "executionMode": "ASYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "",
  "sessionId": ""
}'
```
测试流程：
1. 按配置片段启动服务。
2. 发送会话标识为空的请求。
预期结果：
1. 响应码为 202。
2. 任务被正常受理。

### 用例
用例编号：
```
P4-10
```
优先级：
```
P4
```
用例描述：
请求体包含额外字段，验证忽略策略。
请求地址：
```
http://localhost:8080/api/v1/tasks
```
请求方法：
```
POST
```
请求头：
```
X-API-Key: demo-key
X-Tenant-Id: tenant-demo-1
Content-Type: application/json
```
请求参数：
```json
{
  "query": "查询包含h的用户",
  "executionMode": "ASYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "",
  "sessionId": "session-extra-field",
  "extra": "unused"
}
```
配置文件：
```
application.yml
```
配置片段：
```yaml
server:
  port: 8080
auth:
  api-keys:
    demo-key:
      user-id: demo-user
      roles:
        - ROLE_USER
tenant:
  whitelist-paths:
    - /actuator/health
    - /actuator/info
agent:
  summary:
    enable: false
    raw-output:
      enable: true
  idempotency:
    redis-enabled: true
    ttl-seconds: 86400
  task:
    sync:
      wait-timeout-ms: 30000
      max-wait-timeout-ms: 60000
      max-concurrency: 20
```
请求命令：
```bash
curl -X POST http://localhost:8080/api/v1/tasks \
     -H "X-API-Key: demo-key" \
     -H "X-Tenant-Id: tenant-demo-1" \
     -H "Content-Type: application/json" \
     -d '{
  "query": "查询包含h的用户",
  "executionMode": "ASYNC",
  "waitTimeoutMs": 30000,
  "idempotencyKey": "",
  "sessionId": "session-extra-field",
  "extra": "unused"
}'
```
测试流程：
1. 按配置片段启动服务。
2. 发送包含额外字段的请求。
预期结果：
1. 响应码为 202。
2. 服务忽略未知字段并受理。
