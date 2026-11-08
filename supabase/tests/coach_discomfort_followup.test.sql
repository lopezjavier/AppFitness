-- pgTAP: discomfort follow-up (T-241, US-108; rules in planning/04 section 4.12 and planning/07 section 7).
-- coach_discomfort_followups, coach_swap_candidates, coach_swap_flagged_exercise, decline_discomfort_swap,
-- dismiss_discomfort_note, the two new exercise_discomforts columns, and the level 1-3 end to end (no load increase).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(88);

create function pg_temp.u(n int) returns uuid language sql as $$
  select ('c2e00000-0000-4000-8000-0000000000' || lpad(n::text, 2, '0'))::uuid $$;
create function pg_temp.r(n int) returns uuid language sql as $$
  select ('c2e10000-0000-4000-8000-0000000000' || lpad(n::text, 2, '0'))::uuid $$;
create function pg_temp.as_user(n int) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', pg_temp.u(n), 'role', 'authenticated')::text, true) $$;
-- Catalog exercise by key; x(n) is the n-th isolation exercise (never part of the block).
create function pg_temp.k(p_key text) returns uuid language sql as $$
  select id from public.exercises where key = p_key $$;
create function pg_temp.x(n int) returns uuid language sql as $$
  select id from public.exercises where not is_custom and movement_pattern = 'isolation' order by key offset n - 1 limit 1 $$;

-- A routine exercise at a position: warm-up, three working sets and a drop set, range 8-12, rest 90 s, RPE 8.
create function pg_temp.mkre(rt int, ex uuid, pos int, w numeric) returns void language plpgsql as $$
declare
  v uuid := gen_random_uuid();
begin
  insert into public.routine_exercises
    (id, routine_id, exercise_id, position, target_sets, target_reps, rep_min, rep_max, rest_seconds, target_rpe)
  values (v, pg_temp.r(rt), ex, pos, 5, 8, 8, 12, 90, 8);
  insert into public.routine_exercise_sets (routine_exercise_id, set_index, target_weight_kg, target_reps, set_type) values
    (v, 1, round(w / 2, 2), 10, 'warm_up'),
    (v, 2, w, 8, 'working'),
    (v, 3, w, 8, 'working'),
    (v, 4, w, 8, 'working'),
    (v, 5, round(w * 0.8, 2), 10, 'drop');
end $$;

-- A completed session `days` days ago (routine rt, or none) with one working row per entry of reps.
create function pg_temp.hist(ex uuid, days int, w numeric, reps int[], rpes numeric[] default null,
                             rt int default null, usr int default 1) returns uuid
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
         v_start + interval '30 minutes', 8, 12
  from generate_subscripts(reps, 1) i;
  return v_sid;
end $$;

create function pg_temp.ap(sid uuid, usr int default 1) returns jsonb language plpgsql as $$
begin
  perform pg_temp.as_user(usr);
  return public.apply_coach_progression(sid);
end $$;

-- Planned sets of an exercise in a routine, e.g. warm_up:-x10,working:32x8 (- = no weight).
create function pg_temp.sig(rt int, ex uuid) returns text language sql as $$
  select string_agg(s.set_type::text || ':' || coalesce(trim_scale(s.target_weight_kg)::text, '-') || 'x' || s.target_reps,
                    ',' order by s.set_index)
  from public.routine_exercises re
  join public.routine_exercise_sets s on s.routine_exercise_id = re.id
  where re.routine_id = pg_temp.r(rt) and re.exercise_id = ex $$;
-- What a swap must keep: position | sets | range | rest | rpe.
create function pg_temp.shape(rt int, ex uuid) returns text language sql as $$
  select position || '|' || target_sets || '|' || rep_min || '-' || rep_max || '|' || rest_seconds || '|' || trim_scale(target_rpe)
  from public.routine_exercises where routine_id = pg_temp.r(rt) and exercise_id = ex $$;
-- Follow-up fields of one exercise.
create function pg_temp.offer(ex uuid) returns boolean language sql as $$
  select offer_swap from public.coach_discomfort_followups() where exercise_id = ex $$;
create function pg_temp.note(ex uuid) returns boolean language sql as $$
  select show_note from public.coach_discomfort_followups() where exercise_id = ex $$;
create function pg_temp.reason(ex uuid) returns text language sql as $$
  select note_reason from public.coach_discomfort_followups() where exercise_id = ex $$;

