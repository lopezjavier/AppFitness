-- pgTAP: lower-body muscle-group filing of the catalogue and the adductors group (T-211, 07 section 3 "Taxonomy").
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(33);

insert into auth.users (id, email) values ('11111111-1111-4111-8111-111111111111', 'ana@example.com');

-- A custom quads exercise must stay as the user filed it.
insert into public.exercises (id, name, muscle_group, equipment, metric_type, is_custom, created_by) values
  ('a1100000-0000-4000-8000-000000000001', 'Mi aductora', 'quads', 'machine', 'weight_reps', true,
   '11111111-1111-4111-8111-111111111111');

-- A user routine that references a re-filed exercise keeps its row and still resolves the exercise.
insert into public.routines (id, user_id, name) values
  ('a1100000-0000-4000-8000-0000000000a1', '11111111-1111-4111-8111-111111111111', 'Pierna');
insert into public.routine_exercises (routine_id, exercise_id, position, target_sets, target_reps)
select 'a1100000-0000-4000-8000-0000000000a1', id, 0, 3, 8 from public.exercises where key = 'squat';

create function pg_temp.grp(p_key text) returns text language sql as
$$ select muscle_group::text from public.exercises where key = p_key and not is_custom $$;
create function pg_temp.sec(p_key text) returns text language sql as
$$ select secondary_muscles::text from public.exercises where key = p_key and not is_custom $$;

-- Schema.
select ok('adductors' = any (enum_range(null::public.muscle_group)::text[]), 'muscle_group has adductors');

-- Knee-dominant compounds: quads primary, glutes secondary (04 section 4: squat adds glutes at 0.5).
select is(pg_temp.grp('squat'), 'quads', 'squat is quads');
select is(pg_temp.sec('squat'), '{glutes}', 'squat lists glutes as secondary');
select is(pg_temp.grp('leg-press'), 'quads', 'leg-press is quads');
select is(pg_temp.sec('leg-press'), '{glutes}', 'leg-press lists glutes as secondary');
select is(pg_temp.grp('walking-lunge'), 'quads', 'walking-lunge is quads');
select is(pg_temp.sec('walking-lunge'), '{glutes}', 'walking-lunge lists glutes as secondary');
select is(pg_temp.grp('step-ups'), 'quads', 'step-ups is quads');
select is(pg_temp.grp('wall-sit'), 'quads', 'wall-sit is quads');
select is(pg_temp.sec('kettlebell-squat'), '{glutes}', 'a squat that was already quads gets glutes once');
select is((select count(*)::int from public.exercises
           where not is_custom and movement_pattern in ('squat', 'lunge') and muscle_group = 'quads'
             and not ('glutes' = any (secondary_muscles))), 0,
  'every quads squat or lunge lists glutes as secondary');

-- Hip extension is untouched: hinges stay glutes with hamstrings secondary.
select is(pg_temp.grp('hip-thrust'), 'glutes', 'hip-thrust stays glutes');
select ok((select 'hamstrings' = any (secondary_muscles) from public.exercises where key = 'deadlift'),
  'a bent-knee hinge still lists hamstrings as secondary');

-- Hip abduction: glutes.
select is(pg_temp.grp('clamshells'), 'glutes', 'clamshells is glutes');
select is(pg_temp.grp('fire-hydrant'), 'glutes', 'fire-hydrant is glutes');
select is(pg_temp.grp('side-lying-hip-abduction'), 'glutes', 'side-lying-hip-abduction is glutes');

-- Hip adduction: adductors.
select is(pg_temp.grp('hip-adduction'), 'adductors', 'hip-adduction is adductors');
select is(pg_temp.grp('side-lying-hip-adduction'), 'adductors', 'side-lying-hip-adduction is adductors');
select is(pg_temp.grp('banded-adductor-stretch'), 'adductors', 'an adductor stretch is adductors');

-- Keys filed by the joint action, whatever the category (07 section 3 "Keys, not names").
select is(pg_temp.grp('happy-baby'), 'adductors', 'happy-baby is adductors');
select is(pg_temp.grp('garland-pose'), 'adductors', 'garland-pose is adductors');
select is(pg_temp.grp('seated-straddle-stretch'), 'adductors', 'seated-straddle-stretch is adductors');
select is(pg_temp.grp('lateral-leg-swing'), 'adductors', 'lateral-leg-swing is adductors');
select is((select count(*)::int from public.exercises where not is_custom and muscle_group = 'adductors'), 11,
  'the adductors group holds the 11 hip-adduction keys');
select is(pg_temp.grp('downward-dog-knee-tuck') || pg_temp.sec('downward-dog-knee-tuck') ~ 'quads', false,
  'a downward-dog knee drive is core without quads');
select is(pg_temp.grp('kettlebell-sumo-high-pull') || ':' || pg_temp.sec('kettlebell-sumo-high-pull') ~ '^glutes:.*hamstrings',
  true, 'kettlebell-sumo-high-pull is glutes with hamstrings secondary');
select is(pg_temp.grp('wide-legged-forward-fold'), 'hamstrings', 'wide-legged-forward-fold is hamstrings');

-- Hip flexion: core, never quads.
select is(pg_temp.grp('sit-ups'), 'core', 'sit-ups is core');
select is(pg_temp.grp('hanging-leg-raise'), 'core', 'hanging-leg-raise is core');
select is(pg_temp.grp('mountain-climbers'), 'core', 'mountain-climbers is core');
select is((select count(*)::int from public.exercises
           where not is_custom and 'quads' = any (secondary_muscles) and muscle_group = 'core'), 0,
  'no core row lists quads as secondary');

-- Users' data is untouched.
select is((select muscle_group::text from public.exercises where id = 'a1100000-0000-4000-8000-000000000001'),
  'quads', 'a custom exercise keeps the group its owner chose');
select is((select count(*)::int from public.routine_exercises re join public.exercises e on e.id = re.exercise_id
           where re.routine_id = 'a1100000-0000-4000-8000-0000000000a1' and e.key = 'squat'), 1,
  'a routine referencing a re-filed exercise keeps its row');

select * from finish();
rollback;
