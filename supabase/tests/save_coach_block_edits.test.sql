-- pgTAP: save_coach_block with replacements (T-179, ADR-83; base behaviour in coach_plan_sessions.test.sql).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(24);

create function pg_temp.u(n int) returns uuid language sql as $$
  select ('c9000000-0000-4000-8000-0000000000' || lpad(n::text, 2, '0'))::uuid $$;
create function pg_temp.p(n int) returns uuid language sql as $$
  select ('c9000000-0000-4000-8000-00000000a0' || lpad(n::text, 2, '0'))::uuid $$;
create function pg_temp.as_user(n int) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', pg_temp.u(n), 'role', 'authenticated')::text, true) $$;

create function pg_temp.ex(p_pattern text, p_nth int default 0) returns uuid language sql as $$
  select id from public.exercises e where not is_custom and movement_pattern::text = p_pattern
    and metric_type = 'weight_reps'
    -- The edited main lift must have alternatives; no barbell exercise has any, so it is a machine one.
    and equipment = (case when p_pattern = 'horizontal_push' then 'machine' else 'barbell' end)::public.equipment
  order by key offset p_nth limit 1 $$;
create function pg_temp.iso(p_nth int default 0) returns uuid language sql as $$
  select id from public.exercises where not is_custom and movement_pattern = 'isolation'
    and metric_type = 'weight_reps' and equipment = 'machine' order by key offset p_nth limit 1 $$;
-- An alternative of p_id that is not p_skip.
create function pg_temp.alt(p_id uuid, p_skip uuid default null) returns uuid language sql as $$
  select x.id from (select alternative_id as id, similarity from public.exercise_alternatives where exercise_id = p_id
                    union all select exercise_id, similarity from public.exercise_alternatives where alternative_id = p_id) x
  where x.id is distinct from p_skip order by x.similarity desc, x.id limit 1 $$;
-- A catalogue exercise that is not an alternative of p_id.
create function pg_temp.not_alt(p_id uuid) returns uuid language sql as $$
  select e.id from public.exercises e where not e.is_custom and e.id <> p_id
    and not exists (select 1 from public.exercise_alternatives a
                    where (a.exercise_id = p_id and a.alternative_id = e.id) or (a.alternative_id = p_id and a.exercise_id = e.id))
  order by e.key limit 1 $$;

create function pg_temp.exercise(p_id uuid, p_role text, p_sets int, p_rmin int, p_rmax int, p_rest int) returns jsonb
language sql as $$
  select jsonb_build_object('exercise_id', p_id, 'exercise_key', 'k', 'role', p_role, 'sets', p_sets,
    'rep_min', p_rmin, 'rep_max', p_rmax, 'rest_seconds', p_rest, 'reason', 'Buen ejercicio base para tu objetivo.') $$;
create function pg_temp.sessions() returns jsonb language sql as $$
  select jsonb_build_array(
    jsonb_build_object('session_index', 1, 'name', 'Tren superior', 'focus', jsonb_build_array('chest', 'biceps'),
      'exercises', jsonb_build_array(pg_temp.exercise(pg_temp.ex('horizontal_push', 0), 'main', 4, 6, 10, 150),
                                     pg_temp.exercise(pg_temp.iso(0), 'isolation', 3, 10, 15, 90))),
    jsonb_build_object('session_index', 2, 'name', 'Tren inferior', 'focus', jsonb_build_array('quads'),
      'exercises', jsonb_build_array(pg_temp.exercise(pg_temp.ex('squat', 0), 'main', 4, 6, 10, 150),
                                     pg_temp.exercise(pg_temp.iso(1), 'isolation', 3, 10, 15, 90)))) $$;
create function pg_temp.attach(p_plan uuid) returns jsonb language sql as $$
  select public.attach_coach_block(p_plan,
    jsonb_build_object('days_per_week', 2, 'days_chosen', false, 'session_minutes', 60, 'rep_scheme', 'single',
                       'block_weeks', 4, 'equipment', '[]'::jsonb),
    pg_temp.sessions(), '{"model":"m","attempts":1}', '{}') $$;
create function pg_temp.edit(p_session int, p_pos int, p_ex uuid) returns jsonb language sql as $$
  select jsonb_build_object('session_index', p_session, 'position', p_pos, 'exercise_id', p_ex) $$;
-- Exercise id stored at a slot of a week.
create function pg_temp.slot(p_plan uuid, p_week int, p_session int, p_pos int) returns uuid language sql as $$
  select (exercises->(p_pos - 1)->>'exercise_id')::uuid from public.coach_plan_sessions
  where plan_id = p_plan and week = p_week and session_index = p_session $$;

