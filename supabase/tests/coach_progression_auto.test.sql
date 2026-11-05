-- pgTAP: coach_changes, apply_coach_progression, undo_coach_change, decide_progression_flag (T-227, US-97/US-104;
-- rules in planning/04 section 4.14). The rule examples of progression_suggestions are re-checked on the writer.
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(120);

create function pg_temp.u(n int) returns uuid language sql as $$
  select ('c2700000-0000-4000-8000-0000000000' || lpad(n::text, 2, '0'))::uuid $$;
create function pg_temp.r(n int) returns uuid language sql as $$
  select ('c2710000-0000-4000-8000-0000000000' || lpad(n::text, 2, '0'))::uuid $$;
create function pg_temp.as_user(n int) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', pg_temp.u(n), 'role', 'authenticated')::text, true) $$;
create function pg_temp.bx(n int) returns uuid language sql as $$
  select id from public.exercises where not is_custom and equipment = 'barbell' order by key offset n - 1 limit 1 $$;
create function pg_temp.dx(n int) returns uuid language sql as $$
  select id from public.exercises where not is_custom and equipment = 'dumbbell' order by key offset n - 1 limit 1 $$;

-- A routine exercise with a warm-up, three working sets and a drop set (range null = no rep range).
create function pg_temp.mkre(rt int, ex uuid, rmin int, rmax int, w numeric) returns void language plpgsql as $$
declare
  v uuid := gen_random_uuid();
  pos int;
begin
  perform pg_temp.as_user(case when rt = 6 then 3 else 1 end);
  select coalesce(max(position) + 1, 0) into pos from public.routine_exercises where routine_id = pg_temp.r(rt);
  insert into public.routine_exercises (id, routine_id, exercise_id, position, target_sets, target_reps, rep_min, rep_max)
  values (v, pg_temp.r(rt), ex, pos, 5, coalesce(rmax, 10), rmin, rmax);
  insert into public.routine_exercise_sets (routine_exercise_id, set_index, target_weight_kg, target_reps, set_type) values
    (v, 1, round(w / 2, 2), 10, 'warm_up'),
    (v, 2, w, coalesce(rmax, 10), 'working'),
    (v, 3, w, coalesce(rmax, 10), 'working'),
    (v, 4, w, coalesce(rmax, 10), 'working'),
    (v, 5, round(w * 0.8, 2), 10, 'drop');
end $$;

-- The same exercise in routines 1 and 2 (the block) and 3 (outside the block).
create function pg_temp.block(ex uuid, rmin int, rmax int, w numeric) returns void language plpgsql as $$
begin
  perform pg_temp.mkre(1, ex, rmin, rmax, w);
  perform pg_temp.mkre(2, ex, rmin, rmax, w);
  perform pg_temp.mkre(3, ex, rmin, rmax, w);
end $$;

-- A completed session of routine rt, `days` days ago, one working row per entry of reps.
create function pg_temp.hist(ex uuid, days int, rmin int, rmax int, w numeric, reps int[],
                             rpes numeric[] default null, rt int default 1, usr int default 1) returns uuid
language plpgsql as $$
declare
  v_sid uuid := gen_random_uuid();
  v_start timestamptz := now() - make_interval(days => days);
begin
  perform pg_temp.as_user(usr);
  insert into public.workout_sessions (id, user_id, name, status, started_at, ended_at, routine_id)
  values (v_sid, pg_temp.u(usr), 'Hist', 'completed', v_start, v_start + interval '1 hour', pg_temp.r(rt));
  insert into public.workout_sets
    (session_id, exercise_id, exercise_position, set_index, set_type, weight_kg, reps, rpe, completed_at, rep_min, rep_max)
  select v_sid, ex, 0, i, 'working', w, reps[i], case when rpes is null then null else rpes[i] end,
         v_start + interval '30 minutes', rmin, rmax
  from generate_subscripts(reps, 1) i;
  return v_sid;
end $$;

create function pg_temp.ap(sid uuid, usr int default 1) returns jsonb language plpgsql as $$
begin
  perform pg_temp.as_user(usr);
  return public.apply_coach_progression(sid);
end $$;

-- Planned sets of an exercise in a routine, e.g. warm_up:30x10,working:62.5x8,...
create function pg_temp.sig(rt int, ex uuid) returns text language sql as $$
  select string_agg(s.set_type::text || ':' || trim_scale(s.target_weight_kg)::text || 'x' || s.target_reps, ','
                    order by s.set_index)
  from public.routine_exercises re
  join public.routine_exercise_sets s on s.routine_exercise_id = re.id
  where re.routine_id = pg_temp.r(rt) and re.exercise_id = ex $$;
create function pg_temp.summary(rt int, ex uuid) returns int language sql as $$
  select target_reps::int from public.routine_exercises where routine_id = pg_temp.r(rt) and exercise_id = ex $$;
create function pg_temp.rmax(rt int, ex uuid) returns int language sql as $$
  select rep_max::int from public.routine_exercises where routine_id = pg_temp.r(rt) and exercise_id = ex $$;
