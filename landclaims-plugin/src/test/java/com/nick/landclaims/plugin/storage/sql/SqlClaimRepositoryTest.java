package com.nick.landclaims.plugin.storage.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;

class SqlClaimRepositoryTest {
    @Test
    void initializesInMemorySqliteSchema() {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite::memory:");
        SqlClaimRepository repository = new SqlClaimRepository(dataSource);

        assertThatCode(repository::initialize).doesNotThrowAnyException();
    }

    @Test
    void createStatementsUseBoundedStringColumnsForKeys() {
        assertThat(SqlStatements.CREATE_SCHEMA)
                .noneMatch(statement -> statement.contains("TEXT PRIMARY KEY"));

        assertThat(SqlStatements.CREATE_CLAIMS_TABLE)
                .contains("id CHAR(36) PRIMARY KEY")
                .contains("name VARCHAR(")
                .contains("owner_type VARCHAR(")
                .contains("owner_uuid CHAR(36)")
                .contains("world_id CHAR(36) NOT NULL");

        assertThat(SqlStatements.CREATE_CLAIM_CHUNKS_TABLE)
                .contains("claim_id CHAR(36) NOT NULL")
                .contains("world_id CHAR(36) NOT NULL")
                .contains("PRIMARY KEY (world_id, chunk_x, chunk_z)");

        assertThat(SqlStatements.CREATE_CLAIM_FLAGS_TABLE)
                .contains("claim_id CHAR(36) NOT NULL")
                .contains("flag_key VARCHAR(")
                .contains("PRIMARY KEY (claim_id, flag_key)");
    }
}
