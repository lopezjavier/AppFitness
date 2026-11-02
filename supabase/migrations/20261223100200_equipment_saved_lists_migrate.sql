-- T-201 (ADR-92, planning/07-coach-design.md section 3 "Equipment items" and "Cardio equipment"): move the saved
-- equipment lists to the new items.
--   * the five cardio values of T-184 collapse into one `cardio_machine` (deduplicated, order kept);
--   * `ez_bar` is added only to a gym list or a list holding every old lifting value (barbell, dumbbell, machine,
--     cable, kettlebell, bodyweight, band, band_long; `other` is not required: "Otro" was never part of that set),
--     and never to an empty list (empty = everything); nobody gains `other`.
-- The rule lives in one function so pgTAP can test it on every case. Lists are plain text arrays so the same function
-- serves `training_preferences.equipment` and the jsonb lists stored with a coach plan.

create function public.migrate_equipment_list(p_list text[], p_place text)
returns text[]
language sql
immutable
security invoker
set search_path = ''
as $$
  with src as (
    select e.value, e.ord from unnest(coalesce(p_list, '{}'::text[])) with ordinality e(value, ord)
  ),
  mapped as (
    select case when value in ('cardio_bike', 'treadmill', 'elliptical', 'rower', 'cardio_other')
                then 'cardio_machine' else value end as value, ord
    from src
  ),
  dedup as (
    select value, min(ord) as ord from mapped group by value
  ),
  base as (
    select coalesce(array_agg(value order by ord), '{}'::text[]) as list from dedup
  )
  select case
           when cardinality(b.list) > 0
                and not ('ez_bar' = any (b.list))
                and (p_place = 'gym'
                     or b.list @> array['barbell', 'dumbbell', 'machine', 'cable', 'kettlebell', 'bodyweight',
                                        'band', 'band_long']::text[])
             then b.list || array['ez_bar']::text[]
           else b.list
         end
  from base b
$$;

revoke execute on function public.migrate_equipment_list(text[], text) from public, anon;
grant execute on function public.migrate_equipment_list(text[], text) to authenticated;

-- (a) Saved lists.
update public.training_preferences tp
set equipment = public.migrate_equipment_list(tp.equipment::text[], tp.place::text)::public.equipment[]
where tp.equipment && array['cardio_bike', 'treadmill', 'elliptical', 'rower', 'cardio_other']::public.equipment[]
   or (cardinality(tp.equipment) > 0 and not (tp.equipment @> array['ez_bar']::public.equipment[])
       and (tp.place = 'gym'
            or tp.equipment @> array['barbell', 'dumbbell', 'machine', 'cable', 'kettlebell', 'bodyweight', 'band',
                                     'band_long']::public.equipment[]));

-- (b) The list stored with each coach plan (`coach_plans.training->'equipment'`), other keys untouched. The place is the
-- user's saved place (the plan does not store one). Only plans that hold a non-empty array are rewritten.
update public.coach_plans p
set training = jsonb_set(
  p.training, '{equipment}',
  to_jsonb(public.migrate_equipment_list(
    array(select jsonb_array_elements_text(p.training->'equipment')),
    (select tp.place::text from public.training_preferences tp where tp.user_id = p.user_id))))
where jsonb_typeof(p.training->'equipment') = 'array'
  and jsonb_array_length(p.training->'equipment') > 0;

-- No new write may hold the five old values (they stay in the type, unused). Every row passes after (a).
alter table public.training_preferences
  add constraint training_preferences_no_old_cardio_values
  check (not (equipment && array['cardio_bike', 'treadmill', 'elliptical', 'rower', 'cardio_other']::public.equipment[]));
