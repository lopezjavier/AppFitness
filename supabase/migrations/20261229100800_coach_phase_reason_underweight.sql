-- T-218 (ADR-98): a "Perder grasa" plan of an underweight user (BMI < 18.5) is built in maintenance with the reason
-- 'underweight'. The column check and coach_assert_plan accept the new code; nothing else changes.

alter table public.coach_plans drop constraint coach_plans_phase_reason_values;
alter table public.coach_plans
  add constraint coach_plans_phase_reason_values check (phase_reason in ('intention', 'underweight'));

-- coach_assert_plan: only its list of phase reasons changes. Patch the stored definition and fail loudly if the text is
-- not found.
do $patch$
declare
  v_def text;
  v_new text;
begin
  v_def := pg_get_functiondef('public.coach_assert_plan(uuid, public.training_intention, date, jsonb, jsonb)'::regprocedure);
  v_new := replace(v_def, 'not in (''intention'')', 'not in (''intention'', ''underweight'')');
  if v_new = v_def then
    raise exception 'coach_assert_plan patch did not apply';
  end if;
  execute v_new;
end
$patch$;

revoke execute on function public.coach_assert_plan(uuid, public.training_intention, date, jsonb, jsonb)
  from public, anon, authenticated;
