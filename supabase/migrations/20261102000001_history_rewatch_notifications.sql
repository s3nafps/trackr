-- v1.14: rewatch counts, watch history, and the feed behind friend notifications. Only adds things, so apps already
-- out keep working (they don't send rewatch_count, and an upsert leaves columns it isn't sent alone).

-- ---------- rewatches ----------
alter table public.list_entries
  add column if not exists rewatch_count integer not null default 0
  constraint list_entries_rewatch_count_range check (rewatch_count between 0 and 99);

-- ---------- watch history ----------
-- One row per logged viewing: episodes from_episode..to_episode (null for a movie), or a 'rewatch' marker. Ids come
-- from the device so a retried push can't duplicate a row. Rows are never edited; corrections delete and re-add.
create table if not exists public.watch_events (
  id uuid primary key,
  user_id uuid not null references public.profiles(id) on delete cascade,
  source text not null check (source in ('tmdb', 'anilist')),
  external_id text not null check (char_length(external_id) between 1 and 64),
  media_type text not null check (media_type in ('movie', 'tv', 'anime')),
  title text not null check (char_length(title) between 1 and 300),
  kind text not null check (kind in ('watched', 'rewatch')),
  from_episode integer check (from_episode >= 0),
  to_episode integer check (to_episode >= 0),
  watched_at timestamptz not null,
  created_at timestamptz not null default now()
);
create index if not exists watch_events_user_created_idx on public.watch_events (user_id, created_at);

alter table public.watch_events enable row level security;
create policy "history read own" on public.watch_events
  for select to authenticated using (user_id = (select auth.uid()));
create policy "history add own" on public.watch_events
  for insert to authenticated with check (user_id = (select auth.uid()));
create policy "history delete own" on public.watch_events
  for delete to authenticated using (user_id = (select auth.uid()));

-- Supabase's default privileges grant everything on new tables; start from nothing.
revoke all on public.watch_events from public, anon, authenticated;
grant select, insert, delete on public.watch_events to authenticated;

-- ---------- friend notifications ----------
-- When a request was accepted, so the requester can be told.
alter table public.friendships add column if not exists accepted_at timestamptz;

create or replace function public.friendships_stamp_accepted()
returns trigger language plpgsql set search_path = public as $$
begin
  if new.status = 'accepted' and old.status is distinct from 'accepted' then
    new.accepted_at := now();
  end if;
  return new;
end;
$$;
create trigger friendships_stamp_accepted_trg
  before update on public.friendships
  for each row execute function public.friendships_stamp_accepted();

-- What happened to the caller since [since]: friend requests, accepted requests, comments and reactions on their
-- entries, and recommendations. Security invoker: the caller's own row-level security decides what it can see.
create or replace function public.social_notifications(since timestamptz)
returns table (
  kind text, actor text, title text, detail text, source text, external_id text, media_type text, at timestamptz
)
language sql
stable
security invoker
set search_path = public
as $$
  select * from (
    select 'friend_request'::text, p.username, null::text, null::text, null::text, null::text, null::text, f.created_at
    from public.friendships f join public.profiles p on p.id = f.requester_id
    where f.addressee_id = (select auth.uid()) and f.status = 'pending' and f.created_at > since
    union all
    select 'friend_accepted', p.username, null, null, null, null, null, f.accepted_at
    from public.friendships f join public.profiles p on p.id = f.addressee_id
    where f.requester_id = (select auth.uid()) and f.status = 'accepted' and f.accepted_at > since
    union all
    select 'comment', p.username, le.title, c.body, le.source, le.external_id, le.media_type, c.created_at
    from public.entry_comments c
      join public.list_entries le on le.id = c.entry_id
      join public.profiles p on p.id = c.user_id
    where le.user_id = (select auth.uid()) and c.user_id <> (select auth.uid()) and c.created_at > since
    union all
    select 'reaction', p.username, le.title, r.emoji, le.source, le.external_id, le.media_type, r.created_at
    from public.entry_reactions r
      join public.list_entries le on le.id = r.entry_id
      join public.profiles p on p.id = r.user_id
    where le.user_id = (select auth.uid()) and r.user_id <> (select auth.uid()) and r.created_at > since
    union all
    select 'recommendation', p.username, rc.title, rc.note, rc.source, rc.external_id, rc.media_type, rc.created_at
    from public.recommendations rc join public.profiles p on p.id = rc.from_user
    where rc.to_user = (select auth.uid()) and not rc.seen and rc.created_at > since
  ) n (kind, actor, title, detail, source, external_id, media_type, at)
  order by at
  limit 100;
$$;

revoke all on function public.social_notifications(timestamptz) from public, anon;
grant execute on function public.social_notifications(timestamptz) to authenticated;
