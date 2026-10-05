-- Live updates (app: data/realtime/LiveUpdates.kt). Supabase Realtime only broadcasts changes of tables in the
-- supabase_realtime publication. Before sending an insert or update it checks the subscriber's RLS SELECT policies,
-- so each table keeps exactly the visibility it already has; deletes carry only the primary key and are not checked.
-- The app treats every event as "something changed" and refetches through the normal, RLS-protected API.
do $$
declare
  t text;
begin
  foreach t in array array[
    'list_entries', 'friendships', 'entry_reactions', 'entry_comments', 'recommendations',
    'shared_list_items', 'shared_list_members'
  ] loop
    if not exists (
      select 1 from pg_publication_tables
      where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = t
    ) then
      execute format('alter publication supabase_realtime add table public.%I', t);
    end if;
  end loop;
end $$;
