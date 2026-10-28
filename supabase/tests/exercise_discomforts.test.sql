-- pgTAP: exercise_discomforts (T-147, US-67).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(18);

insert into auth.users (id, email) values
  ('11111111-1111-4111-8111-111111111111', 'ana@example.com'),
  ('22222222-2222-4222-8222-222222222222', 'bea@example.com');

create temp table cat as
  select id from public.exercises where not is_custom order by key limit 1;
grant select on cat to authenticated;

insert into public.exercise_discomforts (id, user_id, exercise_id, note)
  values ('b1000000-0000-4000-8000-000000000001', '22222222-2222-4222-8222-222222222222', (select id from cat), 'de bea');

select ok(
  (select relrowsecurity and relforcerowsecurity from pg_class where oid = 'public.exercise_discomforts'::regclass),
  'RLS is enabled and forced on exercise_discomforts');
select policies_are('public', 'exercise_discomforts', array[
  'exercise_discomforts_select_own', 'exercise_discomforts_insert_own', 'exercise_discomforts_update_own'
], 'exercise_discomforts has the three owner policies and no delete policy');
select table_privs_are('public', 'exercise_discomforts', 'anon', array[]::text[], 'anon has no privileges');
select table_privs_are('public', 'exercise_discomforts', 'authenticated', array['SELECT', 'INSERT'],
  'authenticated has table-level select and insert only (update is column-level)');

set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

select lives_ok($$insert into public.exercise_discomforts (exercise_id, note, severe) values ((select id from cat), 'dolor fuerte', true)$$,
  'A logs a discomfort; user_id defaults to the caller');
select is((select user_id from public.exercise_discomforts where note = 'dolor fuerte'),
  '11111111-1111-4111-8111-111111111111'::uuid, 'the default user_id is auth.uid()');
select lives_ok($$insert into public.exercise_discomforts (exercise_id) values ((select id from cat))$$,
  'a discomfort without a note is accepted');
select lives_ok($$insert into public.exercise_discomforts (exercise_id, note) values ((select id from cat), repeat('a', 200))$$,
  'a 200-character note is accepted');
select throws_ok($$insert into public.exercise_discomforts (exercise_id, note) values ((select id from cat), repeat('a', 201))$$,
  '23514', null, 'a 201-character note is rejected');
select throws_ok($$insert into public.exercise_discomforts (exercise_id, note) values ((select id from cat), '')$$,
  '23514', null, 'an empty note is rejected');
select throws_ok(
  $$insert into public.exercise_discomforts (exercise_id, logged_at, resolved_at)
    values ((select id from cat), '2026-11-10 10:00+00', '2026-11-10 09:00+00')$$,
  '23514', null, 'resolved_at before logged_at is rejected');
select throws_ok($$insert into public.exercise_discomforts (user_id, exercise_id) values ('22222222-2222-4222-8222-222222222222', (select id from cat))$$,
  '42501', null, 'A cannot insert a row as B');
select is((select count(*)::int from public.exercise_discomforts where note = 'de bea'), 0, 'A cannot read B''s rows');

update public.exercise_discomforts set resolved_at = now() where id = 'b1000000-0000-4000-8000-000000000001';
select is((select count(*)::int from public.exercise_discomforts where resolved_at is not null), 0,
  'A update on B''s row changes nothing');
select lives_ok($$update public.exercise_discomforts set resolved_at = now() where note = 'dolor fuerte'$$,
  'A resolves own discomfort');
select throws_ok($$update public.exercise_discomforts set note = 'otra' where note = 'dolor fuerte'$$,
  '42501', null, 'note is immutable (column grant)');
select throws_ok($$delete from public.exercise_discomforts$$, '42501', null, 'delete is denied');

reset role;
select is((select resolved_at is null from public.exercise_discomforts where id = 'b1000000-0000-4000-8000-000000000001'),
  true, 'B''s row stays unresolved');

select * from finish();
rollback;
