-- T-070 (US-25): one-time purge of the cached Open Food Facts foods and everything that references them.
-- Food data was not stable yet (user decision 2026-10-21); a fresh search/scan refills foods with off_data.
-- Personal foods, meal slots and all workout data are never touched. Idempotent: a re-run deletes nothing.

-- Templates that contain at least one OFF item (to remove them if they end up empty).
create temporary table purge_off_templates as
select distinct i.template_id
from public.meal_template_items i
join public.foods f on f.id = i.food_id
where f.source = 'off';

delete from public.food_log_entries
where food_id in (select id from public.foods where source = 'off');

delete from public.favorite_foods
where food_id in (select id from public.foods where source = 'off');

delete from public.meal_template_items
where food_id in (select id from public.foods where source = 'off');

delete from public.meal_templates t
where t.id in (select template_id from purge_off_templates)
  and not exists (select 1 from public.meal_template_items i where i.template_id = t.id);

-- food_portions are removed by ON DELETE CASCADE.
delete from public.foods where source = 'off';

drop table purge_off_templates;
