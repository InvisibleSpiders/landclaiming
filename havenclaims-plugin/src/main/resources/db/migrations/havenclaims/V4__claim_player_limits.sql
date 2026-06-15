CREATE TABLE IF NOT EXISTS claim_player_limits (
    player_uuid TEXT NOT NULL PRIMARY KEY,
    chunk_limit  INTEGER NOT NULL CHECK (chunk_limit >= 1)
);