create function pg_temp.set_working(rt int, ex uuid, w numeric) returns void language sql as $$
  update public.routine_exercise_sets s set target_weight_kg = w
  from public.routine_exercises re
  where s.routine_exercise_id = re.id and re.routine_id = pg_temp.r(rt) and re.exercise_id = ex
    and s.set_type = 'working' $$;
create function pg_temp.prefs(plates numeric[], handle numeric) returns void language sql as $$
  insert into public.training_preferences (user_id, plate_kg, dumbbell_handle_kg)
  values (pg_temp.u(1), plates, handle)
  on conflict (user_id) do update set plate_kg = excluded.plate_kg, dumbbell_handle_kg = excluded.dumbbell_handle_kg $$;
create function pg_temp.change_of(res jsonb, ex uuid) returns jsonb language sql as $$
  select e from jsonb_array_elements(res) e where (e ->> 'exercise_id')::uuid = ex $$;

-- ---------------------------------------------------------------------------
-- Fixtures that need the owner role: users, plans, folders, routines.
-- ---------------------------------------------------------------------------
insert into auth.users (id, email)
select pg_temp.u(n), 'auto' || n || '@example.com' from generate_series(1, 3) n;

insert into public.routine_folders (id, user_id, name) values
  ('c2720000-0000-4000-8000-000000000001', pg_temp.u(1), 'Bloque'),
  ('c2720000-0000-4000-8000-000000000003', pg_temp.u(3), 'Bloque 3');
-- Block started 28 days ago: week 3 (days 14 to 8 ago) is a deload week of routine 1.
insert into public.coach_plans (id, user_id, block_start, intention, nutrition, status, folder_id) values
  ('c2730000-0000-4000-8000-000000000001', pg_temp.u(1), (now() at time zone 'utc')::date - 28, 'muscle', '{}',
   'accepted', 'c2720000-0000-4000-8000-000000000001'),
  ('c2730000-0000-4000-8000-000000000003', pg_temp.u(3), (now() at time zone 'utc')::date - 28, 'muscle', '{}',
   'proposed', 'c2720000-0000-4000-8000-000000000003');
insert into public.routines (id, user_id, name, folder_id) values
  (pg_temp.r(1), pg_temp.u(1), 'R1', 'c2720000-0000-4000-8000-000000000001'),
  (pg_temp.r(2), pg_temp.u(1), 'R2', 'c2720000-0000-4000-8000-000000000001'),
  (pg_temp.r(3), pg_temp.u(1), 'R3 outside', null),
  (pg_temp.r(4), pg_temp.u(1), 'R4', 'c2720000-0000-4000-8000-000000000001'),
  (pg_temp.r(5), pg_temp.u(1), 'R5', 'c2720000-0000-4000-8000-000000000001'),
  (pg_temp.r(6), pg_temp.u(3), 'R6', 'c2720000-0000-4000-8000-000000000003');
insert into public.coach_plan_sessions (plan_id, user_id, week, session_index, name, exercises, routine_id, deload) values
  ('c2730000-0000-4000-8000-000000000001', pg_temp.u(1), 3, 1, 'Sesion 1', '[]', pg_temp.r(1), true);

grant execute on all functions in schema pg_temp to authenticated;
set local role authenticated;
select pg_temp.as_user(1);
select pg_temp.prefs('{25,20,15,10,5,2.5,1.25}', 2);
create temp table res (k text primary key, v jsonb);

-- ---------------------------------------------------------------------------
-- Security and constraints
-- ---------------------------------------------------------------------------
select ok(not has_table_privilege('anon', 'public.coach_changes', 'select'), 'anon cannot read coach_changes');
select ok((select relrowsecurity and relforcerowsecurity from pg_class where oid = 'public.coach_changes'::regclass),
  'RLS is enabled and forced on coach_changes');
select ok(not has_table_privilege('authenticated', 'public.coach_changes', 'delete'),
  'authenticated cannot delete coach_changes rows');
select function_privs_are('public', 'apply_coach_progression', array['uuid'], 'anon', array[]::text[],
  'anon cannot execute apply_coach_progression');
select function_privs_are('public', 'undo_coach_change', array['uuid'], 'anon', array[]::text[],
  'anon cannot execute undo_coach_change');
select function_privs_are('public', 'decide_progression_flag', array['uuid', 'text'], 'anon', array[]::text[],
  'anon cannot execute decide_progression_flag');
select throws_ok($$insert into public.coach_changes (kind, exercise_id, reason, status)
  values ('bogus', pg_temp.bx(1), 'x', 'applied')$$, '23514', null, 'an unknown kind is rejected');
select throws_ok($$insert into public.coach_changes (kind, exercise_id, reason, status)
  values ('load_raise', pg_temp.bx(1), 'x', 'undone')$$, '23514', null, 'an undone row needs decided_at');
select throws_ok($$insert into public.coach_changes (kind, exercise_id, reason, status, decided_at)
  values ('load_raise', pg_temp.bx(1), 'x', 'applied', now())$$, '23514', null, 'an applied row has no decided_at');

