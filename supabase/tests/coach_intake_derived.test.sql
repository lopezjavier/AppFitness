-- pgTAP: coach_compute with the user's intake (T-185; US-68 experience, US-81 priors; planning/04 section 7.5).
-- The no-intake behaviour is covered, unchanged, by coach_derived_values.test.sql.
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(31);

create function pg_temp.d0() returns date language sql as $$ select (now() at time zone 'utc')::date $$;
create function pg_temp.u(n int) returns uuid language sql as $$
  select ('ca000000-0000-4000-8000-0000000000' || lpad(n::text, 2, '0'))::uuid $$;

create function pg_temp.profile(n int) returns void language sql as $$
  update public.profiles
  set sex = 'male', birth_date = pg_temp.d0() - interval '26 years 100 days', height_cm = 178,
      activity_level = 'moderate', onboarding_completed = true
  where id = pg_temp.u(n);
  insert into public.body_measurements (user_id, weight_kg, source, measured_at) values (pg_temp.u(n), 74.2, 'manual', now()) $$;

-- cnt completed sessions: one first_days_ago back, the rest spread over the last hours.
create function pg_temp.sessions(n int, cnt int, first_days_ago int default null) returns void language plpgsql as $$
begin
  if first_days_ago is not null then
    insert into public.workout_sessions (user_id, name, status, started_at, ended_at)
    values (pg_temp.u(n), 's', 'completed', now() - make_interval(days => first_days_ago),
            now() - make_interval(days => first_days_ago) + interval '1 hour');
    cnt := cnt - 1;
  end if;
  insert into public.workout_sessions (user_id, name, status, started_at, ended_at)
  select pg_temp.u(n), 's', 'completed', now() - make_interval(mins => i * 30) - make_interval(days => 0),
         now() - make_interval(mins => i * 30) + interval '10 minutes'
  from generate_series(1, cnt) i;
end $$;

-- One completed session exactly d days ago and nothing else.
create function pg_temp.last_session(n int, d int) returns void language sql as $$
  insert into public.workout_sessions (user_id, name, status, started_at, ended_at)
  values (pg_temp.u(n), 's', 'completed', now() - make_interval(days => d), now() - make_interval(days => d) + interval '1 hour') $$;

create function pg_temp.intake(n int, years numeric default null, maint int default null) returns void language sql as $$
  insert into public.coach_user_intake (user_id, training_years, known_maintenance_kcal)
  values (pg_temp.u(n), years, maint) $$;

create function pg_temp.diary(n int, days int, kcal numeric) returns void language sql as $$
  insert into public.food_log_entries (user_id, log_date, meal_slot_id, food_id, amount, food_name, kcal, protein_g, carbs_g, fat_g)
  select pg_temp.u(n), pg_temp.d0() - i,
         (select id from public.meal_slots where user_id = pg_temp.u(n) and code = 'breakfast'),
         '0ff00000-0000-4000-8000-0000000000ca', 100, 'Alimento', kcal, 10, 10, 10
  from generate_series(0, days - 1) i $$;

create function pg_temp.est(n int) returns jsonb language plpgsql as $$
begin
  perform set_config('request.jwt.claims', json_build_object('sub', pg_temp.u(n), 'role', 'authenticated')::text, true);
  return public.coach_derived_estimates(pg_temp.d0());
end $$;
create function pg_temp.cfm(n int) returns jsonb language plpgsql as $$
begin
  perform set_config('request.jwt.claims', json_build_object('sub', pg_temp.u(n), 'role', 'authenticated')::text, true);
  return public.confirm_derived_values(pg_temp.d0(), null);
end $$;
create function pg_temp.ack(n int) returns void language plpgsql as $$
begin
  perform set_config('request.jwt.claims', json_build_object('sub', pg_temp.u(n), 'role', 'authenticated')::text, true);
  perform public.ack_maintenance_handover();
end $$;
grant execute on all functions in schema pg_temp to authenticated;

insert into auth.users (id, email) select pg_temp.u(n), 'ci' || n || '@example.com' from generate_series(1, 30) n;
insert into public.foods (id, source, name, kcal_100) values ('0ff00000-0000-4000-8000-0000000000ca', 'off', 'Alimento ca', 100);
update public.profiles set onboarding_completed = true;

-- Experience. 1: 3 years + 10 sessions. 2: 4 months + an intermediate history (24 sessions over 14 weeks).
-- 3: nothing. 4..7: 0.4 / 0.5 / 1.9 / 2 years without history. 8: 1 year + advanced history (150 sessions, 52+ weeks).
select pg_temp.intake(1, 3), pg_temp.sessions(1, 10, 20);
select pg_temp.intake(2, 0.3), pg_temp.sessions(2, 24, 98);
select pg_temp.intake(4, 0.4), pg_temp.intake(5, 0.5), pg_temp.intake(6, 1.9), pg_temp.intake(7, 2);
select pg_temp.intake(8, 1), pg_temp.sessions(8, 150, 400);
-- Quiet users (T-198: no situation is derived any more). 10: last session 27 days ago. 11: 28 days. 14/15: 40 days.
select pg_temp.last_session(10, 27), pg_temp.last_session(11, 28), pg_temp.last_session(14, 40), pg_temp.last_session(15, 40),
       pg_temp.last_session(12, 28);
