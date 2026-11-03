-- T-212: the coach's free texts have no upper length cap (user decision 2026-11-03). The RPC invariants keep the
-- minimum (10 characters for a reason, 1 for a technique note: an empty text is a structure defect) and drop the
-- upper bound (300 for reasons, 200 for technique notes). The stored definitions are patched in place, as
-- 20261227100200 does, and the patch fails loudly if the expected text is not found.

do $patch$
declare
  v_def text;
  v_new text;
begin
  -- Nutrition outcomes: reason 10..300 -> 10..
  v_def := pg_get_functiondef('public.coach_assert_plan(uuid, public.training_intention, date, jsonb, jsonb)'::regprocedure);
  v_new := replace(v_def, 'char_length(v_reason) < 10 or char_length(v_reason) > 300', 'char_length(v_reason) < 10');
  if v_new = v_def or v_new like '%> 300%' then
    raise exception 'coach_assert_plan patch did not apply';
  end if;
  execute v_new;

  -- Block: cardio reason and exercise reason 10..300 -> 10..; technique note 1..200 -> 1..
  v_def := pg_get_functiondef('public.coach_assert_block(uuid, jsonb, jsonb)'::regprocedure);
  v_new := replace(v_def, 'char_length(v_reason) < 10 or char_length(v_reason) > 300', 'char_length(v_reason) < 10');
  v_new := replace(v_new,
    'char_length(btrim(v_ex.value->>''technique_note'')) not between 1 and 200',
    'char_length(btrim(v_ex.value->>''technique_note'')) < 1');
  if v_new = v_def or v_new like '%> 300%' or v_new like '%between 1 and 200%' then
    raise exception 'coach_assert_block patch did not apply';
  end if;
  execute v_new;
end
$patch$;

revoke execute on function public.coach_assert_plan(uuid, public.training_intention, date, jsonb, jsonb)
  from public, anon, authenticated;
revoke execute on function public.coach_assert_block(uuid, jsonb, jsonb) from public, anon, authenticated;