-- ---------------------------------------------------------------------------
-- 1. A raise: 60 -> 62,5 kg, reps to rep_min, both block routines, warm-up and drop sets untouched
-- ---------------------------------------------------------------------------
select pg_temp.block(pg_temp.bx(1), 8, 12, 60);
create temp table s1 as select pg_temp.hist(pg_temp.bx(1), 4, 8, 12, 60, array[12, 12, 12], array[8, 8, 7]) as id;
insert into res select 'c1', pg_temp.ap((select id from s1));
select is((select v -> 0 ->> 'kind' from res where k = 'c1'), 'load_raise', 'all sets at the top raise the load');
select is((select v -> 0 ->> 'status' from res where k = 'c1'), 'applied', 'the raise is applied');
select is(pg_temp.sig(1, pg_temp.bx(1)), 'warm_up:30x10,working:62.5x8,working:62.5x8,working:62.5x8,drop:48x10',
  'routine 1 gets 62,5 kg x 8 on working sets only');
select is(pg_temp.sig(2, pg_temp.bx(1)), 'warm_up:30x10,working:62.5x8,working:62.5x8,working:62.5x8,drop:48x10',
  'routine 2 of the block gets the same values');
select is(pg_temp.sig(3, pg_temp.bx(1)), 'warm_up:30x10,working:60x12,working:60x12,working:60x12,drop:48x10',
  'a routine outside the block is untouched');
select is(pg_temp.summary(1, pg_temp.bx(1)), 8, 'the planned reps summary follows the raise');
select is((select (v -> 0 -> 'before_values' -> 0 -> 'sets' -> 1 ->> 'target_weight_kg')::numeric from res where k = 'c1'),
  60::numeric, 'the row keeps the planned weight from before');
select is((select (v -> 0 -> 'after_values' -> 0 -> 'sets' -> 1 ->> 'target_weight_kg')::numeric from res where k = 'c1'),
  62.5::numeric, 'the row keeps the planned weight written');
select is((select jsonb_array_length(v -> 0 -> 'after_values') from res where k = 'c1'), 2,
  'the snapshots hold one entry per block routine');
select is((select (v -> 0 -> 'figures' ->> 'weight_to_kg')::numeric from res where k = 'c1'), 62.5::numeric,
  'the figures carry the new weight');
select is((select v -> 0 ->> 'reason' from res where k = 'c1'), 'all_sets_at_max', 'the reason code is all_sets_at_max');

-- Idempotence: a second call returns the same row and writes nothing more.
insert into res select 'c1b', pg_temp.ap((select id from s1));
select is((select v -> 0 ->> 'id' from res where k = 'c1b'), (select v -> 0 ->> 'id' from res where k = 'c1'),
  'a second call returns the existing row');
select is((select count(*)::int from public.coach_changes where exercise_id = pg_temp.bx(1)), 1,
  'a second call creates no duplicate');
select is(pg_temp.sig(1, pg_temp.bx(1)), 'warm_up:30x10,working:62.5x8,working:62.5x8,working:62.5x8,drop:48x10',
  'a second call writes nothing');

-- ---------------------------------------------------------------------------
-- 2. Hold, boundary RPE and the 04 examples
-- ---------------------------------------------------------------------------
select pg_temp.block(pg_temp.bx(2), 6, 8, 80);
select is(pg_temp.ap(pg_temp.hist(pg_temp.bx(2), 3, 6, 8, 80, array[6, 6, 7])), '[]'::jsonb,
  '80 x 6,6,7 on 6-8 holds the load (no change)');
select is(pg_temp.sig(1, pg_temp.bx(2)), 'warm_up:40x10,working:80x8,working:80x8,working:80x8,drop:64x10',
  'a hold leaves the routine alone');

select pg_temp.block(pg_temp.bx(3), 6, 8, 80);
select pg_temp.prefs('{25,20,15,10,5,2.5}', 2);
insert into res select 'c3', pg_temp.ap(pg_temp.hist(pg_temp.bx(3), 3, 6, 8, 80, array[8, 8, 8], array[8, 8, 8]));
select is(pg_temp.sig(1, pg_temp.bx(3)), 'warm_up:40x10,working:85x6,working:85x6,working:85x6,drop:64x10',
  '8,8,8 at RPE 8 with a 2,5 kg plate raises 80 to 85 kg x 6');
select pg_temp.prefs('{25,20,15,10,5,2.5,1.25}', 2);

select pg_temp.block(pg_temp.bx(4), 6, 8, 80);
select is(pg_temp.ap(pg_temp.hist(pg_temp.bx(4), 3, 6, 8, 80, array[8, 8, 8], array[9, 8, 8])), '[]'::jsonb,
  'a set at RPE 9 blocks the raise');

