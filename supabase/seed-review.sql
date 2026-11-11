-- LOCAL-ONLY rich dataset for the seeded user test@appfitness.local (T-278, US-131).
--
-- Never loaded by `supabase db reset` or `deploy-supabase` (it is not in config.toml [db.seed] sql_paths and it is not a
-- migration). `python scripts/review-emulator.py` applies it with psql after the reset; supabase/tests/seed_review.test.sql
-- includes it inside its own transaction and asserts its content.
--
-- It runs on top of seed.sql (user 00000000-0000-4000-8000-000000000001, onboarded, one 74.20 kg weight, one nutrition goal)
-- and is re-runnable: it first deletes the user's rows in every table it fills. Every date is relative to today (UTC), there
-- are no personal data and no free text beyond food and routine names (ADR-107).
--
-- Timeline (d0 = today):
--   block 1   d0-70 .. d0-43  four weeks, 3 sessions each, ended; its review is stored and the phase kept
--   block 2   d0-24 ..        accepted, saved; plan week 1 done (d0-24), the window d0-17..d0-11 has no workout (paused
--                             week), plan week 2 done (d0-10), plan week 3 started (d0-2); the block end is extended by 3 days
--   notices   weight (last weigh-in d0-3), circumferences (set at d0-20), weekly check-in (today is the check-in day),
--             an unseen weekly summary. No `block_end` notice: it is listed only while a review is open and block 2 is
--             already saved (a single accepted plan forces this order).
-- The plan data is written the way the pgTAP fixtures do (coach_block_review.test.sql); reports, the block review, the
-- extension and the workouts go through the app's RPCs and triggers.

create function pg_temp.uid() returns uuid language sql as $$ select '00000000-0000-4000-8000-000000000001'::uuid $$;
create function pg_temp.d0() returns date language sql as $$ select (now() at time zone 'utc')::date $$;
create function pg_temp.as_user() returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', pg_temp.uid(), 'role', 'authenticated')::text, true) $$;
create function pg_temp.at(p_day date, p_hour int default 10) returns timestamptz language sql as $$
  select (p_day + make_interval(hours => p_hour)) at time zone 'utc' $$;
create function pg_temp.ex(p_key text) returns uuid language sql as $$
  select id from public.exercises where key = p_key and not is_custom $$;

-- Persisted nutrition JSON (as coach_block_review.test.sql), at 76 kg.
create function pg_temp.nut(target int, adj int) returns jsonb language sql as $$
  select jsonb_build_object(
    'intention', 'fat_loss', 'weight_kg', 76, 'expenditure_kcal', target - adj, 'expenditure_source', 'formula',
    'rate_pct_week', 0.5, 'rate_kg_week', adj * 7 / 7700.0, 'target_rate_pct', 0.5, 'limited_by', null, 'floor_kcal', 1500,
    'target_kcal', target, 'calorie_adjustment', adj, 'strategy', 'deficit',
    'protein_pct', 33, 'carbs_pct', 42, 'fat_pct', 25, 'protein_g', 165, 'carbs_g', 210, 'fat_g', 55,
    'protein_g_per_kg', 2.2, 'distribution', jsonb_build_object('per_meal_g', 32, 'g_per_kg', 0.4, 'min_meals', 4),
    'requires_confirmation', false, 'unconfirmed', '[]'::jsonb, 'edited_by_user', false, 'calorie_changes', 0,
    'phase', 'cut', 'phase_reason', 'intention',
    'phase_options', jsonb_build_object('cut_rate_pct', 0.5, 'diet_break', false)) $$;

-- ---------------------------------------------------------------------------
-- 0. Delete first (re-runnable). Children before parents.
-- ---------------------------------------------------------------------------
delete from public.coach_plans where user_id = pg_temp.uid();            -- sessions, reviews, reports, extensions cascade
delete from public.coach_changes where user_id = pg_temp.uid();
delete from public.coach_recommendations where user_id = pg_temp.uid();
delete from public.workout_sessions where user_id = pg_temp.uid();       -- sets cascade
delete from public.routines where user_id = pg_temp.uid();                -- routine exercises cascade
delete from public.routine_folders where user_id = pg_temp.uid();
delete from public.food_log_entries where user_id = pg_temp.uid();
delete from public.foods where created_by = pg_temp.uid() and source = 'user'; -- portions cascade
delete from public.body_measurements where user_id = pg_temp.uid();
delete from public.body_circumferences where user_id = pg_temp.uid();
delete from public.weekly_check_ins where user_id = pg_temp.uid();
delete from public.coach_user_intake where user_id = pg_temp.uid();

