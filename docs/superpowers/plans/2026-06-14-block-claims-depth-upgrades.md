# Block Claims Depth Upgrades Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move LandClaims from chunk-only claims to block-area 2D claims with configurable vertical protection and HavenVault per-claim depth upgrades.

**Architecture:** LandClaims owns claim geometry, protection checks, storage, migrations, and ServicesManager APIs. HavenVault owns pricing, requirements, payment, refund handling, and Dialog flows. Runtime lookups use a chunk-spatial index over exact block regions so protection checks stay fast while borders and limits become block-precise.

**Tech Stack:** Java 25, Paper API 26.1.2, Gradle Kotlin DSL, JUnit 5, AssertJ, Mockito, HavenCore `HavenDataSource`, Bukkit `ServicesManager`, Paper Dialog API.

---

## Scope Check

The approved design spans two plugins and several independent surfaces. Execute it as separate PRs in this order:

1. LandClaims API and model foundation.
2. LandClaims storage migration and repository support.
3. LandClaims protection, selection, visuals, and menus.
4. HavenVault claim-block allowance upgrades.
5. HavenVault selected-claim depth upgrades.
6. Cross-plugin routing from LandClaims claim menu to HavenVault.

Each task below is a mergeable unit with its own tests and commit.

## File Structure

LandClaims API:

- Create `landclaims-api/src/main/java/com/nick/landclaims/api/claim/ClaimRegionView.java`: public immutable block-region view.
- Create `landclaims-api/src/main/java/com/nick/landclaims/api/claim/ClaimVerticalProtection.java`: public vertical protection DTO.
- Create `landclaims-api/src/main/java/com/nick/landclaims/api/claim/ClaimUpgradeTarget.java`: public HavenVault target DTO.
- Create `landclaims-api/src/main/java/com/nick/landclaims/api/claim/ClaimUpgradeResult.java`: public upgrade result DTO.
- Create `landclaims-api/src/main/java/com/nick/landclaims/api/limit/LandClaimsAllowanceService.java`: block allowance service.
- Create `landclaims-api/src/main/java/com/nick/landclaims/api/upgrade/LandClaimsUpgradeService.java`: per-claim upgrade service.
- Modify `landclaims-api/src/main/java/com/nick/landclaims/api/claim/ClaimView.java`: add `regions()` while keeping `chunks()` for compatibility.

LandClaims plugin:

- Create `landclaims-plugin/src/main/java/com/nick/landclaims/plugin/claim/ClaimRegion.java`: internal region record and geometry helpers.
- Create `landclaims-plugin/src/main/java/com/nick/landclaims/plugin/claim/VerticalProtectionConfig.java`: config parser and world bounds resolver.
- Modify `landclaims-plugin/src/main/java/com/nick/landclaims/plugin/claim/Claim.java`: store regions and keep legacy chunks derived during compatibility.
- Modify `landclaims-plugin/src/main/java/com/nick/landclaims/plugin/claim/ClaimIndex.java`: index regions by overlapping chunks.
- Modify `landclaims-plugin/src/main/java/com/nick/landclaims/plugin/claim/ClaimService.java`: build block rectangles and buffer checks.
- Modify `landclaims-plugin/src/main/java/com/nick/landclaims/plugin/storage/sql/SqlClaimRepository.java`: save/load `claim_regions`.
- Create `landclaims-plugin/src/main/java/com/nick/landclaims/plugin/limit/AllowanceService.java`: block allowance implementation.
- Modify `landclaims-plugin/src/main/java/com/nick/landclaims/plugin/limit/LimitService.java`: compatibility adapter over block allowance.
- Create `landclaims-plugin/src/main/java/com/nick/landclaims/plugin/upgrade/ClaimUpgradeService.java`: LandClaimsUpgradeService implementation.
- Modify `landclaims-plugin/src/main/java/com/nick/landclaims/plugin/LandClaimsPlugin.java`: register allowance and upgrade APIs.
- Modify `landclaims-plugin/src/main/resources/db/migrations/landclaims/migrations.index`: append V5 migration.
- Create `landclaims-plugin/src/main/resources/db/migrations/landclaims/V5__block_claim_regions.sql`: block-region schema and migration from chunks.
- Modify selection, protection, visual, command, and UI classes after the storage/model foundation lands.

HavenVault:

- Modify `havenvault-plugin/src/main/java/dev/invisiblespiders/havenvault/plugin/claim/ClaimLimitService.java`: replace chunk vocabulary with block allowance abstraction.
- Create `havenvault-plugin/src/main/java/dev/invisiblespiders/havenvault/plugin/claim/ClaimAllowanceService.java`: reflection wrapper over LandClaimsAllowanceService.
- Create `havenvault-plugin/src/main/java/dev/invisiblespiders/havenvault/plugin/claim/ClaimDepthUpgradeService.java`: selected-claim purchase orchestration.
- Modify `havenvault-plugin/src/main/java/dev/invisiblespiders/havenvault/plugin/claim/ClaimUpgradeConfig.java`: add `allowance` and `depth` sections.
- Modify `havenvault-plugin/src/main/java/dev/invisiblespiders/havenvault/plugin/dialog/UpgradesDialog.java`: render allowance and selected-claim depth sections.
- Modify `havenvault-plugin/src/main/java/dev/invisiblespiders/havenvault/plugin/HavenVaultPlugin.java`: resolve new LandClaims services.

---

### Task 1: LandClaims Public API Foundation

**Files:**
- Create: `landclaims-api/src/main/java/com/nick/landclaims/api/claim/ClaimRegionView.java`
- Create: `landclaims-api/src/main/java/com/nick/landclaims/api/claim/ClaimVerticalProtection.java`
- Create: `landclaims-api/src/main/java/com/nick/landclaims/api/claim/ClaimUpgradeTarget.java`
- Create: `landclaims-api/src/main/java/com/nick/landclaims/api/claim/ClaimUpgradeResult.java`
- Create: `landclaims-api/src/main/java/com/nick/landclaims/api/limit/LandClaimsAllowanceService.java`
- Create: `landclaims-api/src/main/java/com/nick/landclaims/api/upgrade/LandClaimsUpgradeService.java`
- Modify: `landclaims-api/src/main/java/com/nick/landclaims/api/claim/ClaimView.java`
- Test: `landclaims-api/src/test/java/com/nick/landclaims/api/claim/ClaimRegionViewTest.java`

- [ ] **Step 1: Write the failing API DTO test**