-- Drop: two failed sessions, 80 -> 76 -> 75.
select pg_temp.block(pg_temp.bx(5), 6, 8, 80);
select pg_temp.hist(pg_temp.bx(5), 6, 6, 8, 80, array[5, 5, 6]);
insert into res select 'c5', pg_temp.ap(pg_temp.hist(pg_temp.bx(5), 3, 6, 8, 80, array[5, 5, 6]));
select is((select v -> 0 ->> 'kind' from res where k = 'c5'), 'load_drop', 'two failed sessions drop the load');
select is(pg_temp.sig(1, pg_temp.bx(5)), 'warm_up:40x10,working:75x8,working:75x8,working:75x8,drop:64x10',
  '80 lowers 5 % to 76 and rounds to 75 kg, reps unchanged');
select is((select v -> 0 ->> 'reason' from res where k = 'c5'), 'two_sessions_below_min', 'the drop reason is two failed sessions');
select is((select (v -> 0 -> 'figures' ->> 'sessions')::int from res where k = 'c5'), 2, 'the drop figures count two sessions');

select pg_temp.block(pg_temp.bx(6), 6, 8, 80);
select pg_temp.hist(pg_temp.bx(6), 6, 6, 8, 80, array[8, 8, 8]);
select is(pg_temp.ap(pg_temp.hist(pg_temp.bx(6), 3, 6, 8, 80, array[5, 5, 5])), '[]'::jsonb,
  'one failed session after a good one does not drop');

-- ---------------------------------------------------------------------------
-- 3. The 10 % rule: exactly 10 % raises, more than 10 % flags
-- ---------------------------------------------------------------------------
select pg_temp.block(pg_temp.bx(7), 6, 8, 25);
insert into res select 'c7', pg_temp.ap(pg_temp.hist(pg_temp.bx(7), 3, 6, 8, 25, array[8, 8, 8]));
select is((select v -> 0 ->> 'kind' from res where k = 'c7'), 'load_raise', 'a step of exactly 10 % is applied');
select is(pg_temp.sig(1, pg_temp.bx(7)), 'warm_up:12.5x10,working:27.5x6,working:27.5x6,working:27.5x6,drop:20x10',
  '25 kg rises to 27,5 kg');

select pg_temp.block(pg_temp.bx(8), 6, 8, 24);
insert into res select 'c8', pg_temp.ap(pg_temp.hist(pg_temp.bx(8), 3, 6, 8, 24, array[8, 8, 8]));
select is((select v -> 0 ->> 'kind' from res where k = 'c8'), 'load_flag', 'a step over 10 % is flagged');
select is((select v -> 0 ->> 'status' from res where k = 'c8'), 'pending', 'the flag waits for the user');
select is((select v -> 0 ->> 'reason' from res where k = 'c8'), 'step_over_10_percent', 'the flag reason is step_over_10_percent');
select is(pg_temp.sig(1, pg_temp.bx(8)), 'warm_up:12x10,working:24x8,working:24x8,working:24x8,drop:19.2x10',
  'a flag writes nothing to the routines');
select is((select (v -> 0 -> 'figures' ->> 'next_kg')::numeric from res where k = 'c8'), 26.5::numeric, 'the flag stores the next weight');
select is((select (v -> 0 -> 'figures' ->> 'percent')::numeric from res where k = 'c8'), 10.4::numeric, 'the flag stores the percent');

-- A later session of the same exercise adds no second flag and writes no weight.
select is(pg_temp.ap(pg_temp.hist(pg_temp.bx(8), 1, 6, 8, 24, array[8, 8, 8])), '[]'::jsonb,
  'while a flag is pending a later session adds nothing');
select is((select count(*)::int from public.coach_changes where exercise_id = pg_temp.bx(8)), 1, 'the flag is not duplicated');

-- Dumbbells use the handle step: 6 kg + 2 kg is +33 %.
select pg_temp.block(pg_temp.dx(1), 6, 8, 6);
insert into res select 'c9', pg_temp.ap(pg_temp.hist(pg_temp.dx(1), 3, 6, 8, 6, array[8, 8, 8]));
select is((select v -> 0 ->> 'kind' from res where k = 'c9'), 'load_flag', 'dumbbells at 6 kg flag a +33 % step');
select is((select (v -> 0 -> 'figures' ->> 'step_kg')::numeric from res where k = 'c9'), 2::numeric, 'the dumbbell step is the handle');
select pg_temp.block(pg_temp.dx(2), 6, 8, 40);
insert into res select 'c9b', pg_temp.ap(pg_temp.hist(pg_temp.dx(2), 3, 6, 8, 40, array[8, 8, 8]));
select is(pg_temp.sig(1, pg_temp.dx(2)), 'warm_up:20x10,working:42x6,working:42x6,working:42x6,drop:32x10',
  'dumbbells at 40 kg raise by the handle step');

-- ---------------------------------------------------------------------------
-- 4. Discomfort
-- ---------------------------------------------------------------------------
select pg_temp.block(pg_temp.bx(9), 6, 8, 50);
insert into public.exercise_discomforts (user_id, exercise_id, logged_at, level)
values (pg_temp.u(1), pg_temp.bx(9), now() - interval '1 day', 2);
insert into res select 'c10', pg_temp.ap(pg_temp.hist(pg_temp.bx(9), 3, 6, 8, 50, array[8, 8, 8]));
select is((select v -> 0 ->> 'kind' from res where k = 'c10'), 'load_kept', 'an open level 1-3 discomfort keeps the load');
select is((select v -> 0 ->> 'reason' from res where k = 'c10'), 'kept_discomfort', 'the kept reason is kept_discomfort');
select is(pg_temp.sig(1, pg_temp.bx(9)), 'warm_up:25x10,working:50x8,working:50x8,working:50x8,drop:40x10',
  'a kept load writes nothing');