-- ---------------------------------------------------------------------------
-- 1. Profile, training preferences and intake
-- ---------------------------------------------------------------------------
do $$
begin
  perform pg_temp.as_user();
  -- Today is the check-in day: the weekly check-in notice is listed (this week is the one not answered).
  update public.profiles set check_in_weekday = extract(isodow from pg_temp.d0())::smallint where id = pg_temp.uid();

  insert into public.training_preferences (user_id, intention, place, equipment, days_per_week, session_minutes)
  values (pg_temp.uid(), 'fat_loss', 'gym', array['barbell', 'dumbbell', 'machine', 'cable', 'cardio_machine']::public.equipment[], 3, 60)
  on conflict (user_id) do update
    set intention = excluded.intention, place = excluded.place, equipment = excluded.equipment,
        days_per_week = excluded.days_per_week, session_minutes = excluded.session_minutes;

  perform public.save_coach_intake('{"training_years": 2.5}'::jsonb);

  -- Goal of the cut (the coach is the only writer of strategy changes; same switch the pgTAP fixtures use).
  perform set_config('app.coach_write', 'on', true);
  update public.nutrition_goals
  set strategy = 'deficit', calorie_adjustment = -450, target_kcal = 2200, protein_pct = 33, carbs_pct = 42, fat_pct = 25
  where user_id = pg_temp.uid();
  perform set_config('app.coach_write', 'off', true);
end $$;

-- ---------------------------------------------------------------------------
-- 2. Weights (8 weeks, about 5 a week, slowly down, latest d0-3) and one circumference set (d0-20)
-- ---------------------------------------------------------------------------
do $$
declare i int;
begin
  perform pg_temp.as_user();
  for i in 3..59 loop
    if i % 7 not in (2, 5) then
      -- Direct insert: save_body_measurement needs the client's day range; the row is the same (source manual).
      insert into public.body_measurements (user_id, measured_at, weight_kg, source)
      values (pg_temp.uid(), pg_temp.at(pg_temp.d0() - i, 7),
              round(74.6 + 0.035 * i + 0.25 * sin(i * 1.7)::numeric, 1), 'manual');
    end if;
  end loop;

  insert into public.body_circumferences (user_id, measured_at, site, value_cm)
  select pg_temp.uid(), pg_temp.at(pg_temp.d0() - 20, 8), s.site::public.circumference_site, s.cm
  from (values ('waist', 84.5), ('chest', 101.0), ('hip', 96.0), ('arm', 34.5), ('thigh', 57.0)) s(site, cm);
end $$;

