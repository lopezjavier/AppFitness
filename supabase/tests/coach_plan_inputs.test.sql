-- pgTAP: coach_plan_inputs, the list of data a plan or a block reads (T-175, US-78; rules in planning/04 section 7.7).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(65);

-- ---------------------------------------------------------------------------
-- Fixture helpers (run as the migration owner; today is the UTC date, inside the RPC window)
-- ---------------------------------------------------------------------------
create function pg_temp.d0() returns date language sql as $$ select (now() at time zone 'utc')::date $$;

-- The user n as a uuid: c1000000-0000-4000-8000-0000000000NN.
create function pg_temp.u(n int) returns uuid language sql as $$
  select ('c1000000-0000-4000-8000-0000000000' || lpad(n::text, 2, '0'))::uuid $$;

-- Male, 26 years old, 178 cm, sedentary, with a weigh-in kg_days_ago days back (null: no weigh-in).
create function pg_temp.profile(n int, kg_days_ago int default 0) returns void language plpgsql as $$
begin
  update public.profiles
  set sex = 'male', birth_date = pg_temp.d0() - interval '26 years 100 days', height_cm = 178,
      activity_level = 'sedentary', onboarding_completed = true
  where id = pg_temp.u(n);
  if kg_days_ago is not null then
    insert into public.body_measurements (user_id, weight_kg, source, measured_at)
    values (pg_temp.u(n), 74.2, 'manual', now() - make_interval(days => kg_days_ago));
  end if;
end $$;

create function pg_temp.prefs(n int, i public.training_intention, pl public.training_place,
                              eq public.equipment[], d smallint, m smallint) returns void language sql as $$
  insert into public.training_preferences (user_id, intention, place, equipment, days_per_week, session_minutes)
  values (pg_temp.u(n), i, pl, eq, d, m) $$;

create function pg_temp.weigh(n int, weeks_ago int, kg numeric) returns void language sql as $$
  insert into public.body_measurements (user_id, weight_kg, source, measured_at)
  select pg_temp.u(n), kg, 'manual',
         ((pg_temp.d0() - (extract(isodow from pg_temp.d0())::int - 1) - 7 * weeks_ago)::timestamp + interval '30 minutes')
           at time zone 'UTC' $$;

create function pg_temp.diary(n int, days int, kcal numeric) returns void language sql as $$
  insert into public.food_log_entries (user_id, log_date, meal_slot_id, food_id, amount, food_name, kcal, protein_g, carbs_g, fat_g)
  select pg_temp.u(n), pg_temp.d0() - i,
         (select id from public.meal_slots where user_id = pg_temp.u(n) and code = 'breakfast'),
         '0ff00000-0000-4000-8000-0000000000c1', 100, 'Alimento', kcal, 10, 10, 10
  from generate_series(0, days - 1) i $$;

create function pg_temp.plan(n int, st text) returns void language sql as $$
  insert into public.coach_plans (user_id, block_start, intention, nutrition, status)
  values (pg_temp.u(n), pg_temp.d0(), 'muscle', '{}', st) $$;

-- The list as user n.
create function pg_temp.inp(n int, k text) returns jsonb language plpgsql as $$
begin
  perform set_config('request.jwt.claims', json_build_object('sub', pg_temp.u(n), 'role', 'authenticated')::text, true);
  return public.coach_plan_inputs(k, pg_temp.d0());
end $$;

create function pg_temp.cfm(n int) returns jsonb language plpgsql as $$
begin
  perform set_config('request.jwt.claims', json_build_object('sub', pg_temp.u(n), 'role', 'authenticated')::text, true);
  return public.confirm_derived_values(pg_temp.d0());
end $$;

-- "status/blocking/fix_target" of one entry ('-' when there is no fix target).
create function pg_temp.st(j jsonb, k text) returns text language sql as $$
  select x->>'status' || '/' || (x->>'blocking') || '/' || coalesce(x->>'fix_target', '-')
  from jsonb_array_elements(j->'inputs') x where x->>'key' = k $$;

create function pg_temp.ent(j jsonb, k text) returns jsonb language sql as $$
  select x from jsonb_array_elements(j->'inputs') x where x->>'key' = k $$;

create function pg_temp.keys(j jsonb) returns text[] language sql as $$
  select array_agg(x->>'key' order by o) from jsonb_array_elements(j->'inputs') with ordinality t(x, o) $$;

create function pg_temp.col(j jsonb, f text) returns text[] language sql as $$
  select array_agg(x->>f order by o) from jsonb_array_elements(j->'inputs') with ordinality t(x, o) $$;

grant execute on all functions in schema pg_temp to authenticated;

-- ---------------------------------------------------------------------------
-- Fixture data
-- ---------------------------------------------------------------------------
insert into auth.users (id, email)
select pg_temp.u(n), 'inputs' || n || '@example.com'
from generate_series(1, 20) n;