grant execute on all functions in schema pg_temp to authenticated;

insert into auth.users (id, email) select pg_temp.u(n), 'edits' || n || '@example.com' from generate_series(1, 7) n;
insert into public.coach_plans (id, user_id, block_start, intention, nutrition, status) values
  (pg_temp.p(1), pg_temp.u(1), current_date, 'muscle', '{}', 'proposed'),
  (pg_temp.p(2), pg_temp.u(2), current_date, 'muscle', '{}', 'proposed'),
  (pg_temp.p(3), pg_temp.u(3), current_date, 'muscle', '{}', 'proposed'),
  (pg_temp.p(4), pg_temp.u(4), current_date, 'muscle', '{}', 'proposed'),
  (pg_temp.p(5), pg_temp.u(5), current_date, 'muscle', '{}', 'proposed'),
  (pg_temp.p(6), pg_temp.u(6), current_date, 'muscle', '{}', 'proposed'),
  (pg_temp.p(7), pg_temp.u(7), current_date, 'muscle', '{}', 'proposed');

set local role authenticated;
select pg_temp.as_user(1);
select pg_temp.attach(pg_temp.p(1));
select pg_temp.as_user(2);
select pg_temp.attach(pg_temp.p(2));
select pg_temp.as_user(3);
select pg_temp.attach(pg_temp.p(3));
select pg_temp.as_user(4);
select pg_temp.attach(pg_temp.p(4));
select pg_temp.as_user(5);
select pg_temp.attach(pg_temp.p(5));
select pg_temp.as_user(6);
select pg_temp.attach(pg_temp.p(6));

-- ---------------------------------------------------------------------------
-- A valid edit (plan 1, user 1)
-- ---------------------------------------------------------------------------
select pg_temp.as_user(1);
select ok(pg_temp.alt(pg_temp.slot(pg_temp.p(1), 1, 1, 1)) is not null, 'the main lift of session 1 has an alternative');

select public.save_coach_block(pg_temp.p(1),
  jsonb_build_array(pg_temp.edit(1, 1, pg_temp.alt(pg_temp.slot(pg_temp.p(1), 1, 1, 1)))));

select is((select count(*)::int from public.coach_plan_sessions s
           where s.plan_id = pg_temp.p(1) and s.session_index = 1
             and (s.exercises->0->>'exercise_id')::uuid = pg_temp.alt(pg_temp.ex('horizontal_push', 0))),
  4, 'the replacement is stored in every week of the session');
select is(pg_temp.slot(pg_temp.p(1), 1, 2, 1), pg_temp.ex('squat', 0), 'the other session is untouched');
select is((select exercises->0->>'exercise_key' from public.coach_plan_sessions where plan_id = pg_temp.p(1) and week = 3 and session_index = 1),
  (select key from public.exercises where id = pg_temp.alt(pg_temp.ex('horizontal_push', 0))), 'the stored key follows the exercise');
select is((select (exercises->0->>'sets') || '/' || (exercises->0->>'rep_min') || '-' || (exercises->0->>'rep_max') || '/'
                  || (exercises->0->>'rest_seconds') || '/' || (exercises->0->>'reason')
           from public.coach_plan_sessions where plan_id = pg_temp.p(1) and week = 2 and session_index = 1),
  '4/6-10/150/Buen ejercicio base para tu objetivo.', 'sets, rep range, rest and reason are kept');
select is((select re.exercise_id from public.routine_exercises re join public.routines r on r.id = re.routine_id
           join public.coach_plan_sessions s on s.routine_id = r.id
           where s.plan_id = pg_temp.p(1) and s.week = 1 and s.session_index = 1 and re.position = 0),
  pg_temp.alt(pg_temp.ex('horizontal_push', 0)), 'the created routine uses the replacement');
select is((select re.target_sets::int from public.routine_exercises re join public.routines r on r.id = re.routine_id
           join public.coach_plan_sessions s on s.routine_id = r.id
           where s.plan_id = pg_temp.p(1) and s.week = 1 and s.session_index = 1 and re.position = 0),
  4, 'the routine keeps the planned sets');

-- Saving again ignores the edits and returns the same routines.
select is(public.save_coach_block(pg_temp.p(1), jsonb_build_array(pg_temp.edit(1, 2, pg_temp.alt(pg_temp.iso(0)))))->'routine_ids',
  (select jsonb_agg(routine_id order by session_index) from public.coach_plan_sessions where plan_id = pg_temp.p(1) and week = 1),
  'a second save returns the same routines');
