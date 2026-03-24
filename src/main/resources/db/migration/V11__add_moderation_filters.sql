ALTER TABLE player_profiles ADD COLUMN moderation_filters VARCHAR(200);

UPDATE player_profiles
SET moderation_filters = 'profanity,sexual_content,hate_speech,harassment'
WHERE moderation_filters IS NULL;

ALTER TABLE player_profiles ALTER COLUMN moderation_filters
    SET DEFAULT 'profanity,sexual_content,hate_speech,harassment';

ALTER TABLE player_profiles ALTER COLUMN moderation_filters
    SET NOT NULL;
