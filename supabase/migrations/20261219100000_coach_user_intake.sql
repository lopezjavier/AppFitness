-- T-185 (US-81, ADR-86): "Para conocerte" - what the user tells the coach about their training past.
-- Named coach_user_intake because public.coach_intake(date) already exists (the diary intake function).
-- Everything stored here is a provisional prior: coach_compute (20261219100200) reads it, never trusts it over data.

create type public.coach_situation as enum ('first_time', 'continuous', 'returning_break', 'returning_injury');
create type public.coach_injured_area as enum ('shoulder', 'elbow', 'wrist', 'lower_back', 'hip', 'knee', 'ankle', 'other');

create table public.coach_user_intake (
  user_id                  uuid primary key default auth.uid() references auth.users (id) on delete cascade,
  training_years           numeric(4, 1),
  situation                public.coach_situation,
  injured_area             public.coach_injured_area,
  -- Array of at most 12 {exercise_id, weight_kg, reps}; context only, never sets a load.
  pre_break_bests          jsonb not null default '[]'::jsonb,
  known_maintenance_kcal   integer,
  known_cut_kcal           integer,
  free_text                text,
  -- Set once the one-time "your data takes over" card was shown (coach_compute is stable and cannot write).
  maintenance_handover_at  timestamptz,
  saved_at                 timestamptz not null default now(),
  updated_at               timestamptz not null default now(),
  constraint coach_user_intake_years_range check (training_years between 0 and 60),
  constraint coach_user_intake_bests_array check (
    jsonb_typeof(pre_break_bests) = 'array' and jsonb_array_length(pre_break_bests) <= 12),
  constraint coach_user_intake_maintenance_range check (known_maintenance_kcal between 1200 and 6000),
  constraint coach_user_intake_cut_range check (known_cut_kcal between 800 and 5000),
  constraint coach_user_intake_text_length check (char_length(free_text) <= 500)
);

create trigger coach_user_intake_set_updated_at
  before update on public.coach_user_intake
  for each row execute function public.set_updated_at();

alter table public.coach_user_intake enable row level security;
alter table public.coach_user_intake force row level security;

create policy coach_user_intake_select_own on public.coach_user_intake
  for select to authenticated using (user_id = (select auth.uid()));
create policy coach_user_intake_insert_own on public.coach_user_intake
  for insert to authenticated with check (user_id = (select auth.uid()));
create policy coach_user_intake_update_own on public.coach_user_intake
  for update to authenticated
  using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));
create policy coach_user_intake_delete_own on public.coach_user_intake
  for delete to authenticated using (user_id = (select auth.uid()));

revoke all on table public.coach_user_intake from anon, authenticated;
grant select, insert, update, delete on table public.coach_user_intake to authenticated;

-- Validates and stores the single intake row of the caller. Re-checks everything the table checks plus the enums and
-- the lifts (non-custom main-pattern catalogue exercises, mirror of MAIN_PATTERNS in functions/coach/blockRules.ts).
-- Unknown JSON keys are ignored; an invalid value raises 22023 (the extraction already nulls invalid ones).
create function public.save_coach_intake(p jsonb)
returns jsonb
language plpgsql
volatile
security invoker
set search_path = ''
as $$
declare
  v_uid       uuid := auth.uid();
  v_years     numeric;
  v_sit       public.coach_situation;
  v_area      public.coach_injured_area;
  v_maint     integer;
  v_cut       integer;
  v_text      text;
  v_bests     jsonb := '[]'::jsonb;
  v_item      jsonb;
  v_ex        uuid;
  v_w         numeric;
  v_r         numeric;
  v_seen      uuid[] := array[]::uuid[];
  v_old       public.coach_user_intake;
  v_handover  timestamptz;
  v_adaptive  boolean := false;
  v_row       public.coach_user_intake;
