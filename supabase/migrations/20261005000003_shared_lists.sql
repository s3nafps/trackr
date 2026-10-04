-- Shared watchlists: a named list of titles that several friends add to and pick from together.
-- Members see and edit a list's items; only the owner manages members (friends only); members may leave.

create table public.shared_lists (
  id uuid primary key default gen_random_uuid(),
  name text not null check (char_length(btrim(name)) between 1 and 60),
  owner_id uuid not null references public.profiles(id) on delete cascade,
  created_at timestamptz not null default now()
);
create index shared_lists_owner_idx on public.shared_lists (owner_id);

create table public.shared_list_members (
  list_id uuid not null references public.shared_lists(id) on delete cascade,
  user_id uuid not null references public.profiles(id) on delete cascade,
  added_at timestamptz not null default now(),
  primary key (list_id, user_id)
);
create index shared_list_members_user_idx on public.shared_list_members (user_id);

create table public.shared_list_items (
  list_id uuid not null references public.shared_lists(id) on delete cascade,
  source text not null check (source in ('tmdb', 'anilist')),
  external_id text not null,
  media_type text not null check (media_type in ('movie', 'tv', 'anime')),
  title text not null check (char_length(title) between 1 and 300),
  poster_url text,
  added_by uuid references public.profiles(id) on delete set null,
  created_at timestamptz not null default now(),
  primary key (list_id, source, external_id)
);

-- Membership check for policies; security definer so the members table's own policies don't recurse.
-- Like are_friends, it only answers about the caller.
create or replace function public.is_list_member(list uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1 from public.shared_list_members m where m.list_id = list and m.user_id = (select auth.uid())
  );
$$;
revoke all on function public.is_list_member(uuid) from public, anon;
grant execute on function public.is_list_member(uuid) to authenticated;

-- The owner is always a member.
create or replace function public.shared_lists_add_owner()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  insert into public.shared_list_members (list_id, user_id) values (new.id, new.owner_id);
  return new;
end;
$$;
revoke all on function public.shared_lists_add_owner() from public, anon, authenticated;
create trigger shared_lists_add_owner_trg
  after insert on public.shared_lists
  for each row execute function public.shared_lists_add_owner();

-- ---------- RLS ----------
alter table public.shared_lists enable row level security;
alter table public.shared_list_members enable row level security;
alter table public.shared_list_items enable row level security;

-- owner_id too: INSERT ... RETURNING checks this before the AFTER trigger has made the owner a member.
create policy "members see their lists" on public.shared_lists
  for select to authenticated using (owner_id = (select auth.uid()) or public.is_list_member(id));
create policy "create lists as owner" on public.shared_lists
  for insert to authenticated with check (owner_id = (select auth.uid()));
create policy "owner renames" on public.shared_lists
  for update to authenticated
  using (owner_id = (select auth.uid())) with check (owner_id = (select auth.uid()));
create policy "owner deletes" on public.shared_lists
  for delete to authenticated using (owner_id = (select auth.uid()));

create policy "members see members" on public.shared_list_members
  for select to authenticated using (public.is_list_member(list_id));
-- Only the owner adds people, and only their accepted friends.
create policy "owner adds friends" on public.shared_list_members
  for insert to authenticated
  with check (
    exists (select 1 from public.shared_lists l where l.id = list_id and l.owner_id = (select auth.uid()))
    and public.are_friends((select auth.uid()), user_id)
  );
-- A member leaves; the owner removes others. The owner can't leave their own list (they delete it instead).
create policy "leave or remove" on public.shared_list_members
  for delete to authenticated
  using (
    exists (
      select 1 from public.shared_lists l
      where l.id = list_id and l.owner_id <> user_id
        and (user_id = (select auth.uid()) or l.owner_id = (select auth.uid()))
    )
  );

create policy "members see items" on public.shared_list_items
  for select to authenticated using (public.is_list_member(list_id));
create policy "members add items as self" on public.shared_list_items
  for insert to authenticated
  with check (public.is_list_member(list_id) and added_by = (select auth.uid()));
create policy "members remove items" on public.shared_list_items
  for delete to authenticated using (public.is_list_member(list_id));

-- ---------- grants ----------
-- Start from nothing (Supabase's default privileges grant everything on new tables), then grant what's used.
revoke all on public.shared_lists, public.shared_list_members, public.shared_list_items from public, anon, authenticated;
grant select, insert, delete on public.shared_lists, public.shared_list_members, public.shared_list_items to authenticated;
grant update (name) on public.shared_lists to authenticated;
