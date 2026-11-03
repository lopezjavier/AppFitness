-- T-205 (E13, E13 Q56, ADR-93): the "minimal" phase ("Tengo poco tiempo") is removed. Nothing can select it any more.
-- A stored minimal plan becomes maintenance: phase column, phase_reason and the JSON mirrors (nutrition, training,
-- the pending recommendations); calories, macros and the saved block stay as they were. Its cardio target returns to
-- the maintenance default of 150 min/week unless the user edited it. Postgres cannot drop an enum value, so the type is
-- recreated (as 20261222100000 did for regain). Bases (never edited): 20261220100000, 20261222100000.

-- (1) Backfill. The coach_plans_default_phase trigger is before insert only, so these updates are not rewritten.
update public.coach_plans
set nutrition = case when nutrition->>'phase' = 'minimal'
                     then jsonb_set(nutrition, '{phase}', '"maintenance"') else nutrition end,
    training = case when training->>'phase' = 'minimal'
                    then jsonb_set(training, '{phase}', '"maintenance"') else training end
where nutrition->>'phase' = 'minimal' or training->>'phase' = 'minimal';

update public.coach_plans
set nutrition = case
      when jsonb_typeof(nutrition->'activity') = 'object'
           and coalesce((nutrition->'activity'->>'cardio_edited')::boolean, false) = false
      then jsonb_set(nutrition, '{activity,cardio_min_week}', '150')
      else nutrition end,
    phase = 'maintenance',
    phase_reason = 'intention'
where phase = 'minimal';

update public.coach_plans set phase_reason = 'intention' where phase_reason = 'user_busy';
update public.coach_plans
set nutrition = jsonb_set(nutrition, '{phase_reason}', '"intention"')
where nutrition->>'phase_reason' = 'user_busy';

update public.coach_recommendations
set payload = case
      when payload #>> '{nutrition,phase}' = 'minimal' then jsonb_set(payload, '{nutrition,phase}', '"maintenance"')
      else payload end
where payload #>> '{nutrition,phase}' = 'minimal';
update public.coach_recommendations
set payload = jsonb_set(payload, '{training,phase}', '"maintenance"')
where payload #>> '{training,phase}' = 'minimal';

alter table public.coach_plans drop constraint coach_plans_phase_reason_values;
alter table public.coach_plans
  add constraint coach_plans_phase_reason_values check (phase_reason in ('intention'));

-- (2) Enum without minimal (no index, policy or default depends on the column; plpgsql bodies cast by name).
alter type public.coach_phase rename to coach_phase_old;
create type public.coach_phase as enum ('cut', 'gain', 'maintenance', 'recomposition');
alter table public.coach_plans
  alter column phase type public.coach_phase using phase::text::public.coach_phase;
drop type public.coach_phase_old;

-- (3) Default phase trigger without the user_busy reason.
create or replace function public.coach_plans_default_phase()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  if new.phase is null then
    new.phase := (case new.intention
                    when 'fat_loss' then 'cut'
                    when 'muscle' then 'gain'
                    when 'recomposition' then 'recomposition'
                    else 'maintenance' end)::public.coach_phase;
  end if;
  if new.phase_reason is null then
    new.phase_reason := 'intention';
  end if;
  return new;
end;
$$;

-- (4) coach_assert_plan: only its phase list (and one comment) mentions minimal. Patch the stored definition and fail
-- loudly if the text is not found.
do $patch$
declare
  v_def text;
  v_new text;
begin
  v_def := pg_get_functiondef('public.coach_assert_plan(uuid, public.training_intention, date, jsonb, jsonb)'::regprocedure);
  v_new := replace(v_def, '''recomposition'', ''minimal''', '''recomposition''');
  v_new := replace(v_new, 'Maintenance, recomposition and minimal:', 'Maintenance and recomposition:');
  v_new := replace(v_new, '''intention'', ''user_busy''', '''intention''');
  if v_new = v_def or v_new like '%minimal%' or v_new like '%user_busy%' then
    raise exception 'coach_assert_plan patch did not apply';
  end if;
  execute v_new;
end
$patch$;

revoke execute on function public.coach_assert_plan(uuid, public.training_intention, date, jsonb, jsonb)
  from public, anon, authenticated;

-- (5) coach_phase_history: the block builder no longer reads minimal blocks; the field stays at 0 for old clients.
create or replace function public.coach_phase_history(p_today date)
returns jsonb
language plpgsql
stable
security invoker
set search_path = ''
as $$
declare
  v_uid     uuid := public.coach_guard(p_today);
  v_prev    public.coach_plans;
  v_main    jsonb;
  v_ranges  jsonb;
  v_deficit integer := 0;
  v_row     record;
