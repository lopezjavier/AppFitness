-- T-224 (ADR-99): the block is no longer built by a model, so the server objects that fed or cached model calls go.
-- No CASCADE: a dependency error must fail loudly. coach_usage keeps its historical rows (kind has no constraint).
-- Kept on purpose: coach_assert_block, coach_block_rep_ok (save_built_coach_block / save_coach_block call them) and
-- unresolved_severe_discomforts (reserved for the injury task).
drop function if exists public.coach_block_model_input(date);
drop function if exists public.save_folder_review(uuid, text, uuid, text, jsonb);
drop function if exists public.coach_folder_for_review(uuid);
drop table if exists public.coach_folder_reviews;