```java
package com.nick.landclaims.api.claim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClaimRegionViewTest {
    @Test
    void areaUsesInclusiveBlockCoordinates() {
        UUID world = UUID.randomUUID();
        ClaimRegionView region = new ClaimRegionView(world, 0, 0, 15, 15, 32, 320);

        assertEquals(256, region.areaBlocks());
        assertEquals(32, region.protectedMinY());
        assertEquals(320, region.protectedMaxY());
    }

    @Test
    void rejectsInvertedBounds() {
        UUID world = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class,
                () -> new ClaimRegionView(world, 10, 0, 9, 15, 32, 320));
        assertThrows(IllegalArgumentException.class,
                () -> new ClaimRegionView(world, 0, 0, 15, 15, 64, 32));
    }
}
```

- [ ] **Step 2: Run the test and verify it fails**

Run: `.\gradlew.bat :landclaims-api:test --tests "com.nick.landclaims.api.claim.ClaimRegionViewTest"`

Expected: FAIL because `ClaimRegionView` does not exist.

- [ ] **Step 3: Add public API records and services**

`ClaimRegionView.java`:

```java
package com.nick.landclaims.api.claim;

import java.util.Objects;
import java.util.UUID;

public record ClaimRegionView(
        UUID worldId,
        int minX,
        int minZ,
        int maxX,
        int maxZ,
        int protectedMinY,
        int protectedMaxY
) {
    public ClaimRegionView {
        worldId = Objects.requireNonNull(worldId, "worldId");
        if (minX > maxX) throw new IllegalArgumentException("minX must be <= maxX");
        if (minZ > maxZ) throw new IllegalArgumentException("minZ must be <= maxZ");
        if (protectedMinY > protectedMaxY) {
            throw new IllegalArgumentException("protectedMinY must be <= protectedMaxY");
        }
    }

    public int areaBlocks() {
        return Math.multiplyExact(maxX - minX + 1, maxZ - minZ + 1);
    }
}
```

`ClaimVerticalProtection.java`:

```java
package com.nick.landclaims.api.claim;

import java.util.UUID;

public record ClaimVerticalProtection(
        UUID claimId,
        int protectedMinY,
        int protectedMaxY,
        int worldMinY,
        int worldMaxY
) {}
```

`ClaimUpgradeTarget.java`:

```java
package com.nick.landclaims.api.claim;

import java.util.UUID;

public record ClaimUpgradeTarget(
        UUID claimId,
        String name,
        UUID worldId,
        int areaBlocks,
        int protectedMinY,
        int protectedMaxY
) {}
```

`ClaimUpgradeResult.java`:

```java
package com.nick.landclaims.api.claim;

public record ClaimUpgradeResult(boolean success, String message, ClaimVerticalProtection protection) {
    public static ClaimUpgradeResult success(ClaimVerticalProtection protection) {
        return new ClaimUpgradeResult(true, "ok", protection);
    }

    public static ClaimUpgradeResult failure(String message) {
        return new ClaimUpgradeResult(false, message, null);
    }
}
```

`LandClaimsAllowanceService.java`:

```java
package com.nick.landclaims.api.limit;

import java.util.UUID;

public interface LandClaimsAllowanceService {
    int getBlockLimit(UUID playerId);
    void setBlockLimit(UUID playerId, int blocks);
    void addBlocks(UUID playerId, int blocks);
    void removeBlocks(UUID playerId, int blocks);
}
```

`LandClaimsUpgradeService.java`:

```java
package com.nick.landclaims.api.upgrade;

import com.nick.landclaims.api.claim.ClaimUpgradeResult;
import com.nick.landclaims.api.claim.ClaimUpgradeTarget;
import com.nick.landclaims.api.claim.ClaimVerticalProtection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LandClaimsUpgradeService {
    List<ClaimUpgradeTarget> getUpgradeableClaims(UUID playerId);
    Optional<ClaimVerticalProtection> getVerticalProtection(UUID claimId);
    ClaimUpgradeResult expandClaimDepth(UUID playerId, UUID claimId, int blocksDown);
    ClaimUpgradeResult setClaimDepth(UUID playerId, UUID claimId, int protectedMinY);
}
```

Add to `ClaimView`:

```java
Set<ClaimRegionView> regions();
```

- [ ] **Step 4: Run API tests**

Run: `.\gradlew.bat :landclaims-api:test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add landclaims-api/src/main/java/com/nick/landclaims/api/claim \
        landclaims-api/src/main/java/com/nick/landclaims/api/limit \
        landclaims-api/src/main/java/com/nick/landclaims/api/upgrade \
        landclaims-api/src/test/java/com/nick/landclaims/api/claim/ClaimRegionViewTest.java
git commit -m "Add block claim public APIs"
```

### Task 2: Internal Claim Region Model and Vertical Config

**Files:**
- Create: `landclaims-plugin/src/main/java/com/nick/landclaims/plugin/claim/ClaimRegion.java`
- Create: `landclaims-plugin/src/main/java/com/nick/landclaims/plugin/claim/VerticalProtectionConfig.java`
- Modify: `landclaims-plugin/src/main/resources/config.yml`
- Test: `landclaims-plugin/src/test/java/com/nick/landclaims/plugin/claim/ClaimRegionTest.java`
- Test: `landclaims-plugin/src/test/java/com/nick/landclaims/plugin/claim/VerticalProtectionConfigTest.java`

- [ ] **Step 1: Write failing model tests**

```java
package com.nick.landclaims.plugin.claim;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClaimRegionTest {
    @Test
    void containsRequiresWorldXZAndYMatch() {
        UUID world = UUID.randomUUID();
        ClaimRegion region = new ClaimRegion(world, 0, 0, 15, 15, 32, 320);

        assertThat(region.contains(world, 0, 32, 0)).isTrue();
        assertThat(region.contains(world, 15, 320, 15)).isTrue();
        assertThat(region.contains(world, 16, 64, 15)).isFalse();
        assertThat(region.contains(world, 15, 31, 15)).isFalse();
    }

    @Test
    void chunkRangeCoversOverlappingChunks() {
        UUID world = UUID.randomUUID();
        ClaimRegion region = new ClaimRegion(world, 15, -1, 16, 16, 32, 320);

        assertThat(region.overlappingChunks()).containsExactlyInAnyOrder(
                new ClaimChunk(world, 0, -1),
                new ClaimChunk(world, 0, 0),
                new ClaimChunk(world, 1, -1),
                new ClaimChunk(world, 1, 0)
        );
    }
}
```

