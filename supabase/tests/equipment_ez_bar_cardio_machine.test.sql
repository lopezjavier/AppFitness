-- pgTAP: Barra Z, one "Máquina de cardio", "Otro" for small implements and the
-- barbell / EZ bar / dumbbell alternatives (T-201, ADR-92).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(25);

create function pg_temp.eq(p_key text) returns text language sql as $$
  select equipment::text from public.exercises where key = p_key and not is_custom $$;
create function pg_temp.id(p_key text) returns uuid language sql as $$
  select id from public.exercises where key = p_key and not is_custom $$;
create function pg_temp.sim(p_a text, p_b text) returns numeric language sql as $$
  select similarity from public.exercise_alternatives(pg_temp.id(p_a)) where exercise_id = pg_temp.id(p_b) $$;
create function pg_temp.try_update_a() returns int language plpgsql as $$
declare n int;
begin
  update public.training_preferences set equipment = array['band']::public.equipment[]
  where user_id = 'e2010000-0000-4000-8000-000000000001';
  get diagnostics n = row_count;
  return n;
end $$;
grant execute on all functions in schema pg_temp to authenticated;

insert into auth.users (id, email) values
  ('e2010000-0000-4000-8000-000000000001', 'eq-a@example.com'),
  ('e2010000-0000-4000-8000-000000000002', 'eq-b@example.com');
insert into public.training_preferences (user_id, intention, place, equipment, days_per_week, session_minutes)
values ('e2010000-0000-4000-8000-000000000001', 'muscle', 'gym', array['barbell', 'ez_bar']::public.equipment[], 3, 45);

-- ---------------------------------------------------------------------------
-- Catalogue
-- ---------------------------------------------------------------------------
select is((select count(*)::int from public.exercises where not is_custom and equipment = 'ez_bar'), 21,
  'twenty-one catalogue exercises are ez_bar (20 ez-bar keys + preacher-curl)');
select is((select count(*)::int from public.exercises where not is_custom and key like '%ez-bar%' and equipment <> 'ez_bar'),
  0, 'every ez-bar key is ez_bar');
select is((select count(*)::int from public.exercises
           where equipment = 'ez_bar' and (is_custom or (key not like '%ez-bar%' and key <> 'preacher-curl'))), 0,
  'no other exercise is ez_bar');
select is((select count(*)::int from public.exercises where equipment = 'barbell' and key like '%ez-bar%'), 0,
  'barbell holds no EZ-bar exercise');

select is(array[pg_temp.eq('landmine-press'), pg_temp.eq('one-arm-landmine-press'), pg_temp.eq('t-bar-row'),
                pg_temp.eq('hex-bar-deadlift')],
  array['other', 'other', 'other', 'other'], 'landmine, T-bar and hex bar are other');
select is((select count(*)::int from public.exercises where not is_custom and key like 'trx-%' and equipment <> 'other'),
  0, 'every TRX exercise is other');
select is((select count(*)::int from public.exercises
           where not is_custom and (key like 'ring-%' or key = 'rings-inverted-row') and equipment <> 'other'),
  0, 'every ring exercise is other');
select is(array[pg_temp.eq('pull-up'), pg_temp.eq('chin-ups'), pg_temp.eq('dead-hang'), pg_temp.eq('hanging-leg-raise'),
                pg_temp.eq('toes-to-bar'), pg_temp.eq('muscle-ups'), pg_temp.eq('front-lever'),
                pg_temp.eq('straight-bar-dips')],
  array['other', 'other', 'other', 'other', 'other', 'other', 'other', 'other'], 'pull-up bar exercises are other');
select is(array[pg_temp.eq('dips'), pg_temp.eq('weighted-dips'), pg_temp.eq('captains-chair-leg-raise'),
                pg_temp.eq('captains-chair-knee-raise')],
  array['other', 'other', 'other', 'other'], 'dip station exercises are other');
select is(array[pg_temp.eq('human-flag'), pg_temp.eq('back-extension'), pg_temp.eq('band-assisted-pull-ups')],
  array['other', 'other', 'other'], 'human flag, back extension and band-assisted pull-ups are other');
select is((select count(*)::int from public.exercises
           where not is_custom and equipment = 'bodyweight'
             and (key like '%pull-up%' or key like '%chin-up%' or key like 'hanging-%' or key like 'ring-%'
                  or key like 'trx-%')), 0, 'bodyweight holds none of the moved exercises');
