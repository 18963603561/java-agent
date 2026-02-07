# 记忆流程与分层检索分析报告

## 范围与证据
```
src/main/java/com/example/agent/runtime/AgentRuntime.java
src/main/java/com/example/agent/memory/MemoryRecallService.java
src/main/java/com/example/agent/memory/MemoryStore.java
src/main/java/com/example/agent/memory/MemoryWriteService.java
src/main/java/com/example/agent/memory/RecentMemoryStore.java
src/main/java/com/example/agent/memory/SemanticMemoryStore.java
src/main/java/com/example/agent/memory/CompressedMemoryStore.java
src/main/java/com/example/agent/memory/MemoryPolicy.java
src/main/java/com/example/agent/memory/MemoryRecord.java
src/main/java/com/example/agent/memory/MemoryRepository.java
src/main/java/com/example/agent/memory/InMemoryMemoryRepository.java
src/main/java/com/example/agent/memory/JdbcMemoryRepository.java
src/main/java/com/example/agent/memory/VectorStore.java
src/main/java/com/example/agent/memory/QdrantVectorStore.java
src/main/java/com/example/agent/gateway/controller/MemoryController.java
```
用于确认记忆召回、写入、检索与压缩链路。

## 记忆流程概览
```
请求进入
  -> AgentRuntime 触发记忆召回
  -> MemoryRecallService 生成召回请求
  -> MemoryStore 分层检索并返回记录
  -> AgentRuntime 注入上下文并执行任务
  -> 任务结束后 MemoryWriteService 落盘
  -> MemoryStore 保存并触发自动压缩
```

## 记忆召回流程
- 运行入口在执行前触发召回

```
AgentRuntime
```
- 召回条件包括租户、会话与查询长度

```
MemoryRecallService
```
- 召回参数支持请求上下文覆盖

```
memoryRecallEnabled
memoryRecallForce
memoryRecallLimit
memoryRecallMinQueryLength
memoryRecallIncludeCompressed
memoryRecallMaxSummaryChars
memoryRecallMaxRecordChars
```
- 召回结果会被裁剪并生成摘要
- 召回结果注入运行上下文

```
memory
```

## 记忆写入与压缩流程
- 任务完成后写入记忆

```
MemoryWriteService
```
- 默认保存用户输入与最终输出
- 写入记录默认为 recent 层
- 写入后触发自动压缩判断

```
MemoryPolicy
```
- 压缩策略依据数量阈值、Token 阈值与时间阈值
- 压缩记录写入 compressed 层

## 分层记忆检索现状
### 已具备的分层能力
- 记忆记录包含层级字段

```
MemoryRecord.layer
```
- 存取层划分为 recent 与 compressed

```
RecentMemoryStore
CompressedMemoryStore
```
- 语义检索层基于向量检索

```
SemanticMemoryStore
VectorStore
```
- 检索时按固定顺序聚合

```
SemanticMemoryStore -> RecentMemoryStore -> CompressedMemoryStore
```

### 结论
- 当前具备基础分层检索能力
- 分层逻辑以结果聚合为主，缺少统一排序与层级配额

## 不足与风险
- 聚合顺序固定，缺少跨层重排序与权重
- 语义检索仅覆盖非压缩记录
- 压缩层摘要为拼接文本，缺少结构化总结
- 记忆片段模型存在但未接入检索
- 记忆清理与过期淘汰策略缺少落地实现

```
MemoryChunk
```

## 如果需要严格分层检索的改造建议
### 分层模型与策略
- 新增层级枚举与分层策略配置
- 支持不同层级的配额与优先级
- 引入时间衰减与置信度权重

### 检索路径改造
- 对 recent、compressed、semantic 独立检索
- 增加统一的排序与去重策略
- 支持按层级限制返回数量

### 压缩与摘要增强
- 使用模型生成结构化摘要
- 为压缩记录生成向量并进入语义层
- 保留来源记录关联，便于追溯

### 片段化与向量检索
- 将长文本拆分为片段并建立向量索引
- 使用片段命中反向定位原记录

### 接口与上下文控制
- 增加召回策略参数
- 支持仅召回指定层级
- 支持跨会话的长期记忆召回

## 结论
- 当前实现支持基础分层检索
- 若需要严格分层与可控检索策略，需要补充分层策略、统一排序、压缩摘要与片段向量检索