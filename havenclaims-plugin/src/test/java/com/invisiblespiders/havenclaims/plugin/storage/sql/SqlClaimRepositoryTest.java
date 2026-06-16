package com.invisiblespiders.havenclaims.plugin.storage.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.invisiblespiders.havenclaims.plugin.claim.Claim;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimCreationService;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimIndex;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimMember;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimRegion;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimRole;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimService;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimValidationResult;
import com.invisiblespiders.havenclaims.plugin.claim.OwnerType;
import com.invisiblespiders.havenclaims.api.flag.FlagState;
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
    void savesAndLoadsClaimWithRegionFlagsAndMembers(@TempDir Path tempDirectory) throws Exception {
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
        // Region covering chunks (1,2) and (1,3): blocks [16,32] to [47,63]
        ClaimRegion region = new ClaimRegion(worldId, 16, 32, 47, 63);
        Claim claim = new Claim(
                claimId,
                "Home",
                OwnerType.PLAYER,
                ownerId,
                region,
                Map.of("build", FlagState.OFF, "interact", FlagState.OFF),
                Set.of(
                        new ClaimMember(memberId, ClaimRole.MEMBER),
                        new ClaimMember(managerId, ClaimRole.MANAGER)
                ),
                Set.of(deniedId),
                createdAt,
                createdAt
        );

        repository.saveClaim(claim);

        // findClaimAt uses chunk (1,2) which maps to block range overlapping the region
        assertThat(repository.findClaimAt(worldId, 1, 2)).isPresent().satisfies(loaded -> {
            assertThat(loaded.get().id()).isEqualTo(claimId);
            assertThat(loaded.get().region()).isEqualTo(region);
            assertThat(loaded.get().members()).containsExactlyInAnyOrder(
                    new ClaimMember(memberId, ClaimRole.MEMBER),
                    new ClaimMember(managerId, ClaimRole.MANAGER)
            );
            assertThat(loaded.get().deniedPlayers()).containsExactly(deniedId);
        });
        assertThat(repository.findClaimById(claimId)).isPresent().satisfies(loaded -> {
            assertThat(loaded.get().region()).isEqualTo(region);
        });
        assertThat(repository.findClaimsByOwner(OwnerType.PLAYER, ownerId)).hasSize(1).satisfies(list -> {
            assertThat(list.get(0).region()).isEqualTo(region);
        });
        assertThat(repository.findAllClaims()).hasSize(1).satisfies(list -> {
            assertThat(list.get(0).region()).isEqualTo(region);
        });
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
        // chunk (1,2): blocks [16,32] to [31,47]
        ClaimRegion region = new ClaimRegion(worldId, 16, 32, 31, 47);
        Claim claim = new Claim(
                claimId,
                "Home",
                OwnerType.PLAYER,
                ownerId,
                region,
                Map.of("build", FlagState.OFF),
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
    void roundTripsFlagState(@TempDir Path tempDirectory) throws Exception {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + tempDirectory.resolve("havenclaims.db"));
        applyMigrations(dataSource);
        SqlClaimRepository repository = new SqlClaimRepository(dataSource);
        UUID world = UUID.randomUUID();
        // chunk (1,2): blocks [16,32] to [31,47]
        ClaimRegion region = new ClaimRegion(world, 16, 32, 31, 47);
        Claim claim = new Claim(UUID.randomUUID(), "C", OwnerType.PLAYER, UUID.randomUUID(), region,
                Map.of("container_access", FlagState.ALL, "explosion_damage", FlagState.OFF),
                Set.of(), Set.of(), Instant.now(), Instant.now());
        repository.saveClaim(claim);

        Claim loaded = repository.findClaimById(claim.id()).orElseThrow();
        assertEquals(FlagState.ALL, loaded.flags().get("container_access"));
        assertEquals(FlagState.OFF, loaded.flags().get("explosion_damage"));
    }

    @Test
    void savesTwoSeparateClaimsInPhase1(@TempDir Path tempDirectory) throws Exception {
        // Phase 1: merge is disabled. Creating two claims with ClaimRegion results in two rows.
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
        // First claim: chunk (0,0) — blocks [0,0] to [15,15]
        ClaimRegion firstRegion = new ClaimRegion(worldId, 0, 0, 15, 15);
        Claim first = new Claim(
                UUID.randomUUID(),
                "home",
                OwnerType.PLAYER,
                ownerId,
                firstRegion,
                Map.of("build", FlagState.OFF),
                Set.of(),
                Set.of(deniedId),
                createdAt,
                createdAt
        );
        // Second claim: chunk (2,0) — blocks [32,0] to [47,15]
        ClaimRegion secondRegion = new ClaimRegion(worldId, 32, 0, 47, 15);
        Claim second = new Claim(
                UUID.randomUUID(),
                "home",
                OwnerType.PLAYER,
                ownerId,
                secondRegion,
                Map.of("container_access", FlagState.OFF),
                Set.of(),
                Set.of(),
                createdAt,
                createdAt
        );
        repository.saveClaim(first);
        repository.saveClaim(second);
        claimIndex.add(first);
        claimIndex.add(second);

        // Chunk (1,0) is between the two — blocks [16,0] to [31,15]
        // Same owner, so buffer check is skipped — should be allowed
        ClaimValidationResult result = service.createPlayerClaim(
                ownerId, "Home", new ClaimRegion(worldId, 16, 0, 31, 15));

        assertThat(result.isAllowed()).isTrue();
        // No merge in Phase 1: 3 separate claims
        assertThat(repository.findAllClaims()).hasSize(3);
        // Original first claim still has its denied players
        Claim loadedFirst = repository.findClaimById(first.id()).orElseThrow();
        assertThat(loadedFirst.deniedPlayers()).containsExactly(deniedId);
        assertThat(loadedFirst.region()).isEqualTo(firstRegion);
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
                // SQLite JDBC 3.49.x has a double-finalize bug when using Statement.execute()
                // Use executeUpdate() which avoids the issue.
                try (java.sql.Statement st = connection.createStatement()) {
                    st.executeUpdate(trimmed);
                }
            }
        }
    }
}
