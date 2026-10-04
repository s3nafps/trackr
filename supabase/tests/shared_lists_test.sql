-- Shared lists RLS test. Run like rls_test.sql: rolled back by the final exception; success is
-- 'SHARED_LISTS_TESTS_PASSED'.
do $$
declare
  a uuid := gen_random_uuid(); b uuid := gen_random_uuid(); c uuid := gen_random_uuid(); d uuid := gen_random_uuid();
  l uuid; n int;
begin
  insert into auth.users (id, email, raw_user_meta_data) values
    (a, 'a@test.local', '{"name":"Ann"}'), (b, 'b@test.local', '{"name":"Ben"}'),
    (c, 'c@test.local', '{"name":"Cat"}'), (d, 'd@test.local', '{"name":"Dan"}');
  -- a is friends with b and d; c is a stranger.
  insert into public.friendships (requester_id, addressee_id, status) values (a, b, 'accepted'), (a, d, 'accepted');

  -- a creates a list (RETURNING must work for the creator) and is its first member
  perform set_config('request.jwt.claims', json_build_object('sub', a, 'role', 'authenticated')::text, true);
  set local role authenticated;
  insert into public.shared_lists (name, owner_id) values ('Movie night', a) returning id into l;
  if l is null then raise exception 'FAIL: no id returned'; end if;
  select count(*) into n from public.shared_list_members where list_id = l and user_id = a;
  if n <> 1 then raise exception 'FAIL: owner is not a member'; end if;
  begin
    insert into public.shared_lists (name, owner_id) values ('Not mine', b);
    raise exception 'FAIL: created a list for someone else';
  exception when insufficient_privilege then null; end;
  begin
    insert into public.shared_lists (name, owner_id) values ('   ', a);
    raise exception 'FAIL: blank name accepted';
  exception when check_violation then null; end;
  insert into public.shared_list_members (list_id, user_id) values (l, b);
  begin
    insert into public.shared_list_members (list_id, user_id) values (l, c);
    raise exception 'FAIL: added a non-friend';
  exception when insufficient_privilege then null; end;
  insert into public.shared_list_items (list_id, source, external_id, media_type, title, added_by)
    values (l, 'tmdb', '1', 'movie', 'Heat', a);
  reset role;

  -- b (member) sees and adds items, but can't add people, rename, or add as someone else
  perform set_config('request.jwt.claims', json_build_object('sub', b, 'role', 'authenticated')::text, true);
  set local role authenticated;
  select count(*) into n from public.shared_lists where id = l;
  if n <> 1 then raise exception 'FAIL: member cannot see the list'; end if;
  select count(*) into n from public.shared_list_members where list_id = l;
  if n <> 2 then raise exception 'FAIL: member sees % members', n; end if;
  insert into public.shared_list_items (list_id, source, external_id, media_type, title, added_by)
    values (l, 'anilist', '5', 'anime', 'Frieren', b);
  begin
    insert into public.shared_list_items (list_id, source, external_id, media_type, title, added_by)
      values (l, 'tmdb', '2', 'movie', 'Alien', a);
    raise exception 'FAIL: added an item as someone else';
  exception when insufficient_privilege then null; end;
  begin
    insert into public.shared_list_members (list_id, user_id) values (l, d);
    raise exception 'FAIL: non-owner added a member';
  exception when insufficient_privilege then null; end;
  update public.shared_lists set name = 'Mine now' where id = l;
  get diagnostics n = row_count;
  if n <> 0 then raise exception 'FAIL: member renamed the list'; end if;
  -- members can remove items (whoever added them)
  delete from public.shared_list_items where list_id = l and external_id = '1';
  get diagnostics n = row_count;
  if n <> 1 then raise exception 'FAIL: member could not remove an item'; end if;
  -- b leaves and loses access
  delete from public.shared_list_members where list_id = l and user_id = b;
  get diagnostics n = row_count;
  if n <> 1 then raise exception 'FAIL: member could not leave'; end if;
  select count(*) into n from public.shared_list_items where list_id = l;
  if n <> 0 then raise exception 'FAIL: ex-member still sees items'; end if;
  reset role;

  -- c (stranger) sees nothing and can't write
  perform set_config('request.jwt.claims', json_build_object('sub', c, 'role', 'authenticated')::text, true);
  set local role authenticated;
  select count(*) into n from public.shared_lists;
  if n <> 0 then raise exception 'FAIL: stranger sees lists'; end if;
  begin
    insert into public.shared_list_items (list_id, source, external_id, media_type, title, added_by)
      values (l, 'tmdb', '9', 'movie', 'Spam', c);
    raise exception 'FAIL: stranger added an item';
  exception when insufficient_privilege then null; end;
  begin
    insert into public.shared_list_members (list_id, user_id) values (l, c);
    raise exception 'FAIL: stranger joined';
  exception when insufficient_privilege then null; end;
  reset role;

  -- a can't leave their own list, can remove others, can rename but not reassign ownership
  perform set_config('request.jwt.claims', json_build_object('sub', a, 'role', 'authenticated')::text, true);
  set local role authenticated;
  insert into public.shared_list_members (list_id, user_id) values (l, d);
  delete from public.shared_list_members where list_id = l and user_id = a;
  get diagnostics n = row_count;
  if n <> 0 then raise exception 'FAIL: owner left their own list'; end if;
  delete from public.shared_list_members where list_id = l and user_id = d;
  get diagnostics n = row_count;
  if n <> 1 then raise exception 'FAIL: owner could not remove a member'; end if;
  update public.shared_lists set name = 'Friday films' where id = l;
  get diagnostics n = row_count;
  if n <> 1 then raise exception 'FAIL: owner could not rename'; end if;
  begin
    update public.shared_lists set owner_id = b where id = l;
    raise exception 'FAIL: ownership reassigned';
  exception when insufficient_privilege then null; end;
  -- deleting the list takes members and items with it
  delete from public.shared_lists where id = l;
  reset role;
  select count(*) into n from public.shared_list_members where list_id = l;
  if n <> 0 then raise exception 'FAIL: members outlived their list'; end if;
  select count(*) into n from public.shared_list_items where list_id = l;
  if n <> 0 then raise exception 'FAIL: items outlived their list'; end if;

  -- membership helper only answers for the caller, and anon gets nothing
  set local role anon;
  begin
    perform 1 from public.shared_lists;
    raise exception 'FAIL: anon can read shared lists';
  exception when insufficient_privilege then null; end;
  begin
    perform public.is_list_member(l);
    raise exception 'FAIL: anon can call is_list_member';
  exception when insufficient_privilege then null; end;
  reset role;

  raise exception 'SHARED_LISTS_TESTS_PASSED';
end $$;
