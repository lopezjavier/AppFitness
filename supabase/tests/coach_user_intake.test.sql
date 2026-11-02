-- pgTAP: coach_user_intake and save_coach_intake (T-185, US-81; ranges in planning/04 section 7.5).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(51);

create function pg_temp.u(n int) returns uuid language sql as $$
  select ('c8000000-0000-4000-8000-0000000000' || lpad(n::text, 2, '0'))::uuid $$;
create function pg_temp.as_user(n int) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', pg_temp.u(n), 'role', 'authenticated')::text, true) $$;

insert into auth.users (id, email) select pg_temp.u(n), 'intake' || n || '@example.com' from generate_series(1, 4) n;

create temp table cat as
  select (select id from public.exercises where not is_custom and movement_pattern = 'horizontal_push' order by key limit 1) as bench,
         (select id from public.exercises where not is_custom and movement_pattern = 'squat' order by key limit 1) as squat,
         (select id from public.exercises where not is_custom and movement_pattern = 'isolation' order by key limit 1) as iso,
         (select id from public.exercises where not is_custom and movement_pattern = 'cardio' order by key limit 1) as cardio;
insert into public.exercises (id, name, muscle_group, equipment, metric_type, is_custom, created_by, movement_pattern)
values ('e8000000-0000-4000-8000-000000000001', 'Mi press', 'chest', 'barbell', 'weight_reps', true, pg_temp.u(1), 'horizontal_push');
grant select on cat to authenticated;
grant execute on all functions in schema pg_temp to authenticated;

-- Schema and security
select ok((select relrowsecurity and relforcerowsecurity from pg_class where oid = 'public.coach_user_intake'::regclass),
  'RLS is enabled and forced on coach_user_intake');
select ok((select relrowsecurity and relforcerowsecurity from pg_class where oid = 'public.coach_free_text_exclusions'::regclass),
  'RLS is enabled and forced on coach_free_text_exclusions');
select table_privs_are('public', 'coach_user_intake', 'anon', array[]::text[], 'anon has no privileges on coach_user_intake');
select ok(not has_function_privilege('anon', 'public.save_coach_intake(jsonb)', 'execute'), 'anon cannot execute save_coach_intake');
select ok(not has_function_privilege('anon', 'public.ack_maintenance_handover()', 'execute'), 'anon cannot execute ack_maintenance_handover');
select ok(has_function_privilege('authenticated', 'public.save_coach_intake(jsonb)', 'execute'), 'authenticated can execute save_coach_intake');

set local role authenticated;
select set_config('request.jwt.claims', '{"role":"authenticated"}', true);
select throws_ok($$select public.save_coach_intake('{}')$$, '28000', null, 'save_coach_intake requires a user');

-- A saves; the stored row comes back
select pg_temp.as_user(1);
select is((public.save_coach_intake(jsonb_build_object(
    'training_years', 4, 'situation', 'returning_injury', 'injured_area', 'shoulder',
    'pre_break_bests', jsonb_build_array(jsonb_build_object('exercise_id', (select bench from cat), 'weight_kg', 90, 'reps', 5)),
    'known_maintenance_kcal', 2400, 'known_cut_kcal', 1900, 'free_text', '  Lo dejé por el hombro  ',
    'unknown_key', 'ignored'))) - 'saved_at' - 'pre_break_bests',
  '{"training_years": 4.0, "situation": "returning_injury", "injured_area": "shoulder", "known_maintenance_kcal": 2400,
    "known_cut_kcal": 1900, "free_text": "Lo dejé por el hombro", "maintenance_handover_at": null}'::jsonb,
  'the stored row is returned with the text trimmed and unknown keys ignored');
select is((select pre_break_bests from public.coach_user_intake),
  jsonb_build_array(jsonb_build_object('exercise_id', (select bench from cat), 'weight_kg', 90, 'reps', 5)),
  'the lift is stored');
select is((select count(*)::int from public.coach_user_intake), 1, 'one row exists');
select public.save_coach_intake('{"training_years": 1}');
select is((select count(*)::int from public.coach_user_intake), 1, 'saving twice keeps a single row (upsert)');
select is((select known_maintenance_kcal from public.coach_user_intake), null, 'an absent field is cleared by the upsert');
select is((select pre_break_bests from public.coach_user_intake), '[]'::jsonb, 'and absent bests become an empty array');
select is(public.save_coach_intake('{"free_text": "   "}')->'free_text', 'null'::jsonb, 'blank text becomes null');
select is(public.save_coach_intake('{}')->'training_years', 'null'::jsonb, 'an empty object saves an all-null intake');