-- ---------------------------------------------------------------------------
-- Fixtures that need the owner role
-- ---------------------------------------------------------------------------
insert into auth.users (id, email)
select pg_temp.u(n), 'follow' || n || '@example.com' from generate_series(1, 3) n;

insert into public.routine_folders (id, user_id, name) values
  ('c2e20000-0000-4000-8000-000000000001', pg_temp.u(1), 'Bloque'),
  ('c2e20000-0000-4000-8000-000000000002', pg_temp.u(2), 'Bloque 2');
-- User 1: accepted block started 21 days ago (week 2 is the deload week). User 2: a proposed plan only.
insert into public.coach_plans (id, user_id, block_start, intention, nutrition, status, folder_id) values
  ('c2e30000-0000-4000-8000-000000000001', pg_temp.u(1), (now() at time zone 'utc')::date - 21, 'muscle', '{}',
   'accepted', 'c2e20000-0000-4000-8000-000000000001'),
  ('c2e30000-0000-4000-8000-000000000002', pg_temp.u(2), (now() at time zone 'utc')::date - 21, 'muscle', '{}',
   'proposed', 'c2e20000-0000-4000-8000-000000000002');
insert into public.routines (id, user_id, name, folder_id) values
  (pg_temp.r(1), pg_temp.u(1), 'R1', 'c2e20000-0000-4000-8000-000000000001'),
  (pg_temp.r(2), pg_temp.u(1), 'R2', 'c2e20000-0000-4000-8000-000000000001'),
  (pg_temp.r(3), pg_temp.u(1), 'R3 outside', null),
  (pg_temp.r(9), pg_temp.u(2), 'R9', 'c2e20000-0000-4000-8000-000000000002');
select pg_temp.mkre(1, pg_temp.k('db-bench-press'), 0, 20);
select pg_temp.mkre(1, pg_temp.k('bench-press'), 1, 60);
select pg_temp.mkre(1, pg_temp.k('close-grip-bench-press'), 2, 60);
select pg_temp.mkre(1, pg_temp.k('paused-bench-press'), 3, 60);
select pg_temp.mkre(2, pg_temp.k('incline-db-press'), 0, 20);
select pg_temp.mkre(2, pg_temp.k('db-bench-press'), 1, 20);
select pg_temp.mkre(3, pg_temp.k('db-bench-press'), 0, 20);
select pg_temp.mkre(9, pg_temp.k('db-bench-press'), 0, 20);
-- The plan weeks snapshot of the first session slot.
insert into public.coach_plan_sessions (plan_id, user_id, week, session_index, name, exercises, routine_id, deload)
select 'c2e30000-0000-4000-8000-000000000001', pg_temp.u(1), w, 1, 'Sesion 1',
       jsonb_build_array(
         jsonb_build_object('exercise_id', pg_temp.k('db-bench-press')::text, 'exercise_key', 'db-bench-press', 'role', 'main'),
         jsonb_build_object('exercise_id', pg_temp.k('incline-db-press')::text, 'exercise_key', 'incline-db-press', 'role', 'accessory')),
       pg_temp.r(1), w = 2
from generate_series(1, 4) w;
-- Routine-less workouts keep the days 14 to 8 ago as plan week 2 (the deload week).
insert into public.workout_sessions (user_id, name, status, started_at, ended_at)
select pg_temp.u(1), 'Filler', 'completed', now() - make_interval(days => d), now() - make_interval(days => d) + interval '1 hour'
from unnest(array[15, 9]) d;

insert into public.training_preferences (user_id, plate_kg, dumbbell_handle_kg, equipment)
values (pg_temp.u(1), '{25,20,15,10,5,2.5,1.25}', 2, '{dumbbell}');
insert into public.exercise_favorites (user_id, exercise_id) values (pg_temp.u(1), pg_temp.k('push-up'));

