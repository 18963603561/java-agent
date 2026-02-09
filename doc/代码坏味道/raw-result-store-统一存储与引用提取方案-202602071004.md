# RawResultStore 统一存储与引用提取方案

## 1. 目标与范围

### 1.1 目标
1. 在 `RawResultStore` 体系中新增两个默认实现类：
   - `RedisRawResultStore`：默认将原始数据保存到 Redis。
   - `SizeAwareRawResultStore`：当原始数据不超过 `1MB` 时走内存直存；超过 `1MB` 时固定保存到本地文本文件。
2. 文件存储生成唯一文件名，保存到可配置目录。
3. 文件存储提供可下载引用地址，形如：`http://ip:端口/文件名.txt`。
4. 原始引用增加统一标识，支持后续统一提取。
5. 在 `src/main/java/com/example/agent/api/http/controller` 提供统一提取方法，能从 `mem / redis / file(txt)` 读取原始数据。

### 1.2 非目标
1. 本阶段不改造所有上游调用方为“强制使用新字段”，优先兼容旧 `rawRef` 使用方式。
2. 本阶段不做对象存储（OSS/S3）扩展，仅覆盖 `mem/redis/file(txt)`。

---

## 2. 当前现状（基于代码）

1. 当前 `RawResultStore` 仅定义 `store(...)`，没有“按引用读取”能力。
   - 文件：`src/main/java/com/example/agent/runtime/raw/RawResultStore.java`
2. 当前仅有 `InMemoryRawResultStore` 实现。
   - 文件：`src/main/java/com/example/agent/runtime/raw/InMemoryRawResultStore.java`
3. `ModelInvocationService`、`ToolExecutor` 等调用点只消费 `RawRef.key` 并回填到 `rawRef` 字段。
4. `controller` 目录目前没有统一“按原始引用读取数据”的接口。

结论：
当前具备“写引用”的能力，但不具备“跨存储统一读引用”的能力；需要补齐引用协议与统一查询入口。

---

## 3. 设计总览

### 3.1 关键思路
1. 保持 `RawResultStore` 负责“写入”。
2. 新增统一“读取服务”用于按引用解析并读取。
3. 引用分两层：
   - **统一引用标识**：用于系统内统一提取。
   - **访问地址**：用于文件下载（HTTP URL）。
4. 存储来源类型标准化：`mem`、`redis`、`file`、`txt`。

### 3.2 推荐新增组件
1. `RedisRawResultStore`（写 Redis）。
2. `SizeAwareRawResultStore`（<=1MB 内存，>1MB 文件）。
3. `RawRefCodec`（统一编码/解码引用标识）。
4. `RawResultResolveService`（按引用统一读取）。
5. `RawResultController`（统一 HTTP 提取入口 + 文件下载入口）。
6. `RawStoreProperties`（统一配置）。

---

## 4. 引用协议与 RawRef 扩展

### 4.1 引用协议
建议定义统一引用标识：

`rawref:v1:{store}:{id}`

示例：
1. `rawref:v1:redis:raw:model:planner:9f2...`
2. `rawref:v1:mem:raw:tool:sql_query:a31...`
3. `rawref:v1:file:20260207-tenantA-3f6a9d7a.txt`
4. `rawref:v1:txt:20260207-tenantA-3f6a9d7a.txt`

说明：
1. `file` 与 `txt` 都支持，`txt` 可视为 `file` 的文本别名。
2. `id` 不直接暴露本地绝对路径，避免路径泄露风险。

### 4.2 RawRef 建议新增字段
在 `RawRef` 中新增：
1. `refId`：统一引用标识（如 `rawref:v1:file:...`）。
2. `accessUrl`：可下载访问地址（文件场景必填）。
3. `storeReason`：存储原因（如 `default_redis`、`size_le_1mb_mem`、`size_gt_1mb_file`）。
4. `storeType`：来源类型（`mem|redis|file|txt`，可与现有 `store` 合并）。

兼容策略：
1. 保留现有 `key` 字段，避免已有调用方立即改造。
2. 新调用方优先使用 `refId`；若为空再回退 `key`。

---

## 5. 两个默认实现类方案

### 5.1 `RedisRawResultStore`（默认）
职责：
1. 将序列化后的 payload 写入 Redis。
2. 写入 key 建议包含来源前缀与租户信息，便于隔离和审计。
3. 返回 `RawRef`，其中：
   - `store=redis`
   - `key=redis真实key`
   - `refId=rawref:v1:redis:{redis真实key}`
   - `storeReason=default_redis`

建议配置：
1. `agent.runtime.raw.redis.key-prefix`
2. `agent.runtime.raw.redis.ttl-seconds`
3. `agent.runtime.raw.redis.enabled`

### 5.2 `SizeAwareRawResultStore`（阈值路由）
职责：
1. 使用 UTF-8 字节数评估 payload 大小。
2. `size <= 1MB`：写内存容器（`mem`）。
3. `size > 1MB`：写本地 `.txt` 文件（`file/txt`）。

行为细节：
1. 内存分支：
   - `store=mem`
   - `refId=rawref:v1:mem:{id}`
   - `storeReason=size_le_1mb_mem`
