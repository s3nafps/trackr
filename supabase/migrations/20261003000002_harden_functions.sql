alter function public.friendships_lock_parties() set search_path = public;
revoke all on function public.handle_new_user() from public, anon, authenticated;
-- are_friends may only be asked about the caller, so it cannot be used to probe other users' friendships
create or replace function public.are_friends(a uuid, b uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select a = (select auth.uid()) and exists (
    select 1 from public.friendships f
    where f.status = 'accepted'
      and ((f.requester_id = a and f.addressee_id = b)
        or (f.requester_id = b and f.addressee_id = a))
  );
$$;