insert into public.foods (id, source, name, kcal_100) values ('0ff00000-0000-4000-8000-0000000000c1', 'off', 'Alimento c', 100);

-- 01 complete, "Perder grasa". 02 nothing at all.
select pg_temp.profile(1);
select pg_temp.prefs(1, 'fat_loss', 'gym', array['barbell']::public.equipment[], 4::smallint, 60::smallint);
-- 03 no sex. 04 no birth date. 06 no height. 07 no weigh-in. 08 weigh-in 15 days old. 09 weigh-in 14 days old.
select pg_temp.profile(3), pg_temp.profile(4), pg_temp.profile(6), pg_temp.profile(7, null), pg_temp.profile(8, 15),
       pg_temp.profile(9, 14);
select pg_temp.prefs(n, 'muscle', 'gym', array[]::public.equipment[], null, null) from generate_series(3, 9) n;
update public.profiles set sex = null where id = pg_temp.u(3);
update public.profiles set birth_date = null where id = pg_temp.u(4);
update public.profiles set height_cm = null where id = pg_temp.u(6);
-- 05 adaptive path (reliable diary, 4 weeks of weights): no birth date and no height are not blocking.
select pg_temp.profile(5, null);
select pg_temp.weigh(5, 3, 80), pg_temp.weigh(5, 2, 79), pg_temp.weigh(5, 1, 79), pg_temp.weigh(5, 0, 78);
select pg_temp.diary(5, 20, 2000);
update public.profiles set birth_date = null, height_cm = null where id = pg_temp.u(5);
-- 10 estimates to confirm. 11 block with nothing set. 12 complete block.
select pg_temp.profile(10);
select pg_temp.prefs(10, 'strength', 'gym', array[]::public.equipment[], null, null);
select pg_temp.prefs(11, null, null, array[]::public.equipment[], null, null);
select pg_temp.prefs(12, 'muscle', 'home', array['dumbbell', 'barbell']::public.equipment[], 3::smallint, 45::smallint);
-- 13 proposed, 14 accepted, 15 both, 16 superseded and discarded.
select pg_temp.plan(13, 'proposed'), pg_temp.plan(14, 'accepted'), pg_temp.plan(15, 'accepted'),
       pg_temp.plan(15, 'proposed'), pg_temp.plan(16, 'superseded'), pg_temp.plan(16, 'discarded');

-- ---------------------------------------------------------------------------
-- Security and guards
-- ---------------------------------------------------------------------------
select ok(not has_function_privilege('anon', 'public.coach_plan_inputs(text, date)', 'execute'),
  'anon cannot execute coach_plan_inputs');
select ok(has_function_privilege('authenticated', 'public.coach_plan_inputs(text, date)', 'execute'),
  'authenticated can execute coach_plan_inputs');

set local role authenticated;
select set_config('request.jwt.claims', '{"role":"authenticated"}', true);
select throws_ok($$select public.coach_plan_inputs('plan', current_date)$$, '28000', null,
  'coach_plan_inputs requires a user');
select set_config('request.jwt.claims', json_build_object('sub', pg_temp.u(1), 'role', 'authenticated')::text, true);
select throws_ok($$select public.coach_plan_inputs('weekly', (now() at time zone 'utc')::date)$$, '22023', null,
  'an unknown kind is refused');
select throws_ok($$select public.coach_plan_inputs(null, (now() at time zone 'utc')::date)$$, '22023', null,
  'a null kind is refused');
select throws_ok(format($$select public.coach_plan_inputs('plan', %L)$$, (now() at time zone 'utc')::date + 2),
  '22023', null, 'a date two days ahead of UTC today is refused');
select throws_ok(format($$select public.coach_plan_inputs('block', %L)$$, (now() at time zone 'utc')::date - 2),
  '22023', null, 'a date two days before UTC today is refused');

-- ---------------------------------------------------------------------------
-- Plan: complete user
-- ---------------------------------------------------------------------------
select is(pg_temp.keys(pg_temp.inp(1, 'plan')),
  array['intention', 'sex', 'age', 'height', 'weight', 'activity_level', 'total_expenditure', 'weight_trend', 'diary'],
  'plan lists its nine inputs in display order');
select is(pg_temp.inp(1, 'plan')->>'kind', 'plan', 'the kind is echoed');
select is(pg_temp.inp(1, 'plan')->>'as_of', pg_temp.d0()::text, 'as_of is p_today');
select ok(not (pg_temp.inp(1, 'plan') ? 'current_plan'), 'current_plan is absent when the user has no plan');
select is(pg_temp.col(pg_temp.inp(1, 'plan'), 'status'),
  array['ok', 'ok', 'ok', 'ok', 'ok', 'provisional', 'provisional', 'provisional', 'provisional'],
  'a complete profile is ok, unconfirmed estimates are provisional');
