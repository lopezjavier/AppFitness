-- T-184 (ADR-85): cardio machines leave `machine` and become their own equipment values. Its own migration: an enum
-- value cannot be used in the same transaction that adds it (the retag comes in the next migration).
alter type public.equipment add value if not exists 'cardio_bike';
alter type public.equipment add value if not exists 'treadmill';
alter type public.equipment add value if not exists 'elliptical';
alter type public.equipment add value if not exists 'rower';
alter type public.equipment add value if not exists 'cardio_other';
