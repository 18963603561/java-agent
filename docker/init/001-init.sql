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

CREATE TABLE IF NOT EXISTS memory_records (
  memory_id TEXT PRIMARY KEY,
  session_id TEXT,
  task_id TEXT,
  content TEXT,
  summary TEXT,
  embedding_ref TEXT,
  tenant_id TEXT NOT NULL,
  layer TEXT,
  created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_memory_session ON memory_records (tenant_id, session_id);

CREATE TABLE IF NOT EXISTS memory_chunks (
  chunk_id TEXT PRIMARY KEY,
  memory_id TEXT NOT NULL,
  content TEXT NOT NULL,
  embedding_ref TEXT,
  position INT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_memory_chunks_memory ON memory_chunks (memory_id);