-- Discomforts (logged_at in the past, so the owner sets them).
insert into public.exercise_discomforts (user_id, exercise_id, logged_at, level, resolved_at) values
  (pg_temp.u(1), pg_temp.k('db-bench-press'),      now() - interval '1 day',   4, null),
  (pg_temp.u(1), pg_temp.k('decline-bench-press'), now() - interval '1 day',   5, null),
  (pg_temp.u(1), pg_temp.k('push-up'),             now() - interval '1 day',   2, null),
  (pg_temp.u(1), pg_temp.k('wall-push-ups'),       now() - interval '1 day',   5, now() - interval '1 hour'),
  (pg_temp.u(1), pg_temp.x(1),                     now() - interval '13 days', 4, null),
  (pg_temp.u(1), pg_temp.x(2),                     now() - interval '14 days', 4, null),
  (pg_temp.u(1), pg_temp.x(3),                     now() - interval '3 days',  4, null),
  (pg_temp.u(1), pg_temp.x(3),                     now() - interval '2 days',  5, null),
  (pg_temp.u(1), pg_temp.x(3),                     now() - interval '1 day',   4, null),
  (pg_temp.u(1), pg_temp.x(4),                     now() - interval '3 days',  4, null),
  (pg_temp.u(1), pg_temp.x(4),                     now() - interval '1 day',   4, null),
  (pg_temp.u(1), pg_temp.x(5),                     now() - interval '20 days', 4, null),
  (pg_temp.u(1), pg_temp.x(6),                     now() - interval '15 days', 4, null),
  (pg_temp.u(1), pg_temp.x(6),                     now() - interval '2 days',  4, null),
  (pg_temp.u(1), pg_temp.x(6),                     now() - interval '1 day',   4, null),
  -- level 1-3 in the block (end to end): 6 days open still blocks the raise, 7 days has expired
  (pg_temp.u(1), pg_temp.k('bench-press'),            now() - interval '6 days', 3, null),
  (pg_temp.u(1), pg_temp.k('close-grip-bench-press'), now() - interval '7 days', 3, null),
  (pg_temp.u(1), pg_temp.k('paused-bench-press'),     now() - interval '1 day',  4, null),
  (pg_temp.u(2), pg_temp.k('db-bench-press'),         now() - interval '1 day',  4, null);

grant execute on all functions in schema pg_temp to authenticated;
set local role authenticated;
select pg_temp.as_user(1);

-- ---------------------------------------------------------------------------
-- Security
-- ---------------------------------------------------------------------------
select function_privs_are('public', 'coach_discomfort_followups', array[]::text[], 'anon', array[]::text[],
  'anon cannot execute coach_discomfort_followups');
select function_privs_are('public', 'coach_swap_candidates', array['uuid'], 'anon', array[]::text[],
  'anon cannot execute coach_swap_candidates');
select function_privs_are('public', 'coach_swap_flagged_exercise', array['uuid', 'uuid'], 'anon', array[]::text[],
  'anon cannot execute coach_swap_flagged_exercise');
select function_privs_are('public', 'decline_discomfort_swap', array['uuid'], 'anon', array[]::text[],
  'anon cannot execute decline_discomfort_swap');
select function_privs_are('public', 'dismiss_discomfort_note', array['uuid'], 'anon', array[]::text[],
  'anon cannot execute dismiss_discomfort_note');
select function_privs_are('public', 'coach_swap_flagged_exercise', array['uuid', 'uuid'], 'authenticated',
  array['EXECUTE'], 'authenticated can execute coach_swap_flagged_exercise');
select ok(has_column_privilege('authenticated', 'public.exercise_discomforts', 'swap_declined_at', 'UPDATE')
      and has_column_privilege('authenticated', 'public.exercise_discomforts', 'note_dismissed_at', 'UPDATE')
      and has_column_privilege('authenticated', 'public.exercise_discomforts', 'resolved_at', 'UPDATE'),
  'the two new columns and resolved_at can be updated');
select ok(not has_column_privilege('authenticated', 'public.exercise_discomforts', 'level', 'UPDATE'),
  'level stays immutable');
select ok(not has_column_privilege('authenticated', 'public.exercise_discomforts', 'logged_at', 'UPDATE'),
  'logged_at stays immutable');

-- ---------------------------------------------------------------------------
-- Follow-ups: offer
-- ---------------------------------------------------------------------------
select is(pg_temp.offer(pg_temp.k('db-bench-press')), true, 'an unresolved level 4 on an exercise of the block offers a swap');
select is((select discomfort_id from public.coach_discomfort_followups() where exercise_id = pg_temp.k('db-bench-press')),
  (select id from public.exercise_discomforts where user_id = pg_temp.u(1) and exercise_id = pg_temp.k('db-bench-press')),
  'the follow-up names the discomfort row');
