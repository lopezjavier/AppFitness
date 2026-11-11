# Backend feature inventory (draft, T-277 Run B)

US numbers come from the story titles in `planning/epics/`; lines marked (verify) are my best match and the PM should confirm them.

- Edge Function `health` (keepalive ping from `.github/workflows/keepalive.yml`, no JWT): US-02 (Supabase baseline) (verify)
- Edge Function `account-status` (is the account allowed/protected): US-05 sign-in / account (verify)
- Edge Function `delete-account` (delete all my data and the auth user, `delete_account_cascade`): US-45 About and account (verify)
- Edge Function `food-search` (text search in OFF/Gemini-assisted catalogue): US-17 Food text search
- Edge Function `food-barcode` (barcode lookup): US-18 Barcode scan
- Edge Function `food-photo` (AI estimate from a photo): US-48 Add a food from a photo
- Edge Function `exercise-chat` (ask about an exercise): US-130 Ask about an exercise
- Edge Function `coach` kinds `plan_nutrition`, `plan_recompute`, `plan_diet_break`, `plan_phase_change`, `plan_goal_switch` (deterministic nutrition plan, recompute, diet break, phase change, goal switch): US-69, US-99, US-110, US-115 (verify)
- RPC family `save_profile`/`save_preferences`, `sync_google_avatar`, `handle_new_user` (profile and onboarding): US-05, US-06
- RPCs `save_body_measurement`, `delete_body_measurement`, body circumferences (weight and measurements): US-57, US-58
- RPC `weekly check-in` save and read: US-59
- RPCs health sync (`health_sync_state`, `daily_activity`, `sleep_sessions`, `resting_heart_rate`): US-60, US-61, US-62
- RPCs routines (`save_routine`, `update_routine`, folders and ordering, planned duration): US-10, US-35, US-36
- RPCs workout sessions (`start/finish/discard_workout_session`, session history, controls, set types, RPE, metric types, notes): US-11, US-14, US-29, US-30, US-31, US-66
- RPCs `last_performance`, `exercise_history`, `exercise_session_records`, `personal_records`, `weekly_muscle_volume`, `training_summary`, volume options: US-12, US-15, US-28, US-39, US-40, US-41
- RPCs exercise catalogue: favourites, alternatives, custom exercises, equipment swap: US-09, US-96
- RPCs food diary: `save_meal_structure`, meal slots and defaults, templates, portions, favourites, `create_personal_food`, `update_personal_food`, `copy_off_food`, `food_diary_summary`, diary history: US-19, US-21, US-22, US-23, US-24, US-51, US-55
- RPC nutrition goals and macro split: US-07, US-08, US-20
- RPCs coach intake (`save_coach_intake`, `coach_plan_inputs`, `confirm_derived_values`, derived values): US-81, US-68
- RPCs coach plan and block (`coach_builder_inputs`, `save_coach_block`, `accept_coach_plan`, `accept_coach_phase`, `keep_coach_phase`, `attach_coach_block`, block edits, extension, pause, removal, new block, block review): US-72, US-82, US-91, US-94, US-95, US-106
- RPCs progression and deload (`progression_suggestions`, `apply_coach_progression`, `evaluate_coach_deload`, `request/cancel_coach_deload`, `decide_progression_flag`, `undo_coach_change`): US-70, US-75, US-86, US-100, US-104
- RPCs weekly summary, reports and calorie proposal (`coach_week_summary`, `get_coach_report`, `evaluate_coach_calorie_change`, nutrition and stall diagnosis): US-98, US-99, US-114, US-129
- RPCs discomfort log and follow-up (`exercise_discomforts`, `unresolved_severe_discomforts`): US-67, US-108
- RPC notification settings: US-45/US-46 (verify)
- Removed by T-277 (not part of the inventory after the PR): `coach_free_text_lines`, `set_free_text_line_excluded`, table `coach_free_text_exclusions`, `coach_user_intake.free_text`: US-81, US-89 (amended)
- Scheduled jobs: none live (`cleanup-unused-foods` cron was unscheduled by 20270125100200); `keepalive.yml` pings `health` on a schedule from GitHub Actions
