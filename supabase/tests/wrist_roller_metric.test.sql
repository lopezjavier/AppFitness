-- pgTAP: wrist roller catalogue exercise logs weight x reps (T-156).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(1);

select is(
  (select metric_type from public.exercises where not is_custom and key = 'wrist-roller'),
  'weight_reps',
  'the wrist roller takes a weight and reps');

select * from finish();
rollback;