```java
package com.nick.landclaims.plugin.claim;

import static org.assertj.core.api.Assertions.assertThat;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class VerticalProtectionConfigTest {
    @Test
    void readsDefaultRangeAndWorldOverride() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("claims.vertical-protection.default.min-y", 32);
        yaml.set("claims.vertical-protection.default.max-y", "world_max");
        yaml.set("claims.vertical-protection.worlds.world_nether.min-y", "world_min");
        yaml.set("claims.vertical-protection.worlds.world_nether.max-y", "world_max");

        VerticalProtectionConfig config = VerticalProtectionConfig.from(yaml);

        assertThat(config.minY("world", -64, 320)).isEqualTo(32);
        assertThat(config.maxY("world", -64, 320)).isEqualTo(320);
        assertThat(config.minY("world_nether", -64, 320)).isEqualTo(-64);
    }
}
```

- [ ] **Step 2: Run tests and verify they fail**

Run: `.\gradlew.bat :landclaims-plugin:test --tests "com.nick.landclaims.plugin.claim.ClaimRegionTest" --tests "com.nick.landclaims.plugin.claim.VerticalProtectionConfigTest"`

Expected: FAIL because `ClaimRegion` and `VerticalProtectionConfig` do not exist.

- [ ] **Step 3: Add `ClaimRegion`**

```java
package com.nick.landclaims.plugin.claim;

import com.nick.landclaims.api.claim.ClaimRegionView;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record ClaimRegion(
        UUID worldId,
        int minX,
        int minZ,
        int maxX,
        int maxZ,
        int protectedMinY,
        int protectedMaxY
) {
    public ClaimRegion {
        worldId = Objects.requireNonNull(worldId, "worldId");
        if (minX > maxX) throw new IllegalArgumentException("minX must be <= maxX");
        if (minZ > maxZ) throw new IllegalArgumentException("minZ must be <= maxZ");
        if (protectedMinY > protectedMaxY) {
            throw new IllegalArgumentException("protectedMinY must be <= protectedMaxY");
        }
    }

    public boolean contains(UUID world, int x, int y, int z) {
        return worldId.equals(world)
                && x >= minX && x <= maxX
                && z >= minZ && z <= maxZ
                && y >= protectedMinY && y <= protectedMaxY;
    }

    public int areaBlocks() {
        return Math.multiplyExact(maxX - minX + 1, maxZ - minZ + 1);
    }

    public Set<ClaimChunk> overlappingChunks() {
        int minChunkX = Math.floorDiv(minX, 16);
        int maxChunkX = Math.floorDiv(maxX, 16);
        int minChunkZ = Math.floorDiv(minZ, 16);
        int maxChunkZ = Math.floorDiv(maxZ, 16);
        Set<ClaimChunk> chunks = new HashSet<>();
        for (int x = minChunkX; x <= maxChunkX; x++) {
            for (int z = minChunkZ; z <= maxChunkZ; z++) {
                chunks.add(new ClaimChunk(worldId, x, z));
            }
        }
        return Set.copyOf(chunks);
    }

    public ClaimRegionView toView() {
        return new ClaimRegionView(worldId, minX, minZ, maxX, maxZ, protectedMinY, protectedMaxY);
    }
}
```

- [ ] **Step 4: Add vertical config parser**

```java
package com.nick.landclaims.plugin.claim;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.bukkit.configuration.ConfigurationSection;

public record VerticalProtectionConfig(Range defaultRange, Map<String, Range> worlds) {
    public record Range(String minY, String maxY, boolean enabled) {}

    public static VerticalProtectionConfig from(ConfigurationSection root) {
        ConfigurationSection section = root.getConfigurationSection("claims.vertical-protection");
        if (section == null) {
            return new VerticalProtectionConfig(new Range("world_min", "world_max", true), Map.of());
        }
        Range defaultRange = readRange(section.getConfigurationSection("default"),
                new Range("world_min", "world_max", true));
        ConfigurationSection worldsSection = section.getConfigurationSection("worlds");
        Map<String, Range> worlds = worldsSection == null ? Map.of() : worldsSection.getKeys(false).stream()
                .collect(Collectors.toUnmodifiableMap(name -> name,
                        name -> readRange(worldsSection.getConfigurationSection(name), defaultRange)));
        return new VerticalProtectionConfig(defaultRange, worlds);
    }

    public boolean enabled(String worldName) {
        return range(worldName).enabled();
    }

    public int minY(String worldName, int worldMinY, int worldMaxY) {
        return resolve(range(worldName).minY(), worldMinY, worldMaxY);
    }

    public int maxY(String worldName, int worldMinY, int worldMaxY) {
        return resolve(range(worldName).maxY(), worldMinY, worldMaxY);
    }

    private Range range(String worldName) {
        return Optional.ofNullable(worlds.get(worldName)).orElse(defaultRange);
    }

    private static Range readRange(ConfigurationSection section, Range fallback) {
        if (section == null) return fallback;
        return new Range(
                section.getString("min-y", fallback.minY()),
                section.getString("max-y", fallback.maxY()),
                section.getBoolean("enabled", fallback.enabled()));
    }

    private static int resolve(String value, int worldMinY, int worldMaxY) {
        Objects.requireNonNull(value, "value");
        if (value.equalsIgnoreCase("world_min")) return worldMinY;
        if (value.equalsIgnoreCase("world_max")) return worldMaxY;
        return Integer.parseInt(value);
    }
}
```

Add to `config.yml`:

```yaml
claims:
  vertical-protection:
    default:
      enabled: true
      min-y: 32
      max-y: world_max
    worlds: {}
```

- [ ] **Step 5: Run tests**

Run: `.\gradlew.bat :landclaims-plugin:test --tests "com.nick.landclaims.plugin.claim.ClaimRegionTest" --tests "com.nick.landclaims.plugin.claim.VerticalProtectionConfigTest"`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add landclaims-plugin/src/main/java/com/nick/landclaims/plugin/claim/ClaimRegion.java \
        landclaims-plugin/src/main/java/com/nick/landclaims/plugin/claim/VerticalProtectionConfig.java \
        landclaims-plugin/src/main/resources/config.yml \
        landclaims-plugin/src/test/java/com/nick/landclaims/plugin/claim/ClaimRegionTest.java \
        landclaims-plugin/src/test/java/com/nick/landclaims/plugin/claim/VerticalProtectionConfigTest.java
