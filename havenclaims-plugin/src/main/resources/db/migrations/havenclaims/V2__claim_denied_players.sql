CREATE TABLE IF NOT EXISTS claim_denied_players (
    claim_id CHAR(36) NOT NULL,
    player_uuid CHAR(36) NOT NULL,
    PRIMARY KEY (claim_id, player_uuid),
    FOREIGN KEY (claim_id) REFERENCES claims(id) ON DELETE CASCADE
);