2. 文件分支：
   - 文件名唯一：`{yyyyMMddHHmmss}-{tenantId}-{uuid}.txt`
   - `store=file`（或 `txt`）
   - `refId=rawref:v1:file:{filename}`
   - `accessUrl=http://ip:port/{filename}.txt`（或统一路径）
   - `storeReason=size_gt_1mb_file`

注意：
1. 需防止并发重名，必须使用 UUID。
2. 必须对目录自动创建、失败重试、写入异常做日志和错误处理。

---

## 6. 文件下载与本地目录约束

### 6.1 目录配置
建议配置项：
1. `agent.runtime.raw.file.base-dir`：本地落盘目录。
2. `agent.runtime.raw.file.public-base-url`：对外下载 URL 前缀。
3. `agent.runtime.raw.file.retention-days`：保留天数。
4. `agent.runtime.raw.file.max-read-bytes`：单次读取上限。

### 6.2 下载 URL
建议固定为：

`{public-base-url}/{filename}.txt`

例如：

`http://127.0.0.1:8080/api/v1/raw/files/20260207-tenantA-3f6a9d7a.txt`

说明：
1. 用户提出的 `http://ip:端口/文件名.txt` 可通过 `public-base-url` 实现。
2. 推荐保留 `/api/v1/raw/files/` 路径前缀，便于权限控制与审计。

---

## 7. 统一提取能力（Controller 方案）

### 7.1 新增控制器
建议新增：

`src/main/java/com/example/agent/api/http/controller/RawResultController.java`

### 7.2 统一提取接口
建议接口一：
1. `GET /api/v1/raw/resolve?ref={rawRef}`
2. 输入支持：
   - `rawref:v1:...`
   - 兼容旧 `raw:...`
   - 文件下载 URL（自动转为 file/txt 解析）
3. 返回：
   - `refId`
   - `storeType`
   - `storeReason`
   - `mediaType`
   - `size`
   - `payload`

建议接口二：
1. `GET /api/v1/raw/files/{filename}.txt`
2. 直接下载文本文件。
3. 可选 `Content-Disposition` 附件下载。

### 7.3 统一提取流程
1. 解析 `ref`（`RawRefCodec.parse`）。
2. 根据 `storeType` 分发读取：
   - `mem`：内存容器读取。
   - `redis`：Redis 读取。
   - `file/txt`：本地文件读取。
3. 返回标准 DTO。

---

## 8. Bean 装配与默认策略

### 8.1 默认策略
1. 默认 `RawResultStore` 使用 `RedisRawResultStore`（满足“默认保存 Redis”要求）。
2. 当配置 `agent.runtime.raw.store-mode=size-aware` 时切换到 `SizeAwareRawResultStore`。

### 8.2 失败降级
1. Redis 不可用时：
   - 可选降级到 `InMemoryRawResultStore`。
   - 记录 `warn` 日志并写明降级原因。
2. 文件写入失败时：
   - 返回错误并记录 `error` 日志（含 refId/source/size/异常堆栈）。

---

## 9. 日志与可观测性落地点

### 9.1 必加日志
1. 存储开始：`source/mediaType/bytes/strategy`。
2. 存储完成：`storeType/refId/size/hash/durationMs`。
3. 统一提取：`ref/refId/storeType/hitOrMiss/durationMs`。
4. 异常捕获：必须输出上下文与堆栈。

### 9.2 建议指标
1. `raw.store.count`
2. `raw.store.bytes`
3. `raw.store.file.count`
4. `raw.resolve.count`
5. `raw.resolve.latency.ms`

---

## 10. 安全与边界

1. 文件读取必须防目录穿越，仅允许白名单目录与 `.txt` 后缀。
2. URL 下载接口应具备租户校验（沿用现有 `TenantContextFilter`）。
3. 读取超大文件时限制最大返回字节，防止内存放大。
4. 对引用不存在场景返回 `404`，对过期场景可返回 `410`。

---

## 11. 实施步骤建议

### 阶段一：模型与协议
1. 扩展 `RawRef` 字段。
2. 新增 `RawRefCodec` 与解析 DTO。

### 阶段二：存储实现
1. 实现 `RedisRawResultStore`。
2. 实现 `SizeAwareRawResultStore`。
3. 增加配置类 `RawStoreProperties`。

### 阶段三：统一提取
1. 实现 `RawResultResolveService`。
2. 新增 `RawResultController` 的 `resolve` 与 `file-download` 接口。

### 阶段四：调用方兼容
1. `ModelInvocationService`、`ToolExecutor` 输出优先回填 `refId`。
2. 保留对旧 `key/raw:` 的兼容读取。

### 阶段五：测试
1. 单元测试：阈值分流、编码解码、Redis/File/Mem 读取。
2. 集成测试：Controller 统一提取与文件下载。
3. 回归测试：现有 `rawRef` 相关链路不回归。

---

## 12. 需你确认的两点

1. `rawRef` 对外主字段是否切换为 `refId`（推荐切换，保留 `key` 兼容）？
2. 文件下载 URL 是否固定采用 `/api/v1/raw/files/{filename}.txt` 路径（推荐）？

如果确认，我下一步将按该方案开始代码实现，并先提交“阶段一+阶段二”的最小可运行版本。