-- ---------------------------------------------------------------------------
-- 3. Foods and the diary (28 days, 4 comidas a day, plus today's breakfast)
-- ---------------------------------------------------------------------------
do $$
declare
  v_uid uuid := pg_temp.uid();
  v_slot uuid[];
  v_day int;
  v_k numeric;
  r record;
  v_deleted uuid;
  v_tortilla uuid;
  v_batido uuid;
  v_amount numeric;
  v_food public.foods;
begin
  perform pg_temp.as_user();

  -- Catalogue foods (source off, fake barcodes): inserted only if missing, so the dataset is self-contained.
  insert into public.foods (source, created_by, barcode, name, base_unit, kcal_100, protein_100, carbs_100, fat_100)
  values
    ('off', null, '0000000000011', 'Pechuga de pollo a la plancha', 'g', 165, 31, 0, 3.6),
    ('off', null, '0000000000012', 'Arroz blanco cocido', 'g', 130, 2.7, 28, 0.3),
    ('off', null, '0000000000013', 'Huevo cocido', 'g', 155, 13, 1.1, 11),
    ('off', null, '0000000000014', 'Avena en copos', 'g', 372, 13, 60, 7),
    ('off', null, '0000000000015', 'Yogur natural', 'g', 61, 3.5, 4.7, 3.3),
    ('off', null, '0000000000016', 'Plátano', 'g', 89, 1.1, 23, 0.3)
  on conflict (barcode) where source = 'off' do nothing;

  -- Personal foods through the app's RPC; "Ensalada de pasta" is deleted after logging (ADR-105).
  v_tortilla := public.create_personal_food('Tortilla de patatas casera', 'Casa', 'g', 190, 7, 15, 11, null,
                                             '[{"label":"1 ración","amount":150}]'::jsonb);
  v_batido := public.create_personal_food('Batido de proteína casero', null, 'ml', 70, 8, 6, 1.5, null,
                                           '[{"label":"1 vaso","amount":300}]'::jsonb);
  perform public.create_personal_food('Hummus casero', null, 'g', 230, 7, 14, 17, null, '[{"label":"1 cucharada","amount":30}]'::jsonb);
  v_deleted := public.create_personal_food('Ensalada de pasta', null, 'g', 140, 5, 20, 4.5, null,
                                            '[{"label":"1 bol","amount":250}]'::jsonb);

  select array_agg(id order by position) into v_slot from public.meal_slots where user_id = v_uid and enabled;
  if coalesce(array_length(v_slot, 1), 0) < 4 then
    raise exception 'the test user needs its 4 default meal slots';
  end if;

  for v_day in reverse 28..0 loop
    -- Amounts vary a little from day to day; macros are amount x per-100 values.
    v_k := 1 + ((v_day * 7) % 5 - 2) * 0.06;
    for r in
      select s.slot, f.id, f.name, f.kcal_100, f.protein_100, f.carbs_100, f.fat_100, s.grams
      from (values
        (1, '0000000000014', 70), (1, '0000000000015', 125), (1, '0000000000016', 110),
        (2, '0000000000011', 180), (2, '0000000000012', 250), (2, '0000000000013', 60),
        (3, '0000000000015', 125), (3, '0000000000016', 120),
        (4, '0000000000013', 120), (4, '0000000000012', 150)
      ) s(slot, barcode, grams)
      join public.foods f on f.barcode = s.barcode and f.source = 'off'
      where v_day > 0 or s.slot = 1
    loop
      v_amount := round(r.grams * v_k / 5) * 5;
      insert into public.food_log_entries (user_id, log_date, meal_slot_id, food_id, amount, food_name, kcal, protein_g, carbs_g, fat_g)
      values (v_uid, pg_temp.d0() - v_day, v_slot[r.slot], r.id, v_amount, r.name,
              round(v_amount * r.kcal_100 / 100, 1), round(v_amount * r.protein_100 / 100, 1),
              round(v_amount * r.carbs_100 / 100, 1), round(v_amount * r.fat_100 / 100, 1));
    end loop;

    if v_day > 0 then
      -- Personal foods: the tortilla at dinner, the shake at the snack; the pasta salad on three lunches (then deleted).
      select * into v_food from public.foods where id = v_tortilla;
      insert into public.food_log_entries (user_id, log_date, meal_slot_id, food_id, amount, portion_label, food_name, kcal, protein_g, carbs_g, fat_g)
      values (v_uid, pg_temp.d0() - v_day, v_slot[4], v_tortilla, 150, '1 ración', v_food.name,
              round(150 * v_food.kcal_100 / 100, 1), round(150 * v_food.protein_100 / 100, 1),
              round(150 * v_food.carbs_100 / 100, 1), round(150 * v_food.fat_100 / 100, 1));
      select * into v_food from public.foods where id = v_batido;
      insert into public.food_log_entries (user_id, log_date, meal_slot_id, food_id, amount, portion_label, food_name, kcal, protein_g, carbs_g, fat_g)
      values (v_uid, pg_temp.d0() - v_day, v_slot[3], v_batido, 300, '1 vaso', v_food.name,
              round(300 * v_food.kcal_100 / 100, 1), round(300 * v_food.protein_100 / 100, 1),
              round(300 * v_food.carbs_100 / 100, 1), round(300 * v_food.fat_100 / 100, 1));
      if v_day in (5, 12, 19) then
        select * into v_food from public.foods where id = v_deleted;
        insert into public.food_log_entries (user_id, log_date, meal_slot_id, food_id, amount, portion_label, food_name, kcal, protein_g, carbs_g, fat_g)
        values (v_uid, pg_temp.d0() - v_day, v_slot[2], v_deleted, 250, '1 bol', v_food.name,
                round(250 * v_food.kcal_100 / 100, 1), round(250 * v_food.protein_100 / 100, 1),
                round(250 * v_food.carbs_100 / 100, 1), round(250 * v_food.fat_100 / 100, 1));
      end if;
    end if;
  end loop;

  -- The food is deleted after logging: its entries keep food_name and macros (food_id becomes null).
  delete from public.foods where id = v_deleted;
end $$;

-- ---------------------------------------------------------------------------
-- 4. Weekly check-ins: the last 3 weeks answered, the current one not
-- ---------------------------------------------------------------------------
insert into public.weekly_check_ins (user_id, week_start, energy)
select pg_temp.uid(), date_trunc('week', pg_temp.d0()::timestamp)::date - 7 * k, 4 - (k % 2)
from generate_series(1, 3) k;

-- ---------------------------------------------------------------------------
-- 5. Coach: block 1 (ended, reviewed) and block 2 (in progress)
-- ---------------------------------------------------------------------------
-- A saved block: folder, three routines (one per session of the week), plan sessions for each week and the plan row.
create function pg_temp.mk_block(p_start date, p_status text, p_training jsonb, p_nut jsonb, p_weeks int) returns uuid
language plpgsql as $$
declare
  v_plan uuid := gen_random_uuid();
  v_folder uuid;
  v_r uuid[] := '{}';
  v_id uuid;
  w int;
  i int;
begin
  insert into public.routine_folders (user_id, name)
  values (pg_temp.uid(), 'Bloque ' || to_char(p_start, 'DD-MM')) returning id into v_folder;

  for i in 1..3 loop
    insert into public.routines (user_id, name, folder_id, position)
    values (pg_temp.uid(), case i when 1 then 'Torso A' when 2 then 'Pierna' else 'Torso B y cardio' end || ' ' || to_char(p_start, 'DD-MM'),
            v_folder, i - 1)
    returning id into v_id;
    v_r := v_r || v_id;
    insert into public.routine_exercises (routine_id, exercise_id, position, target_sets, target_reps)
    select v_id, pg_temp.ex(k), p - 1, 3, 8
    from unnest(case i when 1 then array['bench-press', 'barbell-row', 'lateral-raise']
                       when 2 then array['leg-press', 'romanian-deadlift', 'leg-curl']
                       else array['lat-pulldown', 'seated-cable-row', 'chin-ups'] end) with ordinality as t(k, p);
    if i = 3 then
      -- The cardio set of the block (a timed set; its planned minutes are in the plan training JSON).
      insert into public.routine_exercises (routine_id, exercise_id, position, target_sets, target_reps)
      values (v_id, pg_temp.ex('stationary-bike'), 3, 1, 1);
    end if;
  end loop;

  insert into public.coach_plans (id, user_id, block_start, block_weeks, intention, nutrition, training, status, folder_id, phase,
                                  phase_options, accepted_at)
  values (v_plan, pg_temp.uid(), p_start, p_weeks, 'fat_loss', p_nut, p_training, p_status, v_folder, 'cut',
          jsonb_build_object('cut_rate_pct', 0.5, 'diet_break', false), pg_temp.at(p_start - 1));

  for w in 1..p_weeks loop
    for i in 1..3 loop
      insert into public.coach_plan_sessions (plan_id, user_id, week, session_index, name, focus, exercises, routine_id)
      values (v_plan, pg_temp.uid(), w, i,
              case i when 1 then 'Torso A' when 2 then 'Pierna' else 'Torso B y cardio' end,
              case i when 1 then array['chest', 'back'] when 2 then array['quads', 'hamstrings'] else array['back', 'cardio'] end,
              (select jsonb_agg(jsonb_build_object('exercise_id', pg_temp.ex(k), 'role', case when p = 1 then 'main' else 'accessory' end, 'sets', 3)
                                order by p)
               from unnest(case i when 1 then array['bench-press', 'barbell-row', 'lateral-raise']
                                  when 2 then array['leg-press', 'romanian-deadlift', 'leg-curl']
                                  else array['lat-pulldown', 'seated-cable-row', 'chin-ups'] end) with ordinality as t(k, p)),
              v_r[i]);
    end loop;
  end loop;
  return v_plan;
end $$;

-- A finished session on a plan routine, through the app's RPC (the trigger links it to the plan session).
-- p_week_load grows the weights week by week; session 3 carries a 25 minute cardio set.
create function pg_temp.do_session(p_plan uuid, p_week int, p_i int, p_day date, p_load numeric) returns void
language plpgsql as $$
declare
  v_routine uuid;
  v_exs jsonb;
begin
  select routine_id into v_routine from public.coach_plan_sessions
  where plan_id = p_plan and week = p_week and session_index = p_i;
  select jsonb_agg(jsonb_build_object(
           'exercise_id', e.exercise_id, 'rest_seconds', 90,
           'sets', (select jsonb_agg(jsonb_build_object('set_type', 'working', 'weight_kg', round(p_load * (case e.position when 0 then 1.0 else 0.7 end) / 2.5) * 2.5,
                                                         'reps', 8 - (s % 2), 'rpe', 8) order by s)
                    from generate_series(1, 3) s)) order by e.position)
  into v_exs
  from public.routine_exercises e where e.routine_id = v_routine;
  if p_i = 3 then
    v_exs := v_exs || jsonb_build_array(jsonb_build_object(
      'exercise_id', pg_temp.ex('stationary-bike'), 'sets', jsonb_build_array(jsonb_build_object('duration_s', 1500))));
  end if;
  perform public.save_completed_workout(jsonb_build_object(
    'name', (select name from public.routines where id = v_routine), 'routine_id', v_routine,
    'started_at', pg_temp.at(p_day, 18), 'duration_s', 3600, 'exercises', v_exs));
end $$;

do $$
declare
  v_uid uuid := pg_temp.uid();
  v_p1 uuid;
  v_p2 uuid;
  v_s1 date := pg_temp.d0() - 70;
  v_s2 date := pg_temp.d0() - 24;
  v_rev jsonb;
  v_pause jsonb;
  k int;
  i int;
begin
  perform pg_temp.as_user();

  -- Block 1: four weeks, a session on days 0, 2 and 4 of each week; ended on d0-43.
  v_p1 := pg_temp.mk_block(v_s1, 'accepted', '{"source":"builder","cardio":{"target_min_week":25}}'::jsonb, pg_temp.nut(2250, -450), 4);
  for k in 0..3 loop
    for i in 1..3 loop
      perform pg_temp.do_session(v_p1, k + 1, i, v_s1 + 7 * k + 2 * (i - 1), 50 + 2.5 * k);
    end loop;
  end loop;

  -- Block 1 review: opened by the RPC, the phase kept; then block 2 replaces it (a single accepted plan at a time).
  v_rev := public.open_coach_block_review(pg_temp.d0());
  if v_rev is not null and exists (select 1 from public.coach_block_reviews
                                   where plan_id = v_p1 and phase_proposal is not null and phase_decision is null) then
    perform public.keep_coach_phase((select id from public.coach_block_reviews where plan_id = v_p1));
  end if;
  update public.coach_plans set status = 'superseded' where id = v_p1;

  -- Block 2: plan week 1 (window 0), a window without workout (d0-17..d0-11: the paused week), plan week 2 (window 2),
  -- plan week 3 started two days ago (window 3 holds today).
  v_p2 := pg_temp.mk_block(v_s2, 'accepted', '{"source":"builder","cardio":{"target_min_week":25}}'::jsonb, pg_temp.nut(2200, -450), 4);
  for i in 1..3 loop
    perform pg_temp.do_session(v_p2, 1, i, v_s2 + 2 * (i - 1), 57.5);
  end loop;
  for i in 1..3 loop
    perform pg_temp.do_session(v_p2, 2, i, pg_temp.d0() - 10 + 2 * (i - 1), 60);
  end loop;
  perform pg_temp.do_session(v_p2, 3, 1, pg_temp.d0() - 2, 62.5);

  -- One extension: the user pushes the end of the block 3 days later (the app's RPC, the same call as the Trainer sheet).
  v_pause := public.coach_block_pause_for(v_uid, v_p2, pg_temp.d0());
  perform public.set_coach_block_end(v_p2, (v_pause->>'block_end')::date + 3, pg_temp.d0());

  -- Reports. The latest finished week (plan week 2) is stored by the RPC on first build and left unseen.
  perform public.coach_week_summary(pg_temp.d0());
  -- The paused week report: record_paused_week_report only builds the previous window of today, so it is stored here with
  -- the exact snapshot it would hold (plan, paused flag and the window dates).
  insert into public.coach_week_reports (user_id, plan_id, week, paused, period_start, period_end, snapshot)
  select v_uid, v_p2, null, true, w.start_date, w.end_date,
         jsonb_build_object('plan_id', v_p2, 'paused', true, 'start_date', w.start_date, 'end_date', w.end_date)
  from public.coach_plan_window_for(v_uid, v_p2, 1) w
  on conflict (user_id, plan_id, period_start) do nothing;
end $$;
