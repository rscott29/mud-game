-- Add active_quests column to store in-progress quest state as JSON
ALTER TABLE player_profiles ADD COLUMN active_quests TEXT;