-- Expenditure prior. 16: no profile, known maintenance. 17: profile + known maintenance. 18: reliable diary + maintenance.
select pg_temp.intake(16, null, 2400);
select pg_temp.profile(17), pg_temp.intake(17, null, 2400);
select pg_temp.profile(18), pg_temp.diary(18, 28, 2000), pg_temp.intake(18, null, 2400);
-- 19 and 20 are identical; 21 gets notes later.
select pg_temp.profile(19), pg_temp.profile(20), pg_temp.profile(21);
select pg_temp.diary(19, 10, 1800), pg_temp.diary(20, 10, 1800), pg_temp.diary(21, 10, 1800);
insert into public.exercises (id, name, muscle_group, equipment, metric_type, is_custom, created_by, movement_pattern)
values ('ea000000-0000-4000-8000-000000000001', 'Mi sentadilla', 'quads', 'barbell', 'weight_reps', true, pg_temp.u(21), 'squat');

set local role authenticated;

-- Experience
select is(pg_temp.est(1)->'experience'->>'value', 'advanced', '3 years said + 10 sessions is advanced');
select is(pg_temp.est(1)->'experience'->>'source', 'intake', 'the source is the intake');
select is((pg_temp.est(1)->'experience'->>'provisional')::boolean, true, 'and it is provisional');
select is(pg_temp.est(1)->'experience'->'missing', '[]'::jsonb, 'the missing training history item is dropped when the intake supplies it');
select is((pg_temp.est(1)->'experience'->>'self_reported_years')::numeric, 3.0, 'the years said are exposed');
select is(pg_temp.est(2)->'experience'->>'value', 'intermediate', '4 months said + an intermediate history is intermediate');
select is(pg_temp.est(2)->'experience'->>'source', 'history', 'the history is the source when it is not lower');
select is(pg_temp.est(3)->'experience'->>'value', 'beginner', 'no intake and no history stays beginner');
select is(pg_temp.est(3)->'experience'->'missing', '["training_history"]'::jsonb, 'and still asks for the history');
select is(pg_temp.est(3)->'experience'->>'source', 'history', 'with the history as source');
select is(pg_temp.est(4)->'experience'->>'value', 'beginner', '0.4 years is beginner');
select is(pg_temp.est(5)->'experience'->>'value', 'intermediate', '0.5 years is intermediate');
select is(pg_temp.est(6)->'experience'->>'value', 'intermediate', '1.9 years is intermediate');
select is(pg_temp.est(7)->'experience'->>'value', 'advanced', '2 years is advanced');
select is(pg_temp.est(8)->'experience'->>'value', 'advanced', 'a higher history takes over from 1 year said');
select is(pg_temp.est(8)->'experience'->>'source', 'history', 'and becomes the source');

-- No situation (T-198): a long pause derives nothing from the intake
select is(pg_temp.est(11) ? 'situation', false, '28 days without training derives no situation');
select is(pg_temp.est(14) ? 'situation', false, 'neither do 40 quiet days');

-- Expenditure prior
select is(pg_temp.est(16)->'total_expenditure'->>'value', '2400', 'the known maintenance is the expenditure when the diary is not enough');
select is(pg_temp.est(16)->'total_expenditure'->>'source', 'intake', 'its source is the intake');
select is((pg_temp.est(16)->'total_expenditure'->>'provisional')::boolean, true, 'and it is provisional');
select is(pg_temp.est(16)->'total_expenditure'->'missing', '["diary"]'::jsonb, 'profile_data is no longer missing, the diary still is');
select is((pg_temp.est(16)->'total_expenditure'->>'allow_calorie_changes')::boolean, false, 'calories cannot change on an unreliable diary');
select is((pg_temp.est(16)->'total_expenditure'->>'unreliable')::boolean, true, 'the diary stays flagged unreliable');
select is(pg_temp.est(17)->'total_expenditure'->>'value', '2400', 'the intake wins over the formula');
select is((pg_temp.cfm(17)->'total_expenditure'->>'provisional')::boolean, true, 'confirming does not make a prior firm');
select is(pg_temp.est(18)->'total_expenditure'->>'source', 'adaptive', 'a reliable diary takes over from the intake');
select is((pg_temp.est(18)->'total_expenditure'->>'handover')::boolean, true, 'and announces the handover once');
select pg_temp.ack(18);
select is((pg_temp.est(18)->'total_expenditure'->>'handover')::boolean, false, 'acknowledging the card hides it for good');

-- Words and context never change numbers
select is(pg_temp.est(19)->'profile_intake', '{"saved": false}'::jsonb, 'without an intake nothing is reported as saved');

create temp table before_notes as select pg_temp.est(21) as j;
grant select on before_notes to authenticated;
reset role;
insert into public.weekly_check_ins (user_id, week_start, energy, notes)
values (pg_temp.u(21), pg_temp.d0() - (extract(isodow from pg_temp.d0())::int - 1), 1, 'me duele mucho la rodilla');
insert into public.exercise_discomforts (user_id, exercise_id, level, note)
values (pg_temp.u(21), 'ea000000-0000-4000-8000-000000000001', 5, 'me duele mucho la rodilla');
set local role authenticated;
select is(pg_temp.est(21), (select j from before_notes), 'a check-in or discomfort note saying "me duele mucho la rodilla" changes no figure');

select * from finish();
rollback;
