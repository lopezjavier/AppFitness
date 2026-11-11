-- pgTAP: the objects removed by T-286 stay removed, the readers keep their other keys, and the scrub of
-- coach_recommendations.input_summary removes free_text (US-123; ADR-107).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(31);

-- ---------------------------------------------------------------------------
-- Dropped functions (by name: no overload may survive)
-- ---------------------------------------------------------------------------
select hasnt_function('public', 'coach_free_text_lines', 'coach_free_text_lines is gone');
select hasnt_function('public', 'set_free_text_line_excluded', 'set_free_text_line_excluded is gone');
select hasnt_function('public', 'save_meal_slots', 'save_meal_slots is gone');
select hasnt_function('public', 'delete_meal_slot', 'delete_meal_slot is gone');
select hasnt_function('public', 'coach_plan_week_of_date', 'coach_plan_week_of_date is gone');
select hasnt_function('public', 'assert_unique_meal_labels', 'assert_unique_meal_labels is gone');
select hasnt_function('public', 'convert_used_off_foods', 'convert_used_off_foods is gone');
select hasnt_function('public', 'coach_rederive_plan_links', 'coach_rederive_plan_links is gone');
select hasnt_function('public', 'migrate_equipment_list', 'migrate_equipment_list is gone');
select hasnt_function('public', 'off_alcohol_g', 'off_alcohol_g is gone');
select hasnt_function('public', 'coach_plan_calendar', 'coach_plan_calendar is gone');

-- ---------------------------------------------------------------------------
-- Dropped table and columns; kept objects
-- ---------------------------------------------------------------------------
select hasnt_table('public', 'coach_free_text_exclusions', 'coach_free_text_exclusions is gone');
select hasnt_column('public', 'coach_user_intake', 'free_text', 'coach_user_intake.free_text is gone');
select hasnt_column('public', 'weekly_check_ins', 'notes', 'weekly_check_ins.notes is gone');
select ok(not exists (select 1 from pg_constraint
                      where conname in ('coach_user_intake_text_length', 'weekly_check_ins_notes_length')),
  'the length constraints of the dropped columns are gone');
select has_column('public', 'exercise_discomforts', 'note', 'exercise_discomforts.note stays (cross-session tracking)');
select has_function('public', 'coach_plan_calendar_for', 'coach_plan_calendar_for stays (the calendar reader)');
select has_function('public', 'copy_off_food_core', 'copy_off_food_core stays (called by copy_off_food)');

-- ---------------------------------------------------------------------------
-- Readers: no free_text, the other keys stay (user A, as the app calls them)
-- ---------------------------------------------------------------------------
insert into auth.users (id, email) values ('f2860000-0000-4000-8000-000000000001', 'removed-a@example.com');
insert into public.coach_recommendations (user_id, kind, payload, input_summary) values
  ('f2860000-0000-4000-8000-000000000001', 'plan', '{"kind": "plan"}',
   '{"generated_for": "2026-11-11", "free_text": [{"origin": "intake", "date": "2026-11-01", "text": "SECRETO_LEGADO"}], "weeks": []}'),
  ('f2860000-0000-4000-8000-000000000001', 'plan', '{"kind": "plan"}',
   '{"generated_for": "2026-11-04", "weeks": [{"week_start": "2026-11-02"}]}');

set local role authenticated;
select set_config('request.jwt.claims',
  json_build_object('sub', 'f2860000-0000-4000-8000-000000000001', 'role', 'authenticated')::text, true);

select ok(not (public.coach_summary((now() at time zone 'utc')::date) ? 'free_text'),
  'coach_summary has no free_text key');
select ok(public.coach_summary((now() at time zone 'utc')::date) ?& array[
    'generated_for', 'window', 'profile', 'training_preferences', 'estimates', 'weeks',
    'cardio_favorites', 'main_lifts', 'open_discomforts'],
  'coach_summary keeps its other keys');
select ok(not exists (select 1
                      from jsonb_array_elements(public.coach_plan_inputs('plan', (now() at time zone 'utc')::date)->'inputs') e
                      where e->>'key' = 'free_text'),
  'coach_plan_inputs (plan) has no free_text entry');
select ok(not exists (select 1
                      from jsonb_array_elements(public.coach_plan_inputs('block', (now() at time zone 'utc')::date)->'inputs') e
                      where e->>'key' = 'free_text'),
  'coach_plan_inputs (block) has no free_text entry');
reset role;

-- ---------------------------------------------------------------------------
-- Scrub: the same statement the migration runs on coach_recommendations.input_summary
-- ---------------------------------------------------------------------------
select is((select count(*) from public.coach_recommendations
           where user_id = 'f2860000-0000-4000-8000-000000000001'), 2::bigint, 'two summary snapshots exist before the scrub');

update public.coach_recommendations set input_summary = input_summary - 'free_text' where input_summary ? 'free_text';

select is((select input_summary ? 'free_text' from public.coach_recommendations
           where user_id = 'f2860000-0000-4000-8000-000000000001' and input_summary->>'generated_for' = '2026-11-11'), false,
  'the free_text key is removed from the snapshot');
select is((select input_summary->'weeks' from public.coach_recommendations
           where user_id = 'f2860000-0000-4000-8000-000000000001' and input_summary->>'generated_for' = '2026-11-11'), '[]'::jsonb,
  'the other keys of that snapshot are intact');
select is((select input_summary->'weeks' from public.coach_recommendations
           where user_id = 'f2860000-0000-4000-8000-000000000001' and input_summary->>'generated_for' = '2026-11-04'),
  '[{"week_start": "2026-11-02"}]'::jsonb, 'a snapshot without free_text is untouched');
select is((select count(*) from public.coach_recommendations where input_summary::text like '%free_text%'), 0::bigint,
  'no snapshot mentions free_text any more');
select is((select count(*) from public.coach_recommendations where input_summary::text like '%SECRETO_LEGADO%'), 0::bigint,
  'the old free text is not in any snapshot');
select is((select count(*) from public.coach_recommendations where user_id = 'f2860000-0000-4000-8000-000000000001'), 2::bigint,
  'the scrub changes the content only, never the row count');
select lives_ok($$update public.coach_recommendations set input_summary = input_summary - 'free_text' where input_summary ? 'free_text'$$,
  'running the scrub a second time is harmless (idempotent)');
select is((select count(*) from public.coach_recommendations where input_summary::text like '%free_text%'), 0::bigint,
  'and still nothing mentions free_text after the second run');

select * from finish();
rollback;
