-- T-254 (E17, US-119): every OFF food already used (diary, favourites, saved comidas) becomes its owner's personal copy.
-- Entry snapshots (food_name, kcal, macros, amount, portion_label) are never written, so no past total changes.
-- The self-check raises on any mismatch: the whole migration (one transaction) aborts and production data stays as it was.
-- The logic lives in a function so pgTAP exercises the exact code that runs here; it stays locked down afterwards.

create function public.convert_used_off_foods() returns void
  language plpgsql
  set search_path = ''
as $$
declare
  v_pair record;
  v_copy uuid;
  v_map jsonb := '[]'::jsonb;
  v_before_log jsonb;
  v_before_tpl jsonb;
begin

  -- Before: per (user, day) totals and counts; per user template item counts.
  select coalesce(jsonb_agg(to_jsonb(b)), '[]'::jsonb) into v_before_log
  from (
    select user_id, log_date, sum(kcal) as kcal, sum(protein_g) as protein_g, sum(carbs_g) as carbs_g,
           sum(fat_g) as fat_g, count(*) as n
    from public.food_log_entries
    group by user_id, log_date
  ) b;
  select coalesce(jsonb_agg(to_jsonb(b)), '[]'::jsonb) into v_before_tpl
  from (
    select t.user_id, count(*) as n
    from public.meal_template_items i join public.meal_templates t on t.id = i.template_id
    group by t.user_id
  ) b;

  -- One copy per (user, OFF food) in use; reuse inside the core makes reruns and shared barcodes idempotent.
  for v_pair in
    select distinct u.user_id, u.food_id
    from (
      select l.user_id, l.food_id from public.food_log_entries l
      union
      select ff.user_id, ff.food_id from public.favorite_foods ff
      union
      select t.user_id, i.food_id
      from public.meal_template_items i join public.meal_templates t on t.id = i.template_id
    ) u
    join public.foods f on f.id = u.food_id and f.source = 'off'
  loop
    v_copy := public.copy_off_food_core(v_pair.user_id, v_pair.food_id);
    v_map := v_map || jsonb_build_array(jsonb_build_object(
      'user_id', v_pair.user_id, 'off_id', v_pair.food_id, 'copy_id', v_copy));
  end loop;

  -- Repoint, always scoped by the same user.
  update public.food_log_entries l
  set food_id = m.copy_id
  from jsonb_to_recordset(v_map) as m(user_id uuid, off_id uuid, copy_id uuid)
  where l.user_id = m.user_id and l.food_id = m.off_id;

  update public.meal_template_items i
  set food_id = m.copy_id
  from public.meal_templates t, jsonb_to_recordset(v_map) as m(user_id uuid, off_id uuid, copy_id uuid)
  where t.id = i.template_id and m.user_id = t.user_id and i.food_id = m.off_id;

  delete from public.favorite_foods ff
  using jsonb_to_recordset(v_map) as m(user_id uuid, off_id uuid, copy_id uuid)
  where ff.user_id = m.user_id and ff.food_id = m.off_id
    and exists (select 1 from public.favorite_foods f2 where f2.user_id = m.user_id and f2.food_id = m.copy_id);
  update public.favorite_foods ff
  set food_id = m.copy_id
  from jsonb_to_recordset(v_map) as m(user_id uuid, off_id uuid, copy_id uuid)
  where ff.user_id = m.user_id and ff.food_id = m.off_id;

  -- Self-check.
  if exists (
    select 1
    from jsonb_to_recordset(v_before_log) as b(user_id uuid, log_date date, kcal numeric, protein_g numeric, carbs_g numeric, fat_g numeric, n bigint)
    full join (
      select user_id, log_date, sum(kcal) as kcal, sum(protein_g) as protein_g, sum(carbs_g) as carbs_g,
             sum(fat_g) as fat_g, count(*) as n
      from public.food_log_entries group by user_id, log_date
    ) a using (user_id, log_date)
    where (b.kcal, b.protein_g, b.carbs_g, b.fat_g, b.n) is distinct from (a.kcal, a.protein_g, a.carbs_g, a.fat_g, a.n)
  ) then
    raise exception 'convert_used_off_foods: a diary total or entry count changed';
  end if;

  if exists (
    select 1
    from jsonb_to_recordset(v_before_tpl) as b(user_id uuid, n bigint)
    full join (
      select t.user_id, count(*) as n
      from public.meal_template_items i join public.meal_templates t on t.id = i.template_id
      group by t.user_id
    ) a using (user_id)
    where b.n is distinct from a.n
  ) then
    raise exception 'convert_used_off_foods: a comida item count changed';
  end if;

  if exists (select 1 from public.food_log_entries l join public.foods f on f.id = l.food_id where f.source = 'off')
     or exists (select 1 from public.favorite_foods ff join public.foods f on f.id = ff.food_id where f.source = 'off')
     or exists (select 1 from public.meal_template_items i join public.foods f on f.id = i.food_id where f.source = 'off')
  then
    raise exception 'convert_used_off_foods: a reference to an OFF food remains';
  end if;

  if exists (
    select 1 from public.food_log_entries l
    join jsonb_to_recordset(v_map) as m(user_id uuid, off_id uuid, copy_id uuid) on m.copy_id = l.food_id
    join public.foods f on f.id = l.food_id
    where l.user_id is distinct from f.created_by
  ) or exists (
    select 1 from public.favorite_foods ff
    join jsonb_to_recordset(v_map) as m(user_id uuid, off_id uuid, copy_id uuid) on m.copy_id = ff.food_id
    join public.foods f on f.id = ff.food_id
    where ff.user_id is distinct from f.created_by
  ) or exists (
    select 1 from public.meal_template_items i
    join public.meal_templates t on t.id = i.template_id
    join jsonb_to_recordset(v_map) as m(user_id uuid, off_id uuid, copy_id uuid) on m.copy_id = i.food_id
    join public.foods f on f.id = i.food_id
    where t.user_id is distinct from f.created_by
  ) then
    raise exception 'convert_used_off_foods: a reference points to another user''s food';
  end if;

end;
$$;

revoke all on function public.convert_used_off_foods() from public, anon, authenticated;

select public.convert_used_off_foods();
