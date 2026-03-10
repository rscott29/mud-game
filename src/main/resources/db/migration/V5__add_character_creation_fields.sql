-- V5: Add character creation fields (race, class, pronouns, description)

ALTER TABLE player_profiles ADD COLUMN race VARCHAR(50);
ALTER TABLE player_profiles ADD COLUMN class VARCHAR(50);
ALTER TABLE player_profiles ADD COLUMN pronouns_subject VARCHAR(20);
ALTER TABLE player_profiles ADD COLUMN pronouns_object VARCHAR(20);
ALTER TABLE player_profiles ADD COLUMN pronouns_possessive VARCHAR(20);
ALTER TABLE player_profiles ADD COLUMN description TEXT;

-- Set default values for existing players
UPDATE player_profiles
SET
    race = 'Human',
    class = 'Adventurer',
    pronouns_subject = 'they',
    pronouns_object = 'them',
    pronouns_possessive = 'their',
    description = 'A mysterious adventurer.'
WHERE race IS NULL;

-- Make fields NOT NULL after setting defaults
ALTER TABLE player_profiles ALTER COLUMN race SET DEFAULT 'Human';
ALTER TABLE player_profiles ALTER COLUMN race SET NOT NULL;
ALTER TABLE player_profiles ALTER COLUMN class SET DEFAULT 'Adventurer';
ALTER TABLE player_profiles ALTER COLUMN class SET NOT NULL;
ALTER TABLE player_profiles ALTER COLUMN pronouns_subject SET DEFAULT 'they';
ALTER TABLE player_profiles ALTER COLUMN pronouns_subject SET NOT NULL;
ALTER TABLE player_profiles ALTER COLUMN pronouns_object SET DEFAULT 'them';
ALTER TABLE player_profiles ALTER COLUMN pronouns_object SET NOT NULL;
ALTER TABLE player_profiles ALTER COLUMN pronouns_possessive SET DEFAULT 'their';
ALTER TABLE player_profiles ALTER COLUMN pronouns_possessive SET NOT NULL;

-- Description can be null (optional)
