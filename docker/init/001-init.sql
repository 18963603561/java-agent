CREATE TABLE IF NOT EXISTS tasks (
  task_id TEXT PRIMARY KEY,
  workflow_id TEXT NOT NULL,
  status TEXT NOT NULL,
  request JSONB,
  result JSONB,
  idempotency_key TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  tenant_id TEXT NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_tasks_tenant_idem
  ON tasks (tenant_id, idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_tasks_tenant_status ON tasks (tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_tasks_tenant_updated ON tasks (tenant_id, updated_at);
COMMENT ON TABLE tasks IS '任务主表，记录工作流任务的生命周期、请求/结果与租户隔离信息，用于任务追踪、重试与幂等控制。';
COMMENT ON COLUMN tasks.task_id IS '任务唯一标识，作为任务主键与外部关联引用。';
COMMENT ON COLUMN tasks.workflow_id IS '任务所属的工作流或执行链路标识，用于跨表关联与流程追踪。';
COMMENT ON COLUMN tasks.status IS '任务当前状态，用于驱动流程分支与运营监控。';
COMMENT ON COLUMN tasks.request IS '任务原始请求载荷，保存业务输入的JSON结构用于审计与回放。';
COMMENT ON COLUMN tasks.result IS '任务处理结果载荷，保存业务输出的JSON结构用于查询与回执。';
COMMENT ON COLUMN tasks.idempotency_key IS '任务幂等键，按租户维度去重，防止重复提交导致重复执行。';
COMMENT ON COLUMN tasks.created_at IS '任务创建时间，用于排序与统计。';
COMMENT ON COLUMN tasks.updated_at IS '任务最近更新时间，用于检测状态变化与超时。';
COMMENT ON COLUMN tasks.tenant_id IS '租户标识，用于多租户数据隔离与权限控制。';

CREATE TABLE IF NOT EXISTS event_logs (
  event_id TEXT PRIMARY KEY,
  workflow_id TEXT NOT NULL,
  type TEXT NOT NULL,
  timestamp TIMESTAMPTZ NOT NULL,
  payload JSONB,
  tenant_id TEXT NOT NULL,
  seq BIGINT,
  stream_id TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_event_logs_tenant_workflow_ts
  ON event_logs (tenant_id, workflow_id, timestamp);
COMMENT ON TABLE event_logs IS '事件日志表，记录工作流中的关键事件，用于审计、追踪与流式回放。';
COMMENT ON COLUMN event_logs.event_id IS '事件唯一标识，用于去重与外部引用。';
COMMENT ON COLUMN event_logs.workflow_id IS '事件所属的工作流标识，用于按流程聚合查询。';
COMMENT ON COLUMN event_logs.type IS '事件类型，标识事件语义与处理策略。';
COMMENT ON COLUMN event_logs.timestamp IS '事件发生时间（业务时间），用于时间线展示与排序。';
COMMENT ON COLUMN event_logs.payload IS '事件载荷，保存事件上下文与业务数据的JSON结构。';
COMMENT ON COLUMN event_logs.tenant_id IS '租户标识，用于多租户数据隔离与权限控制。';
COMMENT ON COLUMN event_logs.seq IS '事件序号，用于同一流程内的有序重放与缺失检测。';
COMMENT ON COLUMN event_logs.stream_id IS '流式会话标识，用于SSE/流式输出的分组与定位。';
COMMENT ON COLUMN event_logs.created_at IS '日志写入时间，用于延迟分析与运维排查。';

CREATE TABLE IF NOT EXISTS step_records (
  step_id TEXT PRIMARY KEY,
  workflow_id TEXT NOT NULL,
  step_seq BIGINT NOT NULL,
  type TEXT,
  status TEXT,
  attempt INT,
  input JSONB,
  output JSONB,
  error_code TEXT,
  tenant_id TEXT NOT NULL,
  started_at TIMESTAMPTZ,
  completed_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_step_records_workflow_seq
  ON step_records (tenant_id, workflow_id, step_seq);
COMMENT ON TABLE step_records IS '步骤执行记录表，按工作流步骤保存输入输出与执行状态，用于可观测性与故障排查。';
COMMENT ON COLUMN step_records.step_id IS '步骤唯一标识，用于步骤级别的精确定位与关联。';
COMMENT ON COLUMN step_records.workflow_id IS '步骤所属的工作流标识，用于按流程聚合步骤记录。';
COMMENT ON COLUMN step_records.step_seq IS '步骤顺序号，用于流程重放与链路排序。';
COMMENT ON COLUMN step_records.type IS '步骤类型，例如工具调用、模型推理或数据处理，用于分类统计。';
COMMENT ON COLUMN step_records.status IS '步骤执行状态，用于流程控制与异常判断。';
COMMENT ON COLUMN step_records.attempt IS '步骤尝试次数或当前重试序号，用于重试策略分析。';
COMMENT ON COLUMN step_records.input IS '步骤输入参数的JSON结构，用于审计与复现。';
COMMENT ON COLUMN step_records.output IS '步骤输出结果的JSON结构，用于回溯与展示。';
COMMENT ON COLUMN step_records.error_code IS '步骤失败错误码，用于故障归因与报警聚合。';
COMMENT ON COLUMN step_records.tenant_id IS '租户标识，用于多租户数据隔离与权限控制。';
COMMENT ON COLUMN step_records.started_at IS '步骤开始时间，用于耗时统计与性能分析。';
COMMENT ON COLUMN step_records.completed_at IS '步骤完成时间，用于耗时统计与状态确认。';

CREATE TABLE IF NOT EXISTS token_usage (
  record_id TEXT PRIMARY KEY,
  usage_id TEXT NOT NULL,
  task_id TEXT NOT NULL,
  agent_id TEXT,
  model TEXT,
  provider TEXT,
  input_tokens INT,
  output_tokens INT,
  total_tokens INT,
  cost_usd NUMERIC(18, 6),
  created_at TIMESTAMPTZ NOT NULL,
  tenant_id TEXT NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_token_usage_unique
  ON token_usage (tenant_id, usage_id);
CREATE INDEX IF NOT EXISTS idx_token_usage_task
  ON token_usage (tenant_id, task_id);
COMMENT ON TABLE token_usage IS '模型调用消耗记录表，记录token与费用，用于成本核算与计费分析。';
COMMENT ON COLUMN token_usage.record_id IS '消耗记录唯一标识，用于账务追踪与对账。';
COMMENT ON COLUMN token_usage.usage_id IS '外部供应商返回的用量标识，用于与第三方账单对账。';
COMMENT ON COLUMN token_usage.task_id IS '关联的任务标识，用于按任务统计成本。';
COMMENT ON COLUMN token_usage.agent_id IS '执行该调用的Agent标识，用于按Agent归因统计。';
COMMENT ON COLUMN token_usage.model IS '模型名称或版本，用于成本与效果对比分析。';
COMMENT ON COLUMN token_usage.provider IS '模型提供方名称，用于供应商维度统计。';
COMMENT ON COLUMN token_usage.input_tokens IS '输入token数量，用于计费与性能分析。';
COMMENT ON COLUMN token_usage.output_tokens IS '输出token数量，用于计费与性能分析。';
COMMENT ON COLUMN token_usage.total_tokens IS '总token数量，作为计费汇总字段。';
COMMENT ON COLUMN token_usage.cost_usd IS '本次调用成本（美元），用于财务统计与预算控制。';
COMMENT ON COLUMN token_usage.created_at IS '记录创建时间，用于账单周期统计。';
COMMENT ON COLUMN token_usage.tenant_id IS '租户标识，用于多租户数据隔离与权限控制。';

CREATE TABLE IF NOT EXISTS scheduled_tasks (
  schedule_id TEXT PRIMARY KEY,
  cron TEXT NOT NULL,
  timezone TEXT NOT NULL,
  status TEXT NOT NULL,
  idempotency_key TEXT,
  tenant_id TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_schedules_idem
  ON scheduled_tasks (tenant_id, idempotency_key) WHERE idempotency_key IS NOT NULL;
COMMENT ON TABLE scheduled_tasks IS '定时任务配置表，定义周期性触发的任务规则与状态。';
COMMENT ON COLUMN scheduled_tasks.schedule_id IS '定时任务配置唯一标识，用于配置管理与执行关联。';
COMMENT ON COLUMN scheduled_tasks.cron IS 'Cron表达式，定义触发周期与时间规则。';
COMMENT ON COLUMN scheduled_tasks.timezone IS '时区配置，确保跨地域触发时间一致性。';
COMMENT ON COLUMN scheduled_tasks.status IS '定时任务状态，用于启停控制与监控。';
COMMENT ON COLUMN scheduled_tasks.idempotency_key IS '定时任务配置幂等键，防止重复创建配置。';
COMMENT ON COLUMN scheduled_tasks.tenant_id IS '租户标识，用于多租户数据隔离与权限控制。';
COMMENT ON COLUMN scheduled_tasks.created_at IS '配置创建时间，用于审计与排序。';
COMMENT ON COLUMN scheduled_tasks.updated_at IS '配置更新时间，用于变更追踪。';

CREATE TABLE IF NOT EXISTS scheduled_task_executions (
  execution_id TEXT PRIMARY KEY,
  schedule_id TEXT NOT NULL,
  status TEXT NOT NULL,
  started_at TIMESTAMPTZ,
  completed_at TIMESTAMPTZ,
  cost_usd NUMERIC(18, 6),
  token_usage INT,
  tenant_id TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_schedule_execs_schedule
  ON scheduled_task_executions (tenant_id, schedule_id);
COMMENT ON TABLE scheduled_task_executions IS '定时任务执行记录表，记录每次触发的执行结果与成本。';
COMMENT ON COLUMN scheduled_task_executions.execution_id IS '执行记录唯一标识，用于单次执行追踪。';
COMMENT ON COLUMN scheduled_task_executions.schedule_id IS '关联的定时任务配置标识，用于执行归属。';
COMMENT ON COLUMN scheduled_task_executions.status IS '执行状态，用于监控与告警。';
COMMENT ON COLUMN scheduled_task_executions.started_at IS '执行开始时间，用于耗时统计与超时判断。';
COMMENT ON COLUMN scheduled_task_executions.completed_at IS '执行完成时间，用于耗时统计与结果确认。';
COMMENT ON COLUMN scheduled_task_executions.cost_usd IS '本次执行成本（美元），用于成本归集。';
COMMENT ON COLUMN scheduled_task_executions.token_usage IS '本次执行消耗的token数量，用于计费核算。';
COMMENT ON COLUMN scheduled_task_executions.tenant_id IS '租户标识，用于多租户数据隔离与权限控制。';

CREATE TABLE IF NOT EXISTS memory_records (
  memory_id TEXT PRIMARY KEY,
  session_id TEXT,
  task_id TEXT,
  content TEXT,
  summary TEXT,
  embedding_ref TEXT,
  tenant_id TEXT NOT NULL,
  layer TEXT,
  created_at TIMESTAMPTZ NOT NULL,
  expires_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_memory_session ON memory_records (tenant_id, session_id);
CREATE INDEX IF NOT EXISTS idx_memory_expire ON memory_records (tenant_id, expires_at);
COMMENT ON TABLE memory_records IS '记忆主表，保存会话或任务相关的长期/短期记忆内容与摘要。';
COMMENT ON COLUMN memory_records.memory_id IS '记忆唯一标识，用于记忆检索与关联。';
COMMENT ON COLUMN memory_records.session_id IS '会话标识，用于按对话维度聚合记忆。';
COMMENT ON COLUMN memory_records.task_id IS '关联的任务标识，用于任务级记忆回溯。';
COMMENT ON COLUMN memory_records.content IS '记忆原文内容，用于检索与上下文构建。';
COMMENT ON COLUMN memory_records.summary IS '记忆摘要，用于快速检索与压缩展示。';
COMMENT ON COLUMN memory_records.embedding_ref IS '向量索引引用，用于语义检索定位。';
COMMENT ON COLUMN memory_records.tenant_id IS '租户标识，用于多租户数据隔离与权限控制。';
COMMENT ON COLUMN memory_records.layer IS '记忆层级标识，例如短期/长期，用于策略控制。';
COMMENT ON COLUMN memory_records.created_at IS '记忆创建时间，用于时间衰减与排序。';
COMMENT ON COLUMN memory_records.expires_at IS '记忆过期时间，用于清理与生命周期管理。';

CREATE TABLE IF NOT EXISTS memory_chunks (
  chunk_id TEXT PRIMARY KEY,
  memory_id TEXT NOT NULL,
  content TEXT NOT NULL,
  embedding_ref TEXT,
  position INT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_memory_chunks_memory ON memory_chunks (memory_id);
COMMENT ON TABLE memory_chunks IS '记忆分块表，将长文本按块存储以支持分段检索与向量化。';
COMMENT ON COLUMN memory_chunks.chunk_id IS '分块唯一标识，用于块级检索与引用。';
COMMENT ON COLUMN memory_chunks.memory_id IS '所属记忆标识，用于与主记忆关联。';
COMMENT ON COLUMN memory_chunks.content IS '分块内容，用于语义检索与上下文拼接。';
COMMENT ON COLUMN memory_chunks.embedding_ref IS '分块向量索引引用，用于向量检索。';
COMMENT ON COLUMN memory_chunks.position IS '分块在原文中的顺序位置，用于还原与排序。';
