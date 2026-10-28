-- pgTAP: exercise_discomforts (T-147, US-67; level scale T-152, ADR-81).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(28);

insert into auth.users (id, email) values
  ('11111111-1111-4111-8111-111111111111', 'ana@example.com'),
  ('22222222-2222-4222-8222-222222222222', 'bea@example.com');

create temp table cat as
  select id from public.exercises where not is_custom order by key limit 1;
grant select on cat to authenticated;

insert into public.exercise_discomforts (id, user_id, exercise_id, level, note)
  values ('b1000000-0000-4000-8000-000000000001', '22222222-2222-4222-8222-222222222222', (select id from cat), 3, 'de bea');

select ok(
  (select relrowsecurity and relforcerowsecurity from pg_class where oid = 'public.exercise_discomforts'::regclass),
  'RLS is enabled and forced on exercise_discomforts');
select policies_are('public', 'exercise_discomforts', array[
  'exercise_discomforts_select_own', 'exercise_discomforts_insert_own', 'exercise_discomforts_update_own'
], 'exercise_discomforts has the three owner policies and no delete policy');
select table_privs_are('public', 'exercise_discomforts', 'anon', array[]::text[], 'anon has no privileges');
select table_privs_are('public', 'exercise_discomforts', 'authenticated', array['SELECT', 'INSERT'],
  'authenticated has table-level select and insert only (update is column-level)');
select hasnt_column('public', 'exercise_discomforts', 'severe', 'the severe column no longer exists');
select col_not_null('public', 'exercise_discomforts', 'level', 'level is required');

-- Migration mapping (severe true -> 5, false -> 2), run on a scratch table.
create temp table scratch_old (severe boolean not null, level smallint);
insert into scratch_old (severe) values (true), (false);
update scratch_old set level = case when severe then 5 else 2 end;
select is((select level from scratch_old where severe), 5::smallint, 'old severe rows map to level 5');
select is((select level from scratch_old where not severe), 2::smallint, 'old non-severe rows map to level 2');

set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

select lives_ok($$insert into public.exercise_discomforts (exercise_id, level, note) values ((select id from cat), 5, 'dolor fuerte')$$,
  'A logs a discomfort; user_id defaults to the caller');
select is((select user_id from public.exercise_discomforts where note = 'dolor fuerte'),
  '11111111-1111-4111-8111-111111111111'::uuid, 'the default user_id is auth.uid()');
select is((select level from public.exercise_discomforts where note = 'dolor fuerte'), 5::smallint,
  'the level is stored as sent; the note text is never interpreted');
select lives_ok($$insert into public.exercise_discomforts (exercise_id, level) values ((select id from cat), 1)$$,
  'level 1 without a note is accepted');
select lives_ok($$insert into public.exercise_discomforts (exercise_id, level) values ((select id from cat), 4)$$,
  'level 4 is accepted');
select throws_ok($$insert into public.exercise_discomforts (exercise_id, level) values ((select id from cat), 0)$$,
  '23514', null, 'level 0 is rejected');
select throws_ok($$insert into public.exercise_discomforts (exercise_id, level) values ((select id from cat), 6)$$,
  '23514', null, 'level 6 is rejected');
select throws_ok($$insert into public.exercise_discomforts (exercise_id) values ((select id from cat))$$,
  '23502', null, 'a missing level is rejected');
select lives_ok($$insert into public.exercise_discomforts (exercise_id, level, note) values ((select id from cat), 2, repeat('a', 200))$$,
  'a 200-character note is accepted');
select throws_ok($$insert into public.exercise_discomforts (exercise_id, level, note) values ((select id from cat), 2, repeat('a', 201))$$,
  '23514', null, 'a 201-character note is rejected');
select throws_ok($$insert into public.exercise_discomforts (exercise_id, level, note) values ((select id from cat), 2, '')$$,
  '23514', null, 'an empty note is rejected');
select throws_ok(
  $$insert into public.exercise_discomforts (exercise_id, level, logged_at, resolved_at)
    values ((select id from cat), 2, '2026-11-10 10:00+00', '2026-11-10 09:00+00')$$,
  '23514', null, 'resolved_at before logged_at is rejected');
select throws_ok($$insert into public.exercise_discomforts (user_id, exercise_id, level) values ('22222222-2222-4222-8222-222222222222', (select id from cat), 2)$$,
  '42501', null, 'A cannot insert a row as B');
select is((select count(*)::int from public.exercise_discomforts where note = 'de bea'), 0, 'A cannot read B''s rows');

update public.exercise_discomforts set resolved_at = now() where id = 'b1000000-0000-4000-8000-000000000001';
select is((select count(*)::int from public.exercise_discomforts where resolved_at is not null), 0,
  'A update on B''s row changes nothing');
select lives_ok($$update public.exercise_discomforts set resolved_at = now() where note = 'dolor fuerte'$$,
  'A resolves own discomfort');
select throws_ok($$update public.exercise_discomforts set note = 'otra' where note = 'dolor fuerte'$$,
  '42501', null, 'note is immutable (column grant)');
select throws_ok($$update public.exercise_discomforts set level = 1 where note = 'dolor fuerte'$$,
  '42501', null, 'level is immutable (column grant)');
select throws_ok($$delete from public.exercise_discomforts$$, '42501', null, 'delete is denied');

reset role;
select is((select resolved_at is null from public.exercise_discomforts where id = 'b1000000-0000-4000-8000-000000000001'),
  true, 'B''s row stays unresolved');

select * from finish();
rollback;
