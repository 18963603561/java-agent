# 项目说明

## 开发启动
构建命令
```
mvn -q -DskipTests package
```
启动命令
```
mvn -q -pl server -am spring-boot:run
```
验证命令
```
curl http://localhost:8080/actuator/health
curl http://localhost:8080/api/v1/ping
```


## 测试提示
- 在 Windows 运行测试需确保 Docker Desktop 已启动，Testcontainers 可正常拉起 PostgreSQL 容器。
- 若测试提示无法连接 Docker，请先启动 Docker Desktop。

## 如何运行 M1 自动流水线

仅校验任务顺序与依赖:
```
.\scripts\run-m1.ps1 -DryRun
```

从指定任务开始:
```
.\scripts\run-m1.ps1 -FromTaskId M1-TASK-001
```

从失败任务继续:
1) 打开 `run/m1-status.json`, 找到 `status` 为 `failed` 的任务编号 (对象键)
2) 使用 `.\scripts\run-m1.ps1 -FromTaskId <任务编号>` 继续执行

## 自动实现+验收（autopilot）

示例：
```
.\scripts\autopilot-m1.ps1 -DryRun
.\scripts\autopilot-m1.ps1
.\scripts\autopilot-m1.ps1 -StartTaskId M1-TASK-012
```

失败后如何继续：
- `.\scripts\run-m1.ps1 -OnlyTaskId <id>` 或 `.\scripts\run-m1.ps1 -FromTaskId <id>`
- `.\scripts\autopilot-m1.ps1 -StartTaskId <id>`
