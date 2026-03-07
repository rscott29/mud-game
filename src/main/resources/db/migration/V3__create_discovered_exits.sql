-- Tracks which hidden exits each player has already discovered.
-- Persisted immediately on discovery so the reveal survives reconnects.
CREATE TABLE discovered_exits (
    username  VARCHAR(50)  NOT NULL,
    room_id   VARCHAR(100) NOT NULL,
    direction VARCHAR(10)  NOT NULL,
    CONSTRAINT pk_discovered_exits PRIMARY KEY (username, room_id, direction),
    CONSTRAINT fk_discovered_exits_account
        FOREIGN KEY (username) REFERENCES accounts (username) ON DELETE CASCADE
);
