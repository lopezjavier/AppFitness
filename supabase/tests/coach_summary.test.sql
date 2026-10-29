-- pgTAP: coach_summary, the compact 8-week summary for the coach model (T-157; rules in planning/07 section 8).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(40);

create function pg_temp.d0() returns date language sql as $$ select (now() at time zone 'utc')::date $$;
create function pg_temp.mon0() returns date language sql as $$
  select pg_temp.d0() - (extract(isodow from pg_temp.d0())::int - 1) $$;
create function pg_temp.u(n int) returns uuid language sql as $$
  select ('c5000000-0000-4000-8000-0000000000' || lpad(n::text, 2, '0'))::uuid $$;

create function pg_temp.sum_(j jsonb, k text) returns numeric language sql as $$
  select coalesce(sum((w ->> k)::numeric), 0) from jsonb_array_elements(j -> 'weeks') w $$;

create function pg_temp.sm(n int) returns jsonb language plpgsql as $$
begin
  perform set_config('request.jwt.claims', json_build_object('sub', pg_temp.u(n), 'role', 'authenticated')::text, true);
  return public.coach_summary(pg_temp.d0());
end $$;

grant execute on all functions in schema pg_temp to authenticated;

insert into auth.users (id, email)
select pg_temp.u(n), 'sum' || n || '@example.com' from generate_series(1, 4) n;
update public.profiles set onboarding_completed = true;

-- ---------------------------------------------------------------------------
-- User 1: a small, readable history plus secrets that must never be exported
-- ---------------------------------------------------------------------------
update public.profiles
set sex = 'male', birth_date = pg_temp.d0() - interval '26 years 100 days', height_cm = 178
where id = pg_temp.u(1);
insert into public.training_preferences (user_id, intention, place, days_per_week, session_minutes)
values (pg_temp.u(1), 'strength', 'gym', 4, 60);

-- Weigh-ins: two this week (74 and 76 on the same day = 75), one 10 weeks ago (outside the window).
insert into public.body_measurements (user_id, weight_kg, source, measured_at) values
  (pg_temp.u(1), 74, 'manual', pg_temp.mon0() + time '01:00' at time zone 'UTC'),
  (pg_temp.u(1), 76, 'manual', pg_temp.mon0() + time '02:00' at time zone 'UTC'),
  (pg_temp.u(1), 90, 'manual', (pg_temp.mon0() - 70) + time '01:00' at time zone 'UTC');

insert into public.exercises (id, name, muscle_group, equipment, metric_type, is_custom, created_by, movement_pattern) values
  ('e5000000-0000-4000-8000-000000000001', 'EJERCICIO_SECRETO', 'chest', 'barbell', 'weight_reps', true, pg_temp.u(1), 'horizontal_push');
create temp table cat as
  select (select id from public.exercises where not is_custom and movement_pattern = 'squat' order by key limit 1) as squat_id,
         (select key from public.exercises where not is_custom and movement_pattern = 'squat' order by key limit 1) as squat_key,
         (select id from public.exercises where not is_custom and movement_pattern = 'cardio' order by key limit 1) as cardio_id;
grant select on cat to authenticated;

insert into public.workout_sessions (id, user_id, name, status, started_at, ended_at, description) values
  ('a5000000-0000-4000-8000-000000000001', pg_temp.u(1), 'now', 'completed', now() - interval '1 minute', now(), 'DESC_SECRETA'),
  ('a5000000-0000-4000-8000-000000000002', pg_temp.u(1), 'old', 'completed', now() - interval '70 days',
   now() - interval '70 days' + interval '1 hour', null);
insert into public.workout_sets (session_id, exercise_id, exercise_position, set_index, set_type, weight_kg, reps, completed_at, duration_s, notes) values
  ('a5000000-0000-4000-8000-000000000001', (select squat_id from cat), 0, 1, 'working', 100, 5, now(), null, null),
  ('a5000000-0000-4000-8000-000000000001', (select squat_id from cat), 0, 2, 'warm_up', 200, 5, now(), null, null),
  ('a5000000-0000-4000-8000-000000000001', 'e5000000-0000-4000-8000-000000000001', 1, 1, 'working', 60, 8, now(), null, null),
  ('a5000000-0000-4000-8000-000000000001', (select cardio_id from cat), 2, 1, 'working', 0, 0, now(), 1800, null),
  ('a5000000-0000-4000-8000-000000000002', (select squat_id from cat), 0, 1, 'working', 150, 5, now() - interval '70 days', null, null);

