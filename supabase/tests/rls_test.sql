-- RLS test: run with the Supabase SQL runner (or psql). Everything is rolled back by a final exception.
-- Success is reported as the exception message 'RLS_TESTS_PASSED'.
do $$
declare
  a uuid := gen_random_uuid(); b uuid := gen_random_uuid(); c uuid := gen_random_uuid();
  n int; fid uuid;
begin
  insert into auth.users (id, email, raw_user_meta_data) values
    (a, 'a@test.local', '{"name":"Alice Test"}'),
    (b, 'b@test.local', '{"name":"Bob Test"}'),
    (c, 'c@test.local', '{"name":"Carol Test"}');
  if (select count(*) from public.profiles where id in (a,b,c)) <> 3 then raise exception 'profile trigger failed'; end if;

  -- A writes an entry
  perform set_config('request.jwt.claims', json_build_object('sub', a, 'role', 'authenticated')::text, true);
  set local role authenticated;
  insert into public.list_entries (user_id, source, external_id, media_type, title, status)
    values (a, 'tmdb', '1', 'movie', 'Alien', 'completed');
  -- A cannot insert for B
  begin
    insert into public.list_entries (user_id, source, external_id, media_type, title, status)
      values (b, 'tmdb', '2', 'movie', 'X', 'completed');
    raise exception 'FAIL: inserted row for another user';
  exception when insufficient_privilege then null; end;
  -- A cannot friend self
  begin
    insert into public.friendships (requester_id, addressee_id) values (a, a);
    raise exception 'FAIL: self friending allowed';
  exception when check_violation then null; end;
  -- A requests B
  insert into public.friendships (requester_id, addressee_id) values (a, b) returning id into fid;
  reset role;

  -- C (stranger) cannot read A's list or friendships
  perform set_config('request.jwt.claims', json_build_object('sub', c, 'role', 'authenticated')::text, true);
  set local role authenticated;
  select count(*) into n from public.list_entries where user_id = a;
  if n <> 0 then raise exception 'FAIL: non-friend can read list (% rows)', n; end if;
  select count(*) into n from public.friendships;
  if n <> 0 then raise exception 'FAIL: stranger can see friendships'; end if;
  select count(*) into n from public.profiles;
  if n < 3 then raise exception 'FAIL: profiles not readable'; end if;
  -- C cannot accept/modify, delete A's entry
  update public.friendships set status = 'accepted' where id = fid;
  get diagnostics n = row_count;
  if n <> 0 then raise exception 'FAIL: stranger updated friendship'; end if;
  delete from public.list_entries where user_id = a;
  get diagnostics n = row_count;
  if n <> 0 then raise exception 'FAIL: stranger deleted entry'; end if;
  reset role;

  -- B (pending addressee) cannot yet read A's list
  perform set_config('request.jwt.claims', json_build_object('sub', b, 'role', 'authenticated')::text, true);
  set local role authenticated;
  select count(*) into n from public.list_entries where user_id = a;
  if n <> 0 then raise exception 'FAIL: pending friend can read list'; end if;
  -- duplicate reverse request blocked
  begin
    insert into public.friendships (requester_id, addressee_id) values (b, a);
    raise exception 'FAIL: duplicate reverse friendship allowed';
  exception when unique_violation then null; end;
  -- B accepts
  update public.friendships set status = 'accepted' where id = fid;
  get diagnostics n = row_count;
  if n <> 1 then raise exception 'FAIL: addressee could not accept'; end if;
  -- now B reads A's list and feed
  select count(*) into n from public.list_entries where user_id = a;
  if n <> 1 then raise exception 'FAIL: friend cannot read list'; end if;
  select count(*) into n from public.friend_activity;
  if n <> 1 then raise exception 'FAIL: friend_activity wrong (%)', n; end if;
  -- B cannot edit A's entry
  update public.list_entries set rating = 1 where user_id = a;
  get diagnostics n = row_count;
  if n <> 0 then raise exception 'FAIL: friend edited entry'; end if;
  reset role;

  -- C still blocked after A/B accepted
  perform set_config('request.jwt.claims', json_build_object('sub', c, 'role', 'authenticated')::text, true);
  set local role authenticated;
  select count(*) into n from public.list_entries;
  if n <> 0 then raise exception 'FAIL: stranger sees entries after friendship'; end if;
  select count(*) into n from public.friend_activity;
  if n <> 0 then raise exception 'FAIL: stranger sees activity'; end if;
  reset role;

  -- anon cannot read anything
  set local role anon;
  begin
    perform 1 from public.list_entries;
    raise exception 'FAIL: anon can select list_entries';
  exception when insufficient_privilege then null; end;
  reset role;

  raise exception 'RLS_TESTS_PASSED';
end $$;
