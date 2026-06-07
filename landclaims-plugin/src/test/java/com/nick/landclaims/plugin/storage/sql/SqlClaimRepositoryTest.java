package com.nick.landclaims.plugin.storage.sql;

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
}
