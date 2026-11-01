-- my_profile() returned one composite, which PostgREST sends as a JSON object; the app reads it as a one-row list
-- (decodeSingle), so loading the own profile failed ("Expected start of the array '['"): no invite code, and
-- username edits failed. As setof it comes back as an array. Apps from 1.13.1 read either shape.
drop function if exists public.my_profile();
create function public.my_profile()
returns setof public.profiles
language sql
stable
security definer
set search_path = public
as $$
  select p.* from public.profiles p where p.id = (select auth.uid());
$$;

revoke all on function public.my_profile() from public, anon;
grant execute on function public.my_profile() to authenticated;

notify pgrst, 'reload schema';
