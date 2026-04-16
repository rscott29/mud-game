ALTER TABLE player_profiles ADD COLUMN vigor INT NOT NULL DEFAULT 0;
ALTER TABLE player_profiles ADD COLUMN cunning INT NOT NULL DEFAULT 0;
ALTER TABLE player_profiles ADD COLUMN will INT NOT NULL DEFAULT 0;
ALTER TABLE player_profiles ADD COLUMN granted_skill_ids TEXT;