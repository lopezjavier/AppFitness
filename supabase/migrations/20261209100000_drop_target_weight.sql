-- ADR-78 (T-162): the target weight is no longer a user input; the accepted coach plan carries the weight
-- outcome. Dropping the column also drops its range check and its column-level UPDATE grant.
-- Data loss is intended.
alter table public.profiles drop column target_weight_kg;
