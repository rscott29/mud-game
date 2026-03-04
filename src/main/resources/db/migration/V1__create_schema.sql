-- ─────────────────────────────────────────────────────────────────────────────
-- MUD Game schema – managed by Flyway
-- ─────────────────────────────────────────────────────────────────────────────

-- Player accounts (authentication + brute-force lockout)
CREATE TABLE accounts (
    username        VARCHAR(50)  NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    locked_until    TIMESTAMP,
    failed_attempts INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT pk_accounts PRIMARY KEY (username)
);

-- Player profile (last known in-game position, etc.)
CREATE TABLE player_profiles (
    username        VARCHAR(50)  NOT NULL,
    current_room_id VARCHAR(100) NOT NULL,
    last_seen_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_player_profiles PRIMARY KEY (username),
    CONSTRAINT fk_profile_account
        FOREIGN KEY (username) REFERENCES accounts (username) ON DELETE CASCADE
);

-- Player inventory (item IDs carried by the player)
-- Populated once a pickup/drop mechanic is implemented.
CREATE TABLE inventory_items (
    username    VARCHAR(50)  NOT NULL,
    item_id     VARCHAR(100) NOT NULL,
    acquired_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_inventory_items PRIMARY KEY (username, item_id),
    CONSTRAINT fk_inventory_account
        FOREIGN KEY (username) REFERENCES accounts (username) ON DELETE CASCADE
);

-- NPC positions (persists wandering NPC locations across server restarts)
CREATE TABLE npc_positions (
    npc_id     VARCHAR(100) NOT NULL,
    room_id    VARCHAR(100) NOT NULL,
    updated_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_npc_positions PRIMARY KEY (npc_id)
);