select is(pg_temp.note(pg_temp.k('db-bench-press')), false, 'one day old: no note yet');
select is(pg_temp.reason(pg_temp.k('db-bench-press')), null, 'no note, no reason');
select is(pg_temp.offer(pg_temp.k('decline-bench-press')), false, 'a level 5 outside the block offers no swap');
select is((select count(*)::int from public.coach_discomfort_followups()
           where exercise_id in (pg_temp.k('push-up'), pg_temp.k('bench-press'), pg_temp.k('close-grip-bench-press'))),
  0, 'level 1-3 discomforts give no follow-up');
select is((select count(*)::int from public.coach_discomfort_followups() where exercise_id = pg_temp.k('wall-push-ups')),
  0, 'a resolved discomfort gives no follow-up');

-- Followups: note triggers (works outside a block)
select is(pg_temp.note(pg_temp.x(1)), false, 'a level 4 open for 13 days shows no note');
select is(pg_temp.note(pg_temp.x(2)), true, 'exactly 14 days shows the note');
select is(pg_temp.reason(pg_temp.x(2)), 'days14', 'the 14-day reason is days14');
select is(pg_temp.offer(pg_temp.x(2)), false, 'the note works outside a block (no swap offer there)');
select is(pg_temp.note(pg_temp.x(3)), true, 'three level 4-5 rows show the note');
select is(pg_temp.reason(pg_temp.x(3)), 'sessions3', 'the three-row reason is sessions3');
select is(pg_temp.note(pg_temp.x(4)), false, 'two level 4-5 rows show no note');
select is(pg_temp.reason(pg_temp.x(6)), 'days14', 'when both triggers hold the 14-day reason wins');

-- ---------------------------------------------------------------------------
-- Swap candidates
-- ---------------------------------------------------------------------------
create temp table cand as select * from public.coach_swap_candidates(pg_temp.k('db-bench-press'));
grant select on cand to authenticated;
select ok(exists (select 1 from cand where exercise_id = pg_temp.k('dumbbell-floor-press')), 'a same-pattern dumbbell exercise is a candidate');
select is((select count(*)::int from cand c join public.exercises e on e.id = c.exercise_id
           where e.movement_pattern is distinct from 'horizontal_push'), 0, 'every candidate has the same movement pattern');
select is((select count(*)::int from cand c where c.exercise_id = pg_temp.k('db-bench-press')), 0, 'the exercise itself is not a candidate');
select is((select count(*)::int from cand c join public.exercises e on e.id = c.exercise_id where e.equipment = 'barbell'),
  0, 'barbell is outside the current equipment');
select ok(exists (select 1 from cand c join public.exercises e on e.id = c.exercise_id where e.equipment = 'bodyweight'),
  'bodyweight is always inside the equipment');
select is((select count(*)::int from cand where exercise_id = pg_temp.k('decline-bench-press')), 0,
  'an exercise with its own unresolved level 4-5 is excluded');
select is((select count(*)::int from cand where exercise_id = pg_temp.k('incline-db-press')), 0,
  'an exercise already in a routine of the block is excluded');
select is((select exercise_id from public.coach_swap_candidates(pg_temp.k('db-bench-press')) limit 1 offset 0),
  pg_temp.k('push-up'), 'favourites come first');
select is((select is_favorite from cand where exercise_id = pg_temp.k('push-up')), true, 'the favourite is flagged');
select is((select exercise_id from public.coach_swap_candidates(pg_temp.k('db-bench-press')) limit 1 offset 1),
  pg_temp.k('dumbbell-floor-press'), 'then the curated similarity, highest first');
select is((select similarity from cand where exercise_id = pg_temp.k('dumbbell-floor-press')), 1.00::numeric, 'the curated similarity is returned');
select is((select similarity from cand where exercise_id = pg_temp.k('close-grip-db-bench-press')), 0::numeric, 'no curated pair is similarity 0');
select is((select count(*)::int from public.coach_swap_candidates(pg_temp.k('push-up'))), 0, 'an exercise outside the block has no candidates');
select is((select count(*)::int from public.coach_swap_candidates(gen_random_uuid())), 0, 'an unknown exercise has no candidates');

