-- Social RLS test (reactions, comments, recommendations). Run like rls_test.sql: everything is rolled back by the
-- final exception, and success is reported as the exception message 'SOCIAL_TESTS_PASSED'.
do $$
declare
  a uuid := gen_random_uuid(); b uuid := gen_random_uuid(); c uuid := gen_random_uuid(); d uuid := gen_random_uuid();
  e1 uuid; n int; rid uuid;
begin
  insert into auth.users (id, email, raw_user_meta_data) values
    (a, 'a@test.local', '{"name":"Ann"}'), (b, 'b@test.local', '{"name":"Ben"}'),
    (c, 'c@test.local', '{"name":"Cat"}'), (d, 'd@test.local', '{"name":"Dan"}');
  -- a is friends with b and with d; b and d are not friends; c is a stranger.
  insert into public.friendships (requester_id, addressee_id, status) values (a, b, 'accepted'), (d, a, 'accepted');
  insert into public.list_entries (user_id, source, external_id, media_type, title, status)
    values (a, 'tmdb', '1', 'movie', 'Heat', 'completed') returning id into e1;

  -- b (friend) reacts and comments
  perform set_config('request.jwt.claims', json_build_object('sub', b, 'role', 'authenticated')::text, true);
  set local role authenticated;
  insert into public.entry_reactions (entry_id, user_id, emoji) values (e1, b, '🔥');
  insert into public.entry_comments (entry_id, user_id, body) values (e1, b, 'Great pick');
  begin
    insert into public.entry_reactions (entry_id, user_id, emoji) values (e1, b, '💩');
    raise exception 'FAIL: unknown emoji accepted';
  exception when check_violation then null; end;
  begin
    insert into public.entry_comments (entry_id, user_id, body) values (e1, b, '   ');
    raise exception 'FAIL: blank comment accepted';
  exception when check_violation then null; end;
  begin
    insert into public.entry_reactions (entry_id, user_id, emoji) values (e1, a, '❤️');
    raise exception 'FAIL: reacted as someone else';
  exception when insufficient_privilege then null; end;
  begin
    update public.entry_comments set body = 'edited' where entry_id = e1;
    raise exception 'FAIL: comments are editable';
  exception when insufficient_privilege then null; end;
  -- b recommends to a; not twice, not to a stranger, not pre-seen
  insert into public.recommendations (from_user, to_user, source, external_id, media_type, title, note)
    values (b, a, 'tmdb', '2', 'movie', 'Collateral', 'You will love it') returning id into rid;
  begin
    insert into public.recommendations (from_user, to_user, source, external_id, media_type, title)
      values (b, a, 'tmdb', '2', 'movie', 'Collateral');
    raise exception 'FAIL: duplicate recommendation';
  exception when unique_violation then null; end;
  begin
    insert into public.recommendations (from_user, to_user, source, external_id, media_type, title)
      values (b, c, 'tmdb', '3', 'movie', 'Thief');
    raise exception 'FAIL: recommended to a non-friend';
  exception when insufficient_privilege then null; end;
  begin
    insert into public.recommendations (from_user, to_user, source, external_id, media_type, title, seen)
      values (b, a, 'tmdb', '4', 'movie', 'Ali', true);
    raise exception 'FAIL: inserted an already-seen recommendation';
  exception when insufficient_privilege then null; end;
  -- the sender can't mark it seen
  update public.recommendations set seen = true where id = rid;
  get diagnostics n = row_count;
  if n <> 0 then raise exception 'FAIL: sender marked recommendation seen'; end if;
  reset role;

  -- d (a's friend, not b's) sees the entry's reactions and comments, including b's
  perform set_config('request.jwt.claims', json_build_object('sub', d, 'role', 'authenticated')::text, true);
  set local role authenticated;
  select count(*) into n from public.entry_comments where entry_id = e1;
  if n <> 1 then raise exception 'FAIL: friend of owner cannot see comments (%)', n; end if;
  -- d can't remove b's reaction
  delete from public.entry_reactions where entry_id = e1;
  get diagnostics n = row_count;
  if n <> 0 then raise exception 'FAIL: removed someone else''s reaction'; end if;
  -- d can't see b's recommendation to a
  select count(*) into n from public.recommendations;
  if n <> 0 then raise exception 'FAIL: third party sees recommendations'; end if;
  reset role;

  -- c (stranger) sees nothing and can't write
  perform set_config('request.jwt.claims', json_build_object('sub', c, 'role', 'authenticated')::text, true);
  set local role authenticated;
  select count(*) into n from public.entry_reactions;
  if n <> 0 then raise exception 'FAIL: stranger sees reactions'; end if;
  select count(*) into n from public.entry_comments;
  if n <> 0 then raise exception 'FAIL: stranger sees comments'; end if;
  begin
    insert into public.entry_reactions (entry_id, user_id, emoji) values (e1, c, '😂');
    raise exception 'FAIL: stranger reacted';
  exception when insufficient_privilege then null; end;
  begin
    insert into public.entry_comments (entry_id, user_id, body) values (e1, c, 'hi');
    raise exception 'FAIL: stranger commented';
  exception when insufficient_privilege then null; end;
  reset role;

  -- a (owner) sees everything on their entry, marks the recommendation seen, can't rewrite it, deletes b's comment
  perform set_config('request.jwt.claims', json_build_object('sub', a, 'role', 'authenticated')::text, true);
  set local role authenticated;
  select count(*) into n from public.entry_reactions where entry_id = e1;
  if n <> 1 then raise exception 'FAIL: owner cannot see reactions'; end if;
  update public.recommendations set seen = true where id = rid;
  get diagnostics n = row_count;
  if n <> 1 then raise exception 'FAIL: recipient could not mark seen'; end if;
  begin
    update public.recommendations set title = 'Something else' where id = rid;
    raise exception 'FAIL: recipient rewrote a recommendation';
  exception when insufficient_privilege then null; end;
  delete from public.entry_comments where entry_id = e1;
  get diagnostics n = row_count;
  if n <> 1 then raise exception 'FAIL: owner could not delete a comment on their entry'; end if;
  reset role;

  -- unfriending hides the entry's reactions from b again
  delete from public.friendships where (requester_id = a and addressee_id = b);
  perform set_config('request.jwt.claims', json_build_object('sub', b, 'role', 'authenticated')::text, true);
  set local role authenticated;
  select count(*) into n from public.entry_reactions where entry_id = e1;
  if n <> 0 then raise exception 'FAIL: ex-friend still sees reactions'; end if;
  reset role;

  -- deleting the entry takes its reactions with it
  delete from public.list_entries where id = e1;
  select count(*) into n from public.entry_reactions where entry_id = e1;
  if n <> 0 then raise exception 'FAIL: reactions outlived their entry'; end if;

  -- anon gets nothing
  set local role anon;
  begin
    perform 1 from public.entry_reactions;
    raise exception 'FAIL: anon can read reactions';
  exception when insufficient_privilege then null; end;
  begin
    perform 1 from public.recommendations;
    raise exception 'FAIL: anon can read recommendations';
  exception when insufficient_privilege then null; end;
  reset role;

  raise exception 'SOCIAL_TESTS_PASSED';
end $$;
