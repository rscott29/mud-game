CREATE TABLE world_settings (
    setting_key VARCHAR(100) PRIMARY KEY,
    setting_value VARCHAR(500) NOT NULL
);

INSERT INTO world_settings (setting_key, setting_value)
VALUES ('moderation_filters', 'profanity,sexual_content,hate_speech,harassment');
