package com.invisiblespiders.havenclaims.plugin.storage.sql;

import static org.assertj.core.api.Assertions.assertThat;

import com.invisiblespiders.havenclaims.plugin.claim.Claim;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimChunk;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimCreationService;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimIndex;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimMember;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimRole;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimService;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimValidationResult;
import com.invisiblespiders.havenclaims.plugin.claim.OwnerType;
import com.invisiblespiders.havenclaims.plugin.flag.FlagRegistry;
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
        dataSource.setUrl("jdbc:sqlite:" + tempDirectory.resolve("havenclaims.db"));
        applyMigrations(dataSource);
        SqlClaimRepository repository = new SqlClaimRepository(dataSource);
        UUID claimId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        UUID deniedId = UUID.randomUUID();
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
                Set.of(deniedId),
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
        dataSource.setUrl("jdbc:sqlite:" + tempDirectory.resolve("havenclaims.db"));
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

    @Test
    void mergesClaimsWithoutConflictingWithRedundantClaimChunks(@TempDir Path tempDirectory) throws Exception {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + tempDirectory.resolve("havenclaims.db"));
        applyMigrations(dataSource);
        SqlClaimRepository repository = new SqlClaimRepository(dataSource);
        ClaimIndex claimIndex = new ClaimIndex();
        ClaimCreationService service = new ClaimCreationService(
                repository,
                claimIndex,
                new ClaimService(),
                FlagRegistry.createDefault(),
                3,
                3,
                32
        );
        UUID ownerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        UUID deniedId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-06-07T00:00:00Z");
        Claim first = new Claim(
                UUID.randomUUID(),
                "home",
                OwnerType.PLAYER,
                ownerId,
                worldId,
                Set.of(new ClaimChunk(worldId, 0, 0)),
                Map.of("build", false),
                Set.of(),
                Set.of(deniedId),
                createdAt,
                createdAt
        );
        Claim second = new Claim(
                UUID.randomUUID(),
                "home",
                OwnerType.PLAYER,
                ownerId,
                worldId,
                Set.of(new ClaimChunk(worldId, 2, 0)),
                Map.of("container_access", false),
                Set.of(),
                Set.of(),
                createdAt,
                createdAt
        );
        repository.saveClaim(first);
        repository.saveClaim(second);
        claimIndex.add(first);
        claimIndex.add(second);

        ClaimValidationResult result = service.createPlayerClaim(ownerId, "Home", Set.of(new ClaimChunk(worldId, 1, 0)));

        assertThat(result.isAllowed()).isTrue();
        assertThat(repository.findAllClaims()).hasSize(1);
        Claim merged = repository.findAllClaims().get(0);
        assertThat(merged.id()).isEqualTo(first.id());
        assertThat(merged.name()).isEqualTo("Home");
        assertThat(merged.deniedPlayers()).containsExactly(deniedId);
        assertThat(merged.claimChunks()).containsExactlyInAnyOrder(
                new ClaimChunk(worldId, 0, 0),
                new ClaimChunk(worldId, 1, 0),
                new ClaimChunk(worldId, 2, 0)
        );
    }

    private static void applyMigrations(DataSource dataSource) throws Exception {
        try (InputStream indexIn = SqlClaimRepositoryTest.class.getClassLoader()
                .getResourceAsStream("db/migrations/havenclaims/migrations.index");
             Connection connection = dataSource.getConnection()) {
            Objects.requireNonNull(indexIn, "migration index resource not found");
            String index = new String(indexIn.readAllBytes(), StandardCharsets.UTF_8);
            for (String line : index.split("\\R")) {
                String migration = line.trim();
                if (migration.isEmpty() || migration.startsWith("#")) {
                    continue;
                }
                try (InputStream migrationIn = SqlClaimRepositoryTest.class.getClassLoader()
                        .getResourceAsStream("db/migrations/havenclaims/" + migration)) {
                    Objects.requireNonNull(migrationIn, migration + " resource not found");
                    applySql(connection, new String(migrationIn.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
        }
    }

    private static void applySql(Connection connection, String sql) throws Exception {
                for (String statement : sql.split(";")) {
                    String trimmed = statement.trim();
                    if (!trimmed.isEmpty()) {
                        connection.prepareStatement(trimmed).execute();
                    }
                }
    }
}
