-- Add completed_quests column to store quests a player has finished
-- Stored as comma-separated quest IDs for simplicity
ALTER TABLE player_profiles ADD COLUMN completed_quests TEXT;
