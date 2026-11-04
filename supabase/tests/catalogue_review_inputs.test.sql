-- pgTAP: T-216 inputs the deterministic folder review reads from the catalogue and from custom exercises.
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(7);

select is((select count(*)::int from public.exercises where not is_custom and muscle_group is null), 0,
  'every catalogue row has a primary group');
select is((select count(*)::int from public.exercises
           where not is_custom and movement_pattern in ('squat', 'hinge', 'lunge', 'horizontal_push', 'vertical_push',
                                                        'horizontal_pull', 'vertical_pull') and not counts_as_strength
             and metric_type not in ('duration', 'weight_duration')
             and key not in ('box-jump', 'jump-squat', 'plyo-lunge')), 0,
  'every main-pattern catalogue row counts for H1 except timed holds and plyometrics');
select is((select count(*)::int from public.exercises
           where not is_custom and counts_as_strength
             and (movement_pattern = 'cardio' or metric_type in ('duration', 'weight_duration', 'distance_duration',
                                                                'floors_duration', 'steps_duration'))), 0,
  'no counting row is cardio or a hold');
select is((select count(*)::int from public.exercises
           where not is_custom and counts_as_strength and movement_pattern in ('horizontal_push', 'vertical_push')
             and muscle_group in ('chest', 'shoulders', 'triceps') and cardinality(secondary_muscles) = 0), 0,
  'every counting press has secondary muscles');
select is((select count(*)::int from public.exercises
           where not is_custom and counts_as_strength and movement_pattern in ('hinge', 'lunge', 'squat')
             and cardinality(secondary_muscles) = 0), 0, 'every counting squat, hinge and lunge has secondary muscles');

-- Custom insert defaults: counting flag true, no secondaries, no pattern.
insert into auth.users (id, email) values ('d8000000-0000-4000-8000-000000000001', 'cri@example.com');
insert into public.exercises (created_by, name, muscle_group, is_custom, equipment)
values ('d8000000-0000-4000-8000-000000000001', 'Mi ejercicio', 'chest', true, 'bodyweight');
select is((select counts_as_strength from public.exercises where name = 'Mi ejercicio'), true,
  'a custom exercise counts by default');
select ok((select secondary_muscles = '{}' and movement_pattern is null from public.exercises where name = 'Mi ejercicio'),
  'a custom exercise has no secondaries and no pattern by default');

select * from finish();
rollback;
