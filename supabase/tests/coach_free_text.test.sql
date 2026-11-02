-- pgTAP: coach_free_text_lines, set_free_text_line_excluded and the exclusions table (T-185, US-89, ADR-87).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(36);

create function pg_temp.d0() returns date language sql as $$ select (now() at time zone 'utc')::date $$;
create function pg_temp.mon0() returns date language sql as $$
  select pg_temp.d0() - (extract(isodow from pg_temp.d0())::int - 1) $$;
create function pg_temp.u(n int) returns uuid language sql as $$
  select ('c9000000-0000-4000-8000-0000000000' || lpad(n::text, 2, '0'))::uuid $$;
create function pg_temp.as_user(n int) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', pg_temp.u(n), 'role', 'authenticated')::text, true) $$;
create function pg_temp.lines(n int) returns jsonb language plpgsql as $$
begin
  perform pg_temp.as_user(n);
  return public.coach_free_text_lines(pg_temp.d0());
end $$;
-- Statuses in order, as a comma-separated string.
create function pg_temp.st(n int) returns text language sql as $$
  select coalesce(string_agg(x ->> 'status', ',' order by o), '') from jsonb_array_elements(pg_temp.lines(n)) with ordinality t(x, o) $$;
grant execute on all functions in schema pg_temp to authenticated;

insert into auth.users (id, email) select pg_temp.u(n), 'ft' || n || '@example.com' from generate_series(1, 9) n;
update public.profiles set onboarding_completed = true;

create temp table cat as select (select id from public.exercises where not is_custom order by key limit 1) as ex;
grant select on cat to authenticated;

-- A check-in note k weeks before this week (k = 0 is the current week).
create function pg_temp.ci(n int, k int, txt text) returns void language sql as $$
  insert into public.weekly_check_ins (user_id, week_start, energy, notes) values (pg_temp.u(n), pg_temp.mon0() - 7 * k, 3, txt) $$;

-- 1: exactly 2 000 bytes -> both sent. 2: 2 001 -> the last one is over the limit.
select pg_temp.ci(1, 0, repeat('a', 1000));
select pg_temp.ci(1, 1, repeat('b', 1000));
select pg_temp.ci(2, 0, repeat('a', 1000));
select pg_temp.ci(2, 1, repeat('b', 1000));
select pg_temp.ci(2, 2, 'c');
-- 3: multi-byte characters count as bytes: 1 000 + 1 002 bytes but only 1 001 characters.
select pg_temp.ci(3, 0, repeat('é', 500));
select pg_temp.ci(3, 1, repeat('é', 501));
-- 4: strict prefix: a short older line after an over-limit one is over the limit too.
select pg_temp.ci(4, 0, repeat('a', 1000));
select pg_temp.ci(4, 1, repeat('b', 800));
select pg_temp.ci(4, 2, repeat('c', 300));
select pg_temp.ci(4, 3, 'short');
-- 5: exclusions
select pg_temp.ci(5, 0, repeat('a', 1000));
select pg_temp.ci(5, 1, repeat('b', 1000));
select pg_temp.ci(5, 2, 'c');
-- 6: window boundary, other sources that must never appear, ordering of equal dates
insert into public.exercises (id, name, muscle_group, equipment, metric_type, is_custom, created_by, movement_pattern)
values ('e9000000-0000-4000-8000-000000000001', 'EJERCICIO_CUSTOM', 'chest', 'barbell', 'weight_reps', true, pg_temp.u(6), 'horizontal_push');
select pg_temp.ci(6, 7, 'check-in dia 49 dentro');
select pg_temp.ci(6, 8, 'check-in dia 56 fuera');
insert into public.exercise_discomforts (user_id, exercise_id, level, note, logged_at, resolved_at) values
  (pg_temp.u(6), (select ex from cat), 2, 'molestia dentro', (pg_temp.mon0() - 49)::timestamp at time zone 'UTC' + interval '12 hours', null),
  (pg_temp.u(6), (select ex from cat), 2, 'molestia fuera', (pg_temp.mon0() - 50)::timestamp at time zone 'UTC' + interval '12 hours', null),
  (pg_temp.u(6), (select ex from cat), 2, 'molestia resuelta', (pg_temp.mon0() - 7)::timestamp at time zone 'UTC' + interval '12 hours',
   (pg_temp.mon0() - 7)::timestamp at time zone 'UTC' + interval '13 hours');