select throws_ok($$select public.undo_coach_change((select (v -> 0 ->> 'id')::uuid from res where k = 'c10'))$$,
  'P0001', 'coach_change_not_undoable', 'a kept row cannot be undone');

select pg_temp.block(pg_temp.bx(10), 6, 8, 50);
insert into public.exercise_discomforts (user_id, exercise_id, logged_at, level)
values (pg_temp.u(1), pg_temp.bx(10), now() - interval '1 day', 4);
select is(pg_temp.ap(pg_temp.hist(pg_temp.bx(10), 3, 6, 8, 50, array[8, 8, 8])), '[]'::jsonb,
  'a severe discomfort changes nothing and writes no row');

-- ---------------------------------------------------------------------------
-- 5. No rep range: flag only
-- ---------------------------------------------------------------------------
select pg_temp.block(pg_temp.bx(11), null, null, 50);
insert into res select 'c11', pg_temp.ap(pg_temp.hist(pg_temp.bx(11), 3, null, null, 50, array[12, 12, 12]));
select is((select v -> 0 ->> 'kind' from res where k = 'c11'), 'no_range_flag', 'an exercise without a range gets a no-range flag');
select is((select v -> 0 ->> 'status' from res where k = 'c11'), 'pending', 'the no-range flag is pending');
select is(pg_temp.sig(1, pg_temp.bx(11)), 'warm_up:25x10,working:50x10,working:50x10,working:50x10,drop:40x10',
  'a no-range flag writes nothing');
select is(pg_temp.ap(pg_temp.hist(pg_temp.bx(11), 1, null, null, 50, array[12, 12, 12])), '[]'::jsonb,
  'a pending no-range flag is not duplicated');
update public.routine_exercises set rep_min = 8, rep_max = 12
where routine_id = pg_temp.r(1) and exercise_id = pg_temp.bx(11);
select pg_temp.ap(pg_temp.hist(pg_temp.bx(2), 0, 6, 8, 80, array[6, 6, 6]));
select is((select decision from public.coach_changes where exercise_id = pg_temp.bx(11)), 'range_added',
  'the no-range flag closes once the exercise has a range');
select is((select status from public.coach_changes where exercise_id = pg_temp.bx(11)), 'decided', 'the closed flag is decided');
select throws_ok($$select public.decide_progression_flag(
  (select id from public.coach_changes where exercise_id = pg_temp.bx(11)), 'raise')$$,
  '22023', null, 'a no-range flag is not decidable');

-- ---------------------------------------------------------------------------
-- 6. Deciding a flag
-- ---------------------------------------------------------------------------
select throws_ok($$select public.decide_progression_flag((select (v -> 0 ->> 'id')::uuid from res where k = 'c8'), 'maybe')$$,
  '22023', null, 'a bad decision is rejected');
insert into res select 'd8', public.decide_progression_flag((select (v -> 0 ->> 'id')::uuid from res where k = 'c8'), 'raise');
select is((select v -> 'flag' ->> 'status' from res where k = 'd8'), 'decided', 'raising anyway closes the flag');
select is((select v -> 'flag' ->> 'decision' from res where k = 'd8'), 'raise', 'the flag stores the decision');
select is((select v -> 'change' ->> 'kind' from res where k = 'd8'), 'load_raise', 'raising anyway records a load_raise row');
select is((select origin_flag_id from public.coach_changes where id = (select (v -> 'change' ->> 'id')::uuid from res where k = 'd8')),
  (select (v -> 0 ->> 'id')::uuid from res where k = 'c8'), 'the new row points at the flag');
select is(pg_temp.sig(1, pg_temp.bx(8)), 'warm_up:12x10,working:26.5x6,working:26.5x6,working:26.5x6,drop:19.2x10',
  'raising anyway writes the stored step to the block');
select is(pg_temp.sig(2, pg_temp.bx(8)), 'warm_up:12x10,working:26.5x6,working:26.5x6,working:26.5x6,drop:19.2x10',
  'raising anyway reaches every routine of the block');
select throws_ok($$select public.decide_progression_flag((select (v -> 0 ->> 'id')::uuid from res where k = 'c8'), 'keep')$$,
  'P0001', 'coach_change_not_pending', 'a decided flag cannot be decided again');
insert into res select 'u8', public.undo_coach_change((select (v -> 'change' ->> 'id')::uuid from res where k = 'd8'));
select is((select (v ->> 'restored')::int from res where k = 'u8'), 2, 'undoing a flag raise restores both routines');
select is(pg_temp.sig(1, pg_temp.bx(8)), 'warm_up:12x10,working:24x8,working:24x8,working:24x8,drop:19.2x10',
  'undo restores the planned weight and reps');

