package com.invisiblespiders.havenclaims.plugin.storage.sql;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class HavenClaimsMigrationResourceTest {
    @Test
    void migrationIndexListsSchemaMigrationsInOrder() throws IOException {
        String index = resource("db/migrations/havenclaims/migrations.index");

        assertThat(index.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#")))
                .containsExactly(
                        "V1__initial_claim_schema.sql",
                        "V2__claim_denied_players.sql"
                );
    }

    @Test
    void initialSchemaMigrationCreatesClaimTables() throws IOException {
        String migration = resource("db/migrations/havenclaims/V1__initial_claim_schema.sql");

        assertThat(migration)
                .contains("CREATE TABLE IF NOT EXISTS claims")
                .contains("CREATE TABLE IF NOT EXISTS claim_chunks")
                .contains("CREATE TABLE IF NOT EXISTS claim_flags")
                .contains("CREATE TABLE IF NOT EXISTS claim_members")
                .contains("PRIMARY KEY (world_id, chunk_x, chunk_z)")
                .contains("PRIMARY KEY (claim_id, member_uuid)");
    }

    @Test
    void deniedPlayersMigrationCreatesClaimDeniedPlayersTable() throws IOException {
        String migration = resource("db/migrations/havenclaims/V2__claim_denied_players.sql");

        assertThat(migration)
                .contains("CREATE TABLE IF NOT EXISTS claim_denied_players")
                .contains("player_uuid CHAR(36) NOT NULL")
                .contains("PRIMARY KEY (claim_id, player_uuid)");
    }

    private static String resource(String path) throws IOException {
        try (InputStream inputStream = HavenClaimsMigrationResourceTest.class.getClassLoader().getResourceAsStream(path)) {
            assertThat(inputStream).as(path).isNotNull();
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