-- Boundaries that are valid
select lives_ok($$select public.save_coach_intake('{"training_years": 0, "known_maintenance_kcal": 1200, "known_cut_kcal": 800}')$$,
  'the lower bounds are accepted');
select lives_ok($$select public.save_coach_intake('{"training_years": 60, "known_maintenance_kcal": 6000, "known_cut_kcal": 5000}')$$,
  'the upper bounds are accepted');
select lives_ok(format($$select public.save_coach_intake(jsonb_build_object('free_text', %L))$$, repeat('a', 500)),
  'a 500-character text is accepted');
select lives_ok(format($$select public.save_coach_intake(jsonb_build_object('pre_break_bests',
  jsonb_build_array(jsonb_build_object('exercise_id', %L, 'weight_kg', 5, 'reps', 1),
                    jsonb_build_object('exercise_id', %L, 'weight_kg', 500, 'reps', 30))))$$,
  (select bench from cat), (select squat from cat)), 'weights 5 and 500 and reps 1 and 30 are accepted');

-- Values that are refused
select throws_ok($$select public.save_coach_intake('{"training_years": 60.1}')$$, '22023', null, '60.1 years is refused');
select throws_ok($$select public.save_coach_intake('{"training_years": -1}')$$, '22023', null, 'negative years are refused');
select throws_ok($$select public.save_coach_intake('{"known_maintenance_kcal": 1199}')$$, '22023', null, 'maintenance 1199 is refused');
select throws_ok($$select public.save_coach_intake('{"known_maintenance_kcal": 6001}')$$, '22023', null, 'maintenance 6001 is refused');
select throws_ok($$select public.save_coach_intake('{"known_cut_kcal": 799}')$$, '22023', null, 'cut 799 is refused');
select throws_ok($$select public.save_coach_intake('{"known_cut_kcal": 5001}')$$, '22023', null, 'cut 5001 is refused');
select throws_ok(format($$select public.save_coach_intake(jsonb_build_object('free_text', %L))$$, repeat('a', 501)),
  '22023', null, 'a 501-character text is refused');
select throws_ok($$select public.save_coach_intake('{"situation": "retired"}')$$, '22023', null, 'an unknown situation is refused');
select throws_ok($$select public.save_coach_intake('{"injured_area": "neck"}')$$, '22023', null, 'an unknown area is refused');
select throws_ok($$select public.save_coach_intake('{"pre_break_bests": "x"}')$$, '22023', null, 'bests must be an array');
select throws_ok(format($$select public.save_coach_intake(jsonb_build_object('pre_break_bests', (
  select jsonb_agg(jsonb_build_object('exercise_id', e.id, 'weight_kg', 50, 'reps', 5))
  from (select id from public.exercises where not is_custom and movement_pattern in ('squat', 'hinge', 'lunge', 'horizontal_push',
        'vertical_push', 'horizontal_pull', 'vertical_pull') order by key limit 13) e)))$$),
  '22023', null, '13 bests are refused');
select throws_ok(format($$select public.save_coach_intake(jsonb_build_object('pre_break_bests', jsonb_build_array(
  jsonb_build_object('exercise_id', %L, 'weight_kg', 50, 'reps', 5), jsonb_build_object('exercise_id', %L, 'weight_kg', 60, 'reps', 3))))$$,
  (select bench from cat), (select bench from cat)), '22023', null, 'a duplicate lift is refused');
select throws_ok(format($$select public.save_coach_intake(jsonb_build_object('pre_break_bests', jsonb_build_array(
  jsonb_build_object('exercise_id', 'e8000000-0000-4000-8000-000000000001', 'weight_kg', 50, 'reps', 5))))$$),
  '22023', null, 'a custom exercise is refused');
select throws_ok(format($$select public.save_coach_intake(jsonb_build_object('pre_break_bests', jsonb_build_array(
  jsonb_build_object('exercise_id', %L, 'weight_kg', 50, 'reps', 5))))$$, (select iso from cat)),
  '22023', null, 'an isolation exercise is refused');
select throws_ok(format($$select public.save_coach_intake(jsonb_build_object('pre_break_bests', jsonb_build_array(
  jsonb_build_object('exercise_id', %L, 'weight_kg', 50, 'reps', 5))))$$, (select cardio from cat)),
  '22023', null, 'a cardio exercise is refused');