select pg_temp.ci(6, 1, 'check-in misma fecha');
insert into public.coach_user_intake (user_id, free_text, saved_at) values (pg_temp.u(6), 'texto de conocerte antiguo', now() - interval '200 days');
insert into public.workout_sessions (id, user_id, name, status, started_at, ended_at, description) values
  ('a9000000-0000-4000-8000-000000000001', pg_temp.u(6), 'NOMBRE_SESION', 'completed', now() - interval '1 hour', now(), 'DESCRIPCION_SESION');
insert into public.workout_sets (session_id, exercise_id, exercise_position, set_index, set_type, weight_kg, reps, completed_at, notes, note_kind) values
  ('a9000000-0000-4000-8000-000000000001', 'e9000000-0000-4000-8000-000000000001', 0, 1, 'working', 60, 8, now(), 'NOTA_SERIE', 'technique');
insert into public.foods (id, source, name, kcal_100) values ('0ff00000-0000-4000-8000-0000000000c9', 'off', 'COMIDA_NOMBRE', 100);
insert into public.food_log_entries (user_id, log_date, meal_slot_id, food_id, amount, food_name, kcal, protein_g, carbs_g, fat_g)
values (pg_temp.u(6), pg_temp.d0(), (select id from public.meal_slots where user_id = pg_temp.u(6) and code = 'breakfast'),
        '0ff00000-0000-4000-8000-0000000000c9', 100, 'COMIDA_NOMBRE', 500, 10, 10, 10);
-- 7: more than 20 lines
insert into public.exercise_discomforts (user_id, exercise_id, level, note, logged_at)
select pg_temp.u(7), (select ex from cat), 2, 'x', now() - make_interval(hours => i) from generate_series(1, 22) i;
-- 9: only the intake text
insert into public.coach_user_intake (user_id, free_text) values (pg_temp.u(9), 'Entrené cuatro años');

-- Security
select ok(not has_function_privilege('anon', 'public.coach_free_text_lines(date)', 'execute'), 'anon cannot read the lines');
select ok(not has_function_privilege('anon', 'public.set_free_text_line_excluded(text, text, boolean)', 'execute'), 'anon cannot exclude a line');
select ok((select relrowsecurity and relforcerowsecurity from pg_class where oid = 'public.coach_free_text_exclusions'::regclass),
  'RLS is enabled and forced on the exclusions');
select policies_are('public', 'coach_free_text_exclusions', array[
  'coach_free_text_exclusions_select_own', 'coach_free_text_exclusions_insert_own', 'coach_free_text_exclusions_delete_own'],
  'the exclusions have owner select, insert and delete policies (no update)');
select table_privs_are('public', 'coach_free_text_exclusions', 'anon', array[]::text[], 'anon has no privileges on the exclusions');

set local role authenticated;
select set_config('request.jwt.claims', '{"role":"authenticated"}', true);
select throws_ok(format($$select public.coach_free_text_lines(%L)$$, pg_temp.d0()), '28000', null, 'the lines require a user');

-- Size budget (strict prefix, bytes of the escaped text)
select is(pg_temp.st(1), 'sent,sent', 'exactly 2 000 bytes are all sent');
select is(pg_temp.st(2), 'sent,sent,over_limit', '2 001 bytes: the line that crosses the limit is not sent');
select is(pg_temp.st(3), 'sent,over_limit', 'multi-byte characters are counted in bytes, not characters');
select is(pg_temp.st(4), 'sent,sent,over_limit,over_limit', 'after the first over-limit line every older one is over the limit too');
select is(pg_temp.lines(4)->2->>'text', repeat('c', 300), 'an over-limit line keeps its full text (never truncated)');
select is(pg_temp.lines(1)->0->>'text', repeat('a', 1000), 'the newest line comes first, verbatim');
select is(pg_temp.lines(1)->0->>'kind', 'check_in', 'a line carries its kind');
select is(pg_temp.lines(1)->0->>'source_id', pg_temp.mon0()::text, 'a check-in line is identified by its week start');
select is(pg_temp.lines(1)->0->>'date', pg_temp.mon0()::text, 'and dated by it');

