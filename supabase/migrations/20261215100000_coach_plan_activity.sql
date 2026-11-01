-- T-181 (E13, US-79): the user edits the weekly cardio minutes of the plan (planning/04 section 8.8).
-- The cardio target lives in coach_plans.nutrition.activity (written by the Edge Function). The owner has no UPDATE
-- on coach_plans, so this RPC is security definer with the same ownership filter as accept_coach_plan.
-- It touches nothing else: calories, macros, sessions and routines stay as they are.
create function public.set_coach_plan_cardio(p_plan_id uuid, p_minutes int)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_uid  uuid := (select auth.uid());
  v_plan public.coach_plans;
  v_out  jsonb;
begin
  if v_uid is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;
  select * into v_plan from public.coach_plans
    where id = p_plan_id and user_id = v_uid and status in ('proposed', 'accepted')
    for update;
  if not found then
    raise exception 'plan not found' using errcode = 'P0002';
  end if;
  -- Technical bounds, not a product limit: 0..600 minutes in steps of 5.
  if p_minutes is null or p_minutes < 0 or p_minutes > 600 or p_minutes % 5 <> 0 then
    raise exception 'cardio minutes must be 0..600 in steps of 5' using errcode = '22023';
  end if;
  if jsonb_typeof(v_plan.nutrition->'activity') is distinct from 'object' then
    raise exception 'plan has no activity targets' using errcode = '22023';
  end if;

  update public.coach_plans
    set nutrition = jsonb_set(
      jsonb_set(nutrition, '{activity,cardio_min_week}', to_jsonb(p_minutes)),
      '{activity,cardio_edited}', 'true'::jsonb)
    where id = p_plan_id and user_id = v_uid
    returning nutrition into v_out;
  return v_out;
end;
$$;

revoke execute on function public.set_coach_plan_cardio(uuid, int) from public, anon;
grant execute on function public.set_coach_plan_cardio(uuid, int) to authenticated;
