-- T-149 (US-65): "Mi entrenamiento" static preferences on training_preferences.
-- Existing owner RLS and table grants cover the new columns; save_preferences is unchanged
-- (the section saves its own row with a direct upsert).

create type public.training_intention as enum ('strength', 'muscle', 'fat_loss', 'recomposition', 'maintenance');
create type public.training_place as enum ('home', 'gym', 'other');

alter table public.training_preferences
  add column intention public.training_intention,
  add column place public.training_place,
  add column equipment public.equipment[] not null default '{}',
  add column days_per_week smallint,
  add column session_minutes smallint,
  add constraint training_preferences_days_per_week_check check (days_per_week between 1 and 7),
  add constraint training_preferences_session_minutes_check
    check (session_minutes between 15 and 180 and session_minutes % 5 = 0);
