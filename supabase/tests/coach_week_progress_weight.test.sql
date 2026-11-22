-- pgTAP: the weekly progress builder, run B sections (T-313, US-141): weight, waist, the phase run, the missing codes
-- and the composer header. Rules: planning/07-coach-design.md section 3 "Weekly report: progress first" (4)-(5);
-- contract: tasks/todo.md "## T-313"; trend parity with WeightPaceFit (R6, `04` section 7.4).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(48);

-- ---------------------------------------------------------------------------
-- Fixture helpers (run as the migration owner; every builder function is internal)
-- ---------------------------------------------------------------------------
create function pg_temp.u(n int) returns uuid language sql as $$
  select ('c9313100-0000-4000-8000-0000000000' || lpad(n::text, 2, '0'))::uuid $$;
-- Day k (day 0 is a Monday, so plan weeks starting on day 7j are ISO weeks).
create function pg_temp.d(k int) returns date language sql as $$ select date '2026-03-02' + k $$;
create function pg_temp.ts(k int, h numeric default 8) returns timestamptz language sql as $$
  select (pg_temp.d(k)::timestamp + make_interval(secs => h * 3600)) at time zone 'UTC' $$;

-- A block of `weeks` plan weeks starting on day k0 in the calorie phase given, trained on the first day of every week
-- (so no window is paused).
create function pg_temp.block(n int, k0 int, weeks int, ph text, st text default 'accepted',
                              outcomes jsonb default '[]') returns uuid language plpgsql as $$
declare v uuid := gen_random_uuid();
begin
  insert into public.coach_plans (id, user_id, block_start, block_weeks, intention, nutrition, phase, outcomes, status,
                                  accepted_at)
  values (v, pg_temp.u(n), pg_temp.d(k0), weeks, (case ph when 'cut' then 'fat_loss' else 'muscle' end)::public.training_intention,
          jsonb_build_object('rate_kg_week', case ph when 'cut' then -0.5 else 0.25 end), ph::public.coach_phase, outcomes,
          st, now());
  insert into public.coach_plan_sessions (plan_id, user_id, week, session_index, name, exercises)
  select v, pg_temp.u(n), w, 1, 'Sesion', '[]' from generate_series(1, weeks) w;
  insert into public.workout_sessions (user_id, name, status, started_at, ended_at)
  select pg_temp.u(n), 'W', 'completed', pg_temp.ts(k0 + 7 * (w - 1), 12), pg_temp.ts(k0 + 7 * (w - 1), 13)
  from generate_series(1, weeks) w;
  return v;
end $$;

create function pg_temp.weigh(n int, k int, kg numeric, h numeric default 8) returns void language sql as $$
  insert into public.body_measurements (user_id, measured_at, weight_kg, source)
  values (pg_temp.u(n), pg_temp.ts(k, h), kg, 'manual') $$;
-- Three weigh-in days (days k, k+1, k+2) at the same weight.
create function pg_temp.weigh3(n int, k int, kg numeric) returns void language sql as $$
  select pg_temp.weigh(n, k + i, kg) from generate_series(0, 2) i $$;
create function pg_temp.girth(n int, k int, cm numeric, site text default 'waist', h numeric default 8)
returns void language sql as $$
  insert into public.body_circumferences (user_id, measured_at, site, value_cm)
  values (pg_temp.u(n), pg_temp.ts(k, h), site::public.circumference_site, cm) $$;

create function pg_temp.plan_of(n int) returns uuid language sql as $$
  select id from public.coach_plans where user_id = pg_temp.u(n) and status = 'accepted' $$;
-- Sections for a plan week of a block whose weeks are 7 days: week wk starts on day k0 + 7(wk - 1).
create function pg_temp.wt(n int, plan uuid, wk int, ks int, ke int) returns jsonb language sql as $$
  select public.coach_progress_weight_for(pg_temp.u(n), plan, wk, pg_temp.d(ks), pg_temp.d(ke)) $$;
create function pg_temp.waist(n int, ks int, ke int) returns jsonb language sql as $$
  select public.coach_progress_waist_for(pg_temp.u(n), pg_temp.plan_of(n), 1, pg_temp.d(ks), pg_temp.d(ke)) $$;
