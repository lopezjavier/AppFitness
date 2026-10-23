-- pgTAP: new objects in public created by the migration owner are not reachable by anon (T-097b).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

-- This test runs as postgres, the role that owns migration-created objects, so its default privileges apply.
select plan(14);

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

-- New objects stay usable by the app roles.
select ok(has_table_privilege('authenticated', 'public.zz_default_acl_probe', 'select, insert, update, delete'),
  'authenticated gets full DML on a newly created table');
select ok(has_table_privilege('service_role', 'public.zz_default_acl_probe', 'select, insert, update, delete'),
  'service_role gets full DML on a newly created table');
select ok(has_function_privilege('authenticated', 'public.zz_default_acl_probe_fn()', 'execute'),
  'authenticated can execute a newly created function');
select ok(has_function_privilege('service_role', 'public.zz_default_acl_probe_fn()', 'execute'),
  'service_role can execute a newly created function');
select ok(has_sequence_privilege('authenticated', 'public.zz_default_acl_probe_seq', 'usage'),
  'authenticated can use a newly created sequence');
select ok(has_sequence_privilege('service_role', 'public.zz_default_acl_probe_seq', 'usage'),
  'service_role can use a newly created sequence');

-- Documented side effect: postgres-created functions outside public also lose implicit PUBLIC execute.
create schema zz_probe_schema;
create function zz_probe_schema.fn() returns int language sql as 'select 1';
select ok(not has_function_privilege('anon', 'zz_probe_schema.fn()', 'execute'),
  'functions created outside public get no implicit PUBLIC execute');

select * from finish();
rollback;