-- Keep the weight and add repetitions.
select pg_temp.block(pg_temp.bx(12), 6, 8, 24);
insert into res select 'c12', pg_temp.ap(pg_temp.hist(pg_temp.bx(12), 3, 6, 8, 24, array[8, 8, 8]));
insert into res select 'd12', public.decide_progression_flag((select (v -> 0 ->> 'id')::uuid from res where k = 'c12'), 'keep');
select is((select v -> 'flag' ->> 'decision' from res where k = 'd12'), 'keep', 'keeping stores the decision');
select is(pg_temp.rmax(1, pg_temp.bx(12)), 10, 'keeping adds 2 to rep_max in routine 1');
select is(pg_temp.rmax(2, pg_temp.bx(12)), 10, 'keeping adds 2 to rep_max in every block routine');
select is(pg_temp.rmax(3, pg_temp.bx(12)), 8, 'a routine outside the block keeps its range');
select is(pg_temp.sig(1, pg_temp.bx(12)), 'warm_up:12x10,working:24x8,working:24x8,working:24x8,drop:19.2x10',
  'keeping does not change the weight');
select throws_ok($$select public.undo_coach_change((select (v -> 'flag' ->> 'id')::uuid from res where k = 'd12'))$$,
  'P0001', 'coach_change_not_undoable', 'a kept flag cannot be undone');
-- The range changed, so the next session is evaluated fresh with the new range.
select is(pg_temp.ap(pg_temp.hist(pg_temp.bx(12), 1, 6, 10, 24, array[8, 8, 8])), '[]'::jsonb,
  'after keeping, 8 reps on 6-10 is no longer at the top');

-- The cap at 50.
select pg_temp.block(pg_temp.bx(13), 40, 49, 24);
insert into res select 'c13', pg_temp.ap(pg_temp.hist(pg_temp.bx(13), 3, 40, 49, 24, array[49, 49, 49]));
select public.decide_progression_flag((select (v -> 0 ->> 'id')::uuid from res where k = 'c13'), 'keep');
select is(pg_temp.rmax(1, pg_temp.bx(13)), 50, 'keeping never goes above 50 repetitions');

-- A stale decision after the user edited the weight.
select pg_temp.block(pg_temp.bx(14), 6, 8, 24);
insert into res select 'c14', pg_temp.ap(pg_temp.hist(pg_temp.bx(14), 3, 6, 8, 24, array[8, 8, 8]));
select pg_temp.set_working(1, pg_temp.bx(14), 30);
select throws_ok($$select public.decide_progression_flag((select (v -> 0 ->> 'id')::uuid from res where k = 'c14'), 'raise')$$,
  'P0001', 'coach_change_conflict', 'a decision after a user weight edit is a conflict');
select is((select status from public.coach_changes where id = (select (v -> 0 ->> 'id')::uuid from res where k = 'c14')),
  'pending', 'the conflicting flag stays pending');
select is(pg_temp.sig(1, pg_temp.bx(14)), 'warm_up:12x10,working:30x8,working:30x8,working:30x8,drop:19.2x10',
  'the conflict keeps the user''s edit');

-- ---------------------------------------------------------------------------
-- 7. Undo
-- ---------------------------------------------------------------------------
insert into res select 'u1', public.undo_coach_change((select (v -> 0 ->> 'id')::uuid from res where k = 'c1'));
select is((select (v ->> 'restored')::int from res where k = 'u1'), 2, 'undo restores both routines');
select is((select (v ->> 'skipped')::int from res where k = 'u1'), 0, 'undo skips nothing when nothing was edited');
select is(pg_temp.sig(1, pg_temp.bx(1)), 'warm_up:30x10,working:60x12,working:60x12,working:60x12,drop:48x10',
  'undo brings back the weight and the planned reps');
select is(pg_temp.summary(1, pg_temp.bx(1)), 12, 'undo restores the planned reps summary');
select is((select status from public.coach_changes where id = (select (v -> 0 ->> 'id')::uuid from res where k = 'c1')),
  'undone', 'the row becomes undone');
select ok((select decided_at is not null from public.coach_changes where id = (select (v -> 0 ->> 'id')::uuid from res where k = 'c1')),
  'the undone row records when');
select throws_ok($$select public.undo_coach_change((select (v -> 0 ->> 'id')::uuid from res where k = 'c1'))$$,
  'P0001', 'coach_change_not_undoable', 'a second undo is refused');
select is((select pg_temp.ap((select id from s1)) -> 0 ->> 'status'),
  'undone', 'a retry after the undo returns the undone row');
select is(pg_temp.sig(1, pg_temp.bx(1)), 'warm_up:30x10,working:60x12,working:60x12,working:60x12,drop:48x10',
  'a retry after the undo does not raise again');

