-- T-201 (ADR-92, planning/07-coach-design.md section 3 "Equipment items"): catalogue rows get their own equipment item.
-- Catalogue rows only (not is_custom): custom exercises keep their tag. Idempotent.

-- 1. Barra Z: every EZ-bar exercise (20 `ez-bar` keys plus `preacher-curl`, which the bundle files under the EZ bar).
update public.exercises set equipment = 'ez_bar'
where not is_custom and (key like '%ez-bar%' or key = 'preacher-curl');

-- 2. One "Máquina de cardio" for the eight cardio machines (`walking` and `running` stay bodyweight).
update public.exercises set equipment = 'cardio_machine'
where not is_custom
  and key in ('stationary-bike', 'air-bike', 'incline-treadmill-walk', 'treadmill-running', 'elliptical-trainer',
              'rowing-machine', 'ski-erg', 'stair-climber');

-- 3. Small implements and fixed structures are "Otro": landmine/T-bar, hex bar, pull-up bar, dip station, rings, TRX,
--    plus the single cases of the rule. `bodyweight` means the floor or a wall only.
update public.exercises set equipment = 'other'
where not is_custom
  and (key in ('landmine-press', 'one-arm-landmine-press', 't-bar-row', 'hex-bar-deadlift',
               'human-flag', 'back-extension', 'band-assisted-pull-ups', 'straight-bar-dips', 'toes-to-bar',
               'muscle-ups', 'dips', 'weighted-dips', 'captains-chair-knee-raise', 'captains-chair-leg-raise',
               'rings-inverted-row', 'dead-hang', 'flexed-arm-hang', 'front-lever', 'back-lever')
       or (equipment = 'bodyweight'
           and (key like '%pull-up%' or key like '%chin-up%' or key like 'hanging-%'
                or key like 'ring-%' or key like 'trx-%')));

-- 4. The two lat-pulldown variants use the same station as `lat-pulldown`.
update public.exercises set equipment = 'cable'
where not is_custom and key in ('behind-the-neck-lat-pulldown', 'v-bar-lat-pulldown');

-- 5. Two rows were filed under biceps.
update public.exercises set muscle_group = 'back'
where not is_custom and key in ('ez-bar-reverse-grip-row', 'smith-machine-reverse-grip-bent-over-row');

do $$
begin
  if (select count(*) from public.exercises where not is_custom and equipment = 'ez_bar') <> 21 then
    raise exception 'EZ-bar retag: expected 21 catalogue rows (20 ez-bar keys + preacher-curl)';
  end if;
  if exists (select 1 from public.exercises where not is_custom and key like '%ez-bar%' and equipment <> 'ez_bar') then
    raise exception 'an ez-bar key is not tagged ez_bar';
  end if;
  if (select count(*) from public.exercises where not is_custom and equipment = 'cardio_machine') <> 8 then
    raise exception 'cardio retag: expected 8 catalogue rows';
  end if;
  if exists (select 1 from public.exercises
             where not is_custom and equipment in ('cardio_bike', 'treadmill', 'elliptical', 'rower', 'cardio_other')) then
    raise exception 'a catalogue row still holds one of the five old cardio values';
  end if;
  if exists (select 1 from public.exercises
             where equipment = 'cardio_machine'
               and (is_custom or key not in ('stationary-bike', 'air-bike', 'incline-treadmill-walk', 'treadmill-running',
                                             'elliptical-trainer', 'rowing-machine', 'ski-erg', 'stair-climber'))) then
    raise exception 'a non-cardio row carries cardio_machine';
  end if;
  if exists (select 1 from public.exercises
             where not is_custom and equipment in ('barbell', 'bodyweight')
               and (key like 'trx-%' or key like 'ring-%' or key like '%pull-up%' or key like '%chin-up%'
                    or key like 'hanging-%'
                    or key in ('landmine-press', 'one-arm-landmine-press', 't-bar-row', 'hex-bar-deadlift', 'dips',
                               'weighted-dips', 'human-flag', 'back-extension', 'toes-to-bar', 'muscle-ups',
                               'straight-bar-dips', 'dead-hang'))) then
    raise exception 'a moved exercise is still barbell or bodyweight';
  end if;
  if (select count(*) from public.exercises
      where not is_custom and key in ('behind-the-neck-lat-pulldown', 'v-bar-lat-pulldown') and equipment = 'cable') <> 2 then
    raise exception 'lat-pulldown variants not retagged to cable';
  end if;
  if (select count(*) from public.exercises
      where not is_custom and key in ('ez-bar-reverse-grip-row', 'smith-machine-reverse-grip-bent-over-row')
        and muscle_group = 'back') <> 2 then
    raise exception 'the two reverse-grip rows are not back';
  end if;
end $$;
