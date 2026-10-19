-- pgTAP: session history access (T-047, US-14): isolation of delete/update, cascade, paging window and the
-- fallback of last_performance / exercise_history after a deletion. Tests only: no migration.
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(15);

insert into auth.users (id, email) values
  ('11111111-1111-4111-8111-111111111111', 'ana@example.com'),
  ('22222222-2222-4222-8222-222222222222', 'bea@example.com');

create temp table cat as
  select id, row_number() over (order by key) as n from public.exercises where not is_custom limit 2;
grant select on cat to authenticated;

-- Ana: 25 completed sessions (n = 1 oldest .. 25 newest), plus a discarded and an in-progress one, both newer.
insert into public.workout_sessions (id, user_id, name, status, started_at, ended_at)
select ('a3000000-0000-4000-8000-0000000000' || lpad(n::text, 2, '0'))::uuid,
       '11111111-1111-4111-8111-111111111111', 'Ana ' || n, 'completed',
       timestamptz '2026-03-01 10:00+00' + n * interval '1 day',
       timestamptz '2026-03-01 11:00+00' + n * interval '1 day'
from generate_series(1, 25) as n;

insert into public.workout_sessions (id, user_id, name, status, started_at, ended_at) values
  ('a3100000-0000-4000-8000-000000000001', '11111111-1111-4111-8111-111111111111', 'Discarded', 'discarded',
   '2026-05-01 10:00+00', '2026-05-01 11:00+00'),
  ('a3100000-0000-4000-8000-000000000002', '11111111-1111-4111-8111-111111111111', 'Open', 'in_progress',
   '2026-05-02 10:00+00', null),
  ('b3000000-0000-4000-8000-000000000001', '22222222-2222-4222-8222-222222222222', 'Bea', 'completed',
   '2026-03-10 10:00+00', '2026-03-10 11:00+00');

-- Session 24: 80 x 8; session 25 (newest): 100 x 5 and 60 x 10 (position 1). Bea: 200 x 5.
insert into public.workout_sets (session_id, exercise_id, exercise_position, set_index, weight_kg, reps, completed_at) values
  ('a3000000-0000-4000-8000-000000000024', (select id from cat where n = 1), 0, 1, 80, 8, '2026-03-25 10:10+00'),
  ('a3000000-0000-4000-8000-000000000025', (select id from cat where n = 1), 0, 1, 100, 5, '2026-03-26 10:10+00'),
  ('a3000000-0000-4000-8000-000000000025', (select id from cat where n = 2), 1, 1, 60, 10, '2026-03-26 10:20+00'),
  ('b3000000-0000-4000-8000-000000000001', (select id from cat where n = 1), 0, 1, 200, 5, '2026-03-10 10:10+00');

-- ---------------------------------------------------------------------------
-- Bea cannot delete or update Ana's completed session or its sets
-- ---------------------------------------------------------------------------
set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"22222222-2222-4222-8222-222222222222","role":"authenticated"}', true);

select lives_ok($$delete from public.workout_sessions where id = 'a3000000-0000-4000-8000-000000000025'$$,
  'Bea deleting Ana''s session is a silent no-op');
select lives_ok($$update public.workout_sets set weight_kg = 1
  where session_id = 'a3000000-0000-4000-8000-000000000025'$$,
  'Bea updating Ana''s sets is a silent no-op');
select lives_ok($$update public.workout_sessions set name = 'Hacked'
  where id = 'a3000000-0000-4000-8000-000000000025'$$,
  'Bea renaming Ana''s session is a silent no-op');

select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

select is((select count(*)::int from public.workout_sessions where id = 'a3000000-0000-4000-8000-000000000025' and name = 'Ana 25'),
  1, 'Ana''s session is intact after Bea''s attempts');
select is((select weight_kg from public.workout_sets
  where session_id = 'a3000000-0000-4000-8000-000000000025' and exercise_position = 0), 100::numeric,
  'Ana''s set is intact after Bea''s attempts');

-- ---------------------------------------------------------------------------
-- Listing: completed only, newest first, 20 per page
-- ---------------------------------------------------------------------------
select is((select count(*)::int from public.workout_sessions where status = 'completed'), 25,
  'a completed listing returns only Ana''s 25 completed sessions');
select is((select count(*)::int from public.workout_sessions where status = 'completed' and name in ('Discarded', 'Open')),
  0, 'discarded and in-progress sessions are not in a completed listing');

select results_eq(
  $$select id from public.workout_sessions where status = 'completed'
    order by started_at desc, id desc limit 20 offset 20$$,
  $$select ('a3000000-0000-4000-8000-0000000000' || lpad(n::text, 2, '0'))::uuid from generate_series(5, 1, -1) as n$$,
  'the second page holds the remaining 5 oldest sessions, newest first');
select is(
  (select count(*)::int from (
    (select id from public.workout_sessions where status = 'completed'
       order by started_at desc, id desc limit 20 offset 0)
    intersect
    (select id from public.workout_sessions where status = 'completed'
       order by started_at desc, id desc limit 20 offset 20)) overlap),
  0, 'the two pages do not overlap');

-- ---------------------------------------------------------------------------
-- Before the deletion: the newest session is the last performance and the last history point
-- ---------------------------------------------------------------------------
select results_eq(
  $$select weight_kg, reps from public.last_performance((select id from cat where n = 1))$$,
  $$values (100::numeric, 5)$$,
  'before the delete the last performance is the newest session');

-- ---------------------------------------------------------------------------
-- Ana deletes the newest session: sets cascade, last_performance and history fall back
-- ---------------------------------------------------------------------------
select lives_ok($$delete from public.workout_sessions where id = 'a3000000-0000-4000-8000-000000000025'$$,
  'Ana deletes her completed session');
select is((select count(*)::int from public.workout_sessions where id = 'a3000000-0000-4000-8000-000000000025'), 0,
  'the session is gone');

reset role;
select is((select count(*)::int from public.workout_sets where session_id = 'a3000000-0000-4000-8000-000000000025'), 0,
  'its sets were deleted by the cascade');

set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

select results_eq(
  $$select weight_kg, reps from public.last_performance((select id from cat where n = 1))$$,
  $$values (80::numeric, 8)$$,
  'after the delete last_performance falls back to the previous session');
select results_eq(
  $$select session_id from public.exercise_history((select id from cat where n = 1), null)$$,
  $$values ('a3000000-0000-4000-8000-000000000024'::uuid)$$,
  'after the delete exercise_history no longer returns the deleted session');

select * from finish();
rollback;
