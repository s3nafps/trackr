-- Per-entry opt-in for airing notifications (Plan to Watch titles). Existing owner-only RLS covers the column.
alter table public.list_entries add column notify boolean not null default false;
