package com.nick.landclaims.plugin.limit;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

class SqlClaimLimitRepositoryTest {
    @Test
    void returnsEmptyWhenNoRowExists(@TempDir Path tmp) throws Exception {
        SqlClaimLimitRepository repo = repo(tmp);
        assertThat(repo.getLimit(UUID.randomUUID())).isEmpty();
    }

    @Test
    void setsAndGetsLimit(@TempDir Path tmp) throws Exception {
        SqlClaimLimitRepository repo = repo(tmp);
        UUID player = UUID.randomUUID();
        repo.setLimit(player, 25);
        assertThat(repo.getLimit(player)).hasValue(25);
    }

    @Test
    void setLimitUpserts(@TempDir Path tmp) throws Exception {
        SqlClaimLimitRepository repo = repo(tmp);
        UUID player = UUID.randomUUID();
        repo.setLimit(player, 10);
        repo.setLimit(player, 30);
        assertThat(repo.getLimit(player)).hasValue(30);
    }

    private static SqlClaimLimitRepository repo(Path tmp) throws Exception {
        SQLiteDataSource ds = new SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + tmp.resolve("test.db"));
        applyMigrations(ds);
        return new SqlClaimLimitRepository(ds);
    }

    private static void applyMigrations(DataSource dataSource) throws Exception {
        try (InputStream indexIn = SqlClaimLimitRepositoryTest.class.getClassLoader()
                .getResourceAsStream("db/migrations/landclaims/migrations.index");
             Connection connection = dataSource.getConnection()) {
            Objects.requireNonNull(indexIn, "migration index resource not found");
            String index = new String(indexIn.readAllBytes(), StandardCharsets.UTF_8);
            for (String line : index.split("\\R")) {
                String migration = line.trim();
                if (migration.isEmpty() || migration.startsWith("#")) continue;
                try (InputStream migIn = SqlClaimLimitRepositoryTest.class.getClassLoader()
                        .getResourceAsStream("db/migrations/landclaims/" + migration)) {
                    Objects.requireNonNull(migIn, migration + " resource not found");
                    applySql(connection, new String(migIn.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
        }
    }

    private static void applySql(Connection connection, String sql) throws Exception {
        for (String stmt : sql.split(";")) {
            String trimmed = stmt.trim();
            if (!trimmed.isEmpty()) {
                try (java.sql.Statement st = connection.createStatement()) {
                    st.executeUpdate(trimmed);
                }
            }
        }
    }
}
