flowchart TB
  %% ========== 顶层入口 ==========
  U[用户/调用方] --> API[api (对外接口层)]
  API --> GOV[governance (治理)]
  API --> SEC[security (安全)]
  API --> ORCH[orchestration (编排)]
  API --> STR[streaming (事件流/SSE)]
  API --> HIS[history (审计/历史)]
  API --> SCH[scheduler (调度)]

  %% ========== 编排到运行时 ==========
  ORCH --> RT[runtime (运行时执行)]
  RT --> CAP[capabilities (能力层)]
  ORCH --> CAP

  %% ========== 运行时与能力层 ==========
  CAP --> LLM[capabilities/llm]
  CAP --> TOOLS[capabilities/tools]
  CAP --> MEM[capabilities/memory]
  CAP --> CTX[capabilities/context]

  %% ========== 规划/推理/反思 ==========
  ORCH --> PLAN[planning (规划)]
  PLAN --> RT
  RT --> REASON[reasoning (推理策略)]
  RT --> REFL[reflection (质量审查/反思)]

  %% ========== 预算横切 ==========
  BUD[budget (预算)] --> CTX
  BUD --> LLM
  BUD --> RT

  %% ========== 启动与公共 ==========
  BOOT[bootstrap (启动/装配)] --> API
  BOOT --> ORCH
  BOOT --> RT
  COMMON[common (通用)] --> API
  COMMON --> ORCH
  COMMON --> RT
  COMMON --> CAP
  COMMON --> GOV
  COMMON --> SEC
  COMMON --> STR
  COMMON --> HIS
  COMMON --> SCH

  %% ========== 流式与历史 ==========
  RT --> STR
  ORCH --> STR
  STR --> HIS

  %% ========== 视觉提示 ==========
  classDef edge fill:#fff,stroke:#333,stroke-width:1px;
  class U,API,GOV,SEC,ORCH,RT,CAP,PLAN,REASON,REFL,BUD,STR,HIS,SCH,BOOT,COMMON edge;

怎么读：

入口在 api/：HTTP Controller 接请求，先过安全/治理，再进入编排。

编排在 orchestration/：决定走 planning 还是直接 runtime，负责任务提交、路由、多智能体协调。

执行在 runtime/：真正跑 step（ReAct/LLM/工具），并写事件、产出结果。

能力在 capabilities/：模型/工具/记忆/上下文是 runtime 的“发动机和材料”。

budget/security/governance/common 属于横切能力，几乎哪里都可能依赖它们。

sequenceDiagram
  autonumber
  participant U as 用户/调用方
  participant C as api/http/controller
  participant S as security/auth + security/redaction
  participant G as governance (policy/ratelimit/circuitbreaker/approval/replay)
  participant O as orchestration (task/workflow/multiagent)
  participant P as planning
  participant R as runtime (engine)
  participant L as capabilities/llm
  participant T as capabilities/tools
  participant M as capabilities/memory
  participant X as capabilities/context
  participant B as budget (token/trim/cost)
  participant E as streaming (sse/payload)
  participant H as history (eventlog/timeline)

  U->>C: HTTP 请求 (Task/Mcp/Memory/...)
  C->>S: 鉴权/租户上下文 + 脱敏策略准备
  S->>G: 限流/熔断/策略/审批预检
  G->>O: 提交任务/选择工作流/多智能体协调

  alt 需要规划
    O->>P: 生成 Plan
    P->>L: 调用模型生成步骤
    L-->>P: PlanResult(steps)
  end

  O->>R: 执行步骤(step loop)
  R->>X: 构建 ContextSnapshot / EvidencePack
  R->>B: 预算分配/剪裁/压缩(可选)
  B-->>R: 裁剪后的上下文/预算状态

  alt 需要工具
    R->>T: ToolExecutor/MCP 调用
    T-->>R: Tool Result (raw)
    R->>X: 记录引用/证据(EvidencePack)
  end

  R->>L: LLM 总结/生成输出(可选)
  L-->>R: StepResult / Final Answer
  R->>E: 发布事件(SSE payload)
  E->>H: 落盘事件日志/时间线
  R-->>C: TaskResponse/状态
  C-->>U: HTTP 响应

怎么读：

Controller 不做业务：只做协议适配/参数/返回。

治理/安全先行：限流/熔断/审批/脱敏在进入核心前做。

Orchestration 决定“怎么跑”：要不要规划、多智能体怎么分工、选哪个 workflow。

Runtime 决定“怎么执行”：一边执行 step，一边跟 capabilities 交互拿结果。

Streaming/History 是旁路：做事件推送、审计回放。

flowchart LR
  ENG[runtime/engine] --> MODEL[runtime/model]
  ENG --> RAW[runtime/raw]
  ENG --> STRU[runtime/structured]
  ENG --> SUM[runtime/summary]
  ENG --> OUT[runtime/output]
  ENG --> REC[runtime/recovery]
  ENG --> CTRL[runtime/control]

  RAW --> MODEL
  STRU --> MODEL
  SUM --> MODEL
  REC --> ENG
  CTRL --> ENG
  OUT --> ENG

  CAP[capabilities] --> ENG
  ENG --> STREAM[streaming]

  classDef m fill:#fff,stroke:#333,stroke-width:1px;
  class ENG,MODEL,RAW,STRU,SUM,OUT,REC,CTRL,CAP,STREAM m;

一句话解释：

engine 是总控（StepRuntimeService/ReactLoopService/LlmStepService）

model 是标准数据结构（StepSpec/StepResult 分层）

raw/structured/summary 是三层收口（原始、可依赖结构化、人类可读摘要）

recovery/control/output 是执行保障（重试、停止/暂停、最终输出）

streaming 把过程“广播出去”（SSE/审计）