-- An edited routine is skipped, the other one restored.
select pg_temp.block(pg_temp.bx(15), 6, 8, 40);
insert into res select 'c15', pg_temp.ap(pg_temp.hist(pg_temp.bx(15), 3, 6, 8, 40, array[8, 8, 8]));
select pg_temp.set_working(2, pg_temp.bx(15), 70);
insert into res select 'u15', public.undo_coach_change((select (v -> 0 ->> 'id')::uuid from res where k = 'c15'));
select is((select (v ->> 'restored')::int from res where k = 'u15'), 1, 'undo restores the routine the user did not touch');
select is((select (v ->> 'skipped')::int from res where k = 'u15'), 1, 'undo skips the routine the user edited');
select is(pg_temp.sig(2, pg_temp.bx(15)), 'warm_up:20x10,working:70x6,working:70x6,working:70x6,drop:32x10',
  'the user''s edit wins');
select is(pg_temp.sig(1, pg_temp.bx(15)), 'warm_up:20x10,working:40x8,working:40x8,working:40x8,drop:32x10',
  'the untouched routine is restored');

-- Every routine edited: conflict, row unchanged.
select pg_temp.block(pg_temp.bx(16), 6, 8, 40);
insert into res select 'c16', pg_temp.ap(pg_temp.hist(pg_temp.bx(16), 3, 6, 8, 40, array[8, 8, 8]));
select pg_temp.set_working(1, pg_temp.bx(16), 70);
select pg_temp.set_working(2, pg_temp.bx(16), 70);
select throws_ok($$select public.undo_coach_change((select (v -> 0 ->> 'id')::uuid from res where k = 'c16'))$$,
  'P0001', 'coach_change_conflict', 'undo with every routine edited is a conflict');
select is((select status from public.coach_changes where id = (select (v -> 0 ->> 'id')::uuid from res where k = 'c16')),
  'applied', 'a conflicting undo leaves the row applied');

-- ---------------------------------------------------------------------------
-- 8. Stale session, outside a block, plan not accepted
-- ---------------------------------------------------------------------------
select pg_temp.block(pg_temp.bx(17), 6, 8, 40);
create temp table stale as select pg_temp.hist(pg_temp.bx(17), 5, 6, 8, 40, array[8, 8, 8]) as id;
select pg_temp.hist(pg_temp.bx(17), 2, 6, 8, 40, array[6, 6, 6]);
select is(pg_temp.ap((select id from stale)), '[]'::jsonb, 'an older session never applies over newer data');
select is(pg_temp.sig(1, pg_temp.bx(17)), 'warm_up:20x10,working:40x8,working:40x8,working:40x8,drop:32x10',
  'a stale session writes nothing');

select pg_temp.block(pg_temp.bx(18), 6, 8, 40);
create temp table outside as select pg_temp.hist(pg_temp.bx(18), 3, 6, 8, 40, array[8, 8, 8], null, 3) as id;
select is(pg_temp.ap((select id from outside)), '[]'::jsonb, 'a session of a routine outside the block applies nothing');
select is(pg_temp.sig(3, pg_temp.bx(18)), 'warm_up:20x10,working:40x8,working:40x8,working:40x8,drop:32x10',
  'the outside routine keeps its values');
select pg_temp.as_user(1);
insert into public.workout_sessions (id, user_id, name, status, started_at, routine_id)
values ('c2740000-0000-4000-8000-000000000001', pg_temp.u(1), 'Now', 'in_progress', now(), pg_temp.r(3));
insert into public.workout_sets (session_id, exercise_id, exercise_position, set_index, rep_min, rep_max)
values ('c2740000-0000-4000-8000-000000000001', pg_temp.bx(18), 0, 1, 6, 8);
select is(public.progression_suggestions('c2740000-0000-4000-8000-000000000001') -> 0 ->> 'action', 'raise_weight',
  'outside a block the live-session suggestion still works');

select pg_temp.mkre(6, pg_temp.bx(19), 6, 8, 40);
select is(pg_temp.ap(pg_temp.hist(pg_temp.bx(19), 3, 6, 8, 40, array[8, 8, 8], null, 6, 3), 3), '[]'::jsonb,
  'a plan that is not accepted applies nothing');
select pg_temp.as_user(1);

-- ---------------------------------------------------------------------------
-- 9. ADR-70 ordering: the routine update runs first, the coach's value wins
-- ---------------------------------------------------------------------------
select pg_temp.mkre(4, pg_temp.bx(20), 8, 12, 60);
select pg_temp.mkre(5, pg_temp.bx(20), 8, 12, 60);
create temp table adr as select pg_temp.hist(pg_temp.bx(20), 3, 8, 12, 60, array[12, 12, 12], array[8, 8, 8], 4) as id;
select public.update_routine_from_session((select id from adr));
select is(pg_temp.sig(4, pg_temp.bx(20)), 'working:60x12,working:60x12,working:60x12',
  'the routine update copies what was lifted');
insert into res select 'c20', pg_temp.ap((select id from adr));
select is(pg_temp.sig(4, pg_temp.bx(20)), 'working:62.5x8,working:62.5x8,working:62.5x8',
  'the coach''s raise goes on top of the routine update');
select is(pg_temp.sig(5, pg_temp.bx(20)), 'warm_up:30x10,working:62.5x8,working:62.5x8,working:62.5x8,drop:48x10',
  'the other block routine gets the raise as well');

