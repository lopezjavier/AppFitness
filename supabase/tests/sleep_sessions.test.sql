-- pgTAP: public.sleep_sessions and the sleep part of import_health_activity (T-140, US-61/62).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(21);

insert into auth.users (id, email) values
  ('11111111-1111-4111-8111-111111111111', 'ana@example.com'),
  ('22222222-2222-4222-8222-222222222222', 'bea@example.com');

insert into public.sleep_sessions (id, user_id, external_id, started_at, ended_at, minutes) values
  ('bbbbbbbb-0000-4000-8000-0000000000e1', '22222222-2222-4222-8222-222222222222', 'b-sleep',
   '2026-10-26T22:00:00Z', '2026-10-27T06:00:00Z', 460);

select has_table('public', 'sleep_sessions', 'public.sleep_sessions exists');
select columns_are('public', 'sleep_sessions', array[
  'id', 'user_id', 'started_at', 'ended_at', 'minutes', 'stages', 'external_id', 'updated_at'
], 'sleep_sessions has exactly the planned columns');
select ok(
  (select relrowsecurity and relforcerowsecurity from pg_class where oid = 'public.sleep_sessions'::regclass),
  'RLS is enabled and forced on sleep_sessions');
select policies_are('public', 'sleep_sessions', array[
  'sleep_sessions_select_own', 'sleep_sessions_insert_own', 'sleep_sessions_update_own', 'sleep_sessions_delete_own'
], 'sleep_sessions has the four owner policies');
select table_privs_are('public', 'sleep_sessions', 'anon', array[]::text[],
  'anon has no privileges on sleep_sessions');
select table_privs_are('public', 'sleep_sessions', 'authenticated', array['SELECT', 'INSERT', 'UPDATE', 'DELETE'],
  'authenticated has full CRUD on sleep_sessions');

set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

select is(
  public.import_health_activity('[]', '[{"external_id":"s1","started_at":"2026-10-26T22:30:00Z",
    "ended_at":"2026-10-27T06:00:00Z","minutes":435,
    "stages":[{"stage":"deep","started_at":"2026-10-26T23:00:00Z","ended_at":"2026-10-27T00:00:00Z"}]}]'::jsonb, '[]')
    ->> 'sleep',
  '1', 'a session is imported');
select results_eq(
  $$select minutes, jsonb_array_length(stages), stages -> 0 ->> 'stage' from public.sleep_sessions
    where external_id = 's1'$$,
  $$values (435, 1, 'deep')$$, 'last night''s sleep stores its duration and stages');

select public.import_health_activity('[]', '[{"external_id":"s1","started_at":"2026-10-26T22:30:00Z",
    "ended_at":"2026-10-27T06:00:00Z","minutes":435}]'::jsonb, '[]');
select is((select count(*)::int from public.sleep_sessions where external_id = 's1'), 1,
  'the same external_id twice leaves one session');

select public.import_health_activity('[]', '[{"external_id":"s1","started_at":"2026-10-26T22:00:00Z",
    "ended_at":"2026-10-27T07:00:00Z","minutes":500}]'::jsonb, '[]');
select results_eq(
  $$select minutes, ended_at, stages from public.sleep_sessions where external_id = 's1'$$,
  $$values (500, '2026-10-27T07:00:00Z'::timestamptz, '[]'::jsonb)$$,
  'an edited session replaces the stored one, stages included');

select public.import_health_activity('[]', '[{"external_id":"s2","started_at":"2026-10-25T22:00:00Z",
    "ended_at":"2026-10-26T06:00:00Z","minutes":400}]'::jsonb, '[]');
select is((select count(*)::int from public.sleep_sessions), 2, 'sessions with different ids are both stored');

select is(
  public.import_health_activity('[]', '[{"minutes":400,"started_at":"2026-10-20T22:00:00Z",
    "ended_at":"2026-10-21T06:00:00Z"}]'::jsonb, '[]') ->> 'sleep',
  '0', 'a session without external_id is skipped');

select throws_ok(
  $$select public.import_health_activity('[]', '[{"external_id":"bad","started_at":"2026-10-27T06:00:00Z",
    "ended_at":"2026-10-27T06:00:00Z","minutes":10}]'::jsonb, '[]')$$,
  '23514', null, 'ended_at must be after started_at');
select throws_ok(
  $$select public.import_health_activity('[]', '[{"external_id":"bad","started_at":"2026-10-27T05:00:00Z",
    "ended_at":"2026-10-27T06:00:00Z","minutes":1441}]'::jsonb, '[]')$$,
  '23514', null, 'minutes above 1440 are rejected');
select throws_ok(
  $$insert into public.sleep_sessions (external_id, started_at, ended_at, minutes, stages)
    values ('bad', '2026-10-27T05:00:00Z', '2026-10-27T06:00:00Z', 60, '{"a":1}')$$,
  '23514', null, 'stages must be a JSON array');
select throws_ok(
  $$select public.import_health_activity('[]',
      (select jsonb_agg(jsonb_build_object('external_id', 'x' || g, 'started_at', '2026-01-01T00:00:00Z',
         'ended_at', '2026-01-01T08:00:00Z', 'minutes', 480)) from generate_series(1, 401) g), '[]')$$,
  '22023', null, '401 sleep rows are rejected');

select is((select count(*)::int from public.sleep_sessions where user_id = '22222222-2222-4222-8222-222222222222'),
  0, 'A cannot read B''s sessions');
select throws_ok(
  $$insert into public.sleep_sessions (user_id, external_id, started_at, ended_at, minutes)
    values ('22222222-2222-4222-8222-222222222222', 'x', '2026-10-27T05:00:00Z', '2026-10-27T06:00:00Z', 60)$$,
  '42501', null, 'A cannot insert a session for B');
select results_eq(
  $$with u as (update public.sleep_sessions set minutes = 1 where id = 'bbbbbbbb-0000-4000-8000-0000000000e1' returning 1),
         d as (delete from public.sleep_sessions where id = 'bbbbbbbb-0000-4000-8000-0000000000e1' returning 1)
    select (select count(*) from u)::int + (select count(*) from d)::int$$,
  $$values (0)$$, 'A updating or deleting B''s session affects 0 rows');

reset role;
select is((select minutes from public.sleep_sessions where id = 'bbbbbbbb-0000-4000-8000-0000000000e1'), 460,
  'B''s session is untouched');

set local role anon;
select throws_ok('select * from public.sleep_sessions', '42501', null, 'anon cannot read sleep_sessions');
reset role;

select * from finish();
rollback;