update public.workout_sets set notes = 'NOTA_SECRETA', note_kind = 'technique'
where session_id = 'a5000000-0000-4000-8000-000000000001' and set_index = 1 and exercise_position = 0;

insert into public.exercise_discomforts (user_id, exercise_id, level, note, logged_at, resolved_at) values
  (pg_temp.u(1), (select squat_id from cat), 3, 'MOLESTIA_SECRETA', now() - interval '1 day', null),
  (pg_temp.u(1), 'e5000000-0000-4000-8000-000000000001', 2, 'resuelta', now() - interval '2 days', now() - interval '1 day'),
  (pg_temp.u(1), 'e5000000-0000-4000-8000-000000000001', 5, 'antigua', now() - interval '8 days', null);

insert into public.foods (id, source, name, kcal_100) values ('0ff00000-0000-4000-8000-0000000000c5', 'off', 'COMIDA_SECRETA', 100);
insert into public.food_log_entries (user_id, log_date, meal_slot_id, food_id, amount, food_name, kcal, protein_g, carbs_g, fat_g)
values (pg_temp.u(1), pg_temp.d0(),
        (select id from public.meal_slots where user_id = pg_temp.u(1) and code = 'breakfast'),
        '0ff00000-0000-4000-8000-0000000000c5', 100, 'COMIDA_SECRETA', 2000, 150, 200, 60);
insert into public.daily_activity (user_id, day, steps, active_kcal) values (pg_temp.u(1), pg_temp.d0(), 8000, 450);
insert into public.sleep_sessions (user_id, external_id, started_at, ended_at, minutes) values
  (pg_temp.u(1), 's1', now() - interval '9 hours', now() - interval '1 hour', 420);
insert into public.weekly_check_ins (user_id, week_start, energy, notes) values (pg_temp.u(1), pg_temp.mon0(), 4, 'CHECKIN_SECRETO');

-- ---------------------------------------------------------------------------
-- User 4: the worst case (8 weeks of daily data, 20 custom main/cardio exercises, 10 open discomforts)
-- ---------------------------------------------------------------------------
update public.profiles set sex = 'female', birth_date = date '1990-05-05', height_cm = 165 where id = pg_temp.u(4);
insert into public.exercises (id, name, muscle_group, equipment, metric_type, is_custom, created_by, movement_pattern)
select ('e5000000-0000-4000-8000-0000000001' || lpad(i::text, 2, '0'))::uuid, 'Custom ' || i,
       (enum_range(null::public.muscle_group))[1 + i % 12], 'barbell', 'weight_reps', true, pg_temp.u(4),
       case when i <= 12 then (array['squat', 'hinge', 'lunge', 'horizontal_push', 'vertical_push', 'horizontal_pull',
                                     'vertical_pull'])[1 + i % 7]::public.movement_pattern
            when i <= 16 then 'isolation'::public.movement_pattern
            else 'cardio'::public.movement_pattern end
from generate_series(1, 20) i;
insert into public.body_measurements (user_id, weight_kg, source, measured_at)
select pg_temp.u(4), 60 + (i % 5) * 0.3, 'manual', now() - make_interval(days => i) from generate_series(0, 55) i;
insert into public.food_log_entries (user_id, log_date, meal_slot_id, food_id, amount, food_name, kcal, protein_g, carbs_g, fat_g)
select pg_temp.u(4), pg_temp.d0() - i,
       (select id from public.meal_slots where user_id = pg_temp.u(4) and code = 'breakfast'),
       '0ff00000-0000-4000-8000-0000000000c5', 100, 'COMIDA_SECRETA', 1800 + i, 120, 190, 60