-- ---------------------------------------------------------------------------
-- 10. Deload weeks and the 14-day gap
-- ---------------------------------------------------------------------------
select pg_temp.block(pg_temp.bx(21), 6, 8, 40);
select is(pg_temp.ap(pg_temp.hist(pg_temp.bx(21), 10, 6, 8, 40, array[8, 8, 8])), '[]'::jsonb,
  'a session in a deload week never raises');
select is(pg_temp.sig(1, pg_temp.bx(21)), 'warm_up:20x10,working:40x8,working:40x8,working:40x8,drop:32x10',
  'a deload-week session writes nothing');

-- The deload session is skipped, not a reset: failed sessions before and after it count as consecutive.
select pg_temp.block(pg_temp.bx(22), 6, 8, 80);
select pg_temp.hist(pg_temp.bx(22), 20, 6, 8, 80, array[5, 5, 5]);
select pg_temp.hist(pg_temp.bx(22), 10, 6, 8, 80, array[8, 8, 8]);
insert into res select 'c22', pg_temp.ap(pg_temp.hist(pg_temp.bx(22), 3, 6, 8, 80, array[5, 5, 5]));
select is((select v -> 0 ->> 'kind' from res where k = 'c22'), 'load_drop',
  'a deload-week session does not reset the failed count');
select is(pg_temp.sig(1, pg_temp.bx(22)), 'warm_up:40x10,working:75x8,working:75x8,working:75x8,drop:64x10',
  'the drop is applied across the deload week');

-- Gap rule: no raise on the first session after 14 or more days.
select pg_temp.block(pg_temp.bx(23), 6, 8, 40);
select pg_temp.hist(pg_temp.bx(23), 20, 6, 8, 40, array[6, 6, 6]);
select is(pg_temp.ap(pg_temp.hist(pg_temp.bx(23), 2, 6, 8, 40, array[8, 8, 8])), '[]'::jsonb,
  'the first session after a 14-day gap does not raise');
-- One failed session after the gap is enough for the drop.
select pg_temp.block(pg_temp.bx(24), 6, 8, 80);
select pg_temp.hist(pg_temp.bx(24), 20, 6, 8, 80, array[7, 7, 7]);
insert into res select 'c24', pg_temp.ap(pg_temp.hist(pg_temp.bx(24), 2, 6, 8, 80, array[5, 5, 5]));
select is((select v -> 0 ->> 'reason' from res where k = 'c24'), 'one_session_below_min_after_gap',
  'one failed session after a gap lowers the load');
select is((select (v -> 0 -> 'figures' ->> 'sessions')::int from res where k = 'c24'), 1, 'the gap drop counts one session');
select is(pg_temp.sig(1, pg_temp.bx(24)), 'warm_up:40x10,working:75x8,working:75x8,working:75x8,drop:64x10',
  'the gap drop writes 75 kg');
-- Without a gap, one failed session does not drop.
select pg_temp.block(pg_temp.bx(25), 6, 8, 80);
select pg_temp.hist(pg_temp.bx(25), 6, 6, 8, 80, array[7, 7, 7]);
select is(pg_temp.ap(pg_temp.hist(pg_temp.bx(25), 2, 6, 8, 80, array[5, 5, 5])), '[]'::jsonb,
  'without a gap one failed session does not drop');

-- progression_suggestions is unchanged by the writer and still reads only earlier sessions.
select pg_temp.as_user(1);
select is(public.progression_suggestions('c2740000-0000-4000-8000-000000000001') -> 0 ->> 'reason', 'all_sets_at_max',
  'progression_suggestions keeps its reason codes');

-- ---------------------------------------------------------------------------
-- 11. Isolation between users
-- ---------------------------------------------------------------------------
select pg_temp.as_user(2);
select is((select count(*)::int from public.coach_changes), 0, 'user B cannot read user A''s coach changes');
select throws_ok($$select public.undo_coach_change((select (v -> 0 ->> 'id')::uuid from res where k = 'c15'))$$,
  'P0002', null, 'user B cannot undo user A''s change');
select throws_ok($$select public.decide_progression_flag((select (v -> 0 ->> 'id')::uuid from res where k = 'c14'), 'keep')$$,
  'P0002', null, 'user B cannot decide user A''s flag');
select is(public.apply_coach_progression((select id from stale)), '[]'::jsonb, 'user B applying user A''s session gets nothing');
select throws_ok($$insert into public.coach_changes (user_id, kind, exercise_id, reason, status)
  values (pg_temp.u(1), 'load_kept', pg_temp.bx(1), 'x', 'applied')$$,
  '42501', null, 'user B cannot insert a row for user A');
select is((select count(*)::int from public.coach_changes where user_id = pg_temp.u(1)), 0, 'user B sees none of A''s rows');
select pg_temp.as_user(1);
select ok((select count(*)::int from public.coach_changes) > 10, 'user A sees their own rows');
select throws_ok($$select public.apply_coach_progression(null)$$, '22023', null, 'a null session id is rejected');

select * from finish();
rollback;
