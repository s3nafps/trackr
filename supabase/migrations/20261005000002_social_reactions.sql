-- Reactions and comments on list entries (what the friends activity feed shows), and title recommendations.
-- Visibility rides on list_entries RLS: the subqueries below only find entries the caller may see (own or an
-- accepted friend's), so these rows are visible and writable exactly for the people who can see the entry.

-- ---------- reactions ----------
create table public.entry_reactions (
  entry_id uuid not null references public.list_entries(id) on delete cascade,
  user_id uuid not null references public.profiles(id) on delete cascade,
  emoji text not null check (emoji in ('🔥', '❤️', '😂', '😮', '👏')),
  created_at timestamptz not null default now(),
  primary key (entry_id, user_id, emoji)
);
create index entry_reactions_user_idx on public.entry_reactions (user_id);

alter table public.entry_reactions enable row level security;
create policy "reactions visible with their entry" on public.entry_reactions
  for select to authenticated
  using (exists (select 1 from public.list_entries le where le.id = entry_id));
create policy "react as self to visible entries" on public.entry_reactions
  for insert to authenticated
  with check (user_id = (select auth.uid()) and exists (select 1 from public.list_entries le where le.id = entry_id));
create policy "remove own reactions" on public.entry_reactions
  for delete to authenticated
  using (user_id = (select auth.uid()));

-- ---------- comments ----------
create table public.entry_comments (
  id uuid primary key default gen_random_uuid(),
  entry_id uuid not null references public.list_entries(id) on delete cascade,
  user_id uuid not null references public.profiles(id) on delete cascade,
  body text not null check (char_length(btrim(body)) between 1 and 500),
  created_at timestamptz not null default now()
);
create index entry_comments_entry_idx on public.entry_comments (entry_id, created_at);
create index entry_comments_user_idx on public.entry_comments (user_id);

alter table public.entry_comments enable row level security;
create policy "comments visible with their entry" on public.entry_comments
  for select to authenticated
  using (exists (select 1 from public.list_entries le where le.id = entry_id));
create policy "comment as self on visible entries" on public.entry_comments
  for insert to authenticated
  with check (user_id = (select auth.uid()) and exists (select 1 from public.list_entries le where le.id = entry_id));
-- The author can delete their comment; the entry's owner can delete any comment on their entry.
create policy "delete own comments or comments on own entries" on public.entry_comments
  for delete to authenticated
  using (
    user_id = (select auth.uid())
    or exists (select 1 from public.list_entries le where le.id = entry_id and le.user_id = (select auth.uid()))
  );

-- ---------- recommendations ----------
create table public.recommendations (
  id uuid primary key default gen_random_uuid(),
  from_user uuid not null references public.profiles(id) on delete cascade,
  to_user uuid not null references public.profiles(id) on delete cascade,
  source text not null check (source in ('tmdb', 'anilist')),
  external_id text not null,
  media_type text not null check (media_type in ('movie', 'tv', 'anime')),
  title text not null check (char_length(title) between 1 and 300),
  poster_url text,
  note text check (note is null or char_length(note) <= 300),
  seen boolean not null default false,
  created_at timestamptz not null default now(),
  constraint no_self_recommendation check (from_user <> to_user),
  -- One recommendation per title per pair: re-sending is a no-op, not spam.
  unique (from_user, to_user, source, external_id)
);
create index recommendations_to_idx on public.recommendations (to_user, created_at desc);

alter table public.recommendations enable row level security;
create policy "recommendations visible to both sides" on public.recommendations
  for select to authenticated
  using ((select auth.uid()) in (from_user, to_user));
create policy "recommend to friends as self" on public.recommendations
  for insert to authenticated
  with check (from_user = (select auth.uid()) and not seen and public.are_friends((select auth.uid()), to_user));
create policy "recipient marks seen" on public.recommendations
  for update to authenticated
  using (to_user = (select auth.uid()))
  with check (to_user = (select auth.uid()));
create policy "either side deletes" on public.recommendations
  for delete to authenticated
  using ((select auth.uid()) in (from_user, to_user));

-- ---------- grants ----------
-- Supabase's default privileges grant everything on new tables; start from nothing so the column grant below holds.
revoke all on public.entry_reactions, public.entry_comments, public.recommendations from public, anon, authenticated;
grant select, insert, delete on public.entry_reactions, public.entry_comments to authenticated;
grant select, insert, delete on public.recommendations to authenticated;
-- Only the seen flag can change after sending.
grant update (seen) on public.recommendations to authenticated;
