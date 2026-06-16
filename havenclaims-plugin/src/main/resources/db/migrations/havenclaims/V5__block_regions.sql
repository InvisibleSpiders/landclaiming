DROP TABLE IF EXISTS claim_chunks;

CREATE TABLE IF NOT EXISTS claim_block_regions (
    claim_id VARCHAR(36) NOT NULL,
    world_id VARCHAR(36) NOT NULL,
    min_x    INTEGER     NOT NULL,
    min_z    INTEGER     NOT NULL,
    max_x    INTEGER     NOT NULL,
    max_z    INTEGER     NOT NULL,
    PRIMARY KEY (claim_id),
    FOREIGN KEY (claim_id) REFERENCES claims(id) ON DELETE CASCADE
);

ALTER TABLE claim_player_limits RENAME COLUMN chunk_limit TO block_limit;
UPDATE claim_player_limits SET block_limit = block_limit * 256;
