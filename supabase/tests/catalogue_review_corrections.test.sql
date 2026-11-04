-- pgTAP: T-216 catalogue corrections (07 section 3 "Catalogue facts the review reads"). Reads the migrated catalogue.
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(30);

create function pg_temp.sec(p_key text) returns text[] language sql as $$
  select coalesce(array_agg(g::text order by g::text), array[]::text[])
  from public.exercises e, unnest(e.secondary_muscles) g where not e.is_custom and e.key = p_key $$;
create function pg_temp.grp(p_key text) returns text language sql as $$
  select muscle_group::text from public.exercises where not is_custom and key = p_key $$;
create function pg_temp.pat(p_key text) returns text language sql as $$
  select movement_pattern::text from public.exercises where not is_custom and key = p_key $$;

-- Guards of the corrections table
select is((select count(*)::int from public.exercises
           where not is_custom and movement_pattern in ('horizontal_push', 'vertical_push') and muscle_group = 'chest'
             and not ('shoulders' = any (secondary_muscles))), 0, 'every chest push row has shoulders as secondary');
select is((select count(*)::int from public.exercises
           where not is_custom and movement_pattern in ('horizontal_pull', 'vertical_pull') and muscle_group = 'back'
             and not ('biceps' = any (secondary_muscles))), 0, 'every back pull row has biceps as secondary');
select is((select count(*)::int from public.exercises
           where not is_custom and counts_as_strength and muscle_group = 'biceps'
             and movement_pattern in ('squat', 'hinge', 'lunge', 'horizontal_push', 'vertical_push', 'horizontal_pull', 'vertical_pull')),
  0, 'no counting main-lift row has biceps as primary');
select is((select count(*)::int from public.exercises b join public.exercises c
             on c.key = substr(b.key, length('band-long-') + 1) and not c.is_custom
           where not b.is_custom and b.key like 'band-long-%'
             and (b.muscle_group, b.secondary_muscles, b.movement_pattern)
                 is distinct from (c.muscle_group, c.secondary_muscles, c.movement_pattern)), 0,
  'each band-long copy equals its cable source in group, secondaries and pattern');
select ok((select count(*) from public.exercises where key like 'band-long-%') > 0, 'the band-long copies exist');
select is((select count(*)::int from public.exercises
           where not is_custom and muscle_group = any (secondary_muscles)), 0, 'no secondary equals its primary');
select is((select count(*)::int from public.exercises
           where not is_custom and (select count(distinct g) from unnest(secondary_muscles) g) <> cardinality(secondary_muscles)),
  0, 'no row lists a secondary twice');

-- A: secondaries added
select is(pg_temp.sec('bench-press'), array['shoulders', 'triceps'], 'bench press: shoulders and triceps help');
select is(pg_temp.pat('bench-press'), 'horizontal_push', 'bench press stays a horizontal push');
select is(pg_temp.sec('dips'), array['shoulders', 'triceps'], 'dips: chest primary, shoulders and triceps help');
select is(pg_temp.sec('close-grip-bench-press'), array['chest', 'shoulders'], 'close-grip bench: triceps primary, chest and shoulders help');
select is(pg_temp.grp('close-grip-bench-press'), 'triceps', 'close-grip bench stays triceps primary');
select is(pg_temp.sec('barbell-row'), array['biceps'], 'barbell row: biceps help');
select is(pg_temp.sec('deadlift'), array['glutes', 'hamstrings', 'quads'], 'deadlift gets glutes and quads and keeps hamstrings');
select is(pg_temp.grp('deadlift'), 'back', 'deadlift stays back primary');
select is(pg_temp.sec('thruster'), array['glutes', 'quads', 'triceps'], 'thruster: triceps, quads, glutes');
select is(pg_temp.sec('glute-kickback'), array['hamstrings'], 'glute kickback: hamstrings help');

-- B: primary changed
select is(pg_temp.grp('chin-ups'), 'back', 'chin-ups are back, no longer biceps');
select is(pg_temp.sec('chin-ups'), array['biceps'], 'and list biceps as secondary');
select is(pg_temp.grp('paused-incline-bench-press'), 'chest', 'paused incline bench is chest');
select is(pg_temp.grp('one-arm-kettlebell-swing'), 'glutes', 'the one-arm kettlebell swing is glutes like the swing');
select is(pg_temp.grp('kettlebell-lunge-press'), 'quads', 'the kettlebell lunge press is quads');
select is(pg_temp.pat('kettlebell-lunge-press'), 'lunge', 'and a lunge, no longer a vertical push');

-- C: pattern changed
select is(pg_temp.pat('straight-arm-pulldown'), 'isolation', 'straight-arm pulldown is an isolation');
select is(pg_temp.pat('band-long-face-pull'), 'isolation', 'face pull copies are isolation');
select is(pg_temp.pat('jefferson-curl'), 'other', 'jefferson curl is not a hinge');
select is(pg_temp.pat('kettlebell-sumo-high-pull'), 'hinge', 'kettlebell sumo high pull is a hinge');

-- Idempotence: appending the same secondaries again changes nothing.
update public.exercises set secondary_muscles = array(
  select distinct g from unnest(secondary_muscles || array['shoulders', 'triceps']::public.muscle_group[]) as g
  where g <> muscle_group order by g)
where not is_custom and key = 'bench-press';
select is(pg_temp.sec('bench-press'), array['shoulders', 'triceps'], 'applying the secondaries again changes nothing');

-- D: not counted
select is((select count(*)::int from public.exercises
           where not is_custom and not counts_as_strength
             and key in ('pilates-roll-down', 'pilates-saw', 'pilates-spine-stretch-forward', 'pilates-leg-pull-back',
                         'pilates-leg-pull-front', 'bird-dog', 'superman', 'pilates-spine-twist', 'pilates-roll-over',
                         'pilates-side-bend')), 10, 'the ten pilates and core flow rows do not count');
select is((select counts_as_strength from public.exercises where key = 'pilates-kneeling-side-kick'), true,
  'the kneeling side kick keeps counting');

select * from finish();
rollback;
