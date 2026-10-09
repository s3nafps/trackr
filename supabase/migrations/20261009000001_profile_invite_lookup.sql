-- The signed-in user's own profile, and the invite-code lookup, as functions. Together with
-- 20261009000002_profile_invite_privacy.sql they keep invite codes off other users' reads. This migration only adds
-- functions, so the app that is out now keeps working until that one is applied.

-- The signed-in user's own profile, invite code included.
create or replace function public.my_profile()
returns public.profiles
language sql
stable
security definer
set search_path = public
as $$
  select p.* from public.profiles p where p.id = (select auth.uid());
$$;

-- The profile with this exact invite code, without the code: how a friend request by invite code finds its target.
create or replace function public.find_profile_by_invite(code text)
returns table (id uuid, username text, username_set boolean, avatar_url text)
language sql
stable
security definer
set search_path = public
as $$
  select p.id, p.username, p.username_set, p.avatar_url
  from public.profiles p
  where p.invite_code = upper(code);
$$;

revoke all on function public.my_profile() from public, anon;
grant execute on function public.my_profile() to authenticated;
revoke all on function public.find_profile_by_invite(text) from public, anon;
grant execute on function public.find_profile_by_invite(text) to authenticated;
