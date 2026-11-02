-- T-201 (ADR-92, planning/07-coach-design.md section 3 "Barbell, EZ bar and dumbbell alternatives"):
--   * the same exercise done with a barbell, an EZ bar or dumbbells is an alternative at 1.00, both directions
--     (one row per pair, exercise_id < alternative_id as the table requires): 34 groups, 71 pairs;
--   * an alternative below 0.90 is never stored: such rows are deleted and the check becomes [0.90, 1].
-- Other pairs of the curated graph keep their scores. Idempotent.

do $$
declare
  v_missing text;
  v_present integer;
begin
  create temporary table tmp_equipment_groups (grp text, key text) on commit drop;
  insert into tmp_equipment_groups (grp, key) values
    ('row', 'barbell-row'), ('row', 'bent-over-ez-bar-row'), ('row', 'bent-over-db-row'),
    ('bench', 'bench-press'), ('bench', 'ez-bar-bench-press'), ('bench', 'db-bench-press'),
    ('incline', 'incline-bench-press'), ('incline', 'incline-bench-ez-bar-press'), ('incline', 'incline-db-press'),
    ('decline', 'decline-bench-press-barbell'), ('decline', 'decline-bench-press-ez-bar'), ('decline', 'decline-bench-press'),
    ('close-bench', 'close-grip-bench-press'), ('close-bench', 'close-grip-ez-bar-bench-press'), ('close-bench', 'close-grip-db-bench-press'),
    ('floor-press', 'floor-press'), ('floor-press', 'floor-ez-bar-press'), ('floor-press', 'dumbbell-floor-press'),
    ('standing-press', 'ohp'), ('standing-press', 'dumbbell-shoulder-press'),
    ('seated-press', 'seated-barbell-overhead-press'), ('seated-press', 'seated-db-press'),
    ('push-press', 'push-press'), ('push-press', 'dumbbell-push-press'),
    ('curl', 'barbell-curl'), ('curl', 'ez-bar-curl'), ('curl', 'bicep-curl'),
    ('close-curl', 'close-grip-barbell-curl'), ('close-curl', 'close-grip-ez-bar-curl'),
    ('preacher', 'barbell-preacher-curl'), ('preacher', 'preacher-curl'), ('preacher', 'db-preacher-curl'),
    ('reverse-curl', 'reverse-curl'), ('reverse-curl', 'ez-bar-reverse-curl'), ('reverse-curl', 'db-reverse-curl'),
    ('spider-curl', 'ez-bar-spider-curl'), ('spider-curl', 'spider-curl'),
    ('wrist-curl', 'barbell-wrist-curl'), ('wrist-curl', 'ez-bar-wrist-curl'), ('wrist-curl', 'wrist-curl'),
    ('shrug', 'shrug'), ('shrug', 'ez-bar-shrug'), ('shrug', 'db-shrug'),
    ('upright-row', 'upright-row'), ('upright-row', 'ez-bar-upright-row'), ('upright-row', 'dumbbell-upright-row'),
    ('front-raise', 'barbell-front-raise'), ('front-raise', 'ez-bar-front-raise'), ('front-raise', 'dumbbell-front-raise'),
    ('overhead-ext', 'barbell-overhead-extension'), ('overhead-ext', 'ez-bar-overhead-extension'), ('overhead-ext', 'overhead-tricep-extension'),
    ('lying-ext', 'skull-crusher'), ('lying-ext', 'ez-bar-lying-tricep-extension'), ('lying-ext', 'db-skull-crusher'), ('lying-ext', 'lying-tricep-extension'),
    ('pullover', 'barbell-pullover'), ('pullover', 'ez-bar-pullover'), ('pullover', 'db-pullover'),
    ('bent-arm-pullover', 'bent-arm-barbell-pullover'), ('bent-arm-pullover', 'bent-arm-ez-bar-pullover'),
    ('supinated-row', 'reverse-grip-bent-over-row'), ('supinated-row', 'ez-bar-reverse-grip-row'),
    ('deadlift', 'deadlift'), ('deadlift', 'dumbbell-deadlift'),
    ('rdl', 'romanian-deadlift'), ('rdl', 'ez-bar-romanian-deadlift'), ('rdl', 'dumbbell-romanian-deadlift'),
    ('back-squat', 'squat'), ('back-squat', 'db-squat'),
    ('front-squat', 'front-squat'), ('front-squat', 'dumbbell-front-squat'),
    ('sumo-squat', 'sumo-squat'), ('sumo-squat', 'db-sumo-squat'),
    ('lunge', 'barbell-lunge'), ('lunge', 'db-lunge'),
    ('reverse-lunge', 'barbell-reverse-lunge'), ('reverse-lunge', 'reverse-lunge'),
    ('bulgarian', 'barbell-bulgarian-split-squat'), ('bulgarian', 'bulgarian-split-squat'),
    ('hip-thrust', 'hip-thrust'), ('hip-thrust', 'dumbbell-hip-thrust'),
    ('calf-raise', 'barbell-calf-raise'), ('calf-raise', 'dumbbell-calf-raise'),
    ('bench-pull', 'bench-pull'), ('bench-pull', 'dumbbell-bench-pull');

  select string_agg(g.key, ', ') into v_missing
  from tmp_equipment_groups g
  where not exists (select 1 from public.exercises e where e.key = g.key and not e.is_custom);
  if v_missing is not null then
    raise exception 'alternatives: catalogue keys missing: %', v_missing;
  end if;

  insert into public.exercise_alternatives (exercise_id, alternative_id, similarity)
  select least(ea.id, eb.id), greatest(ea.id, eb.id), 1.00
  from tmp_equipment_groups a
  join tmp_equipment_groups b on b.grp = a.grp and a.key < b.key
  join public.exercises ea on ea.key = a.key and not ea.is_custom
  join public.exercises eb on eb.key = b.key and not eb.is_custom
  on conflict (exercise_id, alternative_id) do update set similarity = 1.00;

  select count(*) into v_present
  from tmp_equipment_groups a
  join tmp_equipment_groups b on b.grp = a.grp and a.key < b.key
  join public.exercises ea on ea.key = a.key and not ea.is_custom
  join public.exercises eb on eb.key = b.key and not eb.is_custom
  join public.exercise_alternatives x
    on x.exercise_id = least(ea.id, eb.id) and x.alternative_id = greatest(ea.id, eb.id) and x.similarity = 1.00;
  if v_present <> 71 then
    raise exception 'alternatives: expected 71 pairs at 1.00, found %', v_present;
  end if;
end $$;

-- Floor: nothing below 0.90 is stored.
delete from public.exercise_alternatives where similarity < 0.90;
alter table public.exercise_alternatives drop constraint exercise_alternatives_similarity_range;
alter table public.exercise_alternatives
  add constraint exercise_alternatives_similarity_range check (similarity >= 0.90 and similarity <= 1);
