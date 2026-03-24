ALTER TABLE player_profiles
ADD COLUMN equipped_items TEXT;

UPDATE player_profiles
SET equipped_items = 'main_weapon=' || equipped_weapon_id
WHERE equipped_items IS NULL
  AND equipped_weapon_id IS NOT NULL
  AND equipped_weapon_id <> '';
