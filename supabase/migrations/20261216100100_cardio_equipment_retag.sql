-- T-184 (ADR-85): retag the eight cardio machines of the catalogue from `machine` to their own equipment value
-- (planning/07-coach-design.md section 3). `walking` and `running` stay bodyweight. Saved training_preferences lists
-- are not touched: users opt in to the new values themselves. Idempotent.
update public.exercises set equipment = 'cardio_bike'
where not is_custom and key in ('stationary-bike', 'air-bike');

update public.exercises set equipment = 'treadmill'
where not is_custom and key in ('incline-treadmill-walk', 'treadmill-running');

update public.exercises set equipment = 'elliptical'
where not is_custom and key = 'elliptical-trainer';

update public.exercises set equipment = 'rower'
where not is_custom and key = 'rowing-machine';

update public.exercises set equipment = 'cardio_other'
where not is_custom and key in ('ski-erg', 'stair-climber');

do $$
begin
  if (select count(*) from public.exercises
      where not is_custom and equipment in ('cardio_bike', 'treadmill', 'elliptical', 'rower', 'cardio_other')) <> 8 then
    raise exception 'cardio retag incomplete: expected 8 catalogue rows';
  end if;
  if exists (select 1 from public.exercises
             where not is_custom and equipment = 'machine'
               and key in ('stationary-bike', 'air-bike', 'incline-treadmill-walk', 'treadmill-running',
                           'elliptical-trainer', 'rowing-machine', 'ski-erg', 'stair-climber')) then
    raise exception 'a cardio row is still tagged machine';
  end if;
  if exists (select 1 from public.exercises
             where equipment in ('cardio_bike', 'treadmill', 'elliptical', 'rower', 'cardio_other')
               and (is_custom or key not in ('stationary-bike', 'air-bike', 'incline-treadmill-walk', 'treadmill-running',
                                             'elliptical-trainer', 'rowing-machine', 'ski-erg', 'stair-climber'))) then
    raise exception 'a non-cardio row carries a cardio equipment value';
  end if;
end $$;
