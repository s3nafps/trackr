-- Invite codes are shared on purpose, not public. Until now every signed-in user could read every profile's code, because
-- the profiles table is readable for username search. Other users now get the public columns only: the owner reads
-- their own code through my_profile(), and friends are found by find_profile_by_invite(). Apply this only with the app
-- that uses those functions (v1.12.1 or later); older builds select every column and would fail.
revoke select on public.profiles from authenticated;
grant select (id, username, username_set, avatar_url, created_at) on public.profiles to authenticated;
