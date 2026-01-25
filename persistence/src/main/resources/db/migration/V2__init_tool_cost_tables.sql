create table if not exists tool_registry (
  tool_id text primary key,
  tenant_id text,
  tool_name text,
  tool_version text,
  tool_type text,
  status text,
  config jsonb,
  version bigint,
  created_at timestamptz,
  updated_at timestamptz,
  created_by text,
  updated_by text
);

create index if not exists idx_tool_registry_tenant_id on tool_registry (tenant_id);
create index if not exists idx_tool_registry_tool_name_version on tool_registry (tool_name, tool_version);
create index if not exists idx_tool_registry_tool_id on tool_registry (tool_id);

create table if not exists tenant_quota (
  quota_id uuid primary key,
  tenant_id text,
  quota_name text,
  quota_type text,
  limit_value numeric,
  used_value numeric,
  effective_from timestamptz,
  effective_to timestamptz,
  status text,
  version bigint,
  created_at timestamptz,
  updated_at timestamptz,
  created_by text,
  updated_by text
);

create index if not exists idx_tenant_quota_tenant_id on tenant_quota (tenant_id);
create index if not exists idx_tenant_quota_status on tenant_quota (status);
create index if not exists idx_tenant_quota_effective_from on tenant_quota (effective_from);

create table if not exists cost_ledger (
  ledger_id uuid primary key,
  tenant_id text,
  session_id text,
  task_id text,
  run_id text,
  tool_id text,
  tool_name text,
  tool_version text,
  cost_type text,
  amount numeric,
  currency text,
  usage_count numeric,
  occurred_at timestamptz,
  payload jsonb,
  version bigint,
  created_at timestamptz,
  updated_at timestamptz,
  created_by text,
  updated_by text
);

create index if not exists idx_cost_ledger_tenant_id on cost_ledger (tenant_id);
create index if not exists idx_cost_ledger_tenant_time on cost_ledger (tenant_id, occurred_at);
create index if not exists idx_cost_ledger_tenant_tool_time on cost_ledger (tenant_id, tool_id, occurred_at);

create table if not exists context_snapshot (
  snapshot_id uuid primary key,
  tenant_id text,
  session_id text,
  run_id text,
  snapshot_type text,
  token_count int,
  snapshot_at timestamptz,
  payload jsonb,
  version bigint,
  created_at timestamptz,
  updated_at timestamptz,
  created_by text,
  updated_by text
);

create index if not exists idx_context_snapshot_tenant_id on context_snapshot (tenant_id);
create index if not exists idx_context_snapshot_session_id on context_snapshot (session_id);
create index if not exists idx_context_snapshot_tenant_session_time on context_snapshot (tenant_id, session_id, snapshot_at);