select is(pg_temp.slot(pg_temp.p(1), 1, 1, 2), pg_temp.iso(0), 'a second save ignores its edits');

-- ---------------------------------------------------------------------------
-- Rejected edits are atomic
-- ---------------------------------------------------------------------------
select pg_temp.as_user(2);
create temp table before_counts on commit drop as
  select (select count(*) from public.routine_folders) as folders, (select count(*) from public.routines) as routines;
grant select on before_counts to authenticated;

select throws_ok(
  format($$select public.save_coach_block(%L, %L::jsonb)$$, pg_temp.p(2),
    jsonb_build_array(pg_temp.edit(1, 1, pg_temp.not_alt(pg_temp.ex('horizontal_push', 0))))),
  '22023', null, 'a replacement that is not an alternative is rejected');
select throws_ok(
  format($$select public.save_coach_block(%L, %L::jsonb)$$, pg_temp.p(2),
    jsonb_build_array(pg_temp.edit(1, 1, pg_temp.alt(pg_temp.ex('horizontal_push', 0))), pg_temp.edit(1, 9, pg_temp.iso(0)))),
  '22023', null, 'a position out of range rejects the whole save');
select throws_ok(
  format($$select public.save_coach_block(%L, %L::jsonb)$$, pg_temp.p(2),
    jsonb_build_array(pg_temp.edit(5, 1, pg_temp.alt(pg_temp.ex('horizontal_push', 0))))),
  '22023', null, 'a session out of range is rejected');
select throws_ok(
  format($$select public.save_coach_block(%L, %L::jsonb)$$, pg_temp.p(2), '{"not":"an array"}'),
  '22023', null, 'edits that are not an array are rejected');
select throws_ok(
  format($$select public.save_coach_block(%L, %L::jsonb)$$, pg_temp.p(2),
    jsonb_build_array(jsonb_build_object('session_index', 1, 'position', 1, 'exercise_id', 'nope'))),
  '22023', null, 'a malformed edit is rejected');
select is((select count(*)::int from public.routine_folders) - (select folders::int from before_counts), 0,
  'rejected edits create no folder');
select is((select count(*)::int from public.routines) - (select routines::int from before_counts), 0,
  'rejected edits create no routine');
select is(pg_temp.slot(pg_temp.p(2), 1, 1, 1), pg_temp.ex('horizontal_push', 0), 'rejected edits change no stored exercise');

-- A replacement already present in the session is rejected (the isolation lift of session 1 as a main-lift alternative).
select pg_temp.as_user(3);
select throws_ok(
  format($$select public.save_coach_block(%L, %L::jsonb)$$, pg_temp.p(3),
    jsonb_build_array(pg_temp.edit(1, 1, pg_temp.slot(pg_temp.p(3), 1, 1, 2)))),
  '22023', null, 'a replacement already in the session is rejected');

-- The last edit of a slot wins.
select pg_temp.as_user(4);
select public.save_coach_block(pg_temp.p(4), jsonb_build_array(
  pg_temp.edit(1, 1, pg_temp.not_alt(pg_temp.ex('horizontal_push', 0))),
  pg_temp.edit(1, 1, pg_temp.alt(pg_temp.ex('horizontal_push', 0)))));
select is(pg_temp.slot(pg_temp.p(4), 1, 1, 1), pg_temp.alt(pg_temp.ex('horizontal_push', 0)), 'for duplicate edits of a slot the last wins');

-- Empty edits and the one-argument call behave as before.
select pg_temp.as_user(5);
select is(jsonb_array_length(public.save_coach_block(pg_temp.p(5), '[]')->'routine_ids'), 2, 'empty edits save the block as before');
select pg_temp.as_user(6);
select is(jsonb_array_length(public.save_coach_block(pg_temp.p(6))->'routine_ids'), 2, 'the one-argument call still works');
select is(pg_temp.slot(pg_temp.p(6), 1, 1, 1), pg_temp.ex('horizontal_push', 0), 'the one-argument call changes nothing');

-- ---------------------------------------------------------------------------
-- Isolation and internal helper
-- ---------------------------------------------------------------------------
select pg_temp.as_user(7);
select throws_ok(
  format($$select public.save_coach_block(%L, %L::jsonb)$$, pg_temp.p(2),
    jsonb_build_array(pg_temp.edit(1, 1, pg_temp.alt(pg_temp.ex('horizontal_push', 0))))),
  'P0002', null, 'another user cannot save or edit a plan that is not theirs');
select throws_ok($$select public.coach_apply_block_edits(gen_random_uuid(), '[]')$$, '42501', null,
  'coach_apply_block_edits refuses a direct call');

select * from finish();
rollback;
