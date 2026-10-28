-- pgTAP: weekly_check_ins, profiles.check_in_weekday and weekly_records() (T-136, US-59).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(29);

insert into auth.users (id, email) values
  ('11111111-1111-4111-8111-111111111111', 'ana@example.com'),
  ('22222222-2222-4222-8222-222222222222', 'bea@example.com');

create temp table cat as
  select 'b' as k, (select id from public.exercises where not is_custom and equipment = 'barbell'
                      and metric_type = 'weight_reps' order by key limit 1) as id
  union all
  select 'o', (select id from public.exercises where not is_custom and equipment = 'barbell'
                 and metric_type = 'weight_reps' order by key offset 1 limit 1);
grant select on cat to authenticated;

-- 2026-11-09 is a Monday. Ana: exercise b has a session before the range (11-02) and one inside it (11-10,
-- with a heavier warm-up); exercise o only has a session before the range.
insert into public.workout_sessions (id, user_id, name, status, started_at, ended_at) values
  ('a1000000-0000-4000-8000-000000000001', '11111111-1111-4111-8111-111111111111', 'old', 'completed', '2026-11-02 10:00+00', '2026-11-02 11:00+00'),
  ('a1000000-0000-4000-8000-000000000002', '11111111-1111-4111-8111-111111111111', 'new', 'completed', '2026-11-10 10:00+00', '2026-11-10 11:00+00'),
  ('b1000000-0000-4000-8000-000000000001', '22222222-2222-4222-8222-222222222222', 'bea', 'completed', '2026-11-10 10:00+00', '2026-11-10 11:00+00');
insert into public.workout_sets (session_id, exercise_id, exercise_position, set_index, set_type, weight_kg, reps, completed_at) values
  ('a1000000-0000-4000-8000-000000000001', (select id from cat where k = 'b'), 0, 1, 'working', 100, 5, '2026-11-02 10:05+00'),
  ('a1000000-0000-4000-8000-000000000001', (select id from cat where k = 'o'), 1, 1, 'working', 60, 5, '2026-11-02 10:10+00'),
  ('a1000000-0000-4000-8000-000000000002', (select id from cat where k = 'b'), 0, 1, 'working', 120, 5, '2026-11-10 10:05+00'),
  ('a1000000-0000-4000-8000-000000000002', (select id from cat where k = 'b'), 0, 2, 'warm_up', 200, 3, '2026-11-10 10:06+00'),
  ('b1000000-0000-4000-8000-000000000001', (select id from cat where k = 'b'), 0, 1, 'working', 500, 5, '2026-11-10 10:05+00');

insert into public.weekly_check_ins (user_id, week_start, energy)
  values ('22222222-2222-4222-8222-222222222222', '2026-11-09', 3);

-- Schema and security
select ok(
  (select relrowsecurity and relforcerowsecurity from pg_class where oid = 'public.weekly_check_ins'::regclass),
  'RLS is enabled and forced on weekly_check_ins');
select policies_are('public', 'weekly_check_ins', array[
  'weekly_check_ins_select_own', 'weekly_check_ins_insert_own', 'weekly_check_ins_update_own'
], 'weekly_check_ins has the three owner policies and no delete policy');
select table_privs_are('public', 'weekly_check_ins', 'anon', array[]::text[], 'anon has no privileges on weekly_check_ins');
select table_privs_are('public', 'weekly_check_ins', 'authenticated', array['SELECT', 'INSERT', 'UPDATE'],
  'authenticated can select, insert and update weekly_check_ins (no delete)');
select ok(not has_function_privilege('anon', 'public.weekly_records(date, date)', 'execute'), 'anon cannot execute weekly_records');
select ok(has_function_privilege('authenticated', 'public.weekly_records(date, date)', 'execute'), 'authenticated can execute weekly_records');

-- As Ana
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

select lives_ok($$insert into public.weekly_check_ins (week_start, energy, notes) values ('2026-11-09', 4, 'bien')$$,
  'A inserts a check-in; user_id defaults to the caller');
select is((select user_id from public.weekly_check_ins where week_start = '2026-11-09'),
  '11111111-1111-4111-8111-111111111111'::uuid, 'the default user_id is auth.uid()');
