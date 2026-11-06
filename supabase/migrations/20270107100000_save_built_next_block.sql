-- T-235 (E14, US-102): saving the next block built from the end-of-block review starts it and ends the previous one.
-- Until now only accept_coach_plan superseded the accepted plan, and the builder save never accepted anything.
--   (1) coach_begin_next_block: internal step (same app.coach_block_save guard as coach_link_block) that makes the plan
--       of the next block the accepted one and anchors its week 1 on today (training_start; coach_link_block re-bases
--       it to the save day).
--         - target plan different from the previous one and proposed (review "Aceptar"): accept_coach_plan, which
--           supersedes the accepted plan and applies the nutrition part;
--         - target plan different and already accepted: nothing to end, only the week anchor;
--         - target plan equal to the previous accepted plan (review "Seguir como estoy": there is no new plan): the
--           previous plan is superseded and a new accepted plan is created with the same intention, nutrition, outcomes
--           and phase, starting today. nutrition_goals do not change (same nutrition).
--   (2) save_built_coach_block gains p_previous_plan_id (default null = the old behaviour, untouched) and returns the
--       id of the plan that holds the saved block. Already-saved target plans keep returning the saved folder.
-- Base (never edited): 20261230100000_coach_block_builder_save.sql.

create function public.coach_begin_next_block(p_plan_id uuid, p_previous_plan_id uuid)
returns uuid
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_uid   uuid := public.coach_guard((now() at time zone 'utc')::date);
  v_today date := (now() at time zone 'utc')::date;
  v_prev  public.coach_plans;
  v_plan  public.coach_plans;
  v_id    uuid;
begin
  if coalesce(current_setting('app.coach_block_save', true), '') <> 'on' then
    raise exception 'coach_begin_next_block is internal' using errcode = '42501';
  end if;
  if p_plan_id is null or p_previous_plan_id is null then
    raise exception 'invalid plan' using errcode = '22023';
  end if;

  select * into v_prev from public.coach_plans where id = p_previous_plan_id and user_id = v_uid for update;
  if not found then
    raise exception 'plan not found' using errcode = 'P0002';
  end if;

  if p_plan_id <> p_previous_plan_id then
    select * into v_plan from public.coach_plans where id = p_plan_id and user_id = v_uid for update;
    if not found then
      raise exception 'plan not found' using errcode = 'P0002';
    end if;
    if v_plan.status not in ('proposed', 'accepted') or v_plan.folder_id is not null then
      raise exception 'plan is not open' using errcode = '55000';
    end if;
    if v_plan.status = 'proposed' then
      perform public.accept_coach_plan(p_plan_id, v_today);
    end if;
    v_id := p_plan_id;
  else
    if v_prev.status <> 'accepted' or v_prev.folder_id is null then
      raise exception 'previous block is not the accepted block' using errcode = '55000';
    end if;
    update public.coach_plans set status = 'superseded' where id = v_prev.id and user_id = v_uid;
    insert into public.coach_plans (user_id, block_start, block_weeks, intention, nutrition, outcomes, method,
                                    status, accepted_at, phase, phase_reason, phase_options)
    values (v_uid, v_today, v_prev.block_weeks, v_prev.intention, v_prev.nutrition, v_prev.outcomes, v_prev.method,
            'accepted', now(), v_prev.phase, v_prev.phase_reason, v_prev.phase_options)
    returning id into v_id;
  end if;

  update public.coach_plans set training_start = v_today where id = v_id and user_id = v_uid;
  return v_id;
end;
$$;

revoke execute on function public.coach_begin_next_block(uuid, uuid) from public, anon;
grant execute on function public.coach_begin_next_block(uuid, uuid) to authenticated;

-- ---------------------------------------------------------------------------
-- (2) save_built_coach_block with p_previous_plan_id
-- ---------------------------------------------------------------------------
drop function public.save_built_coach_block(uuid, jsonb, jsonb, text);

create function public.save_built_coach_block(
  p_plan_id uuid,
  p_training jsonb,
  p_sessions jsonb,
  p_folder_name text default null,
  p_previous_plan_id uuid default null
)
returns jsonb
language plpgsql
volatile
security invoker
set search_path = ''
as $$
declare
  v_uid  uuid := (select auth.uid());
  v_plan uuid := p_plan_id;
begin
  if v_uid is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;

  -- Next block from the review: end the previous one and start this one, unless the target plan already holds a
  -- saved block (a repeated call returns the saved folder).
  if p_previous_plan_id is not null
     and (p_plan_id = p_previous_plan_id
          or not exists (select 1 from public.coach_plans p
                         where p.id = p_plan_id and p.user_id = v_uid and p.folder_id is not null)) then
    perform set_config('app.coach_block_save', 'on', true);
    v_plan := public.coach_begin_next_block(p_plan_id, p_previous_plan_id);
    perform set_config('app.coach_block_save', 'off', true);
  end if;

  if not exists (select 1 from public.coach_plans p
                 where p.id = v_plan and p.user_id = v_uid
                   and (p.folder_id is not null
                        or exists (select 1 from public.coach_plan_sessions s
                                   where s.plan_id = p.id and s.routine_id is not null))) then
    perform public.attach_coach_block(
      v_plan, p_training, p_sessions,
      jsonb_build_object('source', case when jsonb_typeof(p_training) = 'object'
                                        then coalesce(p_training->>'source', 'builder') else 'builder' end),
      '{}'::jsonb);
  end if;
  return public.save_coach_block(v_plan, '[]'::jsonb, p_folder_name) || jsonb_build_object('plan_id', v_plan);
end;
$$;

revoke execute on function public.save_built_coach_block(uuid, jsonb, jsonb, text, uuid) from public, anon;
grant execute on function public.save_built_coach_block(uuid, jsonb, jsonb, text, uuid) to authenticated;
