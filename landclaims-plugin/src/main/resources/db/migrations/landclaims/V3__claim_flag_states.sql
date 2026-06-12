-- Tri-state flag values. Add a state column and backfill from the legacy enabled flag
-- so existing claims keep their current behavior. The enabled column is retained (now
-- nullable in effect; still written by the plugin for rollback safety).
ALTER TABLE claim_flags ADD COLUMN state TEXT;

UPDATE claim_flags
SET state = CASE
    WHEN flag_key IN ('build','break','interact','container_access','door_access','switch_access','redstone_access')
        THEN CASE WHEN enabled = 1 THEN 'OFF' ELSE 'VISITORS' END
    WHEN flag_key IN ('entity_damage','item_pickup','item_drop','crop_trample')
        THEN CASE WHEN enabled = 1 THEN 'OFF' ELSE 'ALL' END
    WHEN flag_key IN ('piston_protection','fluid_flow','explosion_damage','fire_spread','mob_griefing','remove_hostile_entities','remove_passive_entities')
        THEN CASE WHEN enabled = 1 THEN 'ALL' ELSE 'OFF' END
    ELSE CASE WHEN enabled = 1 THEN 'ALL' ELSE 'OFF' END
END
WHERE state IS NULL;
