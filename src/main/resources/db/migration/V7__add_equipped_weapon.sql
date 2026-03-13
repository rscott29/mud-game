-- Add equipped weapon tracking to player profiles
ALTER TABLE player_profiles
ADD COLUMN equipped_weapon_id VARCHAR(100);
