-- T-273 (E18, ADR-105): the automatic cleanup of unused personal foods (T-272, US-133) is dropped.
-- The job and the function go; pg_cron stays enabled, and food_log_entries.food_id stays nullable
-- (on delete set null) so a diary entry still outlives a food deleted by hand.

select cron.unschedule(jobid) from cron.job where jobname = 'cleanup-unused-foods';

drop function if exists public.cleanup_unused_foods();
