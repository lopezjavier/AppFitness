-- T-243 (E15, US-109): the block-end notice is a fifth notification kind (its own switch in Preferencias). A new enum
-- value cannot be used in the transaction that adds it, so it lives alone in this file. Existing notification_settings
-- rows are untouched (a missing row means enabled).
alter type public.notification_kind add value if not exists 'block_end';
