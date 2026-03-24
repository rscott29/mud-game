CREATE TABLE persisted_corpses (
    corpse_id  VARCHAR(150) PRIMARY KEY,
    room_id    VARCHAR(100) NOT NULL,
    owner_name VARCHAR(100) NOT NULL,
    item_ids   TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_persisted_corpses_room_id ON persisted_corpses (room_id);
CREATE INDEX idx_persisted_corpses_expires_at ON persisted_corpses (expires_at);