-- ---------------------------------------------------------------------------
-- Level 1-3 end to end: no load increase while a mild discomfort is open (7 days)
-- ---------------------------------------------------------------------------
create temp table res (k text primary key, v jsonb);
insert into res select 'open6', pg_temp.ap(pg_temp.hist(pg_temp.k('bench-press'), 4, 60, array[12, 12, 12], array[8, 8, 7], 1));
select is((select v -> 0 ->> 'kind' from res where k = 'open6'), 'load_kept', 'a level 3 open for 6 days keeps the load');
select is(pg_temp.sig(1, pg_temp.k('bench-press')), 'warm_up:30x10,working:60x8,working:60x8,working:60x8,drop:48x10',
  'no weight is written while the discomfort is open');
insert into res select 'open7', pg_temp.ap(pg_temp.hist(pg_temp.k('close-grip-bench-press'), 4, 60, array[12, 12, 12], array[8, 8, 7], 1));
select is((select v -> 0 ->> 'kind' from res where k = 'open7'), 'load_raise', 'a level 3 open for 7 days has expired and the raise happens');
select is(pg_temp.sig(1, pg_temp.k('close-grip-bench-press')), 'warm_up:30x10,working:62.5x8,working:62.5x8,working:62.5x8,drop:48x10',
  'the raise is written once the discomfort has expired');
select is(pg_temp.ap(pg_temp.hist(pg_temp.k('paused-bench-press'), 4, 60, array[12, 12, 12], array[8, 8, 7], 1)), '[]'::jsonb,
  'a level 4 open discomfort gives no progression change');

-- Outside a block (user 3 has no plan): a mild discomfort turns the raise into kept_discomfort.
reset role;
insert into public.exercise_discomforts (user_id, exercise_id, logged_at, level)
values (pg_temp.u(3), pg_temp.k('bench-press'), now() - interval '1 day', 2);
insert into public.workout_sessions (id, user_id, name, status, started_at)
values ('c2e40000-0000-4000-8000-000000000003', pg_temp.u(3), 'Current', 'in_progress', now());
insert into public.workout_sets (session_id, exercise_id, exercise_position, set_index, rep_min, rep_max)
values ('c2e40000-0000-4000-8000-000000000003', pg_temp.k('bench-press'), 0, 1, 8, 12);
insert into public.workout_sessions (id, user_id, name, status, started_at, ended_at)
values ('c2e40000-0000-4000-8000-000000000004', pg_temp.u(3), 'Hist', 'completed', now() - interval '3 days',
        now() - interval '3 days' + interval '1 hour');
insert into public.workout_sets (session_id, exercise_id, exercise_position, set_index, set_type, weight_kg, reps, rpe,
                                 completed_at, rep_min, rep_max)
select 'c2e40000-0000-4000-8000-000000000004', pg_temp.k('bench-press'), 0, i, 'working', 60, 12, 7,
       now() - interval '3 days', 8, 12
from generate_series(1, 3) i;
set local role authenticated;
select pg_temp.as_user(3);
select is(public.progression_suggestions('c2e40000-0000-4000-8000-000000000003') -> 0 ->> 'reason', 'kept_discomfort',
  'outside a block a level 2 gives a kept_discomfort suggestion instead of a raise');
select pg_temp.as_user(1);

-- ---------------------------------------------------------------------------
-- Decline and dismiss
-- ---------------------------------------------------------------------------
-- Dismiss the note of one exercise: only that exercise changes.
select lives_ok($$select public.dismiss_discomfort_note(pg_temp.x(2))$$, 'the note can be dismissed');
select is(pg_temp.note(pg_temp.x(2)), false, 'a dismissed note stays hidden');
select is(pg_temp.reason(pg_temp.x(2)), null, 'a dismissed note has no reason');
select is(pg_temp.note(pg_temp.x(3)), true, 'dismissing one exercise leaves the note of another');
select lives_ok($$select public.dismiss_discomfort_note(pg_temp.x(2))$$, 'dismissing twice is not an error');
select lives_ok($$select public.dismiss_discomfort_note(pg_temp.k('push-up'))$$, 'dismissing with nothing matching is not an error');
select is((select count(*)::int from public.exercise_discomforts where exercise_id = pg_temp.k('push-up') and note_dismissed_at is not null),
  0, 'a level 1-3 row is never stamped');
-- A later discomfort of the same exercise while the group is open stays dismissed (same group).
insert into public.exercise_discomforts (exercise_id, level) values (pg_temp.x(2), 5);
select is(pg_temp.note(pg_temp.x(2)), false, 'a new log in an open dismissed group does not bring the note back');
-- Resolving the group removes it; a later discomfort is a new group.
update public.exercise_discomforts set resolved_at = now() where exercise_id = pg_temp.x(2);
select is((select count(*)::int from public.coach_discomfort_followups() where exercise_id = pg_temp.x(2)), 0,
  'resolving the discomfort removes the follow-up');
