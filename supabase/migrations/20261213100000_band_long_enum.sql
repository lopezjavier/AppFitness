-- T-179 (ADR-83): long band as equipment. Its own migration: an enum value cannot be used in the same
-- transaction that adds it (the catalogue rows come in the next migration).
alter type public.equipment add value if not exists 'band_long';
