# `bootstrap` 包系统设计说明

## 1. 包定位与核心功能
- 包定位：`bootstrap` 是应用启动总入口，负责 Spring 容器引导与模块装配。
- 核心功能：
  - 定义 `AgentApplication` 主启动入口。
  - 通过 `scanBasePackages` 装配全部业务模块。
  - 启动成功后记录关键启动日志。

## 2. 分层线框图
```mermaid
graph TB
    A[JVM 启动] --> B[AgentApplication.main]
    B --> C[SpringApplication.run]
    C --> D[ApplicationContext]
    D --> E[扫描 com.example.agent]
    E --> F[装配模块 Bean]
    F --> G[系统进入 RUNNING]
```

## 3. 关键流程图
```mermaid
graph TB
    A[启动命令] --> B[读取配置]
    B --> C[初始化日志]
    C --> D[创建容器]
    D --> E[加载 Bean]
    E --> F{启动是否成功}
    F -->|是| G[服务就绪]
    F -->|否| H[启动失败退出]
```

## 4. 状态流转图
```mermaid
graph TB
    S0[BOOT_INIT] --> S1[CONTEXT_BUILDING]
    S1 -->|成功| S2[BEAN_WIRING]
    S1 -->|失败| S5[BOOT_FAILED]
    S2 -->|成功| S3[ENDPOINT_READY]
    S2 -->|失败| S5
    S3 --> S4[RUNNING]
    S5 --> S6[PROCESS_EXIT]
```

## 5. 类职责与设计原因
- `AgentApplication`：保持启动入口单一，避免部署行为分叉。

## 6. 关键类直接关系图
```mermaid
graph TB
    A[AgentApplication] --> B[SpringApplication]
    B --> C[ApplicationContext]
    C --> D[api/orchestration/runtime/...]
```

## 7. 重点说明
- 启动入口虽小但关键，决定系统是否可正常装配。
- 所有图统一使用 `graph TB`。

