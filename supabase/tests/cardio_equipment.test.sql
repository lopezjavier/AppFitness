-- pgTAP: one "Máquina de cardio" equipment value (T-184, ADR-85, US-80; collapsed by T-201, ADR-92).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(18);

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

-- 1 only machine. 2 cardio machine + dumbbell. 3 nothing. 4 only cardio. 5 a different selection.
select pg_temp.prefs(1, array['machine']::public.equipment[]);
select pg_temp.prefs(2, array['cardio_machine', 'dumbbell']::public.equipment[]);
select pg_temp.prefs(3, array[]::public.equipment[]);
select pg_temp.prefs(4, array['cardio_machine']::public.equipment[]);
select pg_temp.prefs(5, array['cardio_machine', 'bodyweight']::public.equipment[]);

-- ---------------------------------------------------------------------------
-- Catalogue
-- ---------------------------------------------------------------------------
select is((select array_agg(e::text) from unnest(enum_range(null::public.equipment)) e), array[
  'barbell', 'dumbbell', 'machine', 'cable', 'bodyweight', 'kettlebell', 'band', 'other', 'band_long',
  'cardio_bike', 'treadmill', 'elliptical', 'rower', 'cardio_other', 'ez_bar', 'cardio_machine'],
  'the five old cardio values stay in the type, unused; ez_bar and cardio_machine come last');

select is((select count(*)::int from public.exercises
           where not is_custom and equipment = 'cardio_machine'
             and key in ('stationary-bike', 'air-bike', 'incline-treadmill-walk', 'treadmill-running',
                         'elliptical-trainer', 'rowing-machine', 'ski-erg', 'stair-climber')),
  8, 'the eight cardio machines are cardio_machine');
select is((select array_agg(equipment::text order by key) from public.exercises
           where key in ('running', 'walking') and not is_custom),
  array['bodyweight', 'bodyweight'], 'walking and running stay bodyweight');
select is((select count(*)::int from public.exercises
           where equipment in ('cardio_bike', 'treadmill', 'elliptical', 'rower', 'cardio_other')),
  0, 'no exercise holds an old cardio value');
select is((select count(*)::int from public.exercises where equipment = 'cardio_machine'),
  8, 'only the eight cardio machines carry cardio_machine');
select is((select count(*)::int from public.exercises where equipment = 'machine' and not is_custom),
  59, 'the strength machines stay machine (61 minus the two lat-pulldown variants moved to cable)');

-- ---------------------------------------------------------------------------
-- coach_plan_inputs('block')
-- ---------------------------------------------------------------------------
set local role authenticated;

select is(pg_temp.ent(1, 'equipment')->'value', '["machine"]'::jsonb, 'only machine: equipment keeps machine');
select is(pg_temp.ent(1, 'cardio')->'value', '[]'::jsonb, 'only machine: cardio is empty');
select is(pg_temp.ent(2, 'equipment')->'value', '["dumbbell"]'::jsonb, 'cardio machine + dumbbell: equipment has the lifting value only');
select is(pg_temp.ent(2, 'cardio')->'value', '["cardio_machine"]'::jsonb, 'cardio machine + dumbbell: cardio has cardio_machine');
select is(pg_temp.ent(3, 'equipment')->>'status', 'unset_optional', 'no equipment: equipment is unset_optional');
select is(pg_temp.ent(4, 'equipment')->>'status', 'unset_optional', 'cardio only: the lifting list is judged empty');
select is(
  (select x->>'status' || '/' || (x->>'blocking') || '/' || (x->>'source') || '/' || (x->>'fix_target')
   from (select pg_temp.ent(3, 'cardio') x) t),
  'ok/false/my_training/my_training', 'an empty cardio list is ok, not blocking, points to my_training');
select is(pg_temp.ent(4, 'cardio')->>'status', 'ok', 'a cardio selection is ok');

-- Isolation: user 5's list never shows up for user 2.
select is(pg_temp.ent(2, 'equipment')->'value', '["dumbbell"]'::jsonb,
  'another user''s selection never appears');

-- Saved lists are untouched by the catalogue retags (a stored {machine} stays {machine}).
reset role;
select is((select equipment::text[] from public.training_preferences where user_id = pg_temp.u(1)),
  array['machine'], 'a saved machine list gains nothing');

-- No new write may hold the five old values.
select throws_ok(
  $$update public.training_preferences set equipment = array['rower']::public.equipment[] where user_id = pg_temp.u(1)$$,
  '23514', null, 'a write with an old cardio value is refused');
select lives_ok(
  $$update public.training_preferences set equipment = array['cardio_machine']::public.equipment[] where user_id = pg_temp.u(1)$$,
  'a write with cardio_machine is accepted');

select * from finish();
rollback;
