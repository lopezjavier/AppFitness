-- T-079 (US-26): Health Connect sync. Body composition columns, the written-back copy marker, per-user sync state
-- (last sync, Changes API token, tombstones of deleted imported readings) and two RPCs.

alter table public.body_measurements
  add column lean_mass_kg numeric(5, 2)
    constraint body_measurements_lean_mass_kg_range check (lean_mass_kg between 5 and 400),
  add column water_kg numeric(5, 2)
    constraint body_measurements_water_kg_range check (water_kg between 1 and 300),
  add column bone_kg numeric(4, 2)
    constraint body_measurements_bone_kg_range check (bone_kg between 0.1 and 20),
  add column bmr_kcal integer
    constraint body_measurements_bmr_kcal_range check (bmr_kcal between 300 and 10000),
  -- Base id of the copy written back to Health Connect (client record ids are '<id>:w' and '<id>:f').
  add column hc_record_id text;

alter table public.body_measurements
  add constraint body_measurements_composition_hc_only check (
    source = 'health_connect'
    or (lean_mass_kg is null and water_kg is null and bone_kg is null and bmr_kcal is null)
  ),
  add constraint body_measurements_hc_record_id_manual_only check (
    hc_record_id is null or source = 'manual'
  );

create unique index body_measurements_user_id_hc_record_id_key
  on public.body_measurements (user_id, hc_record_id)
  where hc_record_id is not null;

create table public.health_sync_state (
  user_id               uuid primary key default auth.uid() references auth.users (id) on delete cascade,
  last_synced_at        timestamptz not null,
  changes_token         text,
  -- external_ids of deleted imported readings; the import never brings them back.
  dismissed_external_ids text[] not null default '{}',
  updated_at            timestamptz not null default now()
);

create trigger health_sync_state_set_updated_at
  before update on public.health_sync_state
  for each row execute function public.set_updated_at();

alter table public.health_sync_state enable row level security;
alter table public.health_sync_state force row level security;

create policy health_sync_state_select_own on public.health_sync_state
  for select to authenticated
  using (user_id = (select auth.uid()));

create policy health_sync_state_insert_own on public.health_sync_state
  for insert to authenticated
  with check (user_id = (select auth.uid()));

create policy health_sync_state_update_own on public.health_sync_state
  for update to authenticated
  using (user_id = (select auth.uid()))
  with check (user_id = (select auth.uid()));

revoke all on table public.health_sync_state from anon, authenticated;
grant select, insert, update on table public.health_sync_state to authenticated;

-- Imports Health Connect readings idempotently. Returns the number of rows newly inserted.
create or replace function public.import_health_measurements(
  p_rows           jsonb,
  p_synced_at      timestamptz,
  p_changes_token  text
)
returns integer
language plpgsql
security invoker
set search_path = ''
as $$
declare
  v_user uuid := (select auth.uid());
  v_dismissed text[];
  v_inserted integer := 0;
begin
  if v_user is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;

  if p_rows is null or jsonb_typeof(p_rows) <> 'array' or jsonb_array_length(p_rows) > 1000 then
    raise exception 'p_rows must be a json array of at most 1000 rows' using errcode = '22023';
  end if;

  select coalesce(s.dismissed_external_ids, '{}') into v_dismissed
  from public.health_sync_state s
  where s.user_id = v_user;
  v_dismissed := coalesce(v_dismissed, '{}');

  with incoming as (
    select distinct on (r.external_id) r.*
    from jsonb_to_recordset(p_rows) as r(
      external_id text, measured_at timestamptz, weight_kg numeric, body_fat_pct numeric,
      lean_mass_kg numeric, water_kg numeric, bone_kg numeric, bmr_kcal integer
    )
    where r.external_id is not null
      and not (r.external_id = any (v_dismissed))
    order by r.external_id
  ),
  upserted as (
    insert into public.body_measurements as bm (
      user_id, measured_at, weight_kg, body_fat_pct, lean_mass_kg, water_kg, bone_kg, bmr_kcal,
      source, external_id
    )
    select v_user, i.measured_at, i.weight_kg, i.body_fat_pct, i.lean_mass_kg, i.water_kg, i.bone_kg,
           i.bmr_kcal, 'health_connect', i.external_id
    from incoming i
    on conflict (user_id, external_id) do update set
      body_fat_pct = coalesce(bm.body_fat_pct, excluded.body_fat_pct),
      lean_mass_kg = coalesce(bm.lean_mass_kg, excluded.lean_mass_kg),
      water_kg     = coalesce(bm.water_kg, excluded.water_kg),
      bone_kg      = coalesce(bm.bone_kg, excluded.bone_kg),
      bmr_kcal     = coalesce(bm.bmr_kcal, excluded.bmr_kcal)
    returning (xmax = 0) as inserted
  )
  select count(*) filter (where inserted)::integer into v_inserted from upserted;

  insert into public.health_sync_state (user_id, last_synced_at, changes_token)
  values (v_user, least(p_synced_at, now()), p_changes_token)
  on conflict (user_id) do update set
    last_synced_at = excluded.last_synced_at,
    changes_token = excluded.changes_token;

  return v_inserted;
end;
$$;

-- Deletes one of the caller's measurements; a deleted imported reading is remembered so it is not re-imported.
create or replace function public.delete_body_measurement(p_id uuid)
returns void
language plpgsql
security invoker
set search_path = ''
as $$
declare
  v_user uuid := (select auth.uid());
  v_source public.measurement_source;
  v_external_id text;
begin
  if v_user is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;

  delete from public.body_measurements m
  where m.id = p_id and m.user_id = v_user
  returning m.source, m.external_id into v_source, v_external_id;

  if found and v_source = 'health_connect' and v_external_id is not null then
    insert into public.health_sync_state (user_id, last_synced_at, dismissed_external_ids)
    values (v_user, now(), array[v_external_id])
    on conflict (user_id) do update set
      dismissed_external_ids = case
        when v_external_id = any (public.health_sync_state.dismissed_external_ids)
          then public.health_sync_state.dismissed_external_ids
        else array_append(public.health_sync_state.dismissed_external_ids, v_external_id)
      end;
  end if;
end;
$$;

revoke execute on function public.import_health_measurements(jsonb, timestamptz, text) from public, anon;
grant execute on function public.import_health_measurements(jsonb, timestamptz, text) to authenticated;
revoke execute on function public.delete_body_measurement(uuid) from public, anon;
grant execute on function public.delete_body_measurement(uuid) to authenticated;