insert into public.exercise_discomforts (exercise_id, level) values (pg_temp.x(2), 4);
select is((select note_dismissed_at from public.exercise_discomforts
           where exercise_id = pg_temp.x(2) and resolved_at is null), null, 'a new discomfort after resolving starts clean');

-- Decline: hides the offer for this discomfort, a new level 4-5 log offers again.
select lives_ok($$select public.decline_discomfort_swap(pg_temp.k('db-bench-press'))$$, 'the swap can be declined');
select is(pg_temp.offer(pg_temp.k('db-bench-press')), false, 'a declined offer is hidden');
select is((select count(*)::int from public.exercise_discomforts
           where exercise_id = pg_temp.k('db-bench-press') and user_id = pg_temp.u(1) and swap_declined_at is not null), 1,
  'the decline is stored on the discomfort row');
select lives_ok($$select public.decline_discomfort_swap(pg_temp.k('db-bench-press'))$$, 'declining twice is not an error');
insert into public.exercise_discomforts (exercise_id, level) values (pg_temp.k('db-bench-press'), 5);
select is(pg_temp.offer(pg_temp.k('db-bench-press')), true, 'a new level 4-5 log is offered again');

-- ---------------------------------------------------------------------------
-- Swap
-- ---------------------------------------------------------------------------
select pg_temp.hist(pg_temp.k('dumbbell-floor-press'), 4, 30, array[10, 10, 10]);
select pg_temp.hist(pg_temp.k('dumbbell-floor-press'), 3, 32, array[10, 10, 10]);
select pg_temp.hist(pg_temp.k('db-bench-press'), 5, 20, array[10, 10, 10]);
create temp table before_sets as select count(*)::int as total,
  count(*) filter (where exercise_id = pg_temp.k('db-bench-press'))::int as old_ex from public.workout_sets;
grant select on before_sets to authenticated;
select pg_temp.as_user(1);

select throws_ok($$select public.coach_swap_flagged_exercise(pg_temp.k('db-bench-press'), pg_temp.k('bench-press'))$$,
  '22023', 'replacement is not a candidate', 'a replacement outside the equipment is rejected');
select throws_ok($$select public.coach_swap_flagged_exercise(pg_temp.k('db-bench-press'), pg_temp.k('decline-bench-press'))$$,
  '22023', 'replacement is not a candidate', 'a replacement with its own discomfort is rejected');
select throws_ok($$select public.coach_swap_flagged_exercise(pg_temp.k('db-bench-press'), pg_temp.k('incline-db-press'))$$,
  '22023', 'replacement is not a candidate', 'a replacement already in the block is rejected');
select throws_ok($$select public.coach_swap_flagged_exercise(pg_temp.k('push-up'), pg_temp.k('wall-push-ups'))$$,
  'P0002', 'exercise is not in the block', 'an exercise outside the block cannot be swapped');
select set_config('request.jwt.claims', '', true);
select throws_ok($$select public.coach_swap_flagged_exercise(pg_temp.k('db-bench-press'), pg_temp.k('dumbbell-floor-press'))$$,
  '28000', 'not authenticated', 'the swap needs a signed-in user');
select pg_temp.as_user(1);

select is(public.coach_swap_flagged_exercise(pg_temp.k('db-bench-press'), pg_temp.k('dumbbell-floor-press')),
  '{"routines_changed": 2}'::jsonb, 'the swap changes every routine of the block that holds the exercise');
select is(pg_temp.sig(1, pg_temp.k('dumbbell-floor-press')), 'warm_up:-x10,working:32x8,working:32x8,working:32x8,drop:-x10',
  'working sets get the last working weight of the new exercise, the rest none');
select is(pg_temp.sig(2, pg_temp.k('dumbbell-floor-press')), 'warm_up:-x10,working:32x8,working:32x8,working:32x8,drop:-x10',
  'the second routine of the block gets the same');