from generate_series(0, 55) i;
insert into public.daily_activity (user_id, day, steps, active_kcal)
select pg_temp.u(4), pg_temp.d0() - i, 7000 + i * 13, 300 + i from generate_series(0, 55) i;
insert into public.sleep_sessions (user_id, external_id, started_at, ended_at, minutes)
select pg_temp.u(4), 'sl' || i, now() - make_interval(days => i, hours => 9), now() - make_interval(days => i, hours => 1), 400 + i
from generate_series(0, 55) i;
insert into public.weekly_check_ins (user_id, week_start, energy)
select pg_temp.u(4), pg_temp.mon0() - 7 * i, 1 + i % 5 from generate_series(0, 7) i;
insert into public.workout_sessions (id, user_id, name, status, started_at, ended_at)
select ('a5000000-0000-4000-8000-0000000002' || lpad(i::text, 2, '0'))::uuid, pg_temp.u(4), 's', 'completed',
       now() - make_interval(days => i), now() - make_interval(days => i) + interval '1 hour'
from generate_series(0, 55) i;
insert into public.workout_sets (session_id, exercise_id, exercise_position, set_index, weight_kg, reps, completed_at, duration_s)
select ('a5000000-0000-4000-8000-0000000002' || lpad(s::text, 2, '0'))::uuid,
       ('e5000000-0000-4000-8000-0000000001' || lpad(e::text, 2, '0'))::uuid, e, 1, 50 + e + (55 - s) / 2.0, 8,
       now() - make_interval(days => s), case when e > 16 then 1200 end
from generate_series(0, 55) s, generate_series(1, 20) e;
insert into public.exercise_discomforts (user_id, exercise_id, level, logged_at)
select pg_temp.u(4), ('e5000000-0000-4000-8000-0000000001' || lpad(i::text, 2, '0'))::uuid, 1 + i % 5, now() - make_interval(hours => i)
from generate_series(1, 12) i;

-- ---------------------------------------------------------------------------
-- Security
-- ---------------------------------------------------------------------------
select ok(not has_function_privilege('anon', 'public.coach_summary(date)', 'execute'), 'anon cannot execute coach_summary');

set local role authenticated;
select set_config('request.jwt.claims', '{"role":"authenticated"}', true);
select throws_ok($$select public.coach_summary(current_date)$$, '28000', null, 'coach_summary requires a user');
select set_config('request.jwt.claims', json_build_object('sub', pg_temp.u(2), 'role', 'authenticated')::text, true);
select throws_ok(format($$select public.coach_summary(%L)$$, pg_temp.d0() + 2), '22023', null,
  'coach_summary refuses a date two days from UTC today');

-- ---------------------------------------------------------------------------
-- Shape and window (user 2 has no data at all)
-- ---------------------------------------------------------------------------
select is(jsonb_array_length(pg_temp.sm(2)->'weeks'), 8, 'an empty user still gets exactly 8 week entries');
select is(pg_temp.sm(2)->'weeks'->0->>'week_start', (pg_temp.mon0() - 49)::text, 'the first week starts 7 weeks before the current one');
select is(pg_temp.sm(2)->'weeks'->7->>'week_start', pg_temp.mon0()::text, 'the last week is the current ISO week');
select is(pg_temp.sm(2)->'window', jsonb_build_object('from', pg_temp.mon0() - 49, 'to', pg_temp.d0()), 'the window dates are exposed');
select is(pg_temp.sm(2)->'main_lifts', '[]'::jsonb, 'an empty user has no main lifts');
select is(pg_temp.sm(2)->'open_discomforts', '[]'::jsonb, 'and no open discomforts');
select is(pg_temp.sm(2)->'weeks'->3->'sets_by_muscle', '[]'::jsonb, 'an empty week has an empty muscle list');
select is((pg_temp.sm(2)->'weeks'->3->>'sessions')::int, 0, 'and zero sessions');
select is(pg_temp.sm(2)->'weeks'->3->'cardio', '{"sessions": 0, "minutes": 0}'::jsonb, 'and a stable cardio object');
select ok(pg_temp.sm(2) ? 'estimates', 'the derived estimates are included');
select is(pg_temp.sm(2)->'profile'->'weight_kg', 'null'::jsonb, 'an empty profile has null fields');

