-- T-211 (E13-H feedback, 07 section 3 "Taxonomy" > "Lower-body filing"): a new muscle group for hip adduction. The
-- value is added alone in this file: a new enum value cannot be used in the transaction that adds it (the retag is in
-- the next migration).
alter type public.muscle_group add value if not exists 'adductors';