begin
  if v_uid is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;
  if p is null or jsonb_typeof(p) <> 'object' then
    raise exception 'intake must be a JSON object' using errcode = '22023';
  end if;

  if jsonb_typeof(p->'training_years') = 'number' then
    v_years := (p->>'training_years')::numeric;
    if v_years < 0 or v_years > 60 then
      raise exception 'training_years out of range' using errcode = '22023';
    end if;
  elsif p->'training_years' is not null and jsonb_typeof(p->'training_years') <> 'null' then
    raise exception 'training_years must be a number' using errcode = '22023';
  end if;

  if p->'situation' is not null and jsonb_typeof(p->'situation') <> 'null' then
    if jsonb_typeof(p->'situation') <> 'string'
       or not ((p->>'situation') = any (enum_range(null::public.coach_situation)::text[])) then
      raise exception 'invalid situation' using errcode = '22023';
    end if;
    v_sit := (p->>'situation')::public.coach_situation;
  end if;

  if p->'injured_area' is not null and jsonb_typeof(p->'injured_area') <> 'null' then
    if jsonb_typeof(p->'injured_area') <> 'string'
       or not ((p->>'injured_area') = any (enum_range(null::public.coach_injured_area)::text[])) then
      raise exception 'invalid injured_area' using errcode = '22023';
    end if;
    v_area := (p->>'injured_area')::public.coach_injured_area;
  end if;

  if p->'known_maintenance_kcal' is not null and jsonb_typeof(p->'known_maintenance_kcal') <> 'null' then
    if jsonb_typeof(p->'known_maintenance_kcal') <> 'number'
       or (p->>'known_maintenance_kcal')::numeric <> trunc((p->>'known_maintenance_kcal')::numeric)
       or (p->>'known_maintenance_kcal')::numeric not between 1200 and 6000 then
      raise exception 'invalid known_maintenance_kcal' using errcode = '22023';
    end if;
    v_maint := (p->>'known_maintenance_kcal')::numeric::integer;
  end if;

  if p->'known_cut_kcal' is not null and jsonb_typeof(p->'known_cut_kcal') <> 'null' then
    if jsonb_typeof(p->'known_cut_kcal') <> 'number'
       or (p->>'known_cut_kcal')::numeric <> trunc((p->>'known_cut_kcal')::numeric)
       or (p->>'known_cut_kcal')::numeric not between 800 and 5000 then
      raise exception 'invalid known_cut_kcal' using errcode = '22023';
    end if;
    v_cut := (p->>'known_cut_kcal')::numeric::integer;
  end if;

  if p->'free_text' is not null and jsonb_typeof(p->'free_text') <> 'null' then
    if jsonb_typeof(p->'free_text') <> 'string' then
      raise exception 'free_text must be a string' using errcode = '22023';
    end if;
    v_text := nullif(btrim(p->>'free_text'), '');
    if char_length(v_text) > 500 then
      raise exception 'free_text longer than 500 characters' using errcode = '22023';
    end if;
  end if;

  if p->'pre_break_bests' is not null and jsonb_typeof(p->'pre_break_bests') <> 'null' then
    if jsonb_typeof(p->'pre_break_bests') <> 'array' or jsonb_array_length(p->'pre_break_bests') > 12 then
      raise exception 'pre_break_bests must be an array of at most 12' using errcode = '22023';
    end if;
    for v_item in select * from jsonb_array_elements(p->'pre_break_bests') loop
      if jsonb_typeof(v_item) <> 'object'
         or jsonb_typeof(v_item->'exercise_id') <> 'string'
         or jsonb_typeof(v_item->'weight_kg') <> 'number'
         or jsonb_typeof(v_item->'reps') <> 'number' then
        raise exception 'invalid best' using errcode = '22023';
      end if;
      begin
        v_ex := (v_item->>'exercise_id')::uuid;
      exception when invalid_text_representation then
        raise exception 'invalid best exercise_id' using errcode = '22023';
      end;
      v_w := (v_item->>'weight_kg')::numeric;
      v_r := (v_item->>'reps')::numeric;
      if v_w < 5 or v_w > 500 or v_r < 1 or v_r > 30 or v_r <> trunc(v_r) then
        raise exception 'best weight or reps out of range' using errcode = '22023';
      end if;
      if v_ex = any (v_seen) then
        raise exception 'duplicate lift in pre_break_bests' using errcode = '22023';
      end if;
      if not exists (
        select 1 from public.exercises e
        where e.id = v_ex and not e.is_custom
          and e.movement_pattern::text in ('squat', 'hinge', 'lunge', 'horizontal_push', 'vertical_push',
                                           'horizontal_pull', 'vertical_pull')) then
        raise exception 'best exercise is not a catalogue main lift' using errcode = '22023';
      end if;
      v_seen := v_seen || v_ex;
      v_bests := v_bests || jsonb_build_array(
        jsonb_build_object('exercise_id', v_ex, 'weight_kg', v_w, 'reps', v_r::integer));
    end loop;
  end if;

  select * into v_old from public.coach_user_intake where user_id = v_uid;
  if v_maint is not null then
    if found and v_old.known_maintenance_kcal is not distinct from v_maint then
      v_handover := v_old.maintenance_handover_at;
    else
      v_adaptive := public.coach_compute((now() at time zone 'utc')::date, null)
                      ->'total_expenditure'->>'source' = 'adaptive';
      v_handover := case when v_adaptive then now() end;
    end if;
  end if;

  insert into public.coach_user_intake as c (
    user_id, training_years, situation, injured_area, pre_break_bests, known_maintenance_kcal, known_cut_kcal,
    free_text, maintenance_handover_at, saved_at)
  values (v_uid, v_years, v_sit, v_area, v_bests, v_maint, v_cut, v_text, v_handover, now())
  on conflict (user_id) do update
    set training_years = excluded.training_years, situation = excluded.situation,
        injured_area = excluded.injured_area, pre_break_bests = excluded.pre_break_bests,
        known_maintenance_kcal = excluded.known_maintenance_kcal, known_cut_kcal = excluded.known_cut_kcal,
        free_text = excluded.free_text, maintenance_handover_at = excluded.maintenance_handover_at,
        saved_at = excluded.saved_at
  returning * into v_row;

  return jsonb_build_object(
    'training_years', v_row.training_years,
    'situation', v_row.situation,
    'injured_area', v_row.injured_area,
    'pre_break_bests', v_row.pre_break_bests,
    'known_maintenance_kcal', v_row.known_maintenance_kcal,
    'known_cut_kcal', v_row.known_cut_kcal,
    'free_text', v_row.free_text,
    'maintenance_handover_at', v_row.maintenance_handover_at,
    'saved_at', v_row.saved_at);
end;
$$;

-- The UI calls this once the "your data takes over" card was shown, so it never appears again.
create function public.ack_maintenance_handover()
returns void
language plpgsql
volatile
security invoker
set search_path = ''
as $$
begin
  if auth.uid() is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;
  update public.coach_user_intake
  set maintenance_handover_at = now()
  where user_id = (select auth.uid()) and maintenance_handover_at is null;
end;
$$;

revoke execute on function public.save_coach_intake(jsonb) from public, anon;
revoke execute on function public.ack_maintenance_handover() from public, anon;
grant execute on function public.save_coach_intake(jsonb) to authenticated;
grant execute on function public.ack_maintenance_handover() to authenticated;
