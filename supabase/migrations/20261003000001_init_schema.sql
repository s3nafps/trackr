-- Trackr schema: profiles, list_entries, friendships + RLS + activity view

create extension if not exists pgcrypto;

-- ---------- profiles ----------
create table public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  username text not null unique,
  username_set boolean not null default false,
  avatar_url text,
  invite_code text not null unique
    default upper(substr(encode(gen_random_bytes(6), 'hex'), 1, 8)),
  created_at timestamptz not null default now(),
  constraint username_format check (username ~ '^[A-Za-z0-9_.]{3,24}$')
);

create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  base text;
  candidate text;
begin
  base := lower(regexp_replace(
    coalesce(new.raw_user_meta_data->>'name', new.raw_user_meta_data->>'full_name',
             split_part(new.email, '@', 1), 'user'),
    '[^A-Za-z0-9]', '', 'g'));
  if length(base) < 3 then base := base || 'user'; end if;
  base := left(base, 18);
  candidate := base;
  while exists (select 1 from public.profiles where username = candidate) loop
    candidate := base || floor(random() * 9000 + 1000)::int::text;
  end loop;
  insert into public.profiles (id, username, avatar_url)
  values (new.id, candidate,
          coalesce(new.raw_user_meta_data->>'avatar_url', new.raw_user_meta_data->>'picture'));
  return new;
end;
$$;

create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

-- ---------- list_entries ----------
create table public.list_entries (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  source text not null check (source in ('tmdb', 'anilist')),
  external_id text not null,
  media_type text not null check (media_type in ('movie', 'tv', 'anime')),
  title text not null,
  poster_url text,
  backdrop_url text,
  status text not null check (status in ('watching', 'completed', 'plan_to_watch', 'dropped')),
  rating smallint check (rating between 1 and 10),
  progress integer not null default 0 check (progress >= 0),
  total_episodes integer check (total_episodes >= 0),
  updated_at timestamptz not null default now(),
  unique (user_id, source, external_id)
);
create index list_entries_user_updated_idx on public.list_entries (user_id, updated_at desc);
create index list_entries_updated_idx on public.list_entries (updated_at desc);
create index list_entries_media_idx on public.list_entries (source, external_id);

-- ---------- friendships ----------
create table public.friendships (
  id uuid primary key default gen_random_uuid(),
  requester_id uuid not null references public.profiles(id) on delete cascade,
  addressee_id uuid not null references public.profiles(id) on delete cascade,
  status text not null default 'pending' check (status in ('pending', 'accepted')),
  created_at timestamptz not null default now(),
  constraint no_self_friending check (requester_id <> addressee_id)
);
-- one friendship per unordered pair
create unique index friendships_pair_idx
  on public.friendships (least(requester_id, addressee_id), greatest(requester_id, addressee_id));
create index friendships_addressee_idx on public.friendships (addressee_id);
create index friendships_requester_idx on public.friendships (requester_id);

-- ---------- helper (security definer avoids RLS recursion) ----------
create or replace function public.are_friends(a uuid, b uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1 from public.friendships f
    where f.status = 'accepted'
      and ((f.requester_id = a and f.addressee_id = b)
        or (f.requester_id = b and f.addressee_id = a))
  );
$$;
revoke all on function public.are_friends(uuid, uuid) from public, anon;
grant execute on function public.are_friends(uuid, uuid) to authenticated;

-- ---------- RLS ----------
alter table public.profiles enable row level security;
alter table public.list_entries enable row level security;
alter table public.friendships enable row level security;

create policy "profiles readable by authenticated" on public.profiles
  for select to authenticated using (true);
create policy "profiles insert own" on public.profiles
  for insert to authenticated with check (id = (select auth.uid()));
create policy "profiles update own" on public.profiles
  for update to authenticated
  using (id = (select auth.uid())) with check (id = (select auth.uid()));

create policy "entries read own or friends" on public.list_entries
  for select to authenticated
  using (user_id = (select auth.uid()) or public.are_friends((select auth.uid()), user_id));
create policy "entries insert own" on public.list_entries
  for insert to authenticated with check (user_id = (select auth.uid()));
create policy "entries update own" on public.list_entries
  for update to authenticated
  using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));
create policy "entries delete own" on public.list_entries
  for delete to authenticated using (user_id = (select auth.uid()));

create policy "friendships read parties" on public.friendships
  for select to authenticated
  using ((select auth.uid()) in (requester_id, addressee_id));
create policy "friendships request as self" on public.friendships
  for insert to authenticated
  with check (requester_id = (select auth.uid()) and status = 'pending');
create policy "friendships addressee accepts" on public.friendships
  for update to authenticated
  using (addressee_id = (select auth.uid()))
  with check (addressee_id = (select auth.uid()) and requester_id <> addressee_id);
create policy "friendships parties delete" on public.friendships
  for delete to authenticated
  using ((select auth.uid()) in (requester_id, addressee_id));

-- friendship updates may only change status (not the parties)
create or replace function public.friendships_lock_parties()
returns trigger language plpgsql as $$
begin
  if new.requester_id <> old.requester_id or new.addressee_id <> old.addressee_id then
    raise exception 'cannot change friendship parties';
  end if;
  return new;
end;
$$;
create trigger friendships_lock_parties_trg
  before update on public.friendships
  for each row execute function public.friendships_lock_parties();

-- ---------- grants ----------
revoke all on public.profiles, public.list_entries, public.friendships from anon;
grant select, insert, update on public.profiles to authenticated;
grant select, insert, update, delete on public.list_entries to authenticated;
grant select, insert, update, delete on public.friendships to authenticated;

-- ---------- friends activity feed ----------
-- security_invoker: list_entries RLS applies, so only self + accepted friends appear.
create view public.friend_activity
with (security_invoker = true) as
select
  le.id, le.user_id, p.username, p.avatar_url,
  le.source, le.external_id, le.media_type, le.title, le.poster_url,
  le.status, le.rating, le.progress, le.total_episodes, le.updated_at
from public.list_entries le
join public.profiles p on p.id = le.user_id
where le.user_id <> (select auth.uid());
revoke all on public.friend_activity from anon;
grant select on public.friend_activity to authenticated;
