-- T-094 (US-34): personal-record catalogue (04 §4.8). Two read-only RPCs, no new table.
-- Same eligibility filter as exercise_history: completed sessions, completed sets, warm-up sets only when
-- warm_up_included(). Volume uses set_volume * volume_factor. Duration/distance/steps/floors use the max single set.

create function public.exercise_session_records(p_exercise_id uuid)
returns table (
  session_id uuid, started_at timestamptz, heaviest_lift numeric, best_set numeric, session_volume numeric,
  best_one_rm numeric, total_reps bigint, best_duration_s int, best_distance_m numeric,
  total_steps int, total_floors int
)
language sql
stable
security invoker
set search_path = ''
as $$
  select s.id,
         s.started_at,
         max(ws.weight_kg) filter (where ws.weight_kg > 0),
         max(public.set_volume(e.metric_type, ws.weight_kg, ws.reps, bw.kg) * public.volume_factor(e.id))
           filter (where public.set_volume(e.metric_type, ws.weight_kg, ws.reps, bw.kg) > 0),
         coalesce(sum(public.set_volume(e.metric_type, ws.weight_kg, ws.reps, bw.kg) * public.volume_factor(e.id)), 0),
         max(case
               when e.metric_type in ('weight_reps', 'weighted')
                    and ws.reps between 1 and 12 and ws.weight_kg > 0 then
                 round(case when ws.reps = 1 then ws.weight_kg
                            else ws.weight_kg * (1 + ws.reps / 30.0) end * 2) / 2
             end),
         coalesce(sum(ws.reps), 0)::bigint,
         max(ws.duration_s),
         max(ws.distance_m),
         max(ws.steps),
         max(ws.floors)
  from public.workout_sessions s
  join public.workout_sets ws on ws.session_id = s.id
  join public.exercises e on e.id = ws.exercise_id
  cross join (select public.latest_body_weight_kg() as kg) bw
  where s.status = 'completed'
    and ws.exercise_id = p_exercise_id
    and ws.completed_at is not null
    and (ws.set_type <> 'warm_up' or public.warm_up_included())
  group by s.id, s.started_at
  order by s.started_at;
$$;

revoke execute on function public.exercise_session_records(uuid) from public, anon;
grant execute on function public.exercise_session_records(uuid) to authenticated;

create function public.personal_records(p_exercise_id uuid)
returns table (record_type text, value numeric, session_id uuid, session_date date)
language sql
stable
security invoker
set search_path = ''
as $$
  with r as (select * from public.exercise_session_records(p_exercise_id)),
  fam as (
    select 1 as ord, 'heaviest_lift' as record_type, r.session_id, r.started_at, r.heaviest_lift::numeric as value from r
    union all select 2, 'best_set', r.session_id, r.started_at, r.best_set from r
    union all select 3, 'max_volume', r.session_id, r.started_at, r.session_volume from r
    union all select 4, 'best_1rm', r.session_id, r.started_at, r.best_one_rm from r
    union all select 6, 'best_duration', r.session_id, r.started_at, r.best_duration_s::numeric from r
    union all select 7, 'best_distance', r.session_id, r.started_at, r.best_distance_m from r
    union all select 8, 'most_steps', r.session_id, r.started_at, r.total_steps::numeric from r
    union all select 9, 'most_floors', r.session_id, r.started_at, r.total_floors::numeric from r
  ),
  best as (
    select distinct on (fam.record_type) fam.ord, fam.record_type, fam.value, fam.session_id,
           (fam.started_at at time zone 'UTC')::date as session_date
    from fam
    where fam.value > 0
    order by fam.record_type, fam.value desc, fam.started_at
  )
  select u.record_type, u.value, u.session_id, u.session_date
  from (
    select b.ord, b.record_type, b.value, b.session_id, b.session_date from best b
    union all
    select 5, 'total_reps', sum(r.total_reps)::numeric, null::uuid, null::date
    from r
    having sum(r.total_reps) > 0
  ) u
  order by u.ord;
$$;

revoke execute on function public.personal_records(uuid) from public, anon;
grant execute on function public.personal_records(uuid) to authenticated;