create function pg_temp.prog(n int, plan uuid, wk int, ks int, ke int) returns jsonb language sql as $$
  select public.coach_week_progress_for(pg_temp.u(n), plan, wk, pg_temp.d(ks), pg_temp.d(ke)) $$;

insert into auth.users (id, email)
select pg_temp.u(n), 'progressw' || n || '@example.com' from generate_series(1, 7) n;
insert into public.foods (id, source, name, kcal_100) values ('0ff00000-0000-4000-8000-0000000003e1', 'off', 'Alimento w', 100);

-- ---------------------------------------------------------------------------
-- User 1: one cut block from day 0. Week 1: 2 weigh-in days; week 2: 3 days (day 7 has two weigh-ins, mean 80);
-- week 3: 3 days at 79.46; week 4: 3 days at 79.50 (after week 3's end). No diary. A non-weight outcome only.
-- User 7: user B, the same days with much heavier weights and her own waist pair.
-- ---------------------------------------------------------------------------
select pg_temp.block(1, 0, 4, 'cut', outcomes => '[{"kind": "e1rm", "key": "x", "target_value": 120}]');
select pg_temp.weigh(1, 1, 81);
select pg_temp.weigh(1, 3, 81);
select pg_temp.weigh(1, 7, 79.5, 7);
select pg_temp.weigh(1, 7, 80.5, 20);
select pg_temp.weigh(1, 9, 80);
select pg_temp.weigh(1, 11, 80);
select pg_temp.weigh3(1, 14, 79.46);
select pg_temp.weigh3(1, 21, 79.50);

select pg_temp.block(7, 0, 4, 'cut');
select pg_temp.weigh3(7, 1, 150);
select pg_temp.weigh3(7, 7, 150);
select pg_temp.weigh3(7, 14, 150);
select pg_temp.girth(7, 2, 130);
select pg_temp.girth(7, 30, 120);

select is(pg_temp.wt(1, pg_temp.plan_of(1), 3, 14, 20)->'avg_kg', '79.46'::jsonb,
  'the week''s average is the mean of its daily values; later weigh-ins and B''s are excluded');
select is(pg_temp.wt(1, pg_temp.plan_of(1), 3, 14, 20)->'days', '3'::jsonb, 'days counts the weigh-in days of the week');
select is(pg_temp.wt(1, pg_temp.plan_of(1), 2, 7, 13)->'avg_kg', '80.00'::jsonb, 'a day with two weigh-ins counts as their mean');
select is(jsonb_build_array(pg_temp.wt(1, pg_temp.plan_of(1), 3, 14, 20)->'prev_avg_kg', pg_temp.wt(1, pg_temp.plan_of(1), 3, 14, 20)->'prev_days'),
  '[80.00, 3]'::jsonb, 'the previous plan week''s average and days');
select is(pg_temp.wt(1, pg_temp.plan_of(1), 3, 14, 20)->>'change_kg', '-0.5', 'change with both weeks at 3 days or more, one decimal');
select ok(pg_temp.wt(1, pg_temp.plan_of(1), 2, 7, 13)->'change_kg' = 'null'::jsonb, 'no change when the previous week has 2 days');
select ok(pg_temp.wt(1, pg_temp.plan_of(1), 1, 0, 6)->'change_kg' = 'null'::jsonb, 'no change when this week has 2 days');
select is(pg_temp.wt(1, pg_temp.plan_of(1), 4, 21, 27)->>'change_kg', '0.0', 'a change of +0.04 kg rounds to 0.0');
select is(pg_temp.wt(1, pg_temp.plan_of(1), 3, 14, 20)->'start_avg_kg', '80.00'::jsonb,
  'the phase start is the first week with 3 weigh-in days, not week 1 with 2');
select is(pg_temp.wt(1, pg_temp.plan_of(1), 3, 14, 20)->>'change_since_start_kg', '-0.5', 'change since the phase started');
select ok(pg_temp.wt(1, pg_temp.plan_of(1), 2, 7, 13)->'change_since_start_kg' = 'null'::jsonb,
  'the change since the start needs two qualifying weeks');
select is(pg_temp.wt(1, pg_temp.plan_of(1), 3, 14, 20)->'phase_weeks', '3'::jsonb, 'phase weeks: the run''s weeks up to the report week');
select is(pg_temp.wt(1, pg_temp.plan_of(1), 3, 14, 20)->'series',
  jsonb_build_array(jsonb_build_object('start_date', pg_temp.d(0), 'avg_kg', 81.00, 'days', 2),
                    jsonb_build_object('start_date', pg_temp.d(7), 'avg_kg', 80.00, 'days', 3),
                    jsonb_build_object('start_date', pg_temp.d(14), 'avg_kg', 79.46, 'days', 3)),
  'the series: the run''s plan weeks with a weigh-in, oldest first');
select ok(pg_temp.wt(1, pg_temp.plan_of(1), 3, 14, 20)->'outcome' = 'null'::jsonb, 'no weight outcome: outcome null');
select is(pg_temp.wt(1, pg_temp.plan_of(1), 3, 14, 20)->'rate_kg_week', '-0.5'::jsonb, 'the plan''s rate');
select is(pg_temp.wt(7, pg_temp.plan_of(7), 3, 14, 20)->'avg_kg', '150.00'::jsonb, 'B''s own weight is hers');

-- ---------------------------------------------------------------------------
-- User 2: a cut block whose week 2 is extended by 3 days (days 7-16), a weight outcome after an e1RM one, a diary.
-- ---------------------------------------------------------------------------
select pg_temp.block(2, 0, 4, 'cut',
  outcomes => '[{"kind": "e1rm", "key": "x", "target_value": 120}, {"kind": "weight", "key": "weight", "target_value": 75}]');
insert into public.coach_plan_extensions (plan_id, user_id, window_k, days) values (pg_temp.plan_of(2), pg_temp.u(2), 1, 3);
update public.profiles set onboarding_completed = true where id = pg_temp.u(2);
select pg_temp.weigh3(2, 2, 81);
select pg_temp.weigh(2, 7, 80);
select pg_temp.weigh(2, 15, 80.2);
select pg_temp.weigh(2, 16, 80.4);
select pg_temp.weigh(2, 17, 90);
insert into public.food_log_entries (user_id, log_date, meal_slot_id, food_id, amount, food_name, kcal, protein_g, carbs_g, fat_g)
select pg_temp.u(2), pg_temp.d(k), (select id from public.meal_slots where user_id = pg_temp.u(2) and code = 'breakfast'),
       '0ff00000-0000-4000-8000-0000000003e1', 100, 'Alimento', 2000, 10, 10, 10
from generate_series(10, 14) k;

select is(jsonb_build_array(pg_temp.wt(2, pg_temp.plan_of(2), 2, 7, 16)->'avg_kg', pg_temp.wt(2, pg_temp.plan_of(2), 2, 7, 16)->'days'),
  '[80.20, 3]'::jsonb, 'an extended week averages its 10 days');
select is(pg_temp.wt(2, pg_temp.plan_of(2), 2, 7, 16)->'series'->-1,
  jsonb_build_object('start_date', pg_temp.d(7), 'avg_kg', 80.20, 'days', 3), 'the series uses the extended window');
select is(pg_temp.wt(2, pg_temp.plan_of(2), 2, 7, 16)->'outcome', jsonb_build_object('kg', 75, 'deadline', pg_temp.d(30)),
  'the first weight outcome, due at the extended block end');
select is(pg_temp.prog(2, pg_temp.plan_of(2), 2, 7, 16)->'missing', '[]'::jsonb, 'nothing missing with 3 weigh-ins and 5 diary days');
select is(pg_temp.prog(1, pg_temp.plan_of(1), 3, 14, 20)->'missing', '["diary"]'::jsonb, 'diary missing under 5 complete days');
select is(pg_temp.prog(1, pg_temp.plan_of(1), 1, 0, 6)->'missing', '["weigh_ins", "diary"]'::jsonb,
  'weigh-ins missing under 3 weigh-in days');
select is(pg_temp.prog(1, pg_temp.plan_of(1), 3, 14, 20)->'weight', pg_temp.wt(1, pg_temp.plan_of(1), 3, 14, 20),
  'the composer embeds the weight section');

-- ---------------------------------------------------------------------------
-- User 3: blocks Z (cut, days -14..-1), A (gain, days 0-13), B1 (cut, days 14-41), B2 (cut, days 42-69) and C (cut,
-- accepted, from day 70). Every week has 3 weigh-in days: Z at 85, A at 90, the cut run on a line from 80 down 0.5 a
-- week.
-- ---------------------------------------------------------------------------
select pg_temp.block(3, -14, 2, 'cut', 'superseded');
select pg_temp.block(3, 0, 2, 'gain', 'superseded');
select pg_temp.block(3, 14, 4, 'cut', 'superseded');
select pg_temp.block(3, 42, 4, 'cut', 'superseded');
select pg_temp.block(3, 70, 4, 'cut');
select pg_temp.weigh3(3, k, 85) from generate_series(-14, -7, 7) k;
select pg_temp.weigh3(3, k, 90) from generate_series(0, 7, 7) k;
select pg_temp.weigh3(3, 14 + 7 * i, 80 - 0.5 * i) from generate_series(0, 11) i;
create function pg_temp.b(k0 int) returns uuid language sql as $$
  select id from public.coach_plans where user_id = pg_temp.u(3) and block_start = pg_temp.d(k0) $$;

select is(jsonb_build_array(pg_temp.wt(3, pg_temp.b(42), 1, 42, 48)->'prev_avg_kg', pg_temp.wt(3, pg_temp.b(42), 1, 42, 48)->'change_kg'),
  '[78.50, -0.5]'::jsonb, 'week 1''s previous week is the earlier block''s last counted week');
select is(pg_temp.wt(3, pg_temp.b(42), 1, 42, 48)->'phase_weeks', '5'::jsonb,
  'the run stops at the block of another phase (4 weeks of B1 + 1)');
select is(jsonb_build_array(pg_temp.wt(3, pg_temp.b(42), 1, 42, 48)->'start_avg_kg', pg_temp.wt(3, pg_temp.b(42), 1, 42, 48)->'change_since_start_kg'),
  '[80.00, -2.0]'::jsonb, 'the phase start spans the earlier block of the run');
select is(pg_temp.wt(3, pg_temp.b(14), 4, 35, 41)->'trend', '{"slope": -0.500, "low": -0.500, "high": -0.500, "weeks": 4}'::jsonb,
  'the trend window is clipped to the 4 weeks of the run: the earlier blocks'' weeks are left out');
select is(pg_temp.wt(3, pg_temp.b(14), 4, 35, 41)->'phase_weeks', '4'::jsonb, 'a past block''s week reads its run as of that week');
select is(jsonb_array_length(pg_temp.wt(3, pg_temp.b(70), 1, 70, 76)->'series'), 8, 'the series holds at most 8 weeks');
select is(jsonb_build_array(pg_temp.wt(3, pg_temp.b(70), 1, 70, 76)->'series'->0->'start_date', pg_temp.wt(3, pg_temp.b(70), 1, 70, 76)->'series'->-1->'start_date'),
  jsonb_build_array(pg_temp.d(21), pg_temp.d(70)), 'the latest 8 weeks of the run, oldest first');
select is(jsonb_build_array(pg_temp.wt(3, pg_temp.b(70), 1, 70, 76)->'phase_weeks', pg_temp.wt(3, pg_temp.b(70), 1, 70, 76)->'trend'->'weeks'),
  '[9, 8]'::jsonb, 'a 9-week run caps the trend at 8 ISO weeks');
select is(pg_temp.prog(3, pg_temp.b(70), 1, 70, 76) - 'refreshed_at' - 'missing' - 'context' - 'strength' - 'lifts' - 'records' - 'weight' - 'waist',
  jsonb_build_object('as_of', pg_temp.d(76), 'final', false, 'intention', 'fat_loss', 'phase', 'cut'),
  'the header: as of the week''s end, not final, the intention and the calorie phase');

-- ---------------------------------------------------------------------------
-- User 4: the `04` section 7.4 example (weekly 80, 79, 79, 78 in a 4-week cut), as WeightPaceFitTest.
-- ---------------------------------------------------------------------------
select pg_temp.block(4, 0, 4, 'cut');
select pg_temp.weigh3(4, 0, 80);
select pg_temp.weigh3(4, 7, 79);
select pg_temp.weigh3(4, 14, 79);
select pg_temp.weigh3(4, 21, 78);

select is(pg_temp.wt(4, pg_temp.plan_of(4), 4, 21, 27)->'trend'->>'slope', '-0.600', '80, 79, 79, 78: slope -0.6 (R6 parity)');
select is(pg_temp.wt(4, pg_temp.plan_of(4), 4, 21, 27)->'trend'->>'low', '-1.209', 'and interval low -1.209');
select is(pg_temp.wt(4, pg_temp.plan_of(4), 4, 21, 27)->'trend'->>'high', '0.009', 'and interval high 0.009');
select is(pg_temp.wt(4, pg_temp.plan_of(4), 4, 21, 27)->'trend'->'weeks', '4'::jsonb, 'over 4 weekly averages');
select is(pg_temp.wt(4, pg_temp.plan_of(4), 2, 7, 13)->'trend', '{"slope": -1.000, "low": null, "high": null, "weeks": 2}'::jsonb,
  'two weekly points: a slope, no interval');
select is(pg_temp.wt(4, pg_temp.plan_of(4), 1, 0, 6)->'trend', '{"slope": null, "low": null, "high": null, "weeks": 1}'::jsonb,
  'one weekly point: no slope');
select is(jsonb_build_array(pg_temp.wt(4, pg_temp.plan_of(4), 1, 0, 6)->'prev_avg_kg', pg_temp.wt(4, pg_temp.plan_of(4), 1, 0, 6)->'prev_days'),
  '[null, 0]'::jsonb, 'the first week ever has no previous week');

-- ---------------------------------------------------------------------------
-- Users 5 and 6: waist (user 5's block starts on day 7, so days 28-34 are its week 4). User 5: waist on days 1 (90),
-- 2 (86.9), 10 (85.5), 30 (84.0); chest on day 31; hip on day 5.
-- User 6: waist on days 3 (86) and 30 (84); hip on day 38.
-- ---------------------------------------------------------------------------
select pg_temp.block(5, 7, 4, 'cut');
select pg_temp.girth(5, 1, 90);
select pg_temp.girth(5, 2, 86.9);
select pg_temp.girth(5, 10, 85.5);
select pg_temp.girth(5, 30, 84.0);
select pg_temp.girth(5, 31, 100.0, 'chest');
select pg_temp.girth(5, 5, 70.0, 'hip');
select pg_temp.block(6, 0, 4, 'cut');
select pg_temp.girth(6, 3, 86);
select pg_temp.girth(6, 30, 84);
select pg_temp.girth(6, 38, 70, 'hip');

select is(pg_temp.waist(5, 28, 34),
  jsonb_build_object('cm', 84.0, 'date', pg_temp.d(30), 'ref_cm', 86.9, 'ref_date', pg_temp.d(2), 'change_cm', -2.9),
  'the week''s waist against the latest one 28 days older or more; a 2.9 cm change is returned as is');
select ok(pg_temp.waist(6, 28, 34) is null, 'a waist only 27 days older is no pair: null');
select ok(pg_temp.waist(5, 35, 41) is null, 'no waist measurement in the week: null');
select ok(pg_temp.waist(6, 35, 41) is null, 'other sites never make a waist line');
select ok(pg_temp.waist(1, 28, 34) is null, 'B''s waist pair is never A''s');
select is(pg_temp.prog(5, pg_temp.plan_of(5), 4, 28, 34)->'waist', pg_temp.waist(5, 28, 34), 'the composer embeds the waist section');

-- ---------------------------------------------------------------------------
-- Grants
-- ---------------------------------------------------------------------------
select is((select array_agg(f order by f) from unnest(array[
             'public.coach_phase_run_weeks_for(uuid, uuid, integer, date)',
             'public.coach_progress_weight_for(uuid, uuid, integer, date, date)',
             'public.coach_progress_waist_for(uuid, uuid, integer, date, date)']) f
           where has_function_privilege('authenticated', f, 'execute')
              or has_function_privilege('anon', f, 'execute')
              or has_function_privilege('public', f, 'execute')), null,
  'the run B helpers are not executable by authenticated, anon or public');
select is((select count(*)::int from pg_proc p join pg_namespace n on n.oid = p.pronamespace
           where n.nspname = 'public' and p.prosecdef
             and p.proname in ('coach_phase_run_weeks_for', 'coach_progress_weight_for', 'coach_progress_waist_for')), 0,
  'the run B helpers are security invoker');
select set_config('request.jwt.claims', '', true);
select ok(pg_temp.prog(1, pg_temp.plan_of(1), 3, 14, 20)->'weight'->'avg_kg' = '79.46'::jsonb, 'the weight section needs no JWT');

select * from finish();
rollback;
