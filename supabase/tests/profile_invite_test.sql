-- Invite codes stay with their owner. Run as one statement, like the other tests: the final exception rolls everything
-- back, and success is 'PROFILE_INVITE_TESTS_PASSED'. Needs migration 20261009000001. It applies
-- 20261009000002 inside the rolled-back transaction, so the privacy change can be checked before it goes live.
do $$
declare
  a uuid := gen_random_uuid(); b uuid := gen_random_uuid();
  a_code text; b_code text; r record;
begin
  insert into auth.users (id, email, raw_user_meta_data) values
    (a, 'ia@test.local', '{"name":"Ina"}'), (b, 'ib@test.local', '{"name":"Ivo"}');
  select invite_code into a_code from public.profiles where id = a;
  select invite_code into b_code from public.profiles where id = b;
  if a_code is null or a_code = '' then raise exception 'FAIL: no invite code'; end if;

  -- the migration under test
  revoke select on public.profiles from authenticated;
  grant select (id, username, username_set, avatar_url, created_at) on public.profiles to authenticated;

  perform set_config('request.jwt.claims', json_build_object('sub', a, 'role', 'authenticated')::text, true);
  set local role authenticated;

  -- a reads their own code through my_profile()
  select * into r from public.my_profile();
  if r.invite_code is distinct from a_code then raise exception 'FAIL: my_profile() hides the own code'; end if;

  -- but can't read anyone's code from the table, directly or by filtering on it
  begin
    perform invite_code from public.profiles where id = b;
    raise exception 'FAIL: read another invite code';
  exception when insufficient_privilege then null; end;
  begin
    perform 1 from public.profiles where invite_code = b_code;
    raise exception 'FAIL: matched on another invite code';
  exception when insufficient_privilege then null; end;

  -- other profiles are still readable for username search
  perform username, avatar_url from public.profiles where id = b;

  -- a friend request by code finds b, and only b's public fields come back
  select * into r from public.find_profile_by_invite(b_code);
  if r.id is distinct from b then raise exception 'FAIL: invite lookup missed'; end if;

  -- the lookup needs the whole code
  select * into r from public.find_profile_by_invite(substr(b_code, 1, 4));
  if r.id is not null then raise exception 'FAIL: a partial code matched'; end if;

  reset role;
  raise exception 'PROFILE_INVITE_TESTS_PASSED';
end $$;
