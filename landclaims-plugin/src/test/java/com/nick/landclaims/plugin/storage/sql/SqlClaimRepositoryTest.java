package com.nick.landclaims.plugin.storage.sql;

import static org.assertj.core.api.Assertions.assertThat;

import com.nick.landclaims.plugin.claim.Claim;
import com.nick.landclaims.plugin.claim.ClaimChunk;
import com.nick.landclaims.plugin.claim.ClaimMember;
import com.nick.landclaims.plugin.claim.ClaimRole;
import com.nick.landclaims.plugin.claim.OwnerType;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

class SqlClaimRepositoryTest {
    @Test
    void savesAndLoadsClaimWithChunksFlagsAndMembers(@TempDir Path tempDirectory) throws Exception {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + tempDirectory.resolve("landclaims.db"));
        applyMigrations(dataSource);
        SqlClaimRepository repository = new SqlClaimRepository(dataSource);
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

    @Test
    void deletesClaimAndOwnedRows(@TempDir Path tempDirectory) throws Exception {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + tempDirectory.resolve("landclaims.db"));
        applyMigrations(dataSource);
        SqlClaimRepository repository = new SqlClaimRepository(dataSource);
        UUID claimId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-06-07T00:00:00Z");
        Claim claim = new Claim(
                claimId,
                "Home",
                OwnerType.PLAYER,
                ownerId,
                worldId,
                Set.of(new ClaimChunk(worldId, 1, 2)),
                Map.of("build", false),
                createdAt,
                createdAt
        );
        repository.saveClaim(claim);

        repository.deleteClaim(claimId);

        assertThat(repository.findClaimAt(worldId, 1, 2)).isEmpty();
        assertThat(repository.findClaimById(claimId)).isEmpty();
        assertThat(repository.findAllClaims()).isEmpty();
    }

    private static void applyMigrations(DataSource dataSource) throws Exception {
        try (InputStream in = SqlClaimRepositoryTest.class.getClassLoader()
                .getResourceAsStream("db/migrations/landclaims/V1__initial_claim_schema.sql")) {
            Objects.requireNonNull(in, "V1 migration resource not found");
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            try (Connection connection = dataSource.getConnection()) {
                for (String statement : sql.split(";")) {
                    String trimmed = statement.trim();
                    if (!trimmed.isEmpty()) {
                        connection.prepareStatement(trimmed).execute();
                    }
                }
            }
        }
    }
}
