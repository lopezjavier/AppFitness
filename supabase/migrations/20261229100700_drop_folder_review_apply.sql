-- T-216 (ADR-97): the folder review is a deterministic checklist computed on the device, so applying a model review in a
-- copy of the folder no longer exists. Grep proved the only callers were the client applyCopy (removed in the same task),
-- their tests and comments; block-from-folder applies its changes in TypeScript (blockFromFolder.ts).
-- save_folder_review, coach_folder_reviews and coach_folder_for_review stay (block-from-folder reads them).
drop function if exists public.apply_folder_review_copy(uuid, text[]);
drop function if exists public.coach_apply_change(jsonb, jsonb);
