-- pgTAP: the usage ledger and its budget RPCs are gone (T-259, ADR-103); nothing else changes.
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(7);

select hasnt_table('public', 'coach_usage', 'coach_usage no longer exists');
select hasnt_function('public', 'coach_record_usage', 'coach_record_usage no longer exists');
select hasnt_function('public', 'coach_global_month_cost', 'coach_global_month_cost no longer exists');
select is(to_regclass('public.coach_usage'), null, 'the relation name resolves to nothing');

-- Other tables keep working: a user with rows elsewhere is created, counted and deleted with its data.
insert into auth.users (id, email) values ('f9000000-0000-4000-8000-000000000001', 'drop-usage@example.com');
insert into public.coach_confirmed_values (user_id, key, estimate, uncertainty) values
  ('f9000000-0000-4000-8000-000000000001', 'experience', 0, 0.5);

select is((select count(*)::int from public.profiles where id = 'f9000000-0000-4000-8000-000000000001'), 1,
  'the profile row is created');
select is((select count(*)::int from public.coach_confirmed_values where user_id = 'f9000000-0000-4000-8000-000000000001'), 1,
  'another coach table still stores rows');

delete from auth.users where id = 'f9000000-0000-4000-8000-000000000001';
select is((select count(*)::int from public.coach_confirmed_values where user_id = 'f9000000-0000-4000-8000-000000000001'), 0,
  'deleting the user still removes their data');

select * from finish();
rollback;