select is(pg_temp.shape(1, pg_temp.k('dumbbell-floor-press')), '0|5|8-12|90|8', 'position, sets, range, rest and RPE are kept (R1)');
select is(pg_temp.shape(2, pg_temp.k('dumbbell-floor-press')), '1|5|8-12|90|8', 'the position is kept in the second routine');
select is((select count(*)::int from public.routine_exercises re join public.routines r on r.id = re.routine_id
           where r.folder_id = 'c2e20000-0000-4000-8000-000000000001' and re.exercise_id = pg_temp.k('db-bench-press')),
  0, 'the flagged exercise is gone from the block');
select is(pg_temp.sig(3, pg_temp.k('db-bench-press')), 'warm_up:10x10,working:20x8,working:20x8,working:20x8,drop:16x10',
  'a routine outside the block folder is untouched');
select is((select count(*)::int from public.coach_plan_sessions
           where plan_id = 'c2e30000-0000-4000-8000-000000000001' and exercises -> 0 ->> 'exercise_id' = pg_temp.k('dumbbell-floor-press')::text
             and exercises -> 0 ->> 'exercise_key' = 'dumbbell-floor-press' and exercises -> 0 ->> 'role' = 'main'),
  4, 'every plan week reads the new exercise and keeps the slot role');
select is((select exercises -> 1 ->> 'exercise_key' from public.coach_plan_sessions
           where plan_id = 'c2e30000-0000-4000-8000-000000000001' and week = 1), 'incline-db-press',
  'the other slots of the plan week are untouched');
select is((select count(*)::int from public.workout_sets), (select total from before_sets), 'logged sets are not added or removed');
select is((select count(*)::int from public.workout_sets where exercise_id = pg_temp.k('db-bench-press')),
  (select old_ex from before_sets), 'history keeps the old exercise');
select is(public.coach_swap_flagged_exercise(pg_temp.k('db-bench-press'), pg_temp.k('dumbbell-floor-press')),
  '{"routines_changed": 0}'::jsonb, 'a second call has nothing left to swap');
select is(pg_temp.offer(pg_temp.k('db-bench-press')), false, 'after the swap the exercise is no longer offered');
select is((select count(*)::int from public.coach_swap_candidates(pg_temp.k('db-bench-press'))), 0,
  'the swapped exercise has no candidates (it left the block)');
select is((select count(*)::int from public.coach_changes where plan_id = 'c2e30000-0000-4000-8000-000000000001'
             and kind not in ('load_kept', 'load_raise')), 0, 'the swap writes no coach_changes row');

-- No known weight for the new exercise: planned weights are empty.
select is(public.coach_swap_flagged_exercise(pg_temp.k('incline-db-press'), pg_temp.k('close-grip-db-bench-press')),
  '{"routines_changed": 1}'::jsonb, 'a second exercise can be swapped');
select is(pg_temp.sig(2, pg_temp.k('close-grip-db-bench-press')), 'warm_up:-x10,working:-x8,working:-x8,working:-x8,drop:-x10',
  'without a known weight the planned weights are empty');

-- ---------------------------------------------------------------------------
-- Isolation: user 2
-- ---------------------------------------------------------------------------
select pg_temp.as_user(2);
select is((select count(*)::int from public.coach_discomfort_followups()), 1, 'user 2 sees only their own follow-up');
select is(pg_temp.offer(pg_temp.k('db-bench-press')), false, 'a proposed (not accepted) plan offers no swap');
select is((select count(*)::int from public.coach_swap_candidates(pg_temp.k('db-bench-press'))), 0,
  'no candidates without an accepted plan');
select throws_ok($$select public.coach_swap_flagged_exercise(pg_temp.k('db-bench-press'), pg_temp.k('dumbbell-floor-press'))$$,
  'P0002', 'no accepted block', 'a swap needs an accepted block');
select lives_ok($$select public.decline_discomfort_swap(pg_temp.k('close-grip-db-bench-press'))$$, 'user 2 declining is harmless');
-- RLS hides user 1 rows from user 2: the update matches nothing.
update public.exercise_discomforts set swap_declined_at = now(), note_dismissed_at = now() where user_id = pg_temp.u(1);
select pg_temp.as_user(1);
select is((select count(*)::int from public.exercise_discomforts
           where swap_declined_at is not null and exercise_id <> pg_temp.k('db-bench-press')), 0,
  'user 1 rows are untouched by user 2 updates and calls');
select throws_ok($$update public.exercise_discomforts set level = 1$$, '42501', null, 'level cannot be updated');

select * from finish();
rollback;
