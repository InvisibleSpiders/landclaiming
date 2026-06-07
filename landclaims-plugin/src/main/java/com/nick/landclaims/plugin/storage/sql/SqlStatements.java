package com.nick.landclaims.plugin.storage.sql;

import java.util.List;

public final class SqlStatements {
    public static final String CREATE_CLAIMS_TABLE = """
            CREATE TABLE IF NOT EXISTS claims (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                owner_type TEXT NOT NULL,
                owner_uuid TEXT,
                world_id TEXT NOT NULL,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL
            )
            """;

    public static final String CREATE_CLAIM_CHUNKS_TABLE = """
            CREATE TABLE IF NOT EXISTS claim_chunks (
                claim_id TEXT NOT NULL,
                world_id TEXT NOT NULL,
                chunk_x INTEGER NOT NULL,
                chunk_z INTEGER NOT NULL,
                PRIMARY KEY (world_id, chunk_x, chunk_z),
                FOREIGN KEY (claim_id) REFERENCES claims(id) ON DELETE CASCADE
            )
            """;

    public static final String CREATE_CLAIM_FLAGS_TABLE = """
            CREATE TABLE IF NOT EXISTS claim_flags (
                claim_id TEXT NOT NULL,
                flag_key TEXT NOT NULL,
                enabled INTEGER NOT NULL,
                PRIMARY KEY (claim_id, flag_key),
                FOREIGN KEY (claim_id) REFERENCES claims(id) ON DELETE CASCADE
            )
            """;

    public static final List<String> CREATE_SCHEMA = List.of(
            CREATE_CLAIMS_TABLE,
            CREATE_CLAIM_CHUNKS_TABLE,
            CREATE_CLAIM_FLAGS_TABLE
    );

    private SqlStatements() {
    }
}