select throws_ok(format($$select public.save_coach_intake(jsonb_build_object('pre_break_bests', jsonb_build_array(
  jsonb_build_object('exercise_id', %L, 'weight_kg', 50, 'reps', 0))))$$, (select bench from cat)), '22023', null, 'reps 0 is refused');
select throws_ok(format($$select public.save_coach_intake(jsonb_build_object('pre_break_bests', jsonb_build_array(
  jsonb_build_object('exercise_id', %L, 'weight_kg', 50, 'reps', 31))))$$, (select bench from cat)), '22023', null, 'reps 31 is refused');
select throws_ok(format($$select public.save_coach_intake(jsonb_build_object('pre_break_bests', jsonb_build_array(
  jsonb_build_object('exercise_id', %L, 'weight_kg', 4, 'reps', 5))))$$, (select bench from cat)), '22023', null, 'weight 4 is refused');
select throws_ok(format($$select public.save_coach_intake(jsonb_build_object('pre_break_bests', jsonb_build_array(
  jsonb_build_object('exercise_id', %L, 'weight_kg', 501, 'reps', 5))))$$, (select bench from cat)), '22023', null, 'weight 501 is refused');

-- Handover: stored at save time when the adaptive expenditure already qualifies (a reliable 28-day diary)
select pg_temp.as_user(2);
select is(public.save_coach_intake('{"known_maintenance_kcal": 2400}')->'maintenance_handover_at', 'null'::jsonb,
  'without an adaptive expenditure the handover stays pending');
select is(public.coach_compute((now() at time zone 'utc')::date, null)->'total_expenditure'->>'source', 'intake',
  'and the known maintenance is the provisional prior');
reset role;
select set_config('request.jwt.claims', '', true);
insert into public.foods (id, source, name, kcal_100) values ('0ff00000-0000-4000-8000-0000000000c8', 'off', 'Alimento', 100);
update public.profiles set onboarding_completed = true where id = pg_temp.u(3);
insert into public.food_log_entries (user_id, log_date, meal_slot_id, food_id, amount, food_name, kcal, protein_g, carbs_g, fat_g)
select pg_temp.u(3), (now() at time zone 'utc')::date - i,
       (select id from public.meal_slots where user_id = pg_temp.u(3) and code = 'breakfast'),
       '0ff00000-0000-4000-8000-0000000000c8', 100, 'Alimento', 2000, 10, 10, 10
from generate_series(0, 27) i;
set local role authenticated;
select pg_temp.as_user(3);
select isnt(public.save_coach_intake('{"known_maintenance_kcal": 2400}')->'maintenance_handover_at', 'null'::jsonb,
  'with an adaptive expenditure already qualifying the handover is marked at save, so no useless card appears');
select is(public.coach_compute((now() at time zone 'utc')::date, null)->'total_expenditure'->>'source', 'adaptive',
  'and the data takes over');
select is(public.save_coach_intake('{"known_maintenance_kcal": 2400, "training_years": 3}')->>'maintenance_handover_at' is not null, true,
  'saving again with the same maintenance keeps the mark');

-- Acknowledging the handover
select pg_temp.as_user(2);
select public.ack_maintenance_handover();
select isnt((select maintenance_handover_at from public.coach_user_intake), null, 'ack_maintenance_handover sets the mark');
select lives_ok('select public.ack_maintenance_handover()', 'and is idempotent');

-- Isolation
select is((select count(*)::int from public.coach_user_intake), 1, 'user 2 sees only their own intake row');
select pg_temp.as_user(4);
select is((select count(*)::int from public.coach_user_intake), 0, 'user 4 sees nobody else''s intake');
select throws_ok(format($$insert into public.coach_user_intake (user_id) values (%L)$$, pg_temp.u(1)), '42501', null,
  'a user cannot insert an intake for another user');
update public.coach_user_intake set training_years = 9;
delete from public.coach_user_intake;
reset role;
select is((select count(*)::int from public.coach_user_intake where user_id in (pg_temp.u(1), pg_temp.u(2), pg_temp.u(3))), 3,
  'a user can neither update nor delete the intake of others');
set local role authenticated;
select pg_temp.as_user(4);
select public.save_coach_intake('{"training_years": 7}');
reset role;
select is((select training_years from public.coach_user_intake where user_id = pg_temp.u(1)), null,
  'save_coach_intake never writes another user''s row');
select is((select training_years from public.coach_user_intake where user_id = pg_temp.u(4)), 7.0, 'it writes the caller''s own row');

select * from finish();
rollback;
