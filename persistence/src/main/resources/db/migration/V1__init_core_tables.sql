create table if not exists task_run (
  task_run_id uuid primary key,
  task_id text,
  run_id text,
  tenant_id text,
  session_id text,
  status text,
  start_at timestamptz,
  end_at timestamptz,
  priority int,
  budget numeric
);

create index if not exists idx_task_run_tenant_id on task_run (tenant_id);
create index if not exists idx_task_run_status on task_run (status);

create table if not exists task_step (
  step_id uuid primary key,
  task_id text,
  run_id text,
  agent_id text,
  tool_id text,
  status text,
  start_at timestamptz,
  end_at timestamptz,
  error_class text
);

create index if not exists idx_task_step_task_id on task_step (task_id);
create index if not exists idx_task_step_status on task_step (status);
create index if not exists idx_task_step_agent_id on task_step (agent_id);

create table if not exists event_log (
  event_id uuid primary key,
  task_id text,
  run_id text,
  tenant_id text,
  event_type text,
  seq bigint,
  stream_id text,
  timestamp timestamptz,
  payload jsonb
);

create index if not exists idx_event_log_task_id on event_log (task_id);
create index if not exists idx_event_log_tenant_id on event_log (tenant_id);
create index if not exists idx_event_log_timestamp on event_log (timestamp);

create table if not exists audit_log (
  audit_id uuid primary key,
  tenant_id text,
  actor_id text,
  action text,
  resource_type text,
  resource_id text,
  decision text,
  timestamp timestamptz
);

create index if not exists idx_audit_log_tenant_id on audit_log (tenant_id);
create index if not exists idx_audit_log_timestamp on audit_log (timestamp);
create index if not exists idx_audit_log_action on audit_log (action);