begin
  select p.* into v_prev
  from public.coach_plans p
  where p.user_id = v_uid and p.status in ('superseded', 'accepted')
    and exists (select 1 from public.coach_plan_sessions s where s.plan_id = p.id)
  order by p.block_start desc, p.created_at desc
  limit 1;
  if not found then
    return jsonb_build_object('previous', null, 'minimal_blocks_in_a_row', 0, 'deficit_weeks_done', 0);
  end if;

  select coalesce(jsonb_agg(x.id order by x.first_pos), '[]'::jsonb) into v_main
  from (
    select e.value->>'exercise_id' as id, min(s.session_index * 100 + e.ordinality) as first_pos
    from public.coach_plan_sessions s
    cross join lateral jsonb_array_elements(s.exercises) with ordinality e(value, ordinality)
    where s.plan_id = v_prev.id and s.week = 1 and e.value->>'role' = 'main'
    group by e.value->>'exercise_id'
  ) x;

  select coalesce(jsonb_object_agg(r.id, jsonb_build_array(r.rep_min, r.rep_max)), '{}'::jsonb) into v_ranges
  from (
    select distinct on (e.value->>'exercise_id')
           e.value->>'exercise_id' as id, (e.value->>'rep_min')::int as rep_min, (e.value->>'rep_max')::int as rep_max
    from public.coach_plan_sessions s
    cross join lateral jsonb_array_elements(s.exercises) with ordinality e(value, ordinality)
    where s.plan_id = v_prev.id and s.week = 1 and e.value->>'role' in ('main', 'isolation')
    order by e.value->>'exercise_id', s.session_index, e.ordinality
  ) r;

  for v_row in
    select coalesce(p.nutrition->>'phase', p.phase::text) as calorie_phase,
           coalesce((p.phase_options->>'diet_break')::boolean, false) as diet_break
    from public.coach_plans p
    where p.user_id = v_uid and p.status in ('superseded', 'accepted')
      and exists (select 1 from public.coach_plan_sessions s where s.plan_id = p.id)
    order by p.block_start desc, p.created_at desc
  loop
    exit when v_row.calorie_phase <> 'cut';
    v_deficit := v_deficit + case when v_row.diet_break then 3 else 4 end;
  end loop;

  return jsonb_build_object(
    'previous', jsonb_build_object(
      'phase', v_prev.phase::text,
      'sets_per_group', case when jsonb_typeof(v_prev.training->'weekly_sets') = 'object'
                             then v_prev.training->'weekly_sets' else '{}'::jsonb end,
      'main_lifts', v_main,
      'rep_ranges', v_ranges),
    'minimal_blocks_in_a_row', 0,
    'deficit_weeks_done', v_deficit);
end;
$$;

-- (6) attach_coach_block: as 20261220100000 without the minimal branch. The block restores the phase of the plan JSON;
-- a block with cardio keeps nutrition.activity.cardio_min_week at the block's target unless the user edited it.
create or replace function public.attach_coach_block(
  p_plan_id uuid,
  p_training jsonb,
  p_sessions jsonb,
  p_method jsonb,
  p_input_summary jsonb
)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_uid   uuid := public.coach_guard((now() at time zone 'utc')::date);
  v_plan  public.coach_plans;
  v_ids   jsonb;
begin
  if p_plan_id is null or p_training is null or jsonb_typeof(p_training) <> 'object'
     or p_sessions is null or jsonb_typeof(p_sessions) <> 'array'
     or p_method is null or jsonb_typeof(p_method) <> 'object'
     or p_input_summary is null then
    raise exception 'invalid block' using errcode = '22023';
  end if;

  select * into v_plan from public.coach_plans where id = p_plan_id and user_id = v_uid for update;
  if not found then
    raise exception 'plan not found' using errcode = 'P0002';
  end if;
  if v_plan.status not in ('proposed', 'accepted') then
    raise exception 'plan is not open' using errcode = '55000';
  end if;
  if v_plan.folder_id is not null
     or exists (select 1 from public.coach_plan_sessions s
                where s.plan_id = p_plan_id and s.user_id = v_uid and s.routine_id is not null) then
    raise exception 'block already saved' using errcode = '55000';
  end if;

  perform public.coach_assert_block(v_uid, p_training, p_sessions);

  delete from public.coach_plan_sessions where plan_id = p_plan_id and user_id = v_uid;
  update public.coach_recommendations
    set status = 'discarded', decided_at = now()
    where plan_id = p_plan_id and user_id = v_uid and kind = 'block' and status = 'pending';

  update public.coach_plans
    set training = p_training,
        phase = coalesce((nutrition->>'phase')::public.coach_phase, phase),
        phase_reason = coalesce(nutrition->>'phase_reason', phase_reason),
        nutrition = case
          when jsonb_typeof(nutrition->'activity') = 'object'
               and coalesce((nutrition->'activity'->>'cardio_edited')::boolean, false) = false
               and jsonb_typeof(p_training->'cardio'->'target_min_week') = 'number'
          then jsonb_set(nutrition, '{activity,cardio_min_week}', p_training->'cardio'->'target_min_week')
          else nutrition end
    where id = p_plan_id and user_id = v_uid;

  insert into public.coach_plan_sessions (plan_id, user_id, week, session_index, name, focus, exercises)
  select p_plan_id, v_uid, w.n::smallint, s.ordinality::smallint, btrim(s.value->>'name'),
         coalesce((select array_agg(f.value) from jsonb_array_elements_text(coalesce(s.value->'focus', '[]'::jsonb)) f(value)), '{}'),
         s.value->'exercises'
  from jsonb_array_elements(p_sessions) with ordinality s(value, ordinality)
  cross join generate_series(1, (p_training->>'block_weeks')::int) w(n);

  insert into public.coach_recommendations (user_id, plan_id, kind, payload, input_summary)
  values (v_uid, p_plan_id, 'block',
          jsonb_build_object('training', p_training, 'sessions', p_sessions, 'method', p_method),
          p_input_summary);

  select coalesce(jsonb_agg(s.id order by s.week, s.session_index), '[]'::jsonb) into v_ids
  from public.coach_plan_sessions s where s.plan_id = p_plan_id and s.user_id = v_uid;

  return jsonb_build_object('plan_id', p_plan_id, 'sessions', v_ids);
end;
$$;

revoke execute on function public.attach_coach_block(uuid, jsonb, jsonb, jsonb, jsonb) from public, anon;
grant execute on function public.attach_coach_block(uuid, jsonb, jsonb, jsonb, jsonb) to authenticated;
revoke execute on function public.coach_phase_history(date) from public, anon;
grant execute on function public.coach_phase_history(date) to authenticated;
