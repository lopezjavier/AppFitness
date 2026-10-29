-- T-156: the wrist roller is loaded, so it logs weight x reps (it was wrongly reps_only).
update public.exercises set metric_type = 'weight_reps'
where not is_custom and key = 'wrist-roller';