select lives_ok(
  $$insert into public.weekly_check_ins (week_start, energy) values ('2026-11-09', 5)
    on conflict (user_id, week_start) do update set energy = excluded.energy$$,
  'upserting the same week works');
select is((select energy from public.weekly_check_ins where week_start = '2026-11-09'), 5::smallint,
  'one row per user and week: the upsert updated it');
select is((select count(*)::int from public.weekly_check_ins), 1, 'A sees only their own check-ins');
select throws_ok($$insert into public.weekly_check_ins (week_start, energy) values ('2026-11-16', 0)$$,
  '23514', null, 'energy 0 is rejected');
select throws_ok($$insert into public.weekly_check_ins (week_start, energy) values ('2026-11-16', 6)$$,
  '23514', null, 'energy 6 is rejected');
select throws_ok($$insert into public.weekly_check_ins (week_start, energy) values ('2026-11-15', 3)$$,
  '23514', null, 'a Sunday week_start is rejected');
select throws_ok($$insert into public.weekly_check_ins (week_start, energy, notes) values ('2026-11-16', 3, repeat('a', 1001))$$,
  '23514', null, 'notes over 1000 characters are rejected');
select lives_ok($$insert into public.weekly_check_ins (week_start, energy, notes) values ('2026-11-16', 3, repeat('a', 1000))$$,
  'notes of exactly 1000 characters are accepted');
select throws_ok(
  $$insert into public.weekly_check_ins (user_id, week_start, energy)
    values ('22222222-2222-4222-8222-222222222222', '2026-11-23', 3)$$,
  '42501', null, 'A cannot insert a check-in for B');
select results_eq(
  $$with u as (update public.weekly_check_ins set energy = 1
               where user_id = '22222222-2222-4222-8222-222222222222' returning 1)
    select count(*)::int from u$$,
  $$values (0)$$, 'A updating B''s check-in affects 0 rows');

-- check_in_weekday
select is((select check_in_weekday from public.profiles), 7::smallint, 'check_in_weekday defaults to Sunday (7)');
select throws_ok($$update public.profiles set check_in_weekday = 0$$, '23514', null, 'weekday 0 is rejected');
select throws_ok($$update public.profiles set check_in_weekday = 8$$, '23514', null, 'weekday 8 is rejected');
select lives_ok($$update public.profiles set check_in_weekday = 5$$, 'A updates their own weekday');
select results_eq(
  $$with u as (update public.profiles set check_in_weekday = 2
               where id = '22222222-2222-4222-8222-222222222222' returning 1)
    select count(*)::int from u$$,
  $$values (0)$$, 'A cannot update B''s weekday');

-- weekly_records
select results_eq(
  $$select exercise_id, record_type, value from public.weekly_records('2026-11-09', '2026-11-15') order by record_type$$,
  $$select (select id from cat where k = 'b'), t.record_type, t.value
    from (values ('best_1rm', 140::numeric), ('best_set', 600::numeric),
                 ('heaviest_lift', 120::numeric), ('max_volume', 600::numeric)) t(record_type, value)
    order by t.record_type$$,
  'in range: only Ana''s records of that week, no total_reps, warm-up excluded, B''s 500 kg and exercise o absent');
select is((select count(*)::int from public.weekly_records('2026-11-09', '2026-11-15') where session_id is null), 0,
  'every returned record has a session');
select results_eq(
  $$select exercise_id, value from public.weekly_records('2026-11-02', '2026-11-08') where record_type = 'heaviest_lift' order by value$$,
  $$select id, v from (values ((select id from cat where k = 'o'), 60::numeric)) t(id, v)$$,
  'range-bounded: a record whose best session is outside the range is excluded (b''s 11-02 heaviest was beaten later)');
select is((select count(*)::int from public.weekly_records('2026-11-16', '2026-11-22')), 0, 'an empty week returns nothing');
insert into public.training_preferences (warm_up_included) values (true);
select is((select value from public.weekly_records('2026-11-09', '2026-11-15') where record_type = 'heaviest_lift'),
  200::numeric, 'warm-up included: the 200 kg warm-up is the week''s heaviest lift');

reset role;
select is((select check_in_weekday from public.profiles where id = '22222222-2222-4222-8222-222222222222'),
  7::smallint, 'B''s weekday is untouched');

select * from finish();
rollback;
