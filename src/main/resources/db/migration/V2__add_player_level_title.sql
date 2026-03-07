-- Add level and title columns to player_profiles
ALTER TABLE player_profiles
    ADD COLUMN level INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN title VARCHAR(100) NOT NULL DEFAULT 'Adventurer';
