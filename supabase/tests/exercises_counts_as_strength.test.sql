-- pgTAP: exercises.counts_as_strength, the flag behind the coach's H1 count (T-211, 07 section 3 "What H1 counts").
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(14);

insert into auth.users (id, email) values ('11111111-1111-4111-8111-111111111111', 'ana@example.com');
insert into public.exercises (id, name, muscle_group, equipment, metric_type, is_custom, created_by) values
  ('a1200000-0000-4000-8000-000000000001', 'Mi plancha', 'core', 'bodyweight', 'duration', true,
   '11111111-1111-4111-8111-111111111111');

create function pg_temp.flag(p_key text) returns boolean language sql as
$$ select counts_as_strength from public.exercises where key = p_key and not is_custom $$;

select col_not_null('public', 'exercises', 'counts_as_strength', 'the flag is never null');
select col_default_is('public', 'exercises', 'counts_as_strength', 'true', 'and defaults to true');

select is(pg_temp.flag('squat'), true, 'a barbell squat counts');
select is(pg_temp.flag('bench-press'), true, 'a bench press counts');
select is(pg_temp.flag('glute-bridge'), true, 'a bodyweight glute bridge counts');
select is(pg_temp.flag('clamshells'), true, 'banded clamshells count');

select is(pg_temp.flag('squat-hold'), false, 'a squat hold does not count');
select is(pg_temp.flag('stationary-bike'), false, 'a cardio machine row does not count');
select is(pg_temp.flag('plank'), false, 'a timed hold does not count');
select is(pg_temp.flag('wall-sit'), false, 'a wall sit does not count (stays quads, adds 0 to H1)');
select is(pg_temp.flag('jump-squat'), false, 'a plyometric move does not count');
select is(pg_temp.flag('cat-cow'), false, 'a mobility flow does not count');

select is((select count(*)::int from public.exercises
           where not is_custom and metric_type in ('duration', 'weight_duration', 'distance_duration',
                                                    'floors_duration', 'steps_duration') and counts_as_strength), 0,
  'no timed or distance catalogue key counts');
select is((select counts_as_strength from public.exercises where name = 'Mi plancha'), true,
  'a custom row keeps the default (the coach decides it by metric type)');

select * from finish();
rollback;