-- Line cap
select is(pg_temp.st(7), repeat('sent,', 20) || 'over_limit,over_limit', 'at most 20 lines are sent, the rest is over the limit');

-- Exclusions
select pg_temp.as_user(5);
select public.set_free_text_line_excluded('check_in', (pg_temp.mon0() - 7)::text, true);
select is(pg_temp.st(5), 'sent,excluded,sent', 'an excluded line does not consume bytes: the older line is sent');
select public.set_free_text_line_excluded('check_in', (pg_temp.mon0() - 7)::text, true);
select is((select count(*)::int from public.coach_free_text_exclusions), 1, 'excluding twice keeps one row');
select is((select notes from public.weekly_check_ins where week_start = pg_temp.mon0() - 7), repeat('b', 1000),
  'the source note stays intact');
select public.set_free_text_line_excluded('check_in', (pg_temp.mon0() - 7)::text, false);
select public.set_free_text_line_excluded('check_in', (pg_temp.mon0() - 7)::text, false);
select is(pg_temp.st(5), 'sent,sent,over_limit', 'including again restores the line (and the budget applies again)');
select is((select count(*)::int from public.coach_free_text_exclusions), 0, 'and removing twice is harmless');

-- Window and sources
select is(pg_temp.lines(6) @> '[{"text": "check-in dia 49 dentro"}]'::jsonb, true, 'a check-in on the first day of the window is in');
select is(pg_temp.lines(6) @> '[{"text": "check-in dia 56 fuera"}]'::jsonb, false, 'a check-in one week earlier is out');
select is(pg_temp.lines(6) @> '[{"text": "molestia dentro"}]'::jsonb, true, 'a discomfort note on the first day of the window is in');
select is(pg_temp.lines(6) @> '[{"text": "molestia fuera"}]'::jsonb, false, 'one day before the window it is out');
select is(pg_temp.lines(6) @> '[{"text": "molestia resuelta", "kind": "discomfort"}]'::jsonb, true, 'a resolved discomfort note is still a line');
select is(pg_temp.lines(6) @> '[{"text": "texto de conocerte antiguo", "kind": "intake", "source_id": "intake"}]'::jsonb, true,
  'the intake text is always in, whatever its age');
select ok(pg_temp.lines(6)::text !~ 'NOMBRE_SESION|DESCRIPCION_SESION|NOTA_SERIE|COMIDA_NOMBRE|EJERCICIO_CUSTOM',
  'set notes, session text, food names and custom exercise names are never lines');
select is((select x ->> 'kind' from jsonb_array_elements(pg_temp.lines(6)) with ordinality t(x, o)
           where x ->> 'text' in ('check-in misma fecha', 'molestia resuelta') order by o limit 1), 'check_in',
  'on the same date the kind breaks the tie (check_in before discomfort)');
select is(pg_temp.lines(9), ('[{"kind": "intake", "source_id": "intake", "date": "' || pg_temp.d0() || '", "text": "Entrené cuatro años", "status": "sent"}]')::jsonb,
  'the intake line has the whole shape');

-- Validation
select pg_temp.as_user(8);
select throws_ok($$select public.set_free_text_line_excluded('notes', 'x', true)$$, '22023', null, 'an unknown kind is refused');
select throws_ok($$select public.set_free_text_line_excluded('intake', '', true)$$, '22023', null, 'an empty id is refused');
select throws_ok(format($$select public.set_free_text_line_excluded('intake', %L, true)$$, repeat('a', 65)), '22023', null,
  'a 65-character id is refused');

-- Isolation
select is(pg_temp.lines(8), '[]'::jsonb, 'a user with no notes gets an empty list and never sees another user''s lines');
select throws_ok(format($$insert into public.coach_free_text_exclusions (user_id, kind, source_id) values (%L, 'intake', 'intake')$$, pg_temp.u(1)),
  '42501', null, 'a user cannot write an exclusion for another user');
select public.set_free_text_line_excluded('check_in', pg_temp.mon0()::text, true);
select is(pg_temp.st(1), 'sent,sent', 'another user''s exclusion has no effect on my lines');

select * from finish();
rollback;
