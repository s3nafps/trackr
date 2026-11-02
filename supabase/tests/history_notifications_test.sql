-- Watch history + friend notifications test. Everything is rolled back by the final exception; success is reported
-- as the exception message 'HISTORY_TESTS_PASSED'.
do $$
declare
  a uuid := gen_random_uuid(); b uuid := gen_random_uuid(); c uuid := gen_random_uuid();
  e1 uuid; fid uuid; n int; t0 timestamptz := now() - interval '1 minute';
begin
  insert into auth.users (id, email, raw_user_meta_data) values
    (a, 'a@test.local', '{"name":"Ann"}'), (b, 'b@test.local', '{"name":"Ben"}'), (c, 'c@test.local', '{"name":"Cat"}');
  update public.profiles set username = 'ann_t' where id = a;
  update public.profiles set username = 'ben_t' where id = b;
  update public.profiles set username = 'cat_t' where id = c;
  insert into public.friendships (requester_id, addressee_id, status) values (a, b, 'accepted');
  insert into public.friendships (requester_id, addressee_id) values (c, a) returning id into fid;
  insert into public.list_entries (user_id, source, external_id, media_type, title, status, rewatch_count)
    values (a, 'tmdb', '1', 'tv', 'Severance', 'watching', 1) returning id into e1;

  -- a's history: own rows only, no edits
  perform set_config('request.jwt.claims', json_build_object('sub', a, 'role', 'authenticated')::text, true);
  set local role authenticated;
  insert into public.watch_events (id, user_id, source, external_id, media_type, title, kind, from_episode, to_episode, watched_at)
    values (gen_random_uuid(), a, 'tmdb', '1', 'tv', 'Severance', 'watched', 1, 3, now());
  insert into public.watch_events (id, user_id, source, external_id, media_type, title, kind, watched_at)
    values (gen_random_uuid(), a, 'tmdb', '1', 'tv', 'Severance', 'rewatch', now());
  begin
    insert into public.watch_events (id, user_id, source, external_id, media_type, title, kind, watched_at)
      values (gen_random_uuid(), b, 'tmdb', '1', 'tv', 'X', 'watched', now());
    raise exception 'FAIL: wrote history as someone else';
  exception when insufficient_privilege then null; end;
  begin
    update public.watch_events set title = 'edited';
    raise exception 'FAIL: history is editable';
  exception when insufficient_privilege then null; end;
  begin
    update public.list_entries set rewatch_count = 100 where id = e1;
    raise exception 'FAIL: rewatch count over 99';
  exception when check_violation then null; end;

  -- b (friend) comments and reacts on a's entry
  perform set_config('request.jwt.claims', json_build_object('sub', b, 'role', 'authenticated')::text, true);
  select count(*) into n from public.watch_events;
  if n <> 0 then raise exception 'FAIL: friend reads history (%)', n; end if;
  insert into public.entry_comments (entry_id, user_id, body) values (e1, b, 'So good');
  insert into public.entry_reactions (entry_id, user_id, emoji) values (e1, b, '🔥');
  insert into public.recommendations (from_user, to_user, source, external_id, media_type, title)
    values (b, a, 'tmdb', '2', 'movie', 'Heat');

  -- a accepts c's request; c is told
  perform set_config('request.jwt.claims', json_build_object('sub', a, 'role', 'authenticated')::text, true);
  select count(*) into n from public.social_notifications(t0);
  -- c's request, b's comment, b's reaction, b's recommendation
  if n <> 4 then raise exception 'FAIL: a sees % notifications, expected 4', n; end if;
  select count(*) into n from public.social_notifications(t0) where kind = 'comment' and actor = 'ben_t' and detail = 'So good';
  if n <> 1 then raise exception 'FAIL: comment notification'; end if;
  select count(*) into n from public.social_notifications(now() + interval '1 minute');
  if n <> 0 then raise exception 'FAIL: since is ignored'; end if;
  update public.friendships set status = 'accepted' where id = fid;

  perform set_config('request.jwt.claims', json_build_object('sub', c, 'role', 'authenticated')::text, true);
  select count(*) into n from public.social_notifications(t0) where kind = 'friend_accepted' and actor = 'ann_t';
  if n <> 1 then raise exception 'FAIL: accepted request not reported'; end if;
  select count(*) into n from public.social_notifications(t0) where kind in ('comment', 'reaction', 'recommendation');
  if n <> 0 then raise exception 'FAIL: c sees a''s notifications'; end if;

  raise exception 'HISTORY_TESTS_PASSED';
end;
$$;
