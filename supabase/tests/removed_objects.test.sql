-- pgTAP: the objects removed by T-286 (US-123, ADR-107) and by T-312 (US-142, ADR-110) stay removed, the readers keep
-- their other keys, and the scrubs of coach_recommendations.input_summary remove free_text and check_in_energy.
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(48);

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

-- ===========================================================================
-- T-312 (US-142, ADR-110): the weekly check-in is gone
-- ===========================================================================
select hasnt_table('public', 'weekly_check_ins', 'weekly_check_ins is gone');
select hasnt_column('public', 'profiles', 'check_in_weekday', 'profiles.check_in_weekday is gone');
select ok(not exists (select 1 from pg_constraint where conname = 'profiles_check_in_weekday_range'),
  'the check_in_weekday range constraint is gone');
select hasnt_function('public', 'weekly_records', 'weekly_records is gone');
select hasnt_type('public', 'notification_kind_v2', 'the temporary notification_kind_v2 type is gone');
select col_type_is('public', 'notification_settings', 'kind', 'public', 'notification_kind',
  'notification_settings.kind keeps the type public.notification_kind');
select ok(not exists (select 1
                      from pg_proc p
                      join pg_namespace n on n.oid = p.pronamespace
                      where n.nspname = 'public'
                        and p.prosrc ~* 'weekly_check_in|check_in_weekday|\mweekly_records\M|check_in_energy'),
  'no public function uses the check-in objects');

set local role authenticated;
select set_config('request.jwt.claims',
  json_build_object('sub', 'f2860000-0000-4000-8000-000000000001', 'role', 'authenticated')::text, true);

select is(jsonb_array_length(public.coach_summary((now() at time zone 'utc')::date)->'weeks'), 8,
  'coach_summary still lists 8 weeks');
select ok(not exists (select 1
                      from jsonb_array_elements(public.coach_summary((now() at time zone 'utc')::date)->'weeks') w
                      where w ? 'check_in_energy'),
  'no coach_summary week carries check_in_energy');
select ok(not exists (select 1
                      from jsonb_array_elements(public.coach_summary((now() at time zone 'utc')::date)->'weeks') w
                      where not (w ?& array[
                          'week_start', 'weigh_in_avg', 'weigh_in_days', 'diary_days', 'kcal_avg', 'protein_g_avg',
                          'carbs_g_avg', 'fat_g_avg', 'steps_avg', 'active_kcal_avg', 'sleep_minutes_avg', 'sessions',
                          'sets_by_muscle', 'cardio'])
                         or (select count(*) from jsonb_object_keys(w)) <> 14),
  'every coach_summary week keeps exactly its 14 other keys');
reset role;

-- ---------------------------------------------------------------------------
-- Strip: the same statement the migration runs on coach_recommendations.input_summary
-- ---------------------------------------------------------------------------
insert into auth.users (id, email) values ('f3120000-0000-4000-8000-000000000001', 'removed-checkin@example.com');
insert into public.coach_recommendations (user_id, kind, payload, input_summary) values
  ('f3120000-0000-4000-8000-000000000001', 'plan', '{"kind": "plan"}',
   '{"generated_for": "2026-11-21", "estimates": {"tdee": 2400},
     "weeks": [{"week_start": "2026-11-09", "check_in_energy": 4, "sessions": 2},
               {"week_start": "2026-11-16", "check_in_energy": null, "sessions": 1},
               "not-an-object"]}'),
  ('f3120000-0000-4000-8000-000000000001', 'plan', '{"kind": "plan"}',
   '{"generated_for": "2026-11-14", "weeks": [{"week_start": "2026-11-09", "sessions": 3}]}'),
  ('f3120000-0000-4000-8000-000000000001', 'plan', '{"kind": "plan"}', '{}');

create temp table t312_before as
select id, input_summary::text as summary_text
from public.coach_recommendations
where user_id = 'f3120000-0000-4000-8000-000000000001' and not input_summary::text like '%check_in_energy%';

select is((select count(*) from public.coach_recommendations
           where user_id = 'f3120000-0000-4000-8000-000000000001' and input_summary::text like '%check_in_energy%'),
  1::bigint, 'one summary snapshot holds check_in_energy before the strip');

update public.coach_recommendations r
set input_summary = jsonb_set(r.input_summary, '{weeks}', (
  select jsonb_agg(case when jsonb_typeof(t.e) = 'object' then t.e - 'check_in_energy' else t.e end order by t.o)
  from jsonb_array_elements(r.input_summary->'weeks') with ordinality t(e, o)))
where jsonb_typeof(r.input_summary->'weeks') = 'array'
  and exists (select 1 from jsonb_array_elements(r.input_summary->'weeks') t(e)
              where jsonb_typeof(t.e) = 'object' and t.e ? 'check_in_energy');

select is((select count(*) from public.coach_recommendations where input_summary::text like '%check_in_energy%'),
  0::bigint, 'no snapshot mentions check_in_energy after the strip');
select is((select input_summary from public.coach_recommendations
           where user_id = 'f3120000-0000-4000-8000-000000000001' and input_summary->>'generated_for' = '2026-11-21'),
  '{"generated_for": "2026-11-21", "estimates": {"tdee": 2400},
    "weeks": [{"week_start": "2026-11-09", "sessions": 2}, {"week_start": "2026-11-16", "sessions": 1},
              "not-an-object"]}'::jsonb,
  'only check_in_energy is removed: the other keys and the week order stay');
select is((select count(*) from pg_temp.t312_before b
           join public.coach_recommendations r on r.id = b.id
           where r.input_summary::text = b.summary_text),
  2::bigint, 'the snapshots without check_in_energy are byte-equal');
select is((select count(*) from public.coach_recommendations where user_id = 'f3120000-0000-4000-8000-000000000001'),
  3::bigint, 'the strip changes the content only, never the row count');
select lives_ok($$
  update public.coach_recommendations r
  set input_summary = jsonb_set(r.input_summary, '{weeks}', (
    select jsonb_agg(case when jsonb_typeof(t.e) = 'object' then t.e - 'check_in_energy' else t.e end order by t.o)
    from jsonb_array_elements(r.input_summary->'weeks') with ordinality t(e, o)))
  where jsonb_typeof(r.input_summary->'weeks') = 'array'
    and exists (select 1 from jsonb_array_elements(r.input_summary->'weeks') t(e)
                where jsonb_typeof(t.e) = 'object' and t.e ? 'check_in_energy')$$,
  'running the strip a second time is harmless (idempotent)');
select is((select count(*) from public.coach_recommendations where input_summary::text like '%check_in_energy%'),
  0::bigint, 'and still nothing mentions check_in_energy after the second run');

select * from finish();
rollback;
