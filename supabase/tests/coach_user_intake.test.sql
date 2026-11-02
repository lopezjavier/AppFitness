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

insert into public.exercises (id, name, muscle_group, equipment, metric_type, is_custom, created_by, movement_pattern)
values ('e8000000-0000-4000-8000-000000000001', 'Mi press', 'chest', 'barbell', 'weight_reps', true, pg_temp.u(1), 'horizontal_push');
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

-- Schema after T-198: only the three kept inputs exist
select hasnt_column('public', 'coach_user_intake', 'situation', 'situation column is gone');
select hasnt_column('public', 'coach_user_intake', 'injured_area', 'injured_area column is gone');
select hasnt_column('public', 'coach_user_intake', 'pre_break_bests', 'pre_break_bests column is gone');
select hasnt_column('public', 'coach_user_intake', 'known_cut_kcal', 'known_cut_kcal column is gone');
select hasnt_type('public', 'coach_situation', 'coach_situation type is gone');
select hasnt_type('public', 'coach_injured_area', 'coach_injured_area type is gone');

-- Step 0 (T-198): the payload the old client built. The old RPC refused it with 22023 "best exercise is not a
-- catalogue main lift" as soon as a best used a custom exercise or an unknown id, so the whole save failed
-- ("No se pudo guardar"). The new RPC ignores the removed keys: the kept ones are saved.
select pg_temp.as_user(1);
select is((public.save_coach_intake(jsonb_build_object(
    'training_years', 4, 'situation', 'returning_injury', 'injured_area', 'shoulder',
    'pre_break_bests', jsonb_build_array(jsonb_build_object('exercise_id', 'e8000000-0000-4000-8000-000000000001', 'weight_kg', 90, 'reps', 5),
                                          jsonb_build_object('exercise_id', '00000000-0000-0000-0000-000000000000', 'weight_kg', 50, 'reps', 5)),
    'known_maintenance_kcal', 2400, 'known_cut_kcal', 1900, 'free_text', '  Lo dejé por el hombro  ',
    'unknown_key', 'ignored'))) - 'saved_at',
  '{"training_years": 4.0, "known_maintenance_kcal": 2400, "free_text": "Lo dejé por el hombro", "maintenance_handover_at": null}'::jsonb,
  'the old payload with a custom and an unknown best saves the kept fields, trimmed, ignoring the removed keys');
select is((select count(*)::int from public.coach_user_intake), 1, 'one row exists');

-- Partial update: only the keys present are written
select is(public.save_coach_intake('{"training_years": 1}') - 'saved_at',
  '{"training_years": 1.0, "known_maintenance_kcal": 2400, "free_text": "Lo dejé por el hombro", "maintenance_handover_at": null}'::jsonb,
  'saving the years alone keeps the maintenance and the text');
select is((select count(*)::int from public.coach_user_intake), 1, 'saving twice keeps a single row (upsert)');
select is(public.save_coach_intake('{"known_maintenance_kcal": 2600}')->>'free_text', 'Lo dejé por el hombro', 'saving the maintenance alone keeps the text');
select is((public.save_coach_intake('{"free_text": "Otro texto"}')->>'training_years')::numeric, 1.0, 'saving the text alone keeps the years');
select is(public.save_coach_intake('{"free_text": "Otro texto"}')->>'known_maintenance_kcal', '2600', 'and the maintenance');
select is(public.save_coach_intake('{"training_years": null}')->'training_years', 'null'::jsonb, 'a key present with null clears the value');
select is((select known_maintenance_kcal from public.coach_user_intake), 2600, 'and keeps the others');
select is(public.save_coach_intake('{"free_text": "   "}')->'free_text', 'null'::jsonb, 'blank text becomes null');
select is((select known_maintenance_kcal from public.coach_user_intake), 2600, 'blank text keeps the maintenance');
select is(public.save_coach_intake('{}') - 'saved_at',
  '{"training_years": null, "known_maintenance_kcal": 2600, "free_text": null, "maintenance_handover_at": null}'::jsonb,
  'an empty object changes nothing');
select is(public.save_coach_intake('{"known_maintenance_kcal": null}')->'known_maintenance_kcal', 'null'::jsonb, 'maintenance can be cleared');
select is(public.save_coach_intake('{"training_years": 2.5, "known_maintenance_kcal": 2300, "free_text": "x"}') - 'saved_at',
  '{"training_years": 2.5, "known_maintenance_kcal": 2300, "free_text": "x", "maintenance_handover_at": null}'::jsonb,
  'all three can be saved in one call');

-- Boundaries that are valid
select lives_ok($$select public.save_coach_intake('{"training_years": 0, "known_maintenance_kcal": 1200}')$$, 'the lower bounds are accepted');
select lives_ok($$select public.save_coach_intake('{"training_years": 60, "known_maintenance_kcal": 6000}')$$, 'the upper bounds are accepted');
select lives_ok(format($$select public.save_coach_intake(jsonb_build_object('free_text', %L))$$, repeat('a', 500)),
  'a 500-character text is accepted');

-- Values that are refused
select throws_ok($$select public.save_coach_intake('{"training_years": 60.1}')$$, '22023', null, '60.1 years is refused');
select throws_ok($$select public.save_coach_intake('{"training_years": -1}')$$, '22023', null, 'negative years are refused');
select throws_ok($$select public.save_coach_intake('{"training_years": "3"}')$$, '22023', null, 'years must be a number');
select throws_ok($$select public.save_coach_intake('{"known_maintenance_kcal": 1199}')$$, '22023', null, 'maintenance 1199 is refused');
select throws_ok($$select public.save_coach_intake('{"known_maintenance_kcal": 6001}')$$, '22023', null, 'maintenance 6001 is refused');
select throws_ok($$select public.save_coach_intake('{"known_maintenance_kcal": 2400.5}')$$, '22023', null, 'a decimal maintenance is refused');
select throws_ok(format($$select public.save_coach_intake(jsonb_build_object('free_text', %L))$$, repeat('a', 501)),
  '22023', null, 'a 501-character text is refused');
select is((select free_text from public.coach_user_intake), repeat('a', 500), 'a refused save changes nothing');

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
select is((select training_years from public.coach_user_intake where user_id = pg_temp.u(1)), 60.0,
  'save_coach_intake never writes another user''s row');
select is((select training_years from public.coach_user_intake where user_id = pg_temp.u(4)), 7.0, 'it writes the caller''s own row');

select * from finish();
rollback;
