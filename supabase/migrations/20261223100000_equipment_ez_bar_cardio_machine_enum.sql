-- T-201 (ADR-92): two new equipment values, "Barra Z" and one "Máquina de cardio". Its own migration: an enum value
-- cannot be used in the same transaction that adds it. The five cardio values of T-184 stay in the type, unused
-- (Postgres cannot drop an enum value); the next migrations move every row and saved list off them.
alter type public.equipment add value if not exists 'ez_bar';
alter type public.equipment add value if not exists 'cardio_machine';
