package com.nick.landclaims.plugin.storage.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.nick.landclaims.plugin.claim.Claim;
import com.nick.landclaims.plugin.claim.ClaimChunk;
import com.nick.landclaims.plugin.claim.ClaimMember;
import com.nick.landclaims.plugin.claim.ClaimRole;
import com.nick.landclaims.plugin.claim.OwnerType;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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

        assertThat(SqlStatements.CREATE_CLAIM_MEMBERS_TABLE)
                .contains("claim_id CHAR(36) NOT NULL")
                .contains("member_uuid CHAR(36) NOT NULL")
                .contains("role VARCHAR(")
                .contains("PRIMARY KEY (claim_id, member_uuid)");
    }

    @Test
    void savesAndLoadsClaimWithChunksFlagsAndMembers(@TempDir Path tempDirectory) {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + tempDirectory.resolve("landclaims.db"));
        SqlClaimRepository repository = new SqlClaimRepository(dataSource);
        repository.initialize();
        UUID claimId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-06-07T00:00:00Z");
        Claim claim = new Claim(
                claimId,
                "Home",
                OwnerType.PLAYER,
                ownerId,
                worldId,
                Set.of(new ClaimChunk(worldId, 1, 2), new ClaimChunk(worldId, 1, 3)),
                Map.of("build", false, "interact", false),
                Set.of(
                        new ClaimMember(memberId, ClaimRole.MEMBER),
                        new ClaimMember(managerId, ClaimRole.MANAGER)
                ),
                createdAt,
                createdAt
        );

        repository.saveClaim(claim);

        assertThat(repository.findClaimAt(worldId, 1, 2)).contains(claim);
        assertThat(repository.findClaimById(claimId)).contains(claim);
        assertThat(repository.findClaimsByOwner(OwnerType.PLAYER, ownerId)).containsExactly(claim);
        assertThat(repository.findAllClaims()).containsExactly(claim);
    }
}
