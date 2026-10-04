-- When an entry was marked Completed, for Year in review. Null for rows completed before this existed.
-- Apply before shipping an app build that syncs completed_at, or its upserts are rejected.
alter table public.list_entries add column completed_at timestamptz;
