# ai-agent-book-part1-coverage-v3 证据回填材料
- 日期：2026-01-27

## 1) ReAct 循环证据
| 模块 | 证据位置 | 关键词/检索命令 | 说明 |
| --- | --- | --- | --- |
| ReAct 循环入口 | `src/main/java/com/example/agent/runtime/AgentRuntime.java` `executeStep` | `rg -n "REACT" src/main/java/com/example/agent/runtime/AgentRuntime.java` | `stepType=REACT` 进入 ReAct 循环执行路径 | 
| ReAct 循环执行 | `src/main/java/com/example/agent/runtime/ReactLoopService.java` `run/think/act/observe` | `rg -n "ReactLoopService|THINK_|ACT_|OBSERVE_|REACT_ITERATION" src/main/java/com/example/agent/runtime/ReactLoopService.java` | 明确 Think/Act/Observe 阶段与迭代事件打点 | 
| ReAct 事件类型 | `src/main/java/com/example/agent/domain/event/EventType.java` | `rg -n "THINK_STARTED|ACT_STARTED|OBSERVE_RECORDED|REACT_ITERATION|REACT_STOPPED" src/main/java/com/example/agent/domain/event/EventType.java` | ReAct 事件类型定义 | 
| ReAct 规划入口 | `src/main/java/com/example/agent/planning/PlannerService.java` `shouldUseReact/buildHeuristicPlan` | `rg -n "shouldUseReact|REACT" src/main/java/com/example/agent/planning/PlannerService.java` | 规划阶段可产出 `REACT` 步骤 | 

## 2) 终止护栏证据
| 模块 | 证据位置 | 关键词/检索命令 | 说明 |
| --- | --- | --- | --- |
| 配置类 | `src/main/java/com/example/agent/runtime/ReactRuntimeProperties.java` | `rg -n "ReactRuntimeProperties|maxIterations|minIterations|observationWindow" src/main/java/com/example/agent/runtime/ReactRuntimeProperties.java` | 配置类声明 `maxIterations/minIterations/observationWindow` | 
| 配置默认值 | `src/main/resources/application.yml` | `rg -n "agent.runtime.maxIterations|agent.runtime.minIterations|agent.runtime.observationWindow" src/main/resources/application.yml` | 默认配置项与 quickstart 对齐 | 
| 生效点（上限） | `src/main/java/com/example/agent/runtime/ReactLoopService.java` `run` | `rg -n "getMaxIterations" src/main/java/com/example/agent/runtime/ReactLoopService.java` | 循环上限 `maxIterations` 生效 | 
| 生效点（最小轮次） | `src/main/java/com/example/agent/runtime/ReactStopEvaluator.java` `evaluate` | `rg -n "minIterations" src/main/java/com/example/agent/runtime/ReactStopEvaluator.java` | 未达 `minIterations` 不允许提前完成 | 
| 生效点（观察窗口） | `src/main/java/com/example/agent/runtime/ObservationWindowBuffer.java` | `rg -n "ObservationWindowBuffer|maxSize" src/main/java/com/example/agent/runtime/ObservationWindowBuffer.java` | 观察窗口按条数裁剪 | 

## 3) 测试证据
| 模块 | 证据位置 | 关键词/检索命令 | 说明 |
| --- | --- | --- | --- |
| ReAct 循环测试 | `src/test/java/com/example/agent/runtime/ReactLoopServiceTest.java` | `rg -n "maxIterationsStops|observationWindowIsApplied|minIterationsPreventsEarlyStop" src/test/java/com/example/agent/runtime/ReactLoopServiceTest.java` | 覆盖 maxIterations/minIterations/observationWindow 三项护栏 | 
| maxIterations 生效 | `ReactLoopServiceTest#maxIterationsStopsAndEmitsEvent` | `rg -n "maxIterationsStopsAndEmitsEvent" src/test/java/com/example/agent/runtime/ReactLoopServiceTest.java` | 验证达到上限停止并发出 `REACT_STOPPED` | 
| observationWindow 生效 | `ReactLoopServiceTest#observationWindowIsApplied` | `rg -n "observationWindowIsApplied" src/test/java/com/example/agent/runtime/ReactLoopServiceTest.java` | 验证观察窗口裁剪为 2 条 | 
| minIterations 生效 | `ReactLoopServiceTest#minIterationsPreventsEarlyStop` | `rg -n "minIterationsPreventsEarlyStop" src/test/java/com/example/agent/runtime/ReactLoopServiceTest.java` | 第 1 轮完成信号被延后至第 2 轮 | 
