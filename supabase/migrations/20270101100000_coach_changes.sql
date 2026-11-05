-- T-227 (E14, US-97/US-104): changes the coach applies on its own (load progression) and the flagged progression.
-- Rules: planning/04-business-rules.md section 4.14, planning/07-coach-design.md section 3.
-- One row per applied change or per flag, so it can be listed and undone. Written only by the security invoker RPCs
-- of the next migration; the owner policies below are what lets those RPCs write, there is no delete policy.

create table public.coach_changes (
  id                uuid primary key default gen_random_uuid(),
  user_id           uuid not null default auth.uid() references auth.users (id) on delete cascade,
  plan_id           uuid references public.coach_plans (id) on delete cascade,
  -- text + check (not an enum) so later tasks (deload kinds) extend the set by swapping the constraint.
  kind              text not null
    constraint coach_changes_kind_values check (kind in ('load_raise', 'load_drop', 'load_kept', 'load_flag', 'no_range_flag')),
  exercise_id       uuid not null references public.exercises (id),
  -- The finished session that triggered the change. Null for the raise created by deciding a flag.
  source_session_id uuid references public.workout_sessions (id) on delete set null,
  origin_flag_id    uuid references public.coach_changes (id) on delete set null,
  -- Snapshots keyed by routine and exercise: [{routine_id, exercise_id, rep_min, rep_max, sets:[{set_index,
  -- target_weight_kg, target_reps}]}] (shape in planning/03-data-model.md).
  before_values     jsonb constraint coach_changes_before_array check (before_values is null or jsonb_typeof(before_values) = 'array'),
  after_values      jsonb constraint coach_changes_after_array check (after_values is null or jsonb_typeof(after_values) = 'array'),
  -- A code (all_sets_at_max, two_sessions_below_min, one_session_below_min_after_gap, kept_discomfort,
  -- step_over_10_percent, no_rep_range); the Spanish text lives in the UI.
  reason            text not null,
  figures           jsonb not null default '{}'::jsonb constraint coach_changes_figures_object check (jsonb_typeof(figures) = 'object'),
  status            text not null
    constraint coach_changes_status_values check (status in ('applied', 'undone', 'pending', 'decided')),
  decision          text
    constraint coach_changes_decision_values check (decision is null or decision in ('raise', 'keep', 'range_added')),
  created_at        timestamptz not null default now(),
  decided_at        timestamptz,
  constraint coach_changes_decided_iff_closed check ((decided_at is not null) = (status in ('undone', 'decided')))
);

create index coach_changes_user_created_idx on public.coach_changes (user_id, created_at desc);
create index coach_changes_plan_idx on public.coach_changes (plan_id);
create index coach_changes_source_session_idx on public.coach_changes (source_session_id);
create index coach_changes_origin_flag_idx on public.coach_changes (origin_flag_id);
create index coach_changes_exercise_idx on public.coach_changes (exercise_id);

-- A second call for the same finished session finds the rows of the first one.
create unique index coach_changes_one_per_session_exercise
  on public.coach_changes (user_id, source_session_id, exercise_id) where source_session_id is not null;
-- While a flag waits for the user, later sessions neither write a weight nor add another flag.
create unique index coach_changes_one_pending_flag
  on public.coach_changes (user_id, plan_id, exercise_id) where kind in ('load_flag', 'no_range_flag') and status = 'pending';

alter table public.coach_changes enable row level security;
alter table public.coach_changes force row level security;

create policy coach_changes_select_own on public.coach_changes
  for select to authenticated
  using (user_id = (select auth.uid()));

create policy coach_changes_insert_own on public.coach_changes
  for insert to authenticated
  with check (user_id = (select auth.uid()));

create policy coach_changes_update_own on public.coach_changes
  for update to authenticated
  using (user_id = (select auth.uid()))
  with check (user_id = (select auth.uid()));

revoke all on table public.coach_changes from anon, authenticated;
grant select, insert, update on table public.coach_changes to authenticated;