select is(pg_temp.col(pg_temp.inp(1, 'plan'), 'source'),
  array['my_training', 'profile', 'profile', 'profile', 'weigh_in', 'coach_estimate', 'coach_estimate',
        'coach_estimate', 'coach_estimate'],
  'each input names its source');
select is(array_position(pg_temp.col(pg_temp.inp(1, 'plan'), 'blocking'), 'true'), null, 'nothing blocks a complete profile');
select is(pg_temp.ent(pg_temp.inp(1, 'plan'), 'intention')->>'value', 'fat_loss', 'the intention value is returned');
select is(pg_temp.ent(pg_temp.inp(1, 'plan'), 'weight')->>'date', pg_temp.d0()::text, 'the weigh-in date is returned');
select is((pg_temp.ent(pg_temp.inp(1, 'plan'), 'weight')->>'value')::numeric, 74.2, 'the weigh-in value is returned');
select is(pg_temp.st(pg_temp.inp(1, 'plan'), 'activity_level'), 'provisional/false/activity_sheet',
  'the activity level points to its sheet');
select is(pg_temp.st(pg_temp.inp(1, 'plan'), 'weight_trend'), 'provisional/false/-', 'derived inputs have no fix target');

-- ---------------------------------------------------------------------------
-- Plan: missing data
-- ---------------------------------------------------------------------------
select is(pg_temp.st(pg_temp.inp(2, 'plan'), 'intention'), 'missing/true/my_training', 'no intention is missing and blocking');
select is(pg_temp.st(pg_temp.inp(2, 'plan'), 'sex'), 'missing/true/personal_data', 'no sex is missing and blocking');
select is(pg_temp.st(pg_temp.inp(2, 'plan'), 'age'), 'missing/true/personal_data',
  'no birth date blocks when expenditure has no value');
select is(pg_temp.st(pg_temp.inp(2, 'plan'), 'height'), 'missing/true/personal_data',
  'no height blocks when expenditure has no value');
select is(pg_temp.st(pg_temp.inp(2, 'plan'), 'weight'), 'missing/true/weight', 'no weigh-in is missing and blocking');
select ok(not (pg_temp.ent(pg_temp.inp(2, 'plan'), 'weight') ? 'date'), 'no date key without a weigh-in');
select is(pg_temp.st(pg_temp.inp(2, 'plan'), 'total_expenditure'), 'missing/false/-',
  'a missing expenditure never blocks by itself');
select is(pg_temp.st(pg_temp.inp(3, 'plan'), 'sex'), 'missing/true/personal_data', 'only sex missing: sex blocks');
select is(pg_temp.st(pg_temp.inp(3, 'plan'), 'age'), 'ok/false/personal_data', 'only sex missing: age is ok');
select is(pg_temp.st(pg_temp.inp(4, 'plan'), 'age'), 'missing/true/personal_data', 'only birth date missing: age blocks');
select is(pg_temp.st(pg_temp.inp(6, 'plan'), 'height'), 'missing/true/personal_data', 'only height missing: height blocks');
select is(pg_temp.st(pg_temp.inp(5, 'plan'), 'age'), 'missing/false/personal_data',
  'age does not block on the adaptive path');
select is(pg_temp.st(pg_temp.inp(5, 'plan'), 'height'), 'missing/false/personal_data',
  'height does not block on the adaptive path');
select is(pg_temp.st(pg_temp.inp(7, 'plan'), 'weight'), 'missing/true/weight', 'a profile without weigh-in blocks on weight');

-- ---------------------------------------------------------------------------
-- Plan: weigh-in age
-- ---------------------------------------------------------------------------
select is(pg_temp.st(pg_temp.inp(8, 'plan'), 'weight'), 'stale/false/weight', 'a 15-day-old weigh-in is stale and not blocking');
select is(pg_temp.ent(pg_temp.inp(8, 'plan'), 'weight')->>'date', (pg_temp.d0() - 15)::text, 'the stale weigh-in keeps its date');
select is(pg_temp.st(pg_temp.inp(9, 'plan'), 'weight'), 'ok/false/weight', 'a 14-day-old weigh-in is still ok');

-- ---------------------------------------------------------------------------
-- Provisional becomes ok after confirmation
-- ---------------------------------------------------------------------------
select is(pg_temp.st(pg_temp.inp(10, 'plan'), 'activity_level'), 'provisional/false/activity_sheet', 'activity level starts provisional');
select is(pg_temp.st(pg_temp.inp(10, 'plan'), 'total_expenditure'), 'provisional/false/-', 'expenditure starts provisional');
select is(pg_temp.st(pg_temp.inp(10, 'block'), 'experience'), 'provisional/false/-', 'experience starts provisional');
select ok(pg_temp.cfm(10) is not null, 'the user confirms the derived values');
select is(pg_temp.st(pg_temp.inp(10, 'plan'), 'activity_level'), 'ok/false/activity_sheet', 'activity level is ok once confirmed');
select is(pg_temp.st(pg_temp.inp(10, 'plan'), 'total_expenditure'), 'ok/false/-', 'expenditure is ok once confirmed');
select is(pg_temp.st(pg_temp.inp(10, 'block'), 'experience'), 'ok/false/-', 'experience is ok once confirmed');