select is(pg_temp.eq('push-up'), 'bodyweight', 'floor exercises stay bodyweight');
select is(array[pg_temp.eq('behind-the-neck-lat-pulldown'), pg_temp.eq('v-bar-lat-pulldown'), pg_temp.eq('lat-pulldown')],
  array['cable', 'cable', 'cable'], 'the lat-pulldown variants are cable');
select is((select count(*)::int from public.exercises
           where key in ('ez-bar-reverse-grip-row', 'smith-machine-reverse-grip-bent-over-row') and muscle_group = 'back'),
  2, 'the two reverse-grip rows are back');
select is(pg_temp.eq('barbell-curl') || '/' || pg_temp.eq('bicep-curl'), 'barbell/dumbbell',
  'straight-bar and dumbbell exercises keep their item');

-- ---------------------------------------------------------------------------
-- Alternatives: the same exercise with another implement, 1.00 both ways; floor 0.90
-- ---------------------------------------------------------------------------
select is(pg_temp.sim('barbell-row', 'bent-over-db-row'), 1.00, 'barbell row <-> dumbbell row at 1.00');
select is(pg_temp.sim('bent-over-db-row', 'barbell-row'), 1.00, 'and the reverse direction');
select is(pg_temp.sim('barbell-curl', 'ez-bar-curl') + pg_temp.sim('ez-bar-curl', 'bicep-curl')
          + pg_temp.sim('bicep-curl', 'barbell-curl'), 3.00, 'the curl trio is pairwise 1.00');
select is((select count(*)::int
           from public.exercise_alternatives a
           join public.exercises x on x.id = a.exercise_id
           join public.exercises y on y.id = a.alternative_id
           where a.similarity = 1.00
             and x.key in ('skull-crusher', 'ez-bar-lying-tricep-extension', 'db-skull-crusher', 'lying-tricep-extension')
             and y.key in ('skull-crusher', 'ez-bar-lying-tricep-extension', 'db-skull-crusher', 'lying-tricep-extension')),
  6, 'a group of four holds its six pairs, one row each');
select is((select count(*)::int
           from public.exercise_alternatives a
           join public.exercises x on x.id = a.exercise_id
           join public.exercises y on y.id = a.alternative_id
           where a.similarity = 1.00
             and ((x.key in ('bench-press', 'ez-bar-bench-press', 'db-bench-press')
                   and y.key in ('incline-bench-press', 'incline-bench-ez-bar-press', 'incline-db-press',
                                 'decline-bench-press-barbell', 'decline-bench-press-ez-bar', 'decline-bench-press'))
               or (y.key in ('bench-press', 'ez-bar-bench-press', 'db-bench-press')
                   and x.key in ('incline-bench-press', 'incline-bench-ez-bar-press', 'incline-db-press',
                                 'decline-bench-press-barbell', 'decline-bench-press-ez-bar', 'decline-bench-press')))),
  0, 'flat bench is not mixed with incline or decline at 1.00');
select is((select count(*)::int from public.exercise_alternatives where similarity < 0.90), 0,
  'nothing under 0.90 is stored');
select throws_ok(
  $$insert into public.exercise_alternatives
    select least(pg_temp.id('squat'), pg_temp.id('push-up')), greatest(pg_temp.id('squat'), pg_temp.id('push-up')), 0.85$$,
  '23514', null, 'a similarity of 0.85 is rejected');

-- ---------------------------------------------------------------------------
-- Cross-user isolation on the saved list
-- ---------------------------------------------------------------------------
set local role authenticated;
select set_config('request.jwt.claims',
  json_build_object('sub', 'e2010000-0000-4000-8000-000000000002', 'role', 'authenticated')::text, true);
select is((select count(*)::int from public.training_preferences), 0, 'user B cannot read user A''s saved equipment');
select is(pg_temp.try_update_a(), 0, 'user B cannot change user A''s saved equipment');
reset role;
select is((select equipment::text[] from public.training_preferences where user_id = 'e2010000-0000-4000-8000-000000000001'),
  array['barbell', 'ez_bar'], 'user A''s saved equipment is intact');

select * from finish();
rollback;
