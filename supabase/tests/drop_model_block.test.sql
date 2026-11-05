-- T-224 (ADR-99): the objects of the model-built block are gone; the ones the builder still needs remain.
begin;
select plan(8);

select is(to_regprocedure('public.coach_block_model_input(date)'), null, 'coach_block_model_input is dropped');
select is(to_regprocedure('public.save_folder_review(uuid,text,uuid,text,jsonb)'), null, 'save_folder_review is dropped');
select is(to_regprocedure('public.coach_folder_for_review(uuid)'), null, 'coach_folder_for_review is dropped');
select is(to_regclass('public.coach_folder_reviews'), null, 'coach_folder_reviews is dropped');

select isnt(
  (select count(*) from pg_proc p join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public' and p.proname = 'coach_assert_block'), 0::bigint, 'coach_assert_block stays');
select isnt(
  (select count(*) from pg_proc p join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public' and p.proname = 'coach_block_rep_ok'), 0::bigint, 'coach_block_rep_ok stays');
select isnt(
  (select count(*) from pg_proc p join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public' and p.proname = 'save_built_coach_block'), 0::bigint, 'save_built_coach_block stays');
select isnt(
  (select count(*) from pg_proc p join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public' and p.proname = 'unresolved_severe_discomforts'), 0::bigint, 'unresolved_severe_discomforts stays');

select * from finish();
rollback;
