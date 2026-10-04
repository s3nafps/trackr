-- In-app account deletion (required by Google Play for apps with sign-up).
-- Deleting the auth user cascades: auth.users -> profiles -> list_entries + friendships (and auth sessions/identities).
create or replace function public.delete_account()
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  uid uuid := (select auth.uid());
begin
  if uid is null then
    raise exception 'not authenticated';
  end if;
  delete from auth.users where id = uid;
end;
$$;
revoke all on function public.delete_account() from public, anon;
grant execute on function public.delete_account() to authenticated;
