-- pgTAP: cardio machines as their own equipment values (T-184, ADR-85, US-80).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(23);

create function pg_temp.d0() returns date language sql as $$ select (now() at time zone 'utc')::date $$;

-- The user n as a uuid: c2000000-0000-4000-8000-0000000000NN.
create function pg_temp.u(n int) returns uuid language sql as $$
  select ('c2000000-0000-4000-8000-0000000000' || lpad(n::text, 2, '0'))::uuid $$;

create function pg_temp.prefs(n int, eq public.equipment[]) returns void language sql as $$
  insert into public.training_preferences (user_id, intention, place, equipment, days_per_week, session_minutes)
  values (pg_temp.u(n), 'muscle', 'gym', eq, 3, 45) $$;

-- The block list entry k as user n.
create function pg_temp.ent(n int, k text) returns jsonb language plpgsql as $$
declare j jsonb;
begin
  perform set_config('request.jwt.claims', json_build_object('sub', pg_temp.u(n), 'role', 'authenticated')::text, true);
  j := public.coach_plan_inputs('block', pg_temp.d0());
  return (select x from jsonb_array_elements(j->'inputs') x where x->>'key' = k);
end $$;

grant execute on all functions in schema pg_temp to authenticated;

insert into auth.users (id, email)
select pg_temp.u(n), 'cardio' || n || '@example.com' from generate_series(1, 5) n;

-- 1 only machine. 2 bike + treadmill + dumbbell. 3 nothing. 4 only cardio. 5 a different cardio selection.
select pg_temp.prefs(1, array['machine']::public.equipment[]);
select pg_temp.prefs(2, array['cardio_bike', 'dumbbell', 'treadmill']::public.equipment[]);
select pg_temp.prefs(3, array[]::public.equipment[]);
select pg_temp.prefs(4, array['rower']::public.equipment[]);
select pg_temp.prefs(5, array['elliptical', 'cardio_other']::public.equipment[]);

-- ---------------------------------------------------------------------------
-- Catalogue
-- ---------------------------------------------------------------------------
select is(
  (select array_agg(e::text order by e::text) from unnest(enum_range(null::public.equipment)) e
   where e::text in ('cardio_bike', 'treadmill', 'elliptical', 'rower', 'cardio_other')),
  array['cardio_bike', 'cardio_other', 'elliptical', 'rower', 'treadmill'],
  'the five cardio equipment values exist');
select is((select array_agg(e::text) from unnest(enum_range(null::public.equipment)) e), array[
  'barbell', 'dumbbell', 'machine', 'cable', 'bodyweight', 'kettlebell', 'band', 'other', 'band_long',
  'cardio_bike', 'treadmill', 'elliptical', 'rower', 'cardio_other'],
  'the cardio values come after the existing ones');

select is((select equipment::text from public.exercises where key = 'stationary-bike' and not is_custom),
  'cardio_bike', 'stationary-bike is cardio_bike');
select is((select equipment::text from public.exercises where key = 'air-bike' and not is_custom),
  'cardio_bike', 'air-bike is cardio_bike');
select is((select equipment::text from public.exercises where key = 'incline-treadmill-walk' and not is_custom),
  'treadmill', 'incline-treadmill-walk is treadmill');
select is((select equipment::text from public.exercises where key = 'treadmill-running' and not is_custom),
  'treadmill', 'treadmill-running is treadmill');
select is((select equipment::text from public.exercises where key = 'elliptical-trainer' and not is_custom),
  'elliptical', 'elliptical-trainer is elliptical');
select is((select equipment::text from public.exercises where key = 'rowing-machine' and not is_custom),
  'rower', 'rowing-machine is rower');
select is((select equipment::text from public.exercises where key = 'ski-erg' and not is_custom),
  'cardio_other', 'ski-erg is cardio_other');
select is((select equipment::text from public.exercises where key = 'stair-climber' and not is_custom),
  'cardio_other', 'stair-climber is cardio_other');
select is((select array_agg(equipment::text order by key) from public.exercises
           where key in ('running', 'walking') and not is_custom),
  array['bodyweight', 'bodyweight'], 'walking and running stay bodyweight');
select is((select count(*)::int from public.exercises
           where equipment in ('cardio_bike', 'treadmill', 'elliptical', 'rower', 'cardio_other')),
  8, 'only the eight cardio machines carry a cardio value');
select is((select count(*)::int from public.exercises where equipment = 'machine' and not is_custom),
  61, 'the strength machines stay machine (69 before the retag, minus 8)');

-- ---------------------------------------------------------------------------
-- coach_plan_inputs('block')
-- ---------------------------------------------------------------------------
set local role authenticated;

select is(pg_temp.ent(1, 'equipment')->'value', '["machine"]'::jsonb, 'only machine: equipment keeps machine');
select is(pg_temp.ent(1, 'cardio')->'value', '[]'::jsonb, 'only machine: cardio is empty');
select is(pg_temp.ent(2, 'equipment')->'value', '["dumbbell"]'::jsonb, 'bike + treadmill + dumbbell: equipment has the lifting value only');
select is(pg_temp.ent(2, 'cardio')->'value', '["cardio_bike","treadmill"]'::jsonb, 'bike + treadmill + dumbbell: cardio has the cardio values in order');
select is(pg_temp.ent(3, 'equipment')->>'status', 'unset_optional', 'no equipment: equipment is unset_optional');
select is(pg_temp.ent(4, 'equipment')->>'status', 'unset_optional', 'cardio only: the lifting list is judged empty');
select is(
  (select x->>'status' || '/' || (x->>'blocking') || '/' || (x->>'source') || '/' || (x->>'fix_target')
   from (select pg_temp.ent(3, 'cardio') x) t),
  'ok/false/my_training/my_training', 'an empty cardio list is ok, not blocking, points to my_training');
select is(pg_temp.ent(4, 'cardio')->>'status', 'ok', 'a cardio selection is ok');

-- Isolation: user 5's cardio selection never shows up for user 2.
select is(pg_temp.ent(2, 'cardio')->'value', '["cardio_bike","treadmill"]'::jsonb,
  'another user''s cardio selection never appears');

-- Saved lists are untouched by the retag (a stored {machine} stays {machine}).
reset role;
select is((select equipment::text[] from public.training_preferences where user_id = pg_temp.u(1)),
  array['machine'], 'a saved machine list gains nothing');

select * from finish();
rollback;