git commit -m "Add block claim region model"
```

### Task 3: SQL Migration and Repository Region Support

**Files:**
- Create: `landclaims-plugin/src/main/resources/db/migrations/landclaims/V5__block_claim_regions.sql`
- Modify: `landclaims-plugin/src/main/resources/db/migrations/landclaims/migrations.index`
- Modify: `landclaims-plugin/src/main/java/com/nick/landclaims/plugin/claim/Claim.java`
- Modify: `landclaims-plugin/src/main/java/com/nick/landclaims/plugin/storage/sql/SqlClaimRepository.java`
- Test: `landclaims-plugin/src/test/java/com/nick/landclaims/plugin/storage/sql/SqlClaimRepositoryTest.java`
- Test: `landclaims-plugin/src/test/java/com/nick/landclaims/plugin/storage/sql/LandClaimsMigrationResourceTest.java`

- [ ] **Step 1: Write failing repository test**

Add to `SqlClaimRepositoryTest`:

```java
@Test
void savesAndLoadsBlockRegions(@TempDir Path tempDirectory) throws Exception {
    SQLiteDataSource dataSource = new SQLiteDataSource();
    dataSource.setUrl("jdbc:sqlite:" + tempDirectory.resolve("landclaims.db"));
    applyMigrations(dataSource);
    SqlClaimRepository repository = new SqlClaimRepository(dataSource);
    UUID worldId = UUID.randomUUID();
    Claim claim = new Claim(
            UUID.randomUUID(),
            "Base",
            OwnerType.PLAYER,
            UUID.randomUUID(),
            worldId,
            Set.of(new ClaimRegion(worldId, 10, 20, 30, 40, 32, 320)),
            Map.of("build", FlagState.OFF),
            Instant.now(),
            Instant.now()
    );

    repository.saveClaim(claim);

    Claim loaded = repository.findClaimById(claim.id()).orElseThrow();
    assertThat(loaded.regions()).containsExactly(new ClaimRegion(worldId, 10, 20, 30, 40, 32, 320));
}
```

- [ ] **Step 2: Run test and verify it fails**

Run: `.\gradlew.bat :landclaims-plugin:test --tests "com.nick.landclaims.plugin.storage.sql.SqlClaimRepositoryTest.savesAndLoadsBlockRegions"`

Expected: FAIL because `Claim` has no region constructor and repository does not save `claim_regions`.

- [ ] **Step 3: Add V5 migration**

`V5__block_claim_regions.sql`:

```sql
CREATE TABLE IF NOT EXISTS claim_regions (
    claim_id CHAR(36) NOT NULL,
    region_index INTEGER NOT NULL,
    world_id CHAR(36) NOT NULL,
    min_x INTEGER NOT NULL,
    min_z INTEGER NOT NULL,
    max_x INTEGER NOT NULL,
    max_z INTEGER NOT NULL,
    protected_min_y INTEGER NOT NULL,
    protected_max_y INTEGER NOT NULL,
    PRIMARY KEY (claim_id, region_index),
    FOREIGN KEY (claim_id) REFERENCES claims(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_claim_regions_world_bounds
    ON claim_regions(world_id, min_x, max_x, min_z, max_z);

INSERT INTO claim_regions (
    claim_id, region_index, world_id, min_x, min_z, max_x, max_z, protected_min_y, protected_max_y
)
SELECT
    claim_id,
    ROW_NUMBER() OVER (PARTITION BY claim_id ORDER BY chunk_x, chunk_z) - 1,
    world_id,
    chunk_x * 16,
    chunk_z * 16,
    chunk_x * 16 + 15,
    chunk_z * 16 + 15,
    32,
    320
FROM claim_chunks
WHERE NOT EXISTS (
    SELECT 1 FROM claim_regions existing WHERE existing.claim_id = claim_chunks.claim_id
);

ALTER TABLE claim_player_limits ADD COLUMN block_limit INTEGER;

UPDATE claim_player_limits
SET block_limit = chunk_limit * 256
WHERE block_limit IS NULL;
```

Append `V5__block_claim_regions.sql` to `migrations.index`.

- [ ] **Step 4: Modify `Claim` to store regions**

Add `Set<ClaimRegion> regions` as the canonical geometry. Keep `claimChunks()` as a derived compatibility view from `regions.overlappingChunks()`. Add constructors that accept legacy chunks by converting each chunk to one full chunk region using default Y `32..320` until creation code supplies config-derived values.

Core methods:

```java
public Set<ClaimRegion> regions() {
    return regions;
}

public Set<ClaimChunk> claimChunks() {
    return regions.stream()
            .flatMap(region -> region.overlappingChunks().stream())
            .collect(Collectors.toUnmodifiableSet());
}

@Override
public Set<ClaimRegionView> regions() {
    return regions.stream().map(ClaimRegion::toView).collect(Collectors.toUnmodifiableSet());
}
```

- [ ] **Step 5: Modify repository save/load**

Replace `insertChunks(connection, claim)` with `insertRegions(connection, claim)` and keep writing `claim_chunks` as a compatibility projection during the migration window.

Required helper signatures:

```java
private void insertRegions(Connection connection, Claim claim) throws SQLException
private Map<UUID, Set<ClaimRegion>> bulkLoadRegions(Connection connection, List<UUID> claimIds) throws SQLException
private Set<ClaimRegion> loadRegions(Connection connection, UUID claimId) throws SQLException
```

When loading, prefer `claim_regions` if rows exist. If no regions exist for a claim, fall back to `claim_chunks`.

- [ ] **Step 6: Run repository and migration tests**

Run: `.\gradlew.bat :landclaims-plugin:test --tests "com.nick.landclaims.plugin.storage.sql.SqlClaimRepositoryTest" --tests "com.nick.landclaims.plugin.storage.sql.LandClaimsMigrationResourceTest"`

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add landclaims-plugin/src/main/resources/db/migrations/landclaims/V5__block_claim_regions.sql \
        landclaims-plugin/src/main/resources/db/migrations/landclaims/migrations.index \
        landclaims-plugin/src/main/java/com/nick/landclaims/plugin/claim/Claim.java \
        landclaims-plugin/src/main/java/com/nick/landclaims/plugin/storage/sql/SqlClaimRepository.java \
        landclaims-plugin/src/test/java/com/nick/landclaims/plugin/storage/sql
git commit -m "Persist block claim regions"
```

### Task 4: Block Allowance Service and Limit Compatibility

**Files:**
- Create: `landclaims-plugin/src/main/java/com/nick/landclaims/plugin/limit/AllowanceService.java`
- Modify: `landclaims-plugin/src/main/java/com/nick/landclaims/plugin/limit/ClaimLimitRepository.java`
- Modify: `landclaims-plugin/src/main/java/com/nick/landclaims/plugin/limit/SqlClaimLimitRepository.java`
- Modify: `landclaims-plugin/src/main/java/com/nick/landclaims/plugin/limit/LimitService.java`
- Modify: `landclaims-plugin/src/main/java/com/nick/landclaims/plugin/LandClaimsPlugin.java`
- Test: `landclaims-plugin/src/test/java/com/nick/landclaims/plugin/limit/AllowanceServiceTest.java`
- Test: `landclaims-plugin/src/test/java/com/nick/landclaims/plugin/limit/LimitServiceTest.java`

- [ ] **Step 1: Write failing allowance test**

```java
package com.nick.landclaims.plugin.limit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AllowanceServiceTest {
    @Test
    void usesBlockLimitRepositoryValueOrDefault() {
        ClaimLimitRepository repository = mock(ClaimLimitRepository.class);
        UUID player = UUID.randomUUID();
        when(repository.getBlockLimit(player)).thenReturn(Optional.of(3000));

        AllowanceService service = new AllowanceService(2560, repository);

        assertThat(service.getBlockLimit(player)).isEqualTo(3000);
        assertThat(service.getBlockLimit(UUID.randomUUID())).isEqualTo(2560);
    }
}
```

- [ ] **Step 2: Run test and verify it fails**

Run: `.\gradlew.bat :landclaims-plugin:test --tests "com.nick.landclaims.plugin.limit.AllowanceServiceTest"`

Expected: FAIL because `AllowanceService` and `getBlockLimit` do not exist.

- [ ] **Step 3: Extend repository**

Add methods to `ClaimLimitRepository`:

```java
Optional<Integer> getBlockLimit(UUID playerId);
void setBlockLimit(UUID playerId, int blockLimit);
void updateBlockLimit(UUID playerId, int defaultBlockLimit, IntUnaryOperator updater);
```

Implement them in `SqlClaimLimitRepository` against `claim_player_limits.block_limit`.

- [ ] **Step 4: Add `AllowanceService`**

```java
package com.nick.landclaims.plugin.limit;

import com.nick.landclaims.api.limit.LandClaimsAllowanceService;
import java.util.Objects;
import java.util.UUID;

public final class AllowanceService implements LandClaimsAllowanceService {
    private int defaultBlockLimit;
    private final ClaimLimitRepository repository;

    public AllowanceService(int defaultBlockLimit, ClaimLimitRepository repository) {
        this.defaultBlockLimit = defaultBlockLimit;
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public void reload(int defaultBlockLimit) {
        this.defaultBlockLimit = defaultBlockLimit;
    }

    @Override
    public int getBlockLimit(UUID playerId) {
        return repository.getBlockLimit(playerId).orElse(defaultBlockLimit);
    }

    @Override
    public void setBlockLimit(UUID playerId, int blocks) {
        if (blocks < 1) throw new IllegalArgumentException("blocks must be >= 1");
        repository.setBlockLimit(playerId, blocks);
    }

    @Override
    public void addBlocks(UUID playerId, int blocks) {
        if (blocks < 1) throw new IllegalArgumentException("blocks must be >= 1");
        repository.updateBlockLimit(playerId, defaultBlockLimit, current -> current + blocks);
    }

    @Override
    public void removeBlocks(UUID playerId, int blocks) {
        if (blocks < 1) throw new IllegalArgumentException("blocks must be >= 1");
        repository.updateBlockLimit(playerId, defaultBlockLimit, current -> Math.max(1, current - blocks));
    }
}
```

- [ ] **Step 5: Adapt `LimitService`**

Keep `LandClaimsLimitService` working by translating chunks to blocks:

```java
private static final int BLOCKS_PER_CHUNK = 256;

public int getLimit(UUID playerId) {
    return Math.max(1, (int) Math.ceil(allowanceService.getBlockLimit(playerId) / (double) BLOCKS_PER_CHUNK));
}

public void addChunks(UUID playerId, int chunks) {
    allowanceService.addBlocks(playerId, Math.multiplyExact(chunks, BLOCKS_PER_CHUNK));
}
```

- [ ] **Step 6: Register API**

In `LandClaimsPlugin`, create one `AllowanceService`, register `LandClaimsAllowanceService.class`, then construct `LimitService` as the compatibility wrapper.

- [ ] **Step 7: Run limit tests**

Run: `.\gradlew.bat :landclaims-plugin:test --tests "com.nick.landclaims.plugin.limit.*"`

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add landclaims-plugin/src/main/java/com/nick/landclaims/plugin/limit \
        landclaims-plugin/src/main/java/com/nick/landclaims/plugin/LandClaimsPlugin.java \
        landclaims-plugin/src/test/java/com/nick/landclaims/plugin/limit
git commit -m "Add block allowance service"
```

### Task 5: Region Index and Protection Checks

**Files:**
- Modify: `landclaims-plugin/src/main/java/com/nick/landclaims/plugin/claim/ClaimIndex.java`
- Modify: `landclaims-plugin/src/main/java/com/nick/landclaims/plugin/protection/ProtectionService.java`
- Modify: `landclaims-plugin/src/main/java/com/nick/landclaims/plugin/listener/ProtectionListener.java`
- Test: `landclaims-plugin/src/test/java/com/nick/landclaims/plugin/claim/ClaimIndexTest.java`
- Test: `landclaims-plugin/src/test/java/com/nick/landclaims/plugin/protection/ProtectionServiceTest.java`
- Test: `landclaims-plugin/src/test/java/com/nick/landclaims/plugin/listener/ProtectionListenerTest.java`

- [ ] **Step 1: Write failing index test**

```java
@Test
void findAtUsesExactBlockAndVerticalBounds() {
    UUID world = UUID.randomUUID();
    Claim claim = claimWithRegion(new ClaimRegion(world, 0, 0, 15, 15, 32, 320));
    ClaimIndex index = new ClaimIndex();
    index.add(claim);

    assertThat(index.findAt(world, 0, 32, 0)).contains(claim);
    assertThat(index.findAt(world, 0, 31, 0)).isEmpty();
    assertThat(index.findAt(world, 16, 64, 0)).isEmpty();
}
```

- [ ] **Step 2: Run index test and verify it fails**

Run: `.\gradlew.bat :landclaims-plugin:test --tests "com.nick.landclaims.plugin.claim.ClaimIndexTest.findAtUsesExactBlockAndVerticalBounds"`

Expected: FAIL because `ClaimIndex.findAt(UUID, int, int, int)` does not exist.

- [ ] **Step 3: Implement region-backed index**

Keep `findAt(ClaimChunk)` for compatibility. Add:

```java
public Optional<Claim> findAt(UUID worldId, int blockX, int blockY, int blockZ)
```

Internally store:

```java
private final Map<ClaimChunk, List<Claim>> byOverlappingChunk = new HashMap<>();
```

On add, put the claim in every chunk from each region's `overlappingChunks()`. On lookup, compute the block's chunk, iterate candidates, and return the first claim with any region containing the exact block.

- [ ] **Step 4: Update protection call sites**

Where listeners currently convert a `Location` to `ClaimChunk`, call the new block-level lookup. Keep command flows that need chunk preview on the compatibility method until selection is converted.

- [ ] **Step 5: Run protection tests**

Run: `.\gradlew.bat :landclaims-plugin:test --tests "com.nick.landclaims.plugin.claim.ClaimIndexTest" --tests "com.nick.landclaims.plugin.protection.ProtectionServiceTest" --tests "com.nick.landclaims.plugin.listener.ProtectionListenerTest"`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add landclaims-plugin/src/main/java/com/nick/landclaims/plugin/claim/ClaimIndex.java \
        landclaims-plugin/src/main/java/com/nick/landclaims/plugin/protection/ProtectionService.java \
        landclaims-plugin/src/main/java/com/nick/landclaims/plugin/listener/ProtectionListener.java \
        landclaims-plugin/src/test/java/com/nick/landclaims/plugin/claim/ClaimIndexTest.java \
        landclaims-plugin/src/test/java/com/nick/landclaims/plugin/protection/ProtectionServiceTest.java \
        landclaims-plugin/src/test/java/com/nick/landclaims/plugin/listener/ProtectionListenerTest.java
git commit -m "Use block regions for protection lookups"
```

### Task 6: Claim Creation, Cost, Selection, and Border Visuals

**Files:**
- Modify: `landclaims-plugin/src/main/java/com/nick/landclaims/plugin/selection/SelectionService.java`
- Modify: `landclaims-plugin/src/main/java/com/nick/landclaims/plugin/claim/ClaimCreationService.java`
- Modify: `landclaims-plugin/src/main/java/com/nick/landclaims/plugin/limit/ClaimCostService.java`
- Modify: `landclaims-plugin/src/main/java/com/nick/landclaims/plugin/limit/ClaimCostQuote.java`
- Modify: `landclaims-plugin/src/main/java/com/nick/landclaims/plugin/command/ClaimsCommand.java`
- Modify: `landclaims-plugin/src/main/java/com/nick/landclaims/plugin/visual/ChunkBorderPlanner.java`
- Create: `landclaims-plugin/src/main/java/com/nick/landclaims/plugin/visual/BlockRegionBorderPlanner.java`
- Test: selection, creation, cost, command, and visual test classes.

- [ ] **Step 1: Write failing cost test**

```java
@Test
void quoteUsesClaimBlockAreaInsteadOfChunkCount() {
    UUID owner = UUID.randomUUID();
    UUID world = UUID.randomUUID();
    ClaimCostService service = serviceWithBlockLimit(owner, 300);

    ClaimCostQuote quote = service.quotePlayerClaim(owner,
            Set.of(new ClaimRegion(world, 0, 0, 19, 9, 32, 320)));

    assertThat(quote.selectedBlocks()).isEqualTo(200);
    assertThat(quote.overageBlocks()).isZero();
}
```

The assertion proves area is 200 blocks, not overlapping chunk count.

- [ ] **Step 2: Run test and verify it fails**

Run: `.\gradlew.bat :landclaims-plugin:test --tests "com.nick.landclaims.plugin.limit.ClaimCostServiceTest.quoteUsesClaimBlockAreaInsteadOfChunkCount"`

Expected: FAIL because `quotePlayerClaim` does not accept regions and current quote uses chunk count.

- [ ] **Step 3: Convert selection to block corners**

Store two block positions as `ClaimRegion` preview candidates. Use the configured vertical protection range for the selected world.

- [ ] **Step 4: Convert cost and creation to regions**

`ClaimCreationService.createPlayerClaim` should accept `Set<ClaimRegion>` or one `ClaimRegion` for the first release. Cost service should sum `ClaimRegion.areaBlocks()`. `ClaimCostQuote` should expose `selectedBlocks`, `allowedBlocks`, and `overageBlocks`.

- [ ] **Step 5: Add block-region border planner**

`BlockRegionBorderPlanner` should draw the exact rectangle perimeter at ground level. It should reuse `ChunkGroundHeightProvider` for sampling.

- [ ] **Step 6: Update commands and messages**

Change player-facing text from chunks to claim blocks where area allowance is shown. Keep admin chunk compatibility text only where commands still expose compatibility APIs.

- [ ] **Step 7: Run focused tests**

Run:

```bash
.\gradlew.bat :landclaims-plugin:test --tests "com.nick.landclaims.plugin.selection.*" --tests "com.nick.landclaims.plugin.claim.ClaimCreationServiceTest" --tests "com.nick.landclaims.plugin.limit.ClaimCostServiceTest" --tests "com.nick.landclaims.plugin.command.ClaimsCommandPermissionTest" --tests "com.nick.landclaims.plugin.visual.*"
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add landclaims-plugin/src/main/java/com/nick/landclaims/plugin/selection \
        landclaims-plugin/src/main/java/com/nick/landclaims/plugin/claim \
        landclaims-plugin/src/main/java/com/nick/landclaims/plugin/limit \
        landclaims-plugin/src/main/java/com/nick/landclaims/plugin/command \
        landclaims-plugin/src/main/java/com/nick/landclaims/plugin/visual \
        landclaims-plugin/src/main/resources/messages.yml \
        landclaims-plugin/src/test/java/com/nick/landclaims/plugin
git commit -m "Create and preview block area claims"
```

### Task 7: LandClaims Per-Claim Upgrade Service and Menu Routing

**Files:**
- Create: `landclaims-plugin/src/main/java/com/nick/landclaims/plugin/upgrade/ClaimUpgradeService.java`
- Modify: `landclaims-plugin/src/main/java/com/nick/landclaims/plugin/LandClaimsPlugin.java`
- Modify: `landclaims-plugin/src/main/java/com/nick/landclaims/plugin/ui/ClaimMenuService.java`
- Modify: `landclaims-plugin/src/main/resources/messages.yml`
- Test: `landclaims-plugin/src/test/java/com/nick/landclaims/plugin/upgrade/ClaimUpgradeServiceTest.java`
- Test: `landclaims-plugin/src/test/java/com/nick/landclaims/plugin/ui/ClaimMenuServiceTest.java`

- [ ] **Step 1: Write failing upgrade service test**

```java
@Test
void expandClaimDepthLowersProtectedMinYForOwner() {
    UUID owner = UUID.randomUUID();
    UUID world = UUID.randomUUID();
    Claim claim = claimWithRegion(owner, new ClaimRegion(world, 0, 0, 15, 15, 32, 320));
    InMemoryClaimRepository repository = new InMemoryClaimRepository(claim);
    ClaimIndex index = new ClaimIndex();
    index.add(claim);
    ClaimUpgradeService service = new ClaimUpgradeService(repository, index, () -> Map.of(world, -64));

    ClaimUpgradeResult result = service.expandClaimDepth(owner, claim.id(), 16);

    assertThat(result.success()).isTrue();
    assertThat(result.protection().protectedMinY()).isEqualTo(16);
}
```

- [ ] **Step 2: Run test and verify it fails**

Run: `.\gradlew.bat :landclaims-plugin:test --tests "com.nick.landclaims.plugin.upgrade.ClaimUpgradeServiceTest"`

Expected: FAIL because `ClaimUpgradeService` does not exist.

- [ ] **Step 3: Implement service**

Implement `LandClaimsUpgradeService`. Validate:

- Player owns the claim.
- Claim has at least one region.
- `blocksDown >= 1`.
- New min Y is not below world min Y.
- Target min Y is lower than current min Y.

- [ ] **Step 4: Register service**

In `LandClaimsPlugin.onEnable`, register:

```java
getServer().getServicesManager().register(
        LandClaimsUpgradeService.class, claimUpgradeService, this, ServicePriority.Normal);
```

Unregister it in `onDisable`.

- [ ] **Step 5: Add claim menu action**

Add action label key `upgrade-claim: "Upgrade Claim"` and command:

```java
"/vault upgrades claim " + claim.id()
```

This command target is implemented in HavenVault Task 9.

- [ ] **Step 6: Run tests**

Run: `.\gradlew.bat :landclaims-plugin:test --tests "com.nick.landclaims.plugin.upgrade.ClaimUpgradeServiceTest" --tests "com.nick.landclaims.plugin.ui.ClaimMenuServiceTest"`

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add landclaims-plugin/src/main/java/com/nick/landclaims/plugin/upgrade \
        landclaims-plugin/src/main/java/com/nick/landclaims/plugin/LandClaimsPlugin.java \
        landclaims-plugin/src/main/java/com/nick/landclaims/plugin/ui/ClaimMenuService.java \
        landclaims-plugin/src/main/resources/messages.yml \
        landclaims-plugin/src/test/java/com/nick/landclaims/plugin/upgrade \
        landclaims-plugin/src/test/java/com/nick/landclaims/plugin/ui/ClaimMenuServiceTest.java
git commit -m "Expose claim depth upgrade API"
```

### Task 8: HavenVault Claim-Block Allowance Upgrades

**Files:**
- Modify: `HavenVault-review/havenvault-plugin/src/main/java/dev/invisiblespiders/havenvault/plugin/claim/ClaimUpgradeConfig.java`
- Create: `HavenVault-review/havenvault-plugin/src/main/java/dev/invisiblespiders/havenvault/plugin/claim/ClaimAllowanceService.java`
- Create: `HavenVault-review/havenvault-plugin/src/main/java/dev/invisiblespiders/havenvault/plugin/claim/BukkitLandClaimsAllowanceService.java`
- Modify: `HavenVault-review/havenvault-plugin/src/main/java/dev/invisiblespiders/havenvault/plugin/claim/ClaimChunkUpgradeService.java`
- Modify: `HavenVault-review/havenvault-plugin/src/main/java/dev/invisiblespiders/havenvault/plugin/dialog/UpgradesDialog.java`
- Test: `HavenVault-review/havenvault-plugin/src/test/java/dev/invisiblespiders/havenvault/plugin/claim/ClaimUpgradeConfigTest.java`
- Test: `HavenVault-review/havenvault-plugin/src/test/java/dev/invisiblespiders/havenvault/plugin/claim/ClaimBlockAllowanceUpgradeServiceTest.java`

- [ ] **Step 1: Write failing config test**

```java
@Test
void readsClaimBlockAllowanceOptions() {
    ClaimUpgradeConfig cfg = ClaimUpgradeConfig.from(yaml("""
            claim-upgrades:
              enabled: true
              allowance:
                default-limit-blocks: 2560
                bulk-options:
                  - blocks: 500
                    discount: 0.10
            """));

    assertEquals(2560, cfg.allowance().defaultLimitBlocks());
    assertEquals(500, cfg.allowance().bulkOptions().get(0).blocks());
}
```

- [ ] **Step 2: Run test and verify it fails**

Run: `.\gradlew.bat :havenvault-plugin:test --tests "dev.invisiblespiders.havenvault.plugin.claim.ClaimUpgradeConfigTest.readsClaimBlockAllowanceOptions"`

Expected: FAIL because config has no `allowance()` section.

- [ ] **Step 3: Add config records**

Add nested records:

```java
public record AllowanceConfig(String currency, double baseCost, double costMultiplier,
                              int defaultLimitBlocks, Material itemMaterial,
                              int itemAmount, List<BlockBulkOption> bulkOptions) {}

public record BlockBulkOption(int blocks, double discount) {}
```

Keep old `bulk-options.chunks` parsing as compatibility by converting `chunks * 256` to blocks and logging an admin warning in plugin startup.

- [ ] **Step 4: Add allowance service wrapper**

`ClaimAllowanceService`:

```java
public interface ClaimAllowanceService {
    int getBlockLimit(UUID playerId);
    void addBlocks(UUID playerId, int blocks);
}
```

`BukkitLandClaimsAllowanceService` should reflectively resolve `com.nick.landclaims.api.limit.LandClaimsAllowanceService`.

- [ ] **Step 5: Replace chunk purchase service wording**

Keep implementation class renames out of this task. Change behavior first: compute cost by current block limit and purchased block count, send `claim block(s)` messages, and call `addBlocks`.

- [ ] **Step 6: Run HavenVault claim tests**

Run: `.\gradlew.bat :havenvault-plugin:test --tests "dev.invisiblespiders.havenvault.plugin.claim.*" --tests "dev.invisiblespiders.havenvault.plugin.dialog.UpgradesDialogTest"`

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add havenvault-plugin/src/main/java/dev/invisiblespiders/havenvault/plugin/claim \
        havenvault-plugin/src/main/java/dev/invisiblespiders/havenvault/plugin/dialog/UpgradesDialog.java \
        havenvault-plugin/src/test/java/dev/invisiblespiders/havenvault/plugin/claim \
        havenvault-plugin/src/test/java/dev/invisiblespiders/havenvault/plugin/dialog/UpgradesDialogTest.java
git commit -m "Use claim block allowance upgrades"
```

### Task 9: HavenVault Selected-Claim Depth Upgrades

**Files:**
- Create: `HavenVault-review/havenvault-plugin/src/main/java/dev/invisiblespiders/havenvault/plugin/claim/ClaimDepthService.java`
- Create: `HavenVault-review/havenvault-plugin/src/main/java/dev/invisiblespiders/havenvault/plugin/claim/BukkitLandClaimsDepthService.java`
- Create: `HavenVault-review/havenvault-plugin/src/main/java/dev/invisiblespiders/havenvault/plugin/claim/ClaimDepthUpgradeService.java`
- Modify: `HavenVault-review/havenvault-plugin/src/main/java/dev/invisiblespiders/havenvault/plugin/command/UpgradesCommand.java`
- Modify: `HavenVault-review/havenvault-plugin/src/main/java/dev/invisiblespiders/havenvault/plugin/dialog/UpgradesDialog.java`
- Test: `HavenVault-review/havenvault-plugin/src/test/java/dev/invisiblespiders/havenvault/plugin/claim/ClaimDepthUpgradeServiceTest.java`
- Test: `HavenVault-review/havenvault-plugin/src/test/java/dev/invisiblespiders/havenvault/plugin/command/UpgradesCommandTest.java`

- [ ] **Step 1: Write failing depth service test**

```java
@Test
void refundsPaymentWhenLandClaimsDepthApplyFails() {
    ClaimDepthService depthService = mock(ClaimDepthService.class);
    HavenEconomyService economy = mock(HavenEconomyService.class);
    Player player = mock(Player.class);
    UUID playerId = UUID.randomUUID();
    UUID claimId = UUID.randomUUID();
    when(player.getUniqueId()).thenReturn(playerId);
    when(economy.withdraw(playerId, 500.0)).thenReturn(true);
    when(depthService.expandDepth(playerId, claimId, 16))
            .thenReturn(PurchaseResult.failure("Upgrade failed."));

    ClaimDepthUpgradeService service = service(depthService);

    PurchaseResult result = service.purchaseDepth(player, economy, claimId, option("deeper_16", 16, 500.0));

    assertThat(result.success()).isFalse();
    verify(economy).deposit(playerId, 500.0);
}
```

- [ ] **Step 2: Run test and verify it fails**

Run: `.\gradlew.bat :havenvault-plugin:test --tests "dev.invisiblespiders.havenvault.plugin.claim.ClaimDepthUpgradeServiceTest"`

Expected: FAIL because depth service classes do not exist.

- [ ] **Step 3: Add depth service wrapper**

Reflectively resolve `com.nick.landclaims.api.upgrade.LandClaimsUpgradeService` and methods:

```java
getVerticalProtection(UUID claimId)
expandClaimDepth(UUID playerId, UUID claimId, int blocksDown)
setClaimDepth(UUID playerId, UUID claimId, int protectedMinY)
```

- [ ] **Step 4: Add purchase orchestration**

`ClaimDepthUpgradeService` should:

1. Validate service availability.
2. Validate the option is still applicable to the claim.
3. Withdraw payment.
4. Call LandClaims.
5. Refund payment if LandClaims fails.
6. Return a player-visible `PurchaseResult`.

- [ ] **Step 5: Add command route**

Support:

```text
/vault upgrades claim <claim-id>
```

The command opens `UpgradesDialog` with selected claim context.

- [ ] **Step 6: Add selected-claim dialog section**

When selected claim context exists, render depth buttons before or after allowance buttons. Use the same locked/available styling as normal upgrades.

- [ ] **Step 7: Run tests**

Run: `.\gradlew.bat :havenvault-plugin:test --tests "dev.invisiblespiders.havenvault.plugin.claim.*" --tests "dev.invisiblespiders.havenvault.plugin.command.UpgradesCommandTest" --tests "dev.invisiblespiders.havenvault.plugin.dialog.UpgradesDialogTest"`

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add havenvault-plugin/src/main/java/dev/invisiblespiders/havenvault/plugin/claim \
        havenvault-plugin/src/main/java/dev/invisiblespiders/havenvault/plugin/command/UpgradesCommand.java \
        havenvault-plugin/src/main/java/dev/invisiblespiders/havenvault/plugin/dialog/UpgradesDialog.java \
        havenvault-plugin/src/test/java/dev/invisiblespiders/havenvault/plugin/claim \
        havenvault-plugin/src/test/java/dev/invisiblespiders/havenvault/plugin/command \
        havenvault-plugin/src/test/java/dev/invisiblespiders/havenvault/plugin/dialog
git commit -m "Add selected claim depth upgrades"
```

### Task 10: Cross-Plugin Verification and Documentation

**Files:**
- Modify: `README.md`
- Modify: `docs/configuration.md`
- Modify: `HavenVault-review/README.md`
- Test: existing full test suites in both repos.

- [ ] **Step 1: Update LandClaims docs**

Document:

- Claims are block-area rectangles.
- Default vertical protection range.
- Per-world vertical override.
- Player allowance uses claim blocks.
- `/claim menu` exposes `Upgrade Claim` when HavenVault is installed.

- [ ] **Step 2: Update HavenVault docs**

Document:

- `claim-upgrades.allowance`.
- `claim-upgrades.depth`.
- `/vault upgrades claim <claim-id>`.
- LandClaims service availability requirements.

- [ ] **Step 3: Run LandClaims full verification**

Run: `.\gradlew.bat test`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Run HavenVault full verification**

Run from `HavenVault-review`: `.\gradlew.bat test`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Build both plugin jars**

Run from LandClaims: `.\gradlew.bat clean :landclaims-plugin:shadowJar`

Expected: BUILD SUCCESSFUL.

Run from HavenVault: `.\gradlew.bat clean :havenvault-plugin:shadowJar`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Manual server smoke test**

On a local Paper server with HavenCore, LandClaims, and HavenVault installed:

1. Create a claim from two block corners.
2. Confirm the protected area matches the selected block rectangle.
3. Mine below configured `protected_min_y` as a non-member and confirm it is allowed.
4. Open `/claim menu`, click `Upgrade Claim`.
5. Buy `Protect 16 blocks deeper`.
6. Confirm non-member mining is denied in the newly protected Y range.
7. Buy claim-block allowance from `/vault upgrades`.
8. Confirm `/claim cost` uses the increased block allowance.

- [ ] **Step 7: Commit docs**

```bash
git add README.md docs/configuration.md havenvault-plugin/src/main/resources/config.yml
git commit -m "Document block claim upgrade flow"
```

## Self-Review

Spec coverage:

- Block-area 2D rectangles: Tasks 1, 2, 3, 5, 6.
- Configurable vertical protection: Task 2.
- Per-claim depth upgrades: Tasks 7 and 9.
- Claim-block allowance upgrades: Tasks 4 and 8.
- HavenVault integration boundary: Tasks 8 and 9.
- Migration from chunk claims: Task 3.
- Runtime spatial indexing: Task 5.
- Visuals and selection: Task 6.
- Compatibility and testing: Tasks 4, 8, and 10.

Execution note:

- Start implementation with Task 1 on a fresh branch from updated `master`.
- Do not begin HavenVault implementation until the LandClaims API PR containing Tasks 1 and 7 is merged or published to a dependency coordinate that HavenVault can compile against.