-- ---------------------------------------------------------------------------
-- Content (user 1)
-- ---------------------------------------------------------------------------
select is((pg_temp.sm(1)->'profile'->>'age')::int, 26, 'the profile carries the age');
select is((pg_temp.sm(1)->'profile'->>'height_cm')::numeric, 178::numeric, 'and the height');
select is((pg_temp.sm(1)->'profile'->>'weight_kg')::numeric, 76::numeric, 'and the latest weight');
select is(pg_temp.sm(1)->'training_preferences'->>'intention', 'strength', 'the training preferences are included');
select is((pg_temp.sm(1)->'weeks'->7->>'weigh_in_avg')::numeric, 75::numeric, 'weigh-ins of a week are averaged');
select is(pg_temp.sum_(pg_temp.sm(1), 'weigh_in_days'), 1::numeric, 'a weigh-in older than 8 weeks is excluded');
select is((pg_temp.sm(1)->'weeks'->7->>'sessions')::int, 1, 'the session of this week is counted');
select is(pg_temp.sum_(pg_temp.sm(1), 'sessions'), 1::numeric, 'a session older than 8 weeks is excluded');
select is((pg_temp.sm(1)->'main_lifts'->0->>'exercise'), (select squat_key from cat), 'main lifts are identified by their catalogue key');
select is(jsonb_array_length(pg_temp.sm(1)->'main_lifts'), 2, 'the custom main lift is listed too');
select is((pg_temp.sm(1)->'main_lifts'->0->'weekly_e1rm'->>7)::numeric, 116.5::numeric,
  'weekly e1RM of 100 x 5 is 116.5 and the warm-up and the old 150 x 5 are ignored');
select is((pg_temp.sm(1)->'weeks'->7->>'steps_avg')::numeric, 8000::numeric, 'steps are summarised per week');
select is((pg_temp.sm(1)->'weeks'->7->>'sleep_minutes_avg')::numeric, 420::numeric, 'sleep is summarised per week');
select is((pg_temp.sm(1)->'weeks'->7->>'kcal_avg')::numeric, 2000::numeric, 'the diary is averaged over logged days');
select is(pg_temp.sm(1)->'weeks'->7->'cardio', '{"sessions": 1, "minutes": 30}'::jsonb, 'cardio sessions are listed with their minutes');
select is((pg_temp.sm(1)->'weeks'->7->>'check_in_energy')::int, 4, 'the check-in energy is included');
select is(pg_temp.sm(1)->'weeks'->7->'sets_by_muscle'->0, '{"muscle": "chest", "sets": 1}'::jsonb,
  'working sets per muscle group exclude warm-ups and cardio (chest ties with quads, enum order)');
select is(pg_temp.sm(1)->'open_discomforts', jsonb_build_array(
  jsonb_build_object('exercise', (select squat_key from cat), 'level', 3, 'logged_on', (now() - interval '1 day')::date)),
  'only open discomforts are listed, without their text');
select ok(pg_temp.sm(1)::text !~ 'SECRET|resuelta|antigua', 'no free text of any kind is exported');
select ok(pg_temp.sm(1)::text !~ 'food_log_entries|body_measurements|workout_sets|daily_activity|sleep_sessions',
  'no raw table names or rows are exported');

-- ---------------------------------------------------------------------------
-- Isolation and warm-up preference
-- ---------------------------------------------------------------------------
select is(pg_temp.sum_(pg_temp.sm(2), 'sessions'), 0::numeric, 'another user''s sessions are not in the summary');

select pg_temp.sm(1);
update public.training_preferences set warm_up_included = true;
select is((pg_temp.sm(1)->'main_lifts'->0->'weekly_e1rm'->>7)::numeric, 233.5::numeric,
  'with warm-ups included the same sets give 200 x 5 = 233.5');

-- ---------------------------------------------------------------------------
-- Size cap on the worst case
-- ---------------------------------------------------------------------------
select is(jsonb_array_length(pg_temp.sm(4)->'main_lifts'), 12, 'main lifts are capped at 12');
select is(jsonb_array_length(pg_temp.sm(4)->'open_discomforts'), 10, 'open discomforts are capped at 10');
select ok(octet_length(pg_temp.sm(4)::text) <= 16384, 'the worst-case summary fits in 16 384 bytes');
select ok(octet_length(pg_temp.sm(4)::text) <= 13000, 'and keeps a margin of about 20 percent');

select * from finish();
rollback;
