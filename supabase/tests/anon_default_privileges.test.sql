-- pgTAP: new objects in public created by the migration owner are not reachable by anon (T-097b).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(7);

create table public.zz_default_acl_probe (id int);
create function public.zz_default_acl_probe_fn() returns int language sql as 'select 1';
create sequence public.zz_default_acl_probe_seq;

select ok(not has_table_privilege('anon', 'public.zz_default_acl_probe', 'select'),
  'anon cannot select a newly created table');
select ok(not has_table_privilege('anon', 'public.zz_default_acl_probe', 'insert'),
  'anon cannot insert into a newly created table');
select ok(not has_function_privilege('anon', 'public.zz_default_acl_probe_fn()', 'execute'),
  'anon cannot execute a newly created function');
select ok(not has_sequence_privilege('anon', 'public.zz_default_acl_probe_seq', 'usage'),
  'anon cannot use a newly created sequence');

select ok(has_table_privilege('authenticated', 'public.profiles', 'select'),
  'authenticated keeps select on existing tables');
select ok(has_function_privilege('anon', 'public.immutable_unaccent(text)', 'execute'),
  'anon keeps execute on the existing immutable helper');
select ok(has_function_privilege('authenticated', 'public.immutable_unaccent(text)', 'execute'),
  'authenticated keeps execute on the existing immutable helper');

select * from finish();
rollback;
