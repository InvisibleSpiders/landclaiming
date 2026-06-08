CREATE TABLE IF NOT EXISTS claims (
    id CHAR(36) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    owner_type VARCHAR(32) NOT NULL,
    owner_uuid CHAR(36),
    world_id CHAR(36) NOT NULL,
    created_at VARCHAR(32) NOT NULL,
    updated_at VARCHAR(32) NOT NULL
);

CREATE TABLE IF NOT EXISTS claim_chunks (
    claim_id CHAR(36) NOT NULL,
    world_id CHAR(36) NOT NULL,
    chunk_x INTEGER NOT NULL,
    chunk_z INTEGER NOT NULL,
    PRIMARY KEY (world_id, chunk_x, chunk_z),
    FOREIGN KEY (claim_id) REFERENCES claims(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_claim_chunks_claim_id ON claim_chunks(claim_id);

CREATE TABLE IF NOT EXISTS claim_flags (
    claim_id CHAR(36) NOT NULL,
    flag_key VARCHAR(64) NOT NULL,
    enabled INTEGER NOT NULL,
    PRIMARY KEY (claim_id, flag_key),
    FOREIGN KEY (claim_id) REFERENCES claims(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS claim_members (
    claim_id CHAR(36) NOT NULL,
    member_uuid CHAR(36) NOT NULL,
    role VARCHAR(32) NOT NULL,
    PRIMARY KEY (claim_id, member_uuid),
    FOREIGN KEY (claim_id) REFERENCES claims(id) ON DELETE CASCADE
);
