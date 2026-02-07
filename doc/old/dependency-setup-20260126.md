# 依赖环境启动说明

> 本文用于启动本项目本地依赖环境：`PostgreSQL`、`Redis`、`Qdrant`。

## 前置条件

- 已安装 `Docker Desktop` 或 `Docker Engine`
- 已启用 `Docker Compose v2`

## 启动依赖

```bash
docker compose -f docker/compose.yaml up -d
```

## 检查状态

```bash
docker compose -f docker/compose.yaml ps
```

## 连接信息

- `PostgreSQL`
  - 地址: `localhost:5432`
  - 数据库: `agent`
  - 用户: `agent`
  - 密码: `agent`

- `Redis`
  - 地址: `localhost:6379`

- `Qdrant`
  - 地址: `http://localhost:6333`

## 应用侧配置建议

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/agent
    username: agent
    password: agent
  data:
    redis:
      host: localhost
      port: 6379

agent:
  storage:
    mode: postgres
  memory:
    vector:
      enabled: true
      provider: qdrant
      base-url: http://localhost:6333
```

## 停止与清理

```bash
# 停止并移除容器
docker compose -f docker/compose.yaml down

# 停止并清理数据卷（会清空数据）
docker compose -f docker/compose.yaml down -v
```