-- ---------------------------------------------------------------------------
-- Block
-- ---------------------------------------------------------------------------
select is(pg_temp.keys(pg_temp.inp(11, 'block')),
  array['intention', 'place', 'equipment', 'cardio', 'days_per_week', 'session_minutes', 'experience'],
  'block lists its seven inputs, different from plan');
select is(pg_temp.st(pg_temp.inp(11, 'block'), 'intention'), 'missing/true/my_training', 'block: no intention blocks');
select is(pg_temp.st(pg_temp.inp(11, 'block'), 'place'), 'missing/true/my_training', 'block: no place blocks');
select is(pg_temp.st(pg_temp.inp(11, 'block'), 'equipment'), 'unset_optional/false/my_training', 'block: empty equipment is optional');
select is(pg_temp.st(pg_temp.inp(11, 'block'), 'days_per_week'), 'unset_optional/false/my_training', 'block: no days is optional');
select is(pg_temp.st(pg_temp.inp(11, 'block'), 'session_minutes'), 'unset_optional/false/my_training',
  'block: no minutes is optional');
select is(pg_temp.st(pg_temp.inp(11, 'block'), 'experience'), 'provisional/false/-', 'block: experience has no fix target');
select is(pg_temp.col(pg_temp.inp(12, 'block'), 'status'),
  array['ok', 'ok', 'ok', 'ok', 'ok', 'ok', 'provisional'], 'a complete block is ok');
select is(pg_temp.ent(pg_temp.inp(12, 'block'), 'equipment')->'value', '["dumbbell","barbell"]'::jsonb,
  'the equipment list is returned');
select is((pg_temp.ent(pg_temp.inp(12, 'block'), 'days_per_week')->>'value')::int, 3, 'the days value is returned');
select is((pg_temp.ent(pg_temp.inp(12, 'block'), 'session_minutes')->>'value')::int, 45, 'the minutes value is returned');
select is(array_position(pg_temp.col(pg_temp.inp(12, 'block'), 'blocking'), 'true'), null, 'nothing blocks a complete block');
select is(pg_temp.ent(pg_temp.inp(12, 'block'), 'place')->>'value', 'home', 'the place value is returned');

-- ---------------------------------------------------------------------------
-- Current plan
-- ---------------------------------------------------------------------------
select is(pg_temp.inp(13, 'plan')->>'current_plan', 'proposed', 'a proposed plan is reported');
select is(pg_temp.inp(14, 'plan')->>'current_plan', 'accepted', 'an accepted plan is reported');
select is(pg_temp.inp(15, 'plan')->>'current_plan', 'accepted', 'accepted wins over proposed');
select ok(not (pg_temp.inp(16, 'plan') ? 'current_plan'), 'superseded and discarded plans are ignored');
select is(pg_temp.inp(14, 'block')->>'current_plan', 'accepted', 'the block list reports the current plan too');
select ok(not (pg_temp.inp(1, 'plan') ? 'current_plan'), 'another user''s plans never appear');

-- ---------------------------------------------------------------------------
-- Cross-user isolation and no writes
-- ---------------------------------------------------------------------------
select is(pg_temp.st(pg_temp.inp(2, 'plan'), 'weight'), 'missing/true/weight', 'another user''s weigh-ins never appear');
select is(pg_temp.ent(pg_temp.inp(2, 'plan'), 'intention')->'value', 'null'::jsonb, 'another user''s intention never appears');

select set_config('request.jwt.claims', json_build_object('sub', pg_temp.u(15), 'role', 'authenticated')::text, true);
create temp table before_counts as
select (select count(*) from public.coach_confirmed_values) c,
       (select count(*) from public.coach_plans) p,
       (select count(*) from public.body_measurements) b,
       (select count(*) from public.training_preferences) t;
grant select on before_counts to authenticated;
select pg_temp.inp(15, 'plan'), pg_temp.inp(15, 'block');
select is(
  (select (select count(*) from public.coach_confirmed_values) || '/' || (select count(*) from public.coach_plans)
          || '/' || (select count(*) from public.body_measurements) || '/' || (select count(*) from public.training_preferences)),
  (select c || '/' || p || '/' || b || '/' || t from before_counts),
  'the call writes nothing');

select * from finish();
rollback;
