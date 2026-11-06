-- T-231 (E14, US-98): the weekly summary notice is a fourth notification kind. A new enum value cannot be used in the
-- transaction that adds it, so it lives alone in this file. Existing notification_settings rows are untouched
-- (a missing row means enabled).
alter type public.notification_kind add value if not exists 'week_summary';
