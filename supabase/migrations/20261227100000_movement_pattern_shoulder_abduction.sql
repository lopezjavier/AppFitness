-- T-205 (E13, ADR-93, 07 section 3 "Taxonomy"): a new movement pattern for the lateral-raise family. The value is
-- added alone in this file: a new enum value cannot be used in the transaction that adds it (the retag is in the
-- next migration).
alter type public.movement_pattern add value if not exists 'shoulder_abduction';
