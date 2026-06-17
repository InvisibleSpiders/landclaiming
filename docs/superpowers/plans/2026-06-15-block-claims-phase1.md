# Block Claims Phase 1 Implementation Plan

**Plan status:** Historical execution artifact. The unchecked boxes below are preserved from the original plan and should not be treated as the current active backlog.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the chunk-based claim system with block-exact rectangular claims — block-coord selection, block-area limits, flat-per-block pricing, block accrual, and a YES/NO purchase prompt for over-limit claims.

**Architecture:** `ClaimRegion(worldId, minX, minZ, maxX, maxZ)` replaces `Set<ClaimChunk>` as the authoritative spatial model. A chunk-keyed `ClaimIndex` is kept as a fast spatial lookup; block-exact containment is verified after a chunk hit. `SelectionService` records exact block corners and returns `ClaimRegion`. A new `BlockAccrualService` runs every N seconds and uses `AfkDetector` (HavenCore or noop) to grant blocks. Over-limit purchases are stored as `PendingOverLimitPurchase` entries confirmed via `/claim confirm-purchase`.

**Tech Stack:** Java 25, Paper 26.1.2 API, Bukkit `BukkitRunnable`, Adventure API (clickable text), JUnit 5 + AssertJ + Mockito, Gradle Kotlin DSL, SQLite (tests), Flyway migrations via HavenCore `HavenDataSource`.

---

## File Structure

**Create:**
- `havenclaims-api/.../api/claim/ClaimRegionView.java` — API interface for region bounds
- `havenclaims-plugin/.../plugin/claim/ClaimRegion.java` — block-exact region record, implements ClaimRegionView
- `havenclaims-plugin/.../plugin/selection/BlockPos.java` — transient selection corner
- `havenclaims-plugin/.../plugin/accrual/AfkDetector.java` — AFK check interface
- `havenclaims-plugin/.../plugin/accrual/HavenCoreAfkDetector.java` — HavenCore implementation
- `havenclaims-plugin/.../plugin/accrual/NoopAfkDetector.java` — always-false fallback
- `havenclaims-plugin/.../plugin/accrual/BlockAccrualService.java` — repeating runnable
- `havenclaims-plugin/.../plugin/limit/PendingOverLimitPurchase.java` — in-memory purchase record
- `havenclaims-plugin/.../plugin/limit/OverLimitConfirmService.java` — stores/validates pending purchases
- `havenclaims-plugin/src/main/resources/db/migrations/havenclaims/V5__block_regions.sql`

**Modify:**
- `havenclaims-api/.../api/claim/ClaimView.java` — add `region()` method
- `havenclaims-api/.../api/limit/HavenClaimsLimitService.java` — rename chunk→block methods
- `havenclaims-plugin/.../plugin/claim/Claim.java` — replace `worldId`+`claimChunks` with `region`
- `havenclaims-plugin/.../plugin/claim/ClaimIndex.java` — iterate `overlappingChunks()`
- `havenclaims-plugin/.../plugin/claim/ClaimService.java` — add `blockRectangle()`, `isWithinBlockBuffer()`
- `havenclaims-plugin/.../plugin/claim/ClaimCreationService.java` — accept `ClaimRegion`, block buffer, disable merge
- `havenclaims-plugin/.../plugin/selection/SelectionService.java` — block corners, return `ClaimRegion`
- `havenclaims-plugin/.../plugin/limit/ClaimCostQuote.java` — rename `*Chunks` → `*Blocks`
- `havenclaims-plugin/.../plugin/limit/ClaimCostConfig.java` — flat-only pricing, add `confirmTimeoutSeconds`
- `havenclaims-plugin/.../plugin/limit/ClaimCostService.java` — block-area totals
- `havenclaims-plugin/.../plugin/limit/ClaimCostMessageService.java` — rename placeholders
- `havenclaims-plugin/.../plugin/limit/LimitService.java` — rename methods, `overageBlocks()`
- `havenclaims-plugin/.../plugin/storage/sql/SqlClaimRepository.java` — write/load `ClaimRegion`
- `havenclaims-plugin/.../plugin/visual/ChunkBorderPlanner.java` — add `planRectangle()`
- `havenclaims-plugin/.../plugin/visual/ChunkBorderVisualService.java` — add `showSelection(ClaimRegion)`
- `havenclaims-plugin/.../plugin/listener/ClaimToolListener.java` — block selection, new messages
- `havenclaims-plugin/.../plugin/listener/ProtectionListener.java` — block-exact containment
- `havenclaims-plugin/.../plugin/command/ClaimsCommand.java` — block API callers, `confirm-purchase`
- `havenclaims-plugin/.../plugin/HavenClaimsPlugin.java` — wire all new services, new config keys
- `havenclaims-plugin/src/main/resources/config.yml` — rename/remove/add keys
- `havenclaims-plugin/src/main/resources/messages.yml` — updated keys and placeholders

---

## Task 1: New Value Types + SQL Migration

**Files:**
- Create: `havenclaims-api/src/main/java/com/invisiblespiders/havenclaims/api/claim/ClaimRegionView.java`
- Create: `havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/claim/ClaimRegion.java`
- Create: `havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/selection/BlockPos.java`
- Create: `havenclaims-plugin/src/main/resources/db/migrations/havenclaims/V5__block_regions.sql`
- Test: `havenclaims-plugin/src/test/java/com/invisiblespiders/havenclaims/plugin/claim/ClaimRegionTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.invisiblespiders.havenclaims.plugin.claim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClaimRegionTest {
    private final UUID world = UUID.randomUUID();

    @Test
    void areaIsInclusiveBlockCount() {
        ClaimRegion region = new ClaimRegion(world, 0, 0, 3, 4);
        assertThat(region.area()).isEqualTo(4 * 5); // (3-0+1)*(4-0+1)
    }

    @Test
    void singleBlockAreaIsOne() {
        assertThat(new ClaimRegion(world, 5, 5, 5, 5).area()).isEqualTo(1);
    }

    @Test
    void containsBlockIncludesBoundary() {
        ClaimRegion r = new ClaimRegion(world, 10, 20, 20, 30);
        assertThat(r.containsBlock(10, 20)).isTrue();
        assertThat(r.containsBlock(20, 30)).isTrue();
        assertThat(r.containsBlock(15, 25)).isTrue();
        assertThat(r.containsBlock(9, 20)).isFalse();
        assertThat(r.containsBlock(21, 25)).isFalse();
    }

    @Test
    void overlappingChunksCoversAllTouchingChunks() {
        // Block (0..15,0..15) → exactly chunk (0,0)
        ClaimRegion single = new ClaimRegion(world, 0, 0, 15, 15);
        assertThat(single.overlappingChunks()).containsExactlyInAnyOrder(
                new ClaimChunk(world, 0, 0));

        // Block (0..16,0..0) → chunks (0,0) and (1,0)
        ClaimRegion twoChunks = new ClaimRegion(world, 0, 0, 16, 0);
        assertThat(twoChunks.overlappingChunks()).containsExactlyInAnyOrder(
                new ClaimChunk(world, 0, 0), new ClaimChunk(world, 1, 0));
    }

    @Test
    void overlappingChunksHandlesNegativeCoordinates() {
        // Block (-1,-1,-1,-1) → chunk (-1,-1) (floorDiv, not >>4)
        ClaimRegion neg = new ClaimRegion(world, -1, -1, -1, -1);
        assertThat(neg.overlappingChunks()).containsExactlyInAnyOrder(
                new ClaimChunk(world, -1, -1));
    }

    @Test
    void constructorRejectsInvertedBounds() {
        assertThatThrownBy(() -> new ClaimRegion(world, 5, 0, 4, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ClaimRegion(world, 0, 5, 0, 4))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew :havenclaims-plugin:test --tests "*.ClaimRegionTest"`
Expected: FAIL — `ClaimRegion` class not found

- [ ] **Step 3: Create `ClaimRegionView` (API)**

```java
// havenclaims-api/src/main/java/com/invisiblespiders/havenclaims/api/claim/ClaimRegionView.java
package com.invisiblespiders.havenclaims.api.claim;

import java.util.UUID;

public interface ClaimRegionView {
    UUID worldId();
    int minX();
    int minZ();
    int maxX();
    int maxZ();
    int area();
}
```

- [ ] **Step 4: Create `ClaimRegion` (plugin)**

```java
// havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/claim/ClaimRegion.java
package com.invisiblespiders.havenclaims.plugin.claim;

import com.invisiblespiders.havenclaims.api.claim.ClaimRegionView;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record ClaimRegion(UUID worldId, int minX, int minZ, int maxX, int maxZ)
        implements ClaimRegionView {
    public ClaimRegion {
        Objects.requireNonNull(worldId, "worldId");
        if (minX > maxX) throw new IllegalArgumentException("minX must be <= maxX");
        if (minZ > maxZ) throw new IllegalArgumentException("minZ must be <= maxZ");
    }

    @Override
    public int area() {
        return (maxX - minX + 1) * (maxZ - minZ + 1);
    }

    public boolean containsBlock(int blockX, int blockZ) {
        return blockX >= minX && blockX <= maxX && blockZ >= minZ && blockZ <= maxZ;
    }

    public Set<ClaimChunk> overlappingChunks() {
        int minChunkX = Math.floorDiv(minX, 16);
        int minChunkZ = Math.floorDiv(minZ, 16);
        int maxChunkX = Math.floorDiv(maxX, 16);
        int maxChunkZ = Math.floorDiv(maxZ, 16);
        Set<ClaimChunk> chunks = new HashSet<>();
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                chunks.add(new ClaimChunk(worldId, cx, cz));
            }
        }
        return Set.copyOf(chunks);
    }
}
```

- [ ] **Step 5: Create `BlockPos`**

```java
// havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/selection/BlockPos.java
package com.invisiblespiders.havenclaims.plugin.selection;

import java.util.Objects;
import java.util.UUID;

public record BlockPos(UUID worldId, int blockX, int blockZ) {
    public BlockPos {
        Objects.requireNonNull(worldId, "worldId");
    }
}
```

- [ ] **Step 6: Create V5 SQL migration**

```sql
-- havenclaims-plugin/src/main/resources/db/migrations/havenclaims/V5__block_regions.sql
DROP TABLE IF EXISTS claim_chunks;

CREATE TABLE claim_block_regions (
    claim_id VARCHAR(36) NOT NULL,
    world_id VARCHAR(36) NOT NULL,
    min_x    INTEGER     NOT NULL,
    min_z    INTEGER     NOT NULL,
    max_x    INTEGER     NOT NULL,
    max_z    INTEGER     NOT NULL,
    PRIMARY KEY (claim_id),
    FOREIGN KEY (claim_id) REFERENCES claims(id) ON DELETE CASCADE
);

ALTER TABLE player_claim_limits RENAME COLUMN chunk_limit TO block_limit;
UPDATE player_claim_limits SET block_limit = block_limit * 256;
```

- [ ] **Step 7: Run test to verify it passes**

Run: `.\gradlew :havenclaims-plugin:test --tests "*.ClaimRegionTest"`
Expected: PASS — BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add havenclaims-api/src/main/java/com/invisiblespiders/havenclaims/api/claim/ClaimRegionView.java \
        havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/claim/ClaimRegion.java \
        havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/selection/BlockPos.java \
        "havenclaims-plugin/src/main/resources/db/migrations/havenclaims/V5__block_regions.sql" \
        havenclaims-plugin/src/test/java/com/invisiblespiders/havenclaims/plugin/claim/ClaimRegionTest.java
git commit -m "feat: add ClaimRegion, ClaimRegionView, BlockPos, V5 SQL migration"
```

---

## Task 2: `ClaimService` — Block Methods

**Files:**
- Modify: `havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/claim/ClaimService.java`
- Test: `havenclaims-plugin/src/test/java/com/invisiblespiders/havenclaims/plugin/claim/ClaimServiceTest.java`

- [ ] **Step 1: Write failing tests (append to existing `ClaimServiceTest`)**

```java
@Test
void blockRectangleNormalizesCornerOrder() {
    UUID world = UUID.randomUUID();
    ClaimService service = new ClaimService();
    BlockPos p1 = new BlockPos(world, 10, 20);
    BlockPos p2 = new BlockPos(world, 5, 30);
    ClaimRegion region = service.blockRectangle(p1, p2);
    assertThat(region).isEqualTo(new ClaimRegion(world, 5, 20, 10, 30));
    assertThat(service.blockRectangle(p2, p1)).isEqualTo(region);
}

@Test
void blockRectangleRejectsDifferentWorlds() {
    ClaimService service = new ClaimService();
    BlockPos p1 = new BlockPos(UUID.randomUUID(), 0, 0);
    BlockPos p2 = new BlockPos(UUID.randomUUID(), 10, 10);
    assertThatThrownBy(() -> service.blockRectangle(p1, p2))
            .isInstanceOf(IllegalArgumentException.class);
}

@Test
void blockBufferDetectsOverlapAndGap() {
    UUID world = UUID.randomUUID();
    ClaimService service = new ClaimService();
    ClaimRegion a = new ClaimRegion(world, 0, 0, 9, 9);   // blocks 0-9
    ClaimRegion b = new ClaimRegion(world, 20, 0, 29, 9); // blocks 20-29
    // gap between a.maxX(9) and b.minX(20): 20-9-1 = 10 blocks
    assertThat(service.isWithinBlockBuffer(a, b, 10)).isFalse();  // gap == buffer
    assertThat(service.isWithinBlockBuffer(a, b, 11)).isTrue();   // gap < buffer

    // adjacent claims: gap = 0
    ClaimRegion adjacent = new ClaimRegion(world, 10, 0, 19, 9);
    assertThat(service.isWithinBlockBuffer(a, adjacent, 1)).isTrue();
    assertThat(service.isWithinBlockBuffer(a, adjacent, 0)).isFalse();
}

@Test
void blockBufferReturnsFalseAcrossWorlds() {
    ClaimService service = new ClaimService();
    ClaimRegion a = new ClaimRegion(UUID.randomUUID(), 0, 0, 9, 9);
    ClaimRegion b = new ClaimRegion(UUID.randomUUID(), 0, 0, 9, 9);
    assertThat(service.isWithinBlockBuffer(a, b, 1000)).isFalse();
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `.\gradlew :havenclaims-plugin:test --tests "*.ClaimServiceTest"`
Expected: FAIL — `blockRectangle`, `isWithinBlockBuffer` not found

- [ ] **Step 3: Add block methods to `ClaimService`**

Append to `ClaimService.java` (keep existing `expandRectangle` and `isWithinChunkBuffer`):

```java
// Add import at top: import com.invisiblespiders.havenclaims.plugin.selection.BlockPos;

public ClaimRegion blockRectangle(BlockPos p1, BlockPos p2) {
    Objects.requireNonNull(p1, "p1");
    Objects.requireNonNull(p2, "p2");
    if (!p1.worldId().equals(p2.worldId())) {
        throw new IllegalArgumentException("Both positions must be in the same world");
    }
    return new ClaimRegion(
            p1.worldId(),
            Math.min(p1.blockX(), p2.blockX()),
            Math.min(p1.blockZ(), p2.blockZ()),
            Math.max(p1.blockX(), p2.blockX()),
            Math.max(p1.blockZ(), p2.blockZ())
    );
}

public boolean isWithinBlockBuffer(ClaimRegion proposed, ClaimRegion existing, int bufferBlocks) {
    Objects.requireNonNull(proposed, "proposed");
    Objects.requireNonNull(existing, "existing");
    if (bufferBlocks < 0) throw new IllegalArgumentException("bufferBlocks must be non-negative");
    return minimumBlockGap(proposed, existing) < bufferBlocks;
}

static int minimumBlockGap(ClaimRegion a, ClaimRegion b) {
    if (!a.worldId().equals(b.worldId())) return Integer.MAX_VALUE;
    int gapX = Math.max(0, Math.max(a.minX(), b.minX()) - Math.min(a.maxX(), b.maxX()) - 1);
    int gapZ = Math.max(0, Math.max(a.minZ(), b.minZ()) - Math.min(a.maxZ(), b.maxZ()) - 1);
    return Math.max(gapX, gapZ);
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `.\gradlew :havenclaims-plugin:test --tests "*.ClaimServiceTest"`
Expected: PASS — BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/claim/ClaimService.java \
        havenclaims-plugin/src/test/java/com/invisiblespiders/havenclaims/plugin/claim/ClaimServiceTest.java
git commit -m "feat: add blockRectangle() and isWithinBlockBuffer() to ClaimService"
```

---

## Task 3: `HavenClaimsLimitService` + `LimitService` API Rename

**Files:**
- Modify: `havenclaims-api/src/main/java/com/invisiblespiders/havenclaims/api/limit/HavenClaimsLimitService.java`
- Modify: `havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/limit/LimitService.java`
- Modify: `havenclaims-plugin/src/test/java/com/invisiblespiders/havenclaims/plugin/limit/LimitServiceTest.java`

- [ ] **Step 1: Update `HavenClaimsLimitService` interface**

Replace the file entirely:

```java
package com.invisiblespiders.havenclaims.api.limit;

import java.util.UUID;

public interface HavenClaimsLimitService {
    int getBlockLimit(UUID playerId);
    void setBlockLimit(UUID playerId, int limit);
    void addBlocks(UUID playerId, int blocks);
    void removeBlocks(UUID playerId, int blocks);
}
```

- [ ] **Step 2: Update `LimitService` to implement new interface**

Replace method bodies (keep `defaultLimit`, `repository`, `reload()` unchanged):

```java
@Override
public int getBlockLimit(UUID playerId) {
    Objects.requireNonNull(playerId, "playerId");
    return repository.getLimit(playerId).orElse(defaultLimit);
}

@Override
public void setBlockLimit(UUID playerId, int limit) {
    Objects.requireNonNull(playerId, "playerId");
    if (limit < 1) throw new IllegalArgumentException("limit must be >= 1");
    repository.setLimit(playerId, limit);
}

@Override
public void addBlocks(UUID playerId, int blocks) {
    Objects.requireNonNull(playerId, "playerId");
    if (blocks < 1) throw new IllegalArgumentException("blocks must be >= 1");
    repository.updateLimit(playerId, defaultLimit, current -> current + blocks);
}

@Override
public void removeBlocks(UUID playerId, int blocks) {
    Objects.requireNonNull(playerId, "playerId");
    if (blocks < 1) throw new IllegalArgumentException("blocks must be >= 1");
    repository.updateLimit(playerId, defaultLimit, current -> Math.max(1, current - blocks));
}

public int overageBlocks(int proposedTotalBlocks, int allowedBlocks) {
    return Math.max(0, proposedTotalBlocks - allowedBlocks);
}

public static double flatOverLimitCost(int overageBlocks, double costPerBlock) {
    return Math.max(0, overageBlocks) * Math.max(0.0, costPerBlock);
}
```

Remove `overageChunks()`, `exponentialOverLimitCost()` static method.

- [ ] **Step 3: Fix `ClaimsCommand` inline anonymous `HavenClaimsLimitService` (constructor shim)**

In `ClaimsCommand.java`, find the anonymous implementation in the no-arg test constructor and update:

```java
// Old:
@Override public void addChunks(java.util.UUID playerId, int chunks) {}
@Override public void removeChunks(java.util.UUID playerId, int chunks) {}
@Override public int getLimit(java.util.UUID playerId) { return 0; }
@Override public void setLimit(java.util.UUID playerId, int limit) {}

// New:
@Override public int getBlockLimit(java.util.UUID playerId) { return 0; }
@Override public void setBlockLimit(java.util.UUID playerId, int limit) {}
@Override public void addBlocks(java.util.UUID playerId, int blocks) {}
@Override public void removeBlocks(java.util.UUID playerId, int blocks) {}
```

Also update the three call sites in `ClaimsCommand.manageAdminLimit()`:
```java
// "add" case:
claimLimitService.addBlocks(targetId, amount);
// "remove" case:
claimLimitService.removeBlocks(targetId, amount);
// "get" case:
int limit = claimLimitService.getBlockLimit(targetId);
// "set" case:
claimLimitService.setBlockLimit(targetId, amount);
```

And update any call to `claimLimitService.getLimit()` → `claimLimitService.getBlockLimit()`.

- [ ] **Step 4: Run all tests**

Run: `.\gradlew test`
Expected: PASS — BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add havenclaims-api/src/main/java/com/invisiblespiders/havenclaims/api/limit/HavenClaimsLimitService.java \
        havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/limit/LimitService.java \
        havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/command/ClaimsCommand.java \
        havenclaims-plugin/src/test/java/com/invisiblespiders/havenclaims/plugin/limit/LimitServiceTest.java
git commit -m "refactor: rename chunk limit API to block limit (addBlocks, removeBlocks, getBlockLimit)"
```

---

## Task 4: `ClaimCostQuote` + `ClaimCostConfig` + `ClaimCostService` + `ClaimCostMessageService`

**Files:**
- Modify: `havenclaims-plugin/.../plugin/limit/ClaimCostQuote.java`
- Modify: `havenclaims-plugin/.../plugin/limit/ClaimCostConfig.java`
- Modify: `havenclaims-plugin/.../plugin/limit/ClaimCostService.java`
- Modify: `havenclaims-plugin/.../plugin/limit/ClaimCostMessageService.java`
- Test: `havenclaims-plugin/src/test/java/com/invisiblespiders/havenclaims/plugin/limit/ClaimCostConfigTest.java`
- Test: `havenclaims-plugin/src/test/java/com/invisiblespiders/havenclaims/plugin/limit/ClaimCostServiceTest.java`

- [ ] **Step 1: Replace `ClaimCostQuote`**

```java
package com.invisiblespiders.havenclaims.plugin.limit;

public record ClaimCostQuote(
        int allowedBlocks,
        int existingBlocks,
        int selectedBlocks,
        int proposedTotalBlocks,
        int overageBlocks,
        double cost
) {}
```

- [ ] **Step 2: Replace `ClaimCostConfig`**

```java
package com.invisiblespiders.havenclaims.plugin.limit;

import org.bukkit.configuration.file.FileConfiguration;

public record ClaimCostConfig(
        boolean overLimitEnabled,
        double flatCostPerBlock,
        int confirmTimeoutSeconds
) {
    public static ClaimCostConfig from(FileConfiguration configuration) {
        return new ClaimCostConfig(
                configuration.getBoolean("limits.over-limit.enabled", true),
                configuration.getDouble("limits.over-limit.flat-cost-per-block", 0.10),
                configuration.getInt("limits.over-limit.confirm-timeout-seconds", 60)
        );
    }

    public double priceOverage(int overageBlocks) {
        if (!overLimitEnabled || overageBlocks <= 0) return 0.0;
        return overageBlocks * flatCostPerBlock;
    }
}
```

- [ ] **Step 3: Update `ClaimCostService`**

Replace `quotePlayerClaim` and `computeDeletionRefund`. Note: `Claim` still has `claimChunks()` shim (added in Task 6) — for now keep using it for the interim. However, since `ClaimCostService` is updated NOW, we want it to call `claim.region().area()` after Task 6 swaps `Claim`. Use a temporary approach: since `Claim` has both `claimChunks()` and will soon have `region()`, update now to use the region once Task 6 lands.

Because `Claim.region()` doesn't exist yet (Task 6 adds it), keep `claimChunks().size()` calls in `ClaimCostService` for now and note they will be updated in Task 6.

Actually, replace the whole file with block semantics but keep `claimChunks().size()` as a stand-in — it returns the same count after Task 6 (overlapping chunks, NOT block area). Switch to `region().area()` in Task 6.

For now, update `quotePlayerClaim` to accept `ClaimRegion` (the new API) and add a shim for `Set<ClaimChunk>`:

```java
package com.invisiblespiders.havenclaims.plugin.limit;

import com.invisiblespiders.havenclaims.plugin.claim.ClaimIndex;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimRegion;
import com.invisiblespiders.havenclaims.plugin.claim.OwnerType;
import java.util.Objects;
import java.util.UUID;

public final class ClaimCostService {
    private final ClaimIndex claimIndex;
    private final LimitService limitService;
    private ClaimCostConfig claimCostConfig;

    public ClaimCostService(ClaimIndex claimIndex, LimitService limitService, ClaimCostConfig claimCostConfig) {
        this.claimIndex = Objects.requireNonNull(claimIndex, "claimIndex");
        this.limitService = Objects.requireNonNull(limitService, "limitService");
        this.claimCostConfig = Objects.requireNonNull(claimCostConfig, "claimCostConfig");
    }

    public void reload(ClaimCostConfig newConfig) {
        this.claimCostConfig = Objects.requireNonNull(newConfig, "newConfig");
    }

    public boolean isPaidOverLimitEnabled() {
        return claimCostConfig.overLimitEnabled();
    }

    public int confirmTimeoutSeconds() {
        return claimCostConfig.confirmTimeoutSeconds();
    }

    /** Primary API: quote using exact block area. */
    public ClaimCostQuote quotePlayerClaim(UUID ownerId, ClaimRegion selectedRegion) {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(selectedRegion, "selectedRegion");

        int allowedBlocks = limitService.getBlockLimit(ownerId);
        // existingBlocks will use region().area() after Task 6; uses claimChunks().size() for now
        int existingBlocks = claimIndex.findAll().stream()
                .filter(c -> c.owner() == OwnerType.PLAYER && ownerId.equals(c.ownerUuid()))
                .mapToInt(c -> c.claimChunks().size())
                .sum();
        int selectedBlocks = selectedRegion.area();
        int proposedTotalBlocks = existingBlocks + selectedBlocks;
        int overageBlocks = limitService.overageBlocks(proposedTotalBlocks, allowedBlocks);
        return new ClaimCostQuote(allowedBlocks, existingBlocks, selectedBlocks,
                proposedTotalBlocks, overageBlocks, claimCostConfig.priceOverage(overageBlocks));
    }

    public double computeDeletionRefund(UUID ownerId, int blocksBeingRemoved) {
        Objects.requireNonNull(ownerId, "ownerId");
        int allowedBlocks = limitService.getBlockLimit(ownerId);
        int existingTotal = claimIndex.findAll().stream()
                .filter(c -> c.owner() == OwnerType.PLAYER && ownerId.equals(c.ownerUuid()))
                .mapToInt(c -> c.claimChunks().size())
                .sum();
        int afterDeletion = existingTotal - blocksBeingRemoved;
        double costBefore = claimCostConfig.priceOverage(Math.max(0, existingTotal - allowedBlocks));
        double costAfter  = claimCostConfig.priceOverage(Math.max(0, afterDeletion  - allowedBlocks));
        return Math.max(0.0, costBefore - costAfter);
    }
}
```

- [ ] **Step 4: Update `ClaimCostMessageService`**

Replace field names in the `Map.of(...)`:

```java
// Old fields: "selected_chunks", "proposed_total_chunks", "allowed_chunks", "overage_chunks"
// Old checks:  quote.overageChunks(), quote.selectedChunks(), etc.
// New:
if (quote.overageBlocks() > 0 && !paidOverLimitEnabled) {
    ...
}
Map<String, String> placeholders = Map.of(
        "selected_blocks", String.valueOf(quote.selectedBlocks()),
        "proposed_total_blocks", String.valueOf(quote.proposedTotalBlocks()),
        "allowed_blocks", String.valueOf(quote.allowedBlocks()),
        "overage_blocks", String.valueOf(quote.overageBlocks()),
        "cost", costText
);
```

- [ ] **Step 5: Fix `ClaimsCommand` call sites that reference old `ClaimCostQuote` fields**

Find and replace in `ClaimsCommand.java`:
- `quote.selectedChunks()` → `quote.selectedBlocks()`
- `quote.allowedChunks()` → `quote.allowedBlocks()`
- `quote.overageChunks()` → `quote.overageBlocks()`
- `quote.proposedTotalChunks()` → `quote.proposedTotalBlocks()`

The `quotePlayerClaim` call in `ClaimsCommand` currently passes `Set<ClaimChunk>`. Update to pass the region. But `ClaimRegion` isn't available from `SelectionService` yet (Task 7). Add a temporary helper in `ClaimsCommand` that gets the selection as chunks and wraps into a synthetic region for quoting (uses min/max of chunks):

Locate the `createClaim` helper method (around line 1869) and the `previewClaimCost` method. Both call `selectionService.pendingSelection()` returning `Optional<Set<ClaimChunk>>`. For the quote call, add a local helper:

```java
private ClaimRegion selectionAsRegion(Set<ClaimChunk> chunks) {
    UUID worldId = chunks.iterator().next().worldId();
    int minX = chunks.stream().mapToInt(c -> c.chunkX() * 16).min().getAsInt();
    int minZ = chunks.stream().mapToInt(c -> c.chunkZ() * 16).min().getAsInt();
    int maxX = chunks.stream().mapToInt(c -> c.chunkX() * 16 + 15).max().getAsInt();
    int maxZ = chunks.stream().mapToInt(c -> c.chunkZ() * 16 + 15).max().getAsInt();
    return new ClaimRegion(worldId, minX, minZ, maxX, maxZ);
}
```

And change each `claimCostService.quotePlayerClaim(playerId, chunks)` call to:
```java
claimCostService.quotePlayerClaim(player.getUniqueId(), selectionAsRegion(chunks))
```

The shim method and `selectionAsRegion` helper are removed in Task 7.

- [ ] **Step 6: Update `AdminClaimBrowserService` if it uses old quote fields**

Search for `overageChunks\|selectedChunks\|allowedChunks` in `AdminClaimBrowserService.java` and rename to `*Blocks`.

- [ ] **Step 7: Run all tests**

Run: `.\gradlew test`
Expected: PASS — BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/limit/ \
        havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/command/ClaimsCommand.java \
        havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/ui/AdminClaimBrowserService.java \
        havenclaims-plugin/src/test/java/com/invisiblespiders/havenclaims/plugin/limit/
git commit -m "refactor: rename ClaimCostQuote/Config/Service to block semantics, flat-only pricing"
```

---

## Task 5: Visual Border — Block Rectangle

**Files:**
- Modify: `havenclaims-plugin/.../plugin/visual/ChunkBorderPlanner.java`
- Modify: `havenclaims-plugin/.../plugin/visual/ChunkBorderVisualService.java`
- Test: `havenclaims-plugin/src/test/java/com/invisiblespiders/havenclaims/plugin/visual/ChunkBorderPlannerTest.java`

- [ ] **Step 1: Write failing test (append to `ChunkBorderPlannerTest`)**

```java
@Test
void planRectangleProducesFourEdgesForSingleBlock() {
    UUID world = UUID.randomUUID();
    ClaimRegion region = new ClaimRegion(world, 5, 10, 5, 10);
    ChunkBorderPlan plan = ChunkBorderPlanner.planRectangle(region, (w, x, z) -> 64.0, BorderColor.GREEN, 100);
    // Single block 5,10 → border at x=5..6, z=10..11 → 4 edges (1 per side)
    assertThat(plan.edges()).hasSize(4);
}

@Test
void planRectangleEdgesMatchRegionBounds() {
    UUID world = UUID.randomUUID();
    ClaimRegion region = new ClaimRegion(world, 0, 0, 15, 15);
    ChunkBorderPlan plan = ChunkBorderPlanner.planRectangle(region, (w, x, z) -> 64.0, BorderColor.GREEN, 100);
    // North edge at z=0: 16 segments (x=0..15)
    // South edge at z=16: 16 segments
    // West edge at x=0: 16 segments (z=0..15)
    // East edge at x=16: 16 segments
    assertThat(plan.edges()).hasSize(64);
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `.\gradlew :havenclaims-plugin:test --tests "*.ChunkBorderPlannerTest"`
Expected: FAIL — `planRectangle` not found

- [ ] **Step 3: Add `planRectangle()` to `ChunkBorderPlanner`**

Add this static method to `ChunkBorderPlanner.java` (import `ClaimRegion` at top):

```java
public static ChunkBorderPlan planRectangle(
        ClaimRegion region,
        ChunkGroundHeightProvider heightProvider,
        BorderColor color,
        int durationTicks
) {
    Objects.requireNonNull(region, "region");
    Objects.requireNonNull(heightProvider, "heightProvider");
    Objects.requireNonNull(color, "color");

    UUID worldId = region.worldId();
    int edgeMinX = region.minX();
    int edgeMaxX = region.maxX() + 1;
    int edgeMinZ = region.minZ();
    int edgeMaxZ = region.maxZ() + 1;

    List<BorderEdge> edges = new ArrayList<>();
    addHorizontalSegments(edges, worldId, edgeMinX, edgeMaxX, edgeMinZ, edgeMinZ, heightProvider, color);
    addHorizontalSegments(edges, worldId, edgeMinX, edgeMaxX, edgeMaxZ, edgeMaxZ - 1, heightProvider, color);
    addVerticalSegments(edges, worldId, edgeMinX, edgeMinZ, edgeMaxZ, edgeMinX, heightProvider, color);
    addVerticalSegments(edges, worldId, edgeMaxX, edgeMinZ, edgeMaxZ, edgeMaxX - 1, heightProvider, color);

    edges.sort(Comparator
            .comparing(BorderEdge::worldId)
            .thenComparingInt(BorderEdge::x1)
            .thenComparingInt(BorderEdge::z1)
            .thenComparingInt(BorderEdge::x2)
            .thenComparingInt(BorderEdge::z2));
    return new ChunkBorderPlan(edges, durationTicks);
}
```

- [ ] **Step 4: Add `showSelection(Player, ClaimRegion, BorderColor)` to `ChunkBorderVisualService`**

```java
public void showSelection(Player player, ClaimRegion region, BorderColor color) {
    Objects.requireNonNull(player, "player");
    Objects.requireNonNull(region, "region");
    Objects.requireNonNull(color, "color");

    UUID playerId = player.getUniqueId();
    if (activePlayers.contains(playerId)) {
        renderer.clear(playerId);
    }
    activePlayers.add(playerId);
    renderer.show(player, ChunkBorderPlanner.planRectangle(region, heightProvider, color, durationTicks));
}
```

Add import `import com.invisiblespiders.havenclaims.plugin.claim.ClaimRegion;` at the top.

- [ ] **Step 5: Run all tests**

Run: `.\gradlew test`
Expected: PASS — BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/visual/ChunkBorderPlanner.java \
        havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/visual/ChunkBorderVisualService.java \
        havenclaims-plugin/src/test/java/com/invisiblespiders/havenclaims/plugin/visual/ChunkBorderPlannerTest.java
git commit -m "feat: add planRectangle() and showSelection(ClaimRegion) to border visuals"
```

---

## Task 6: `Claim` Model Swap + `ClaimIndex` + `SqlClaimRepository` + `ClaimCreationService`

This is the largest task — it migrates the core data model. All four files must change together or the project won't compile.

**Files:**
- Modify: `havenclaims-api/src/main/java/com/invisiblespiders/havenclaims/api/claim/ClaimView.java`
- Modify: `havenclaims-plugin/.../plugin/claim/Claim.java`
- Modify: `havenclaims-plugin/.../plugin/claim/ClaimIndex.java`
- Modify: `havenclaims-plugin/.../plugin/storage/sql/SqlClaimRepository.java`
- Modify: `havenclaims-plugin/.../plugin/claim/ClaimCreationService.java`
- Modify: `havenclaims-plugin/.../plugin/limit/ClaimCostService.java` (switch to `region().area()`)
- Test: `havenclaims-plugin/src/test/java/com/invisiblespiders/havenclaims/plugin/claim/ClaimTest.java`
- Test: `havenclaims-plugin/src/test/java/com/invisiblespiders/havenclaims/plugin/claim/ClaimIndexTest.java`
- Test: `havenclaims-plugin/src/test/java/com/invisiblespiders/havenclaims/plugin/storage/sql/SqlClaimRepositoryTest.java`
- Test: `havenclaims-plugin/src/test/java/com/invisiblespiders/havenclaims/plugin/claim/ClaimCreationServiceTest.java`

- [ ] **Step 1: Write failing test for `Claim` constructor**

Add/update `ClaimTest.java`:

```java
package com.invisiblespiders.havenclaims.plugin.claim;

import static org.assertj.core.api.Assertions.assertThat;
import com.invisiblespiders.havenclaims.api.flag.FlagState;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClaimTest {
    private final UUID world = UUID.randomUUID();
    private final UUID owner = UUID.randomUUID();
    private final ClaimRegion region = new ClaimRegion(world, 0, 0, 15, 15);
    private final Instant now = Instant.now();

    @Test
    void worldIdDelegatestoRegion() {
        Claim claim = claim(region);
        assertThat(claim.worldId()).isEqualTo(world);
    }

    @Test
    void overlappingChunksComesFromRegion() {
        Claim claim = claim(region);
        assertThat(claim.overlappingChunks()).isEqualTo(region.overlappingChunks());
    }

    @Test
    void regionAccessorReturnsRegion() {
        Claim claim = claim(region);
        assertThat(claim.region()).isEqualTo(region);
    }

    private Claim claim(ClaimRegion r) {
        return new Claim(UUID.randomUUID(), "Home", OwnerType.PLAYER, owner,
                r, Map.of(), now, now);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew :havenclaims-plugin:test --tests "*.ClaimTest"`
Expected: FAIL — constructors don't match

- [ ] **Step 3: Update `ClaimView` interface**

Add `region()` method:

```java
// Add to ClaimView.java:
import com.invisiblespiders.havenclaims.api.claim.ClaimRegionView;

ClaimRegionView region();
```

- [ ] **Step 4: Rewrite `Claim` record**

Replace `Claim.java` entirely:

```java
package com.invisiblespiders.havenclaims.plugin.claim;

import com.invisiblespiders.havenclaims.api.claim.ClaimChunkView;
import com.invisiblespiders.havenclaims.api.claim.ClaimView;
import com.invisiblespiders.havenclaims.api.flag.FlagState;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record Claim(
        UUID id,
        String name,
        OwnerType owner,
        UUID ownerUuid,
        ClaimRegion region,
        Map<String, FlagState> flags,
        Set<ClaimMember> members,
        Set<UUID> deniedPlayers,
        Instant createdAt,
        Instant updatedAt
) implements ClaimView {
    public Claim {
        id = Objects.requireNonNull(id, "id");
        name = Objects.requireNonNull(name, "name");
        owner = Objects.requireNonNull(owner, "owner");
        region = Objects.requireNonNull(region, "region");
        flags = Map.copyOf(Objects.requireNonNull(flags, "flags"));
        members = Set.copyOf(Objects.requireNonNull(members, "members"));
        deniedPlayers = Set.copyOf(Objects.requireNonNull(deniedPlayers, "deniedPlayers"));
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public Claim(UUID id, String name, OwnerType owner, UUID ownerUuid, ClaimRegion region,
                 Map<String, FlagState> flags, Set<ClaimMember> members,
                 Instant createdAt, Instant updatedAt) {
        this(id, name, owner, ownerUuid, region, flags, members, Set.of(), createdAt, updatedAt);
    }

    public Claim(UUID id, String name, OwnerType owner, UUID ownerUuid, ClaimRegion region,
                 Map<String, FlagState> flags, Instant createdAt, Instant updatedAt) {
        this(id, name, owner, ownerUuid, region, flags, Set.of(), Set.of(), createdAt, updatedAt);
    }

    public UUID worldId() { return region.worldId(); }
    public Set<ClaimChunk> overlappingChunks() { return region.overlappingChunks(); }

    /** Backward-compat shim — removed in Task 14. */
    public Set<ClaimChunk> claimChunks() { return overlappingChunks(); }

    @Override
    public String ownerType() { return owner.name(); }

    @Override
    public Set<ClaimChunkView> chunks() {
        return region.overlappingChunks().stream()
                .map(chunk -> new ClaimChunkView(chunk.worldId(), chunk.chunkX(), chunk.chunkZ()))
                .collect(Collectors.toUnmodifiableSet());
    }
}
```

- [ ] **Step 5: Update `ClaimIndex`**

Change `add()` to use `overlappingChunks()`:

```java
public void add(Claim claim) {
    Objects.requireNonNull(claim, "claim");
    for (ClaimChunk chunk : claim.overlappingChunks()) {
        byChunk.put(chunk, claim);
    }
}
```

- [ ] **Step 6: Update `SqlClaimRepository`**

Replace the chunk-based methods with region-based equivalents. Key changes:

**a) `deleteClaim(Connection, UUID)` — remove the `claim_chunks` DELETE (now handled by V5 migration cascade)**:
```java
private void deleteClaim(Connection connection, UUID claimId) throws SQLException {
    executeDelete(connection, "DELETE FROM claim_members WHERE claim_id = ?", claimId);
    executeDelete(connection, "DELETE FROM claim_denied_players WHERE claim_id = ?", claimId);
    executeDelete(connection, "DELETE FROM claim_flags WHERE claim_id = ?", claimId);
    executeDelete(connection, "DELETE FROM claim_block_regions WHERE claim_id = ?", claimId);
    executeDelete(connection, "DELETE FROM claims WHERE id = ?", claimId);
}
```

**b) `replaceClaim(Connection, Claim)` — call `insertRegion` instead of `insertChunks`**:
```java
private void replaceClaim(Connection connection, Claim claim) throws SQLException {
    deleteClaim(connection, claim.id());
    insertClaim(connection, claim);
    insertRegion(connection, claim);
    insertFlags(connection, claim);
    insertMembers(connection, claim);
    insertDeniedPlayers(connection, claim);
}
```

**c) Add `insertRegion(Connection, Claim)`**:
```java
private void insertRegion(Connection connection, Claim claim) throws SQLException {
    String sql = """
            INSERT INTO claim_block_regions (claim_id, world_id, min_x, min_z, max_x, max_z)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
        statement.setString(1, claim.id().toString());
        statement.setString(2, claim.worldId().toString());
        statement.setInt(3, claim.region().minX());
        statement.setInt(4, claim.region().minZ());
        statement.setInt(5, claim.region().maxX());
        statement.setInt(6, claim.region().maxZ());
        statement.executeUpdate();
    }
}
```

**d) Replace `bulkLoadChunks()` with `bulkLoadRegions()`**:
```java
private Map<UUID, ClaimRegion> bulkLoadRegions(Connection connection, List<UUID> claimIds) throws SQLException {
    Map<UUID, ClaimRegion> result = new HashMap<>();
    String sql = "SELECT claim_id, world_id, min_x, min_z, max_x, max_z FROM claim_block_regions WHERE claim_id IN ("
            + inClausePlaceholders(claimIds.size()) + ")";
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
        bindClaimIds(statement, claimIds);
        try (ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                UUID claimId = UUID.fromString(resultSet.getString("claim_id"));
                result.put(claimId, new ClaimRegion(
                        UUID.fromString(resultSet.getString("world_id")),
                        resultSet.getInt("min_x"),
                        resultSet.getInt("min_z"),
                        resultSet.getInt("max_x"),
                        resultSet.getInt("max_z")
                ));
            }
        }
    }
    return result;
}
```

**e) Update `mapClaims()`** — replace `bulkLoadChunks` with `bulkLoadRegions`, use `ClaimRegion` when constructing `Claim`:
```java
Map<UUID, ClaimRegion> regionsByClaim = bulkLoadRegions(connection, claimIds);
// ...
for (ClaimRow row : rows) {
    ClaimRegion region = regionsByClaim.get(row.id());
    if (region == null) continue; // skip claims with no region (data integrity issue)
    claims.add(new Claim(
            row.id(), row.name(), row.ownerType(), row.ownerUuid(),
            region,
            flagsByClaim.getOrDefault(row.id(), Map.of()),
            membersByClaim.getOrDefault(row.id(), Set.of()),
            deniedByClaim.getOrDefault(row.id(), Set.of()),
            row.createdAt(), row.updatedAt()
    ));
}
```

**f) Remove `mapClaim(Connection, ResultSet)` and `loadChunks()`** — single-record load path is now unused (all callers use `mapClaims`). Remove these methods or update `findClaimById()` to use the bulk path with a single ID.

**g) Update `findClaimAt(UUID worldId, int chunkX, int chunkZ)`** — update SQL to use `claim_block_regions` (the chunk-based lookup is gone; this method now does a range check):
```java
@Override
public Optional<Claim> findClaimAt(UUID worldId, int chunkX, int chunkZ) {
    // Convert chunk coords to block range and do a range overlap query
    int blockMinX = chunkX * 16, blockMaxX = blockMinX + 15;
    int blockMinZ = chunkZ * 16, blockMaxZ = blockMinZ + 15;
    String sql = """
            SELECT c.*
            FROM claims c
            INNER JOIN claim_block_regions r ON c.id = r.claim_id
            WHERE r.world_id = ?
              AND r.min_x <= ? AND r.max_x >= ?
              AND r.min_z <= ? AND r.max_z >= ?
            """;
    try (Connection connection = dataSource.getConnection();
         PreparedStatement statement = connection.prepareStatement(sql)) {
        statement.setString(1, worldId.toString());
        statement.setInt(2, blockMaxX);
        statement.setInt(3, blockMinX);
        statement.setInt(4, blockMaxZ);
        statement.setInt(5, blockMinZ);
        try (ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) return Optional.empty();
            UUID claimId = UUID.fromString(resultSet.getString("id"));
            List<UUID> ids = List.of(claimId);
            // Re-use bulk loaders for consistency
            Map<UUID, ClaimRegion> regions = bulkLoadRegions(connection, ids);
            Map<UUID, Map<String, FlagState>> flags = bulkLoadFlags(connection, ids);
            Map<UUID, Set<ClaimMember>> members = bulkLoadMembers(connection, ids);
            Map<UUID, Set<UUID>> denied = bulkLoadDeniedPlayers(connection, ids);
            ClaimRegion region = regions.get(claimId);
            if (region == null) return Optional.empty();
            return Optional.of(new Claim(
                    claimId,
                    resultSet.getString("name"),
                    OwnerType.valueOf(resultSet.getString("owner_type")),
                    nullableUuid(resultSet.getString("owner_uuid")),
                    region,
                    flags.getOrDefault(claimId, Map.of()),
                    members.getOrDefault(claimId, Set.of()),
                    denied.getOrDefault(claimId, Set.of()),
                    Instant.parse(resultSet.getString("created_at")),
                    Instant.parse(resultSet.getString("updated_at"))
            ));
        }
    } catch (SQLException exception) {
        throw new IllegalStateException("Failed to find claim by chunk.", exception);
    }
}
```

- [ ] **Step 7: Update `ClaimCreationService`**

Replace the entire file. Key changes:
- `createPlayerClaim(UUID, String, ClaimRegion)` is the primary method
- Merge logic disabled (`findMergeTargets` returns `List.of()`)
- Buffer uses `isWithinBlockBuffer`
- Add a shim `createPlayerClaim(UUID, String, Set<ClaimChunk>)` for `ClaimsCommand` backward compat (removed in Task 7)

```java
package com.invisiblespiders.havenclaims.plugin.claim;

import com.invisiblespiders.havenclaims.api.flag.FlagState;
import com.invisiblespiders.havenclaims.plugin.flag.FlagRegistry;
import com.invisiblespiders.havenclaims.plugin.storage.ClaimRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class ClaimCreationService {
    private final ClaimRepository claimRepository;
    private final ClaimIndex claimIndex;
    private final ClaimService claimService;
    private final FlagRegistry flagRegistry;
    private int playerBufferDistance;
    private int adminBufferDistance;
    private int maxClaimNameLength;

    public ClaimCreationService(
            ClaimRepository claimRepository, ClaimIndex claimIndex, ClaimService claimService,
            FlagRegistry flagRegistry, int playerBufferDistance, int adminBufferDistance, int maxClaimNameLength) {
        this.claimRepository = Objects.requireNonNull(claimRepository);
        this.claimIndex = Objects.requireNonNull(claimIndex);
        this.claimService = Objects.requireNonNull(claimService);
        this.flagRegistry = Objects.requireNonNull(flagRegistry);
        if (playerBufferDistance < 0 || adminBufferDistance < 0)
            throw new IllegalArgumentException("buffer distances must be non-negative");
        if (maxClaimNameLength < 1) throw new IllegalArgumentException("maxClaimNameLength must be >= 1");
        this.playerBufferDistance = playerBufferDistance;
        this.adminBufferDistance = adminBufferDistance;
        this.maxClaimNameLength = maxClaimNameLength;
    }

    public void reload(int newPlayerBufferDistance, int newAdminBufferDistance, int newMaxNameLength) {
        if (newPlayerBufferDistance < 0 || newAdminBufferDistance < 0)
            throw new IllegalArgumentException("buffer distances must be non-negative");
        if (newMaxNameLength < 1) throw new IllegalArgumentException("maxClaimNameLength must be >= 1");
        this.playerBufferDistance = newPlayerBufferDistance;
        this.adminBufferDistance = newAdminBufferDistance;
        this.maxClaimNameLength = newMaxNameLength;
    }

    public ClaimValidationResult createPlayerClaim(UUID ownerUuid, String name, ClaimRegion region) {
        return createPlayerClaim(ownerUuid, name, region, false);
    }

    public ClaimValidationResult createPlayerClaim(UUID ownerUuid, String name, ClaimRegion region, boolean bypassBuffer) {
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(region, "region");

        String trimmedName = name.trim();
        ClaimValidationResult validation = validatePlayerClaim(ownerUuid, trimmedName, region, bypassBuffer);
        if (!validation.isAllowed()) return validation;

        Instant now = Instant.now();
        Claim claim = new Claim(UUID.randomUUID(), trimmedName, OwnerType.PLAYER, ownerUuid,
                region, defaultFlags(), now, now);
        claimRepository.saveClaim(claim);
        claimIndex.add(claim);
        return ClaimValidationResult.allowed();
    }

    public ClaimValidationResult validatePlayerClaim(UUID ownerUuid, String name, ClaimRegion region) {
        return validatePlayerClaim(ownerUuid, name, region, false);
    }

    public ClaimValidationResult validatePlayerClaim(UUID ownerUuid, String name, ClaimRegion region, boolean bypassBuffer) {
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(region, "region");

        String trimmedName = name.trim();
        if (trimmedName.isEmpty() || trimmedName.length() > maxClaimNameLength)
            return ClaimValidationResult.denied("claims.invalid-name");

        for (ClaimChunk chunk : region.overlappingChunks()) {
            if (claimIndex.findAt(chunk).isPresent())
                return ClaimValidationResult.denied("claims.overlap");
        }

        if (!bypassBuffer) {
            List<Claim> allClaims = claimIndex.findAll();
            for (Claim existingClaim : allClaims) {
                ClaimValidationResult bufferResult = validateBlockBuffer(ownerUuid, region, existingClaim);
                if (!bufferResult.isAllowed()) return bufferResult;
            }
        }

        return ClaimValidationResult.allowed();
    }

    private ClaimValidationResult validateBlockBuffer(UUID ownerUuid, ClaimRegion proposed, Claim existingClaim) {
        if (existingClaim.owner() == OwnerType.PLAYER && ownerUuid.equals(existingClaim.ownerUuid()))
            return ClaimValidationResult.allowed();
        int bufferBlocks = existingClaim.owner() == OwnerType.ADMIN ? adminBufferDistance : playerBufferDistance;
        if (!claimService.isWithinBlockBuffer(proposed, existingClaim.region(), bufferBlocks))
            return ClaimValidationResult.allowed();
        if (existingClaim.owner() == OwnerType.ADMIN)
            return ClaimValidationResult.denied("claims.too-close-admin");
        return ClaimValidationResult.denied("claims.too-close");
    }

    /** Merge targets always empty in Phase 1 — block rectangles don't merge cleanly. */
    public List<Claim> findMergeTargets(UUID ownerUuid, String name, ClaimRegion region) {
        return List.of();
    }

    private Map<String, FlagState> defaultFlags() {
        return flagRegistry.keys().stream()
                .collect(Collectors.toUnmodifiableMap(key -> key, flagRegistry::defaultState));
    }
}
```

- [ ] **Step 8: Update `ClaimCostService.quotePlayerClaim` to use `region().area()`**

In `ClaimCostService`, replace the `existingBlocks` computation:
```java
// Remove: .mapToInt(c -> c.claimChunks().size())
// Add:
int existingBlocks = claimIndex.findAll().stream()
        .filter(c -> c.owner() == OwnerType.PLAYER && ownerId.equals(c.ownerUuid()))
        .mapToInt(c -> c.region().area())
        .sum();
```

And similarly in `computeDeletionRefund`.

- [ ] **Step 9: Update `SqlClaimRepositoryTest`**

Update the test to construct `Claim` with `ClaimRegion` instead of `Set<ClaimChunk>`:

```java
UUID worldId = UUID.randomUUID();
ClaimRegion region = new ClaimRegion(worldId, 16, 32, 47, 63);
Claim claim = new Claim(
        claimId, "Home", OwnerType.PLAYER, ownerId,
        region,
        Map.of("build", FlagState.OFF, "interact", FlagState.OFF),
        Set.of(new ClaimMember(memberId, ClaimRole.MEMBER), new ClaimMember(managerId, ClaimRole.MANAGER)),
        Set.of(deniedId),
        createdAt, createdAt
);
repository.saveClaim(claim);
List<Claim> loaded = repository.findAllClaims();
assertThat(loaded).hasSize(1);
Claim loaded0 = loaded.get(0);
assertThat(loaded0.region()).isEqualTo(region);
assertThat(loaded0.worldId()).isEqualTo(worldId);
```

The `applyMigrations(dataSource)` helper needs to apply all 5 migrations. Verify the test helper loads V5 migration too (it likely uses Flyway/classpath scanning — it should pick up the new file automatically).

- [ ] **Step 10: Run all tests**

Run: `.\gradlew test`
Expected: PASS — BUILD SUCCESSFUL

- [ ] **Step 11: Commit**

```bash
git add havenclaims-api/src/main/java/com/invisiblespiders/havenclaims/api/claim/ClaimView.java \
        havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/claim/Claim.java \
        havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/claim/ClaimIndex.java \
        havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/claim/ClaimCreationService.java \
        havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/limit/ClaimCostService.java \
        havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/storage/sql/SqlClaimRepository.java \
        havenclaims-plugin/src/test/java/com/invisiblespiders/havenclaims/plugin/claim/ \
        havenclaims-plugin/src/test/java/com/invisiblespiders/havenclaims/plugin/storage/sql/SqlClaimRepositoryTest.java
git commit -m "feat: migrate Claim model from chunk-set to ClaimRegion, update ClaimIndex and SqlClaimRepository"
```

---

## Task 7: `SelectionService` + `ClaimToolListener` + `ClaimsCommand` Create Flow

**Files:**
- Modify: `havenclaims-plugin/.../plugin/selection/SelectionService.java`
- Modify: `havenclaims-plugin/.../plugin/listener/ClaimToolListener.java`
- Modify: `havenclaims-plugin/.../plugin/command/ClaimsCommand.java`
- Test: `havenclaims-plugin/src/test/java/com/invisiblespiders/havenclaims/plugin/selection/SelectionServiceTest.java`
- Test: `havenclaims-plugin/src/test/java/com/invisiblespiders/havenclaims/plugin/listener/ClaimToolListenerTest.java`

- [ ] **Step 1: Write failing `SelectionServiceTest`**

```java
package com.invisiblespiders.havenclaims.plugin.selection;

import static org.assertj.core.api.Assertions.assertThat;

import com.invisiblespiders.havenclaims.plugin.claim.ClaimRegion;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SelectionServiceTest {
    private final UUID world = UUID.randomUUID();
    private final SelectionService service = new SelectionService(new ClaimService());

    @Test
    void firstClickStoresCornerReturnsEmpty() {
        BlockPos p1 = new BlockPos(world, 10, 20);
        Optional<ClaimRegion> result = service.select(UUID.randomUUID(), p1);
        assertThat(result).isEmpty();
    }

    @Test
    void secondClickReturnsNormalizedRegion() {
        UUID player = UUID.randomUUID();
        BlockPos p1 = new BlockPos(world, 20, 5);
        BlockPos p2 = new BlockPos(world, 10, 15);
        service.select(player, p1);
        Optional<ClaimRegion> result = service.select(player, p2);
        assertThat(result).contains(new ClaimRegion(world, 10, 5, 20, 15));
    }

    @Test
    void crossWorldClickResetsSelection() {
        UUID player = UUID.randomUUID();
        service.select(player, new BlockPos(world, 0, 0));
        Optional<ClaimRegion> result = service.select(player, new BlockPos(UUID.randomUUID(), 1, 1));
        assertThat(result).isEmpty();
        // next click in new world starts fresh
    }

    @Test
    void pendingSelectionReturnsMostRecentCompletedRegion() {
        UUID player = UUID.randomUUID();
        service.select(player, new BlockPos(world, 0, 0));
        service.select(player, new BlockPos(world, 5, 5));
        assertThat(service.pendingSelection(player)).isPresent();
    }

    @Test
    void consumeSelectionClearsState() {
        UUID player = UUID.randomUUID();
        service.select(player, new BlockPos(world, 0, 0));
        service.select(player, new BlockPos(world, 5, 5));
        assertThat(service.consumeSelection(player)).isPresent();
        assertThat(service.pendingSelection(player)).isEmpty();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew :havenclaims-plugin:test --tests "*.SelectionServiceTest"`
Expected: FAIL — `select(UUID, BlockPos)` not found

- [ ] **Step 3: Rewrite `SelectionService`**

```java
package com.invisiblespiders.havenclaims.plugin.selection;

import com.invisiblespiders.havenclaims.plugin.claim.ClaimRegion;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimService;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public class SelectionService {
    private final ClaimService claimService;
    private final Map<UUID, BlockPos> firstCorners = new HashMap<>();
    private final Map<UUID, ClaimRegion> completedSelections = new HashMap<>();

    public SelectionService(ClaimService claimService) {
        this.claimService = Objects.requireNonNull(claimService, "claimService");
    }

    public Optional<ClaimRegion> select(Player player, Block block) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(block, "block");
        UUID worldId = block.getWorld().getUID();
        return select(player.getUniqueId(), new BlockPos(worldId, block.getX(), block.getZ()));
    }

    public Optional<ClaimRegion> select(UUID playerId, BlockPos pos) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(pos, "pos");

        BlockPos firstCorner = firstCorners.get(playerId);
        if (firstCorner == null) {
            completedSelections.remove(playerId);
            firstCorners.put(playerId, pos);
            return Optional.empty();
        }

        if (!firstCorner.worldId().equals(pos.worldId())) {
            completedSelections.remove(playerId);
            firstCorners.put(playerId, pos);
            return Optional.empty();
        }

        ClaimRegion region = claimService.blockRectangle(firstCorner, pos);
        completedSelections.put(playerId, region);
        return Optional.of(region);
    }

    public Optional<ClaimRegion> pendingSelection(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return Optional.ofNullable(completedSelections.get(playerId));
    }

    public Optional<ClaimRegion> consumeSelection(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        firstCorners.remove(playerId);
        return Optional.ofNullable(completedSelections.remove(playerId));
    }

    public boolean clear(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        boolean hadFirst = firstCorners.remove(playerId) != null;
        boolean hadCompleted = completedSelections.remove(playerId) != null;
        return hadFirst || hadCompleted;
    }

    public boolean clear(Player player) {
        Objects.requireNonNull(player, "player");
        return clear(player.getUniqueId());
    }
}
```

- [ ] **Step 4: Update `ClaimToolListener`**

The key changes:
1. `select(player, chunk)` → `select(player, block)` (now takes a `Block`)
2. Second-click result is `ClaimRegion` not `Set<ClaimChunk>`
3. Show border using `chunkBorderVisualService.showSelection(player, region, color)`
4. First-click message: `"claim.tool.first-corner-selected"`
5. Second-click message: `"claim.tool.selection-complete"` with placeholder `"area"` = `region.area()`

Replace `handleClaimToolSelection` method. The new method removes the chunk-based `normalizeSelection` logic (block selections can't overlap existing claims chunk-by-chunk — overlap is caught by `ClaimCreationService`):

```java
public void handleClaimToolSelection(PlayerInteractEvent event) {
    event.setCancelled(true);
    Player player = event.getPlayer();
    if (!player.hasPermission(CLAIM_PERMISSION)) {
        sendMissingPermission(player);
        return;
    }

    Block clickedBlock = event.getClickedBlock();
    if (clickedBlock == null) {
        clickedBlock = player.getLocation().getBlock();
    }

    selectionService.select(player, clickedBlock).ifPresentOrElse(
            region -> {
                showBorder(player, region, BorderColor.GREEN);
                sendSelectionComplete(player, region);
            },
            () -> {
                player.sendMessage(message("claim.tool.first-corner-selected"));
            }
    );
}

private void sendSelectionComplete(Player player, ClaimRegion region) {
    player.sendMessage(message("claim.tool.selection-complete",
            Map.of("area", String.valueOf(region.area()))));
}

private void showBorder(Player player, ClaimRegion region, BorderColor color) {
    if (chunkBorderVisualService != null) {
        chunkBorderVisualService.showSelection(player, region, color);
    }
}
```

Also update `onPlayerQuit` to call the new `clear(Player)` signature (unchanged).

Remove the old `showBorder(Player, Set<ClaimChunk>, BorderColor)` overload and the chunk-based border calls for `claim.claimChunks()`.

For `viewBorder` re-use in `ClaimsCommand`, we'll still use the existing `showSelection(Player, Set<ClaimChunk>, BorderColor)` overload (kept in `ChunkBorderVisualService`) to show existing claim borders via `claim.overlappingChunks()` — this is fine for Phase 1.

- [ ] **Step 5: Update `ClaimsCommand` — remove the chunk shims**

a) Remove `selectionAsRegion(Set<ClaimChunk>)` helper method added in Task 4.

b) Update `previewClaimCost(Player)` to use the new `SelectionService` API:
```java
private boolean previewClaimCost(Player player) {
    Optional<ClaimRegion> pending = selectionService == null
            ? Optional.empty()
            : selectionService.pendingSelection(player.getUniqueId());
    if (pending.isEmpty()) {
        player.sendMessage(message("claim.cost-preview.no-selection"));
        return true;
    }
    // ... rest unchanged, just uses ClaimRegion directly
    ClaimCostQuote quote = claimCostService.quotePlayerClaim(player.getUniqueId(), pending.orElseThrow());
    // ... render quote
}
```

c) Update `createClaim(Player, String, boolean)`:
```java
private boolean createClaim(Player player, String claimName, boolean mergeConfirmed) {
    Optional<ClaimRegion> pending = selectionService.pendingSelection(player.getUniqueId());
    if (pending.isEmpty()) {
        player.sendMessage(message("claim.create.no-selection"));
        return true;
    }
    ClaimRegion region = pending.orElseThrow();
    String trimmedName = claimName.trim();
    
    ClaimCostQuote quote = claimCostService != null
            ? claimCostService.quotePlayerClaim(player.getUniqueId(), region)
            : null;
    
    // over-limit: if overageBlocks > 0 and over-limit enabled, send prompt (handled in Task 12)
    if (quote != null && quote.overageBlocks() > 0 && !player.hasPermission(CLAIM_LIMIT_BYPASS_PERMISSION)) {
        if (!claimCostService.isPaidOverLimitEnabled()) {
            player.sendMessage(message("claim.over-limit-denied"));
            return true;
        }
        // Prompt handled in Task 12 — for now, deny over-limit claims
        player.sendMessage(message("claim.over-limit-denied"));
        return true;
    }
    
    boolean bypass = player.hasPermission(CLAIM_BUFFER_BYPASS_PERMISSION);
    ClaimValidationResult result = claimCreationService.createPlayerClaim(
            player.getUniqueId(), trimmedName, region, bypass);
    if (!result.isAllowed()) {
        player.sendMessage(message(result.messageKey().orElse("claims.denied")));
        return true;
    }
    
    selectionService.consumeSelection(player.getUniqueId());
    if (chunkBorderVisualService != null) {
        chunkBorderVisualService.clear(player.getUniqueId());
    }
    player.sendMessage(message("claim.created"));
    return true;
}
```

d) Update `viewBorder` methods that call `selectionService.pendingSelection()` to use `ClaimRegion`.

e) Remove `replacePendingSelection` calls (this method was on old `SelectionService` and is gone).

- [ ] **Step 6: Run all tests**

Run: `.\gradlew test`
Expected: PASS — BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/selection/SelectionService.java \
        havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/listener/ClaimToolListener.java \
        havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/command/ClaimsCommand.java \
        havenclaims-plugin/src/test/java/com/invisiblespiders/havenclaims/plugin/selection/SelectionServiceTest.java \
        havenclaims-plugin/src/test/java/com/invisiblespiders/havenclaims/plugin/listener/ClaimToolListenerTest.java
git commit -m "feat: SelectionService uses block coords and returns ClaimRegion; update ClaimToolListener and ClaimsCommand"
```

---

## Task 8: `ProtectionListener` — Block-Exact Containment

**Files:**
- Modify: `havenclaims-plugin/.../plugin/listener/ProtectionListener.java`
- Test: `havenclaims-plugin/src/test/java/com/invisiblespiders/havenclaims/plugin/listener/ProtectionListenerTest.java`

- [ ] **Step 1: Write failing test**

```java
// In ProtectionListenerTest, add:
@Test
void blockOutsideRegionInSameChunkIsNotProtected() {
    UUID world = UUID.randomUUID();
    // Claim covers only blocks 0-7 in X, chunk 0 covers 0-15
    ClaimRegion region = new ClaimRegion(world, 0, 0, 7, 15);
    Claim claim = testClaim(region, world);
    when(claimIndex.findAt(new ClaimChunk(world, 0, 0))).thenReturn(Optional.of(claim));

    // Block at x=10 is in chunk 0 but outside the region
    Optional<ClaimProtectionResult> result = listener.checkProtection(
            new ClaimChunk(world, 0, 0), 10, 5, null, p -> false, "build");
    assertThat(result).isEmpty();
}

@Test
void blockOnExactBoundaryIsProtected() {
    UUID world = UUID.randomUUID();
    ClaimRegion region = new ClaimRegion(world, 0, 0, 7, 7);
    Claim claim = testClaim(region, world);
    when(claimIndex.findAt(new ClaimChunk(world, 0, 0))).thenReturn(Optional.of(claim));

    Optional<ClaimProtectionResult> result = listener.checkProtection(
            new ClaimChunk(world, 0, 0), 7, 7, null, p -> false, "build");
    assertThat(result).isPresent();
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew :havenclaims-plugin:test --tests "*.ProtectionListenerTest"`
Expected: FAIL — `checkProtection` doesn't have blockX, blockZ params

- [ ] **Step 3: Update `ProtectionListener`**

Change the signature of `checkProtection` to include block coordinates:

```java
Optional<ClaimProtectionResult> checkProtection(
        ClaimChunk claimChunk,
        int blockX,
        int blockZ,
        UUID actorUuid,
        Predicate<String> permissionCheck,
        String flagKey
) {
    Objects.requireNonNull(claimChunk, "claimChunk");
    Objects.requireNonNull(permissionCheck, "permissionCheck");
    Objects.requireNonNull(flagKey, "flagKey");

    Optional<Claim> claim = claimIndex.findAt(claimChunk);
    if (claim.isEmpty()) return Optional.empty();
    if (!claim.orElseThrow().region().containsBlock(blockX, blockZ)) return Optional.empty();

    if (permissionCheck.test(BYPASS_PERMISSION) || permissionCheck.test(BYPASS_PERMISSION + "." + flagKey))
        return Optional.of(ClaimProtectionResult.ALLOW);

    return Optional.of(protectionService.checkClaimFlag(claim.orElseThrow(), actorUuid, flagKey));
}
```

Update all callers in `ProtectionListener` to pass `block.getX(), block.getZ()`:

```java
// isDeniedWithMessage:
boolean denied = checkProtection(claimChunk(block), block.getX(), block.getZ(),
        player.getUniqueId(), player::hasPermission, flagKey)
        .filter(result -> result != ClaimProtectionResult.ALLOW)
        .isPresent();

// isDenied(Block, UUID, Predicate, String):
private boolean isDenied(Block block, UUID actorUuid, Predicate<String> permissionCheck, String flagKey) {
    return checkProtection(claimChunk(block), block.getX(), block.getZ(),
            actorUuid, permissionCheck, flagKey)
            .filter(r -> r != ClaimProtectionResult.ALLOW).isPresent();
}
```

For piston and fluid-flow events (which use `ClaimChunk` lists without exact block coordinates), use a sentinel value indicating "check at chunk granularity only". The safest approach is: pass the chunk's min-block coordinate so the containment check always passes for claims aligned to chunk boundaries. **Note**: piston/fluid block-exact checking is deferred to a future phase — for now, pass `claimChunk.chunkX() * 16, claimChunk.chunkZ() * 16` as a conservative "inside region if chunk overlaps":

Update the private `isDenied(ClaimChunk, UUID, Predicate, String)` overload used by piston/fluid:
```java
private boolean isDenied(ClaimChunk claimChunk, UUID actorUuid, Predicate<String> permissionCheck, String flagKey) {
    // Use chunk min-corner for piston/fluid events — block-exact piston checking is future work
    return checkProtection(claimChunk, claimChunk.chunkX() * 16, claimChunk.chunkZ() * 16,
            actorUuid, permissionCheck, flagKey)
            .filter(r -> r != ClaimProtectionResult.ALLOW).isPresent();
}
```

This means a claim that starts at block 8 in a chunk will still catch piston events for blocks 0-7 in that chunk, which is a conservative overprotection — acceptable for Phase 1.

Similarly for `isDeniedEnteringClaim(ClaimChunk, ClaimChunk, String)`, pass chunk min coords.

- [ ] **Step 4: Run all tests**

Run: `.\gradlew test`
Expected: PASS — BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/listener/ProtectionListener.java \
        havenclaims-plugin/src/test/java/com/invisiblespiders/havenclaims/plugin/listener/ProtectionListenerTest.java
git commit -m "feat: block-exact containment check in ProtectionListener"
```

---

## Task 9: `AfkDetector` + `BlockAccrualService`

**Files:**
- Create: `havenclaims-plugin/.../plugin/accrual/AfkDetector.java`
- Create: `havenclaims-plugin/.../plugin/accrual/HavenCoreAfkDetector.java`
- Create: `havenclaims-plugin/.../plugin/accrual/NoopAfkDetector.java`
- Create: `havenclaims-plugin/.../plugin/accrual/BlockAccrualService.java`
- Test: `havenclaims-plugin/src/test/java/com/invisiblespiders/havenclaims/plugin/accrual/BlockAccrualServiceTest.java`

- [ ] **Step 1: Write failing test**

```java
package com.invisiblespiders.havenclaims.plugin.accrual;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.invisiblespiders.havenclaims.plugin.limit.LimitService;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BlockAccrualServiceTest {
    private final LimitService limitService = mock(LimitService.class);
    private final AfkDetector afkDetector = mock(AfkDetector.class);

    @Test
    void grantsBlocksToActivePlayer() {
        UUID player = UUID.randomUUID();
        when(afkDetector.isAfk(player)).thenReturn(false);
        BlockAccrualService service = new BlockAccrualService(limitService, afkDetector, 10, 50000, "reduced", 0.5);
        service.accrueFor(player);
        verify(limitService).addBlocks(player, 10);
    }

    @Test
    void reducedAfkGrantsFlooredAmount() {
        UUID player = UUID.randomUUID();
        when(afkDetector.isAfk(player)).thenReturn(true);
        // 10 * 0.5 = 5.0, floor = 5
        BlockAccrualService service = new BlockAccrualService(limitService, afkDetector, 10, 50000, "reduced", 0.5);
        service.accrueFor(player);
        verify(limitService).addBlocks(player, 5);
    }

    @Test
    void zeroAfkGrantsNothing() {
        UUID player = UUID.randomUUID();
        when(afkDetector.isAfk(player)).thenReturn(true);
        BlockAccrualService service = new BlockAccrualService(limitService, afkDetector, 10, 50000, "zero", 0.0);
        service.accrueFor(player);
        verify(limitService, never()).addBlocks(any(), anyInt());
    }

    @Test
    void maxBlocksCapIsRespected() {
        UUID player = UUID.randomUUID();
        when(afkDetector.isAfk(player)).thenReturn(false);
        when(limitService.getBlockLimit(player)).thenReturn(49998);
        BlockAccrualService service = new BlockAccrualService(limitService, afkDetector, 10, 50000, "reduced", 0.5);
        service.accrueFor(player);
        // Would grant 10 but current is 49998; cap at 50000; delta = 2
        verify(limitService).addBlocks(player, 2);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew :havenclaims-plugin:test --tests "*.BlockAccrualServiceTest"`
Expected: FAIL — classes not found

- [ ] **Step 3: Create `AfkDetector` interface**

```java
// plugin/accrual/AfkDetector.java
package com.invisiblespiders.havenclaims.plugin.accrual;

import java.util.UUID;

public interface AfkDetector {
    boolean isAfk(UUID playerId);
}
```

- [ ] **Step 4: Create `NoopAfkDetector`**

```java
package com.invisiblespiders.havenclaims.plugin.accrual;

import java.util.UUID;

public final class NoopAfkDetector implements AfkDetector {
    @Override
    public boolean isAfk(UUID playerId) {
        return false;
    }
}
```

- [ ] **Step 5: Create `HavenCoreAfkDetector`**

```java
package com.invisiblespiders.havenclaims.plugin.accrual;

import dev.invisiblespiders.haven.api.HavenAPI;
import dev.invisiblespiders.haven.api.service.HavenAfkService;
import java.util.UUID;

public final class HavenCoreAfkDetector implements AfkDetector {
    private final HavenAfkService havenAfkService;

    public HavenCoreAfkDetector(HavenAfkService havenAfkService) {
        this.havenAfkService = havenAfkService;
    }

    public static AfkDetector create() {
        HavenAfkService afkService = HavenAPI.get(HavenAfkService.class);
        return afkService != null ? new HavenCoreAfkDetector(afkService) : new NoopAfkDetector();
    }

    @Override
    public boolean isAfk(UUID playerId) {
        return havenAfkService.isAfk(playerId);
    }
}
```

**Note:** If `HavenAfkService` is not available in the API jar, check the haven-api dependency for the correct class name. Use `HavenAPI.get(HavenAfkService.class)` per the existing `HavenEconomyService` pattern in `HavenClaimsPlugin`.

- [ ] **Step 6: Create `BlockAccrualService`**

```java
package com.invisiblespiders.havenclaims.plugin.accrual;

import com.invisiblespiders.havenclaims.plugin.limit.LimitService;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public final class BlockAccrualService {
    private final LimitService limitService;
    private final AfkDetector afkDetector;
    private final int blocksPerInterval;
    private final int maxBlocks;
    private final String afkMode;     // "reduced" or "zero"
    private final double afkMultiplier;
    private BukkitRunnable task;

    public BlockAccrualService(
            LimitService limitService,
            AfkDetector afkDetector,
            int blocksPerInterval,
            int maxBlocks,
            String afkMode,
            double afkMultiplier
    ) {
        this.limitService = Objects.requireNonNull(limitService, "limitService");
        this.afkDetector = Objects.requireNonNull(afkDetector, "afkDetector");
        this.blocksPerInterval = blocksPerInterval;
        this.maxBlocks = maxBlocks;
        this.afkMode = Objects.requireNonNull(afkMode, "afkMode");
        this.afkMultiplier = afkMultiplier;
    }

    public void start(JavaPlugin plugin, long intervalTicks) {
        if (task != null) task.cancel();
        task = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    accrueFor(player.getUniqueId());
                }
            }
        };
        task.runTaskTimer(plugin, intervalTicks, intervalTicks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    void accrueFor(UUID playerId) {
        boolean afk = afkDetector.isAfk(playerId);
        int grant;
        if (!afk) {
            grant = blocksPerInterval;
        } else if ("zero".equalsIgnoreCase(afkMode)) {
            return;
        } else {
            grant = (int) Math.floor(blocksPerInterval * afkMultiplier);
        }
        if (grant <= 0) return;

        if (maxBlocks > 0) {
            int current = limitService.getBlockLimit(playerId);
            if (current >= maxBlocks) return;
            grant = Math.min(grant, maxBlocks - current);
        }

        limitService.addBlocks(playerId, grant);
    }
}
```

- [ ] **Step 7: Run tests to verify they pass**

Run: `.\gradlew :havenclaims-plugin:test --tests "*.BlockAccrualServiceTest"`
Expected: PASS — BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/accrual/ \
        havenclaims-plugin/src/test/java/com/invisiblespiders/havenclaims/plugin/accrual/
git commit -m "feat: AfkDetector, HavenCoreAfkDetector, NoopAfkDetector, BlockAccrualService"
```

---

## Task 10: Over-Limit Purchase Flow

**Files:**
- Create: `havenclaims-plugin/.../plugin/limit/PendingOverLimitPurchase.java`
- Create: `havenclaims-plugin/.../plugin/limit/OverLimitConfirmService.java`
- Test: `havenclaims-plugin/src/test/java/com/invisiblespiders/havenclaims/plugin/limit/OverLimitConfirmServiceTest.java`

- [ ] **Step 1: Write failing test**

```java
package com.invisiblespiders.havenclaims.plugin.limit;

import static org.assertj.core.api.Assertions.assertThat;

import com.invisiblespiders.havenclaims.plugin.claim.ClaimRegion;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OverLimitConfirmServiceTest {
    private final UUID player = UUID.randomUUID();
    private final ClaimRegion region = new ClaimRegion(UUID.randomUUID(), 0, 0, 15, 15);

    @Test
    void storePendingAndRetrieve() {
        OverLimitConfirmService service = new OverLimitConfirmService(60);
        service.store(player, region, "MyBase", 15.00);
        assertThat(service.getPending(player)).isPresent();
    }

    @Test
    void expiredPurchaseReturnsEmpty() {
        OverLimitConfirmService service = new OverLimitConfirmService(0); // 0-second timeout
        service.store(player, region, "MyBase", 15.00, Instant.now().minusSeconds(1));
        assertThat(service.getPending(player)).isEmpty();
    }

    @Test
    void consumeRemovesPending() {
        OverLimitConfirmService service = new OverLimitConfirmService(60);
        service.store(player, region, "MyBase", 15.00);
        assertThat(service.consume(player)).isPresent();
        assertThat(service.getPending(player)).isEmpty();
    }

    @Test
    void clearRemovesPending() {
        OverLimitConfirmService service = new OverLimitConfirmService(60);
        service.store(player, region, "MyBase", 15.00);
        service.clear(player);
        assertThat(service.getPending(player)).isEmpty();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew :havenclaims-plugin:test --tests "*.OverLimitConfirmServiceTest"`
Expected: FAIL — classes not found

- [ ] **Step 3: Create `PendingOverLimitPurchase`**

```java
package com.invisiblespiders.havenclaims.plugin.limit;

import com.invisiblespiders.havenclaims.plugin.claim.ClaimRegion;
import java.time.Instant;
import java.util.Objects;

public record PendingOverLimitPurchase(
        ClaimRegion region,
        String claimName,
        double cost,
        Instant expiresAt
) {
    public PendingOverLimitPurchase {
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(claimName, "claimName");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
```

- [ ] **Step 4: Create `OverLimitConfirmService`**

```java
package com.invisiblespiders.havenclaims.plugin.limit;

import com.invisiblespiders.havenclaims.plugin.claim.ClaimRegion;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class OverLimitConfirmService {
    private final int timeoutSeconds;
    private final Map<UUID, PendingOverLimitPurchase> pending = new HashMap<>();

    public OverLimitConfirmService(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public void store(UUID playerId, ClaimRegion region, String claimName, double cost) {
        store(playerId, region, claimName, cost, Instant.now().plusSeconds(timeoutSeconds));
    }

    void store(UUID playerId, ClaimRegion region, String claimName, double cost, Instant expiresAt) {
        Objects.requireNonNull(playerId, "playerId");
        pending.put(playerId, new PendingOverLimitPurchase(region, claimName, cost, expiresAt));
    }

    public Optional<PendingOverLimitPurchase> getPending(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        PendingOverLimitPurchase purchase = pending.get(playerId);
        if (purchase == null || purchase.isExpired()) {
            pending.remove(playerId);
            return Optional.empty();
        }
        return Optional.of(purchase);
    }

    public Optional<PendingOverLimitPurchase> consume(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        PendingOverLimitPurchase purchase = pending.remove(playerId);
        if (purchase == null || purchase.isExpired()) return Optional.empty();
        return Optional.of(purchase);
    }

    public void clear(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        pending.remove(playerId);
    }
}
```

- [ ] **Step 5: Run tests**

Run: `.\gradlew :havenclaims-plugin:test --tests "*.OverLimitConfirmServiceTest"`
Expected: PASS — BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/limit/PendingOverLimitPurchase.java \
        havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/limit/OverLimitConfirmService.java \
        havenclaims-plugin/src/test/java/com/invisiblespiders/havenclaims/plugin/limit/OverLimitConfirmServiceTest.java
git commit -m "feat: PendingOverLimitPurchase and OverLimitConfirmService for YES/NO purchase flow"
```

---

## Task 11: `ClaimsCommand` — Over-Limit Prompt and `confirm-purchase`

**Files:**
- Modify: `havenclaims-plugin/.../plugin/command/ClaimsCommand.java`

This task wires the `OverLimitConfirmService` into `ClaimsCommand` so that over-limit claims show a clickable YES/NO prompt, and `/claim confirm-purchase` processes the pending purchase.

- [ ] **Step 1: Add `OverLimitConfirmService` field to `ClaimsCommand`**

Add field:
```java
private final OverLimitConfirmService overLimitConfirmService;
```

Add to the full constructor's parameter list (after `adminClaimBrowserService`):
```java
OverLimitConfirmService overLimitConfirmService
```

Assign in constructor:
```java
this.overLimitConfirmService = overLimitConfirmService; // may be null
```

- [ ] **Step 2: Update `createClaim` to send over-limit prompt instead of denying**

Replace the "for now, deny over-limit" block from Task 7 with:

```java
if (quote != null && quote.overageBlocks() > 0 && !player.hasPermission(CLAIM_LIMIT_BYPASS_PERMISSION)) {
    if (!claimCostService.isPaidOverLimitEnabled() || overLimitConfirmService == null) {
        player.sendMessage(message("claim.over-limit-denied"));
        return true;
    }
    // Store pending purchase and send clickable prompt
    double cost = quote.cost();
    overLimitConfirmService.store(player.getUniqueId(), region, trimmedName, cost);
    Component prompt = messageService.render("claim.over-limit-prompt", Map.of(
            "shortage", String.valueOf(quote.overageBlocks()),
            "cost", String.format("%.2f", cost)
    )).append(Component.text(" "))
      .append(Component.text("[YES]")
              .clickEvent(ClickEvent.runCommand("/claim confirm-purchase")))
      .append(Component.text(" "))
      .append(Component.text("[NO]")
              .clickEvent(ClickEvent.runCommand("/claim cancel")));
    player.sendMessage(prompt);
    return true;
}
```

**Note:** The message key `claim.over-limit-prompt` uses MiniMessage formatting defined in `messages.yml`. The `[YES]` and `[NO]` text comes from the message template using `<green>[YES]</green>` and `<red>[NO]</red>`. Build the clickable component from the rendered message. Simplest approach: render the message that includes `[YES] [NO]` text already styled, then add `ClickEvent` to those sub-components. If `messages.yml` renders the full line as a single component, split at `[YES]` and `[NO]` to attach click events.

Alternative simpler approach for Phase 1 — build the prompt entirely in code:

```java
Component prompt = messageService.render("claim.over-limit-prompt", Map.of(
        "shortage", String.valueOf(quote.overageBlocks())
)).append(Component.text(" "))
  .append(messageService.render("claim.over-limit-yes-button", Map.of())
          .clickEvent(ClickEvent.runCommand("/claim confirm-purchase")))
  .append(Component.text(" "))
  .append(messageService.render("claim.over-limit-no-button", Map.of())
          .clickEvent(ClickEvent.runCommand("/claim cancel")));
player.sendMessage(prompt);
```

Add message keys in `messages.yml` (Task 12): `claim.over-limit-yes-button: "<green>[YES]</green>"` and `claim.over-limit-no-button: "<red>[NO]</red>"`.

- [ ] **Step 3: Add `confirm-purchase` handler to `onCommand`**

```java
if (args.length == 1 && args[0].equalsIgnoreCase("confirm-purchase")) {
    return confirmOverLimitPurchase(player);
}
```

- [ ] **Step 4: Implement `confirmOverLimitPurchase`**

```java
private boolean confirmOverLimitPurchase(Player player) {
    if (overLimitConfirmService == null) {
        player.sendMessage(message("command.unavailable.claim-creation"));
        return true;
    }
    Optional<PendingOverLimitPurchase> pendingOpt = overLimitConfirmService.consume(player.getUniqueId());
    if (pendingOpt.isEmpty()) {
        player.sendMessage(message("claim.over-limit-expired"));
        return true;
    }
    PendingOverLimitPurchase pending = pendingOpt.orElseThrow();

    if (claimPaymentService != null && pending.cost() > 0) {
        ClaimPaymentResult payment = claimPaymentService.charge(player.getUniqueId(), pending.cost());
        if (!payment.succeeded()) {
            player.sendMessage(message("claim.over-limit-payment-failed"));
            return true;
        }
    }

    boolean bypass = player.hasPermission(CLAIM_BUFFER_BYPASS_PERMISSION);
    ClaimValidationResult result = claimCreationService.createPlayerClaim(
            player.getUniqueId(), pending.claimName(), pending.region(), bypass);
    if (!result.isAllowed()) {
        player.sendMessage(message(result.messageKey().orElse("claims.denied")));
        return true;
    }

    selectionService.consumeSelection(player.getUniqueId());
    if (chunkBorderVisualService != null) chunkBorderVisualService.clear(player.getUniqueId());
    player.sendMessage(messageService.render("claim.over-limit-confirmed",
            Map.of("cost", String.format("%.2f", pending.cost()))));
    return true;
}
```

Add `import com.invisiblespiders.havenclaims.plugin.limit.OverLimitConfirmService;` and `import com.invisiblespiders.havenclaims.plugin.limit.PendingOverLimitPurchase;`.

- [ ] **Step 5: Add `confirm-purchase` to `ROOT_SUGGESTIONS`**

```java
private static final List<String> ROOT_SUGGESTIONS = List.of(
        "mode", "create", "cost", "quote", "menu", "flags", "viewborder",
        "flag", "member", "deny", "undeny", "denied", "delete", "abandon",
        "deleteconfirm", "admin", "cancel", "info", "confirm-purchase"
);
```

- [ ] **Step 6: Run all tests**

Run: `.\gradlew test`
Expected: PASS — BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/command/ClaimsCommand.java
git commit -m "feat: over-limit YES/NO chat prompt and /claim confirm-purchase command"
```

---

## Task 12: `config.yml` + `messages.yml`

**Files:**
- Modify: `havenclaims-plugin/src/main/resources/config.yml`
- Modify: `havenclaims-plugin/src/main/resources/messages.yml`

- [ ] **Step 1: Update `config.yml`**

Replace relevant sections:

```yaml
claiming:
  max-name-length: 32
  player-buffer-distance: 16    # blocks (was chunks)
  admin-buffer-distance: 16     # blocks (was chunks)
  # allow-irregular-claims REMOVED
  allow-merge-with-own-claims: true

limits:
  starting-claim-blocks: 500    # renamed from default-claim-limit
  accrual:
    enabled: true
    blocks-per-interval: 10
    interval-seconds: 60
    max-blocks: 50000
    afk:
      mode: reduced       # reduced or zero
      rate-multiplier: 0.5
  over-limit:
    enabled: true
    flat-cost-per-block: 0.10
    confirm-timeout-seconds: 60
```

Remove `allow-irregular-claims`, `default-claim-limit`, and exponential pricing keys (`pricing-mode`, `exponential-base-cost`, `exponential-multiplier`, `flat-cost-per-chunk`).

- [ ] **Step 2: Update `messages.yml`**

Change or add these keys:

```yaml
claim:
  tool:
    first-corner-selected: "<yellow>Position 1 set."
    selection-complete: "<green>Position 2 set. <yellow><area></yellow> claim blocks required."
  blocked-near-other: "<red>You cannot claim within <yellow><distance></yellow> blocks of another player's land."
  over-limit-prompt: "<yellow>You're short by <red><shortage></red> claim blocks."
  over-limit-yes-button: "<green>[YES]</green>"
  over-limit-no-button: "<red>[NO]</red>"
  over-limit-expired: "<red>Purchase confirmation expired."
  over-limit-confirmed: "<green>Charged <yellow><cost></yellow>. Claim created."
  over-limit-denied: "<red>You don't have enough claim blocks."
  over-limit-payment-failed: "<red>Payment failed. Claim not created."
```

Rename all `*_chunks` / `chunk_count` placeholders to `*_blocks` / `block_count` throughout the file. In particular, update:
- `admin.userclaims.list-entry` — replace `chunk_count` placeholder name with `block_count` (or `area`) and update the value: `<area>` block area
- `claim.cost-preview.*` messages — replace `*_chunks` → `*_blocks`
- Any `claim.info.chunks` → `claim.info.area`

Also rename `claim.tool.first-corner-selected` if the old key was different.

- [ ] **Step 3: Run all tests (including config resource test)**

Run: `.\gradlew test`
Expected: PASS — BUILD SUCCESSFUL

**If `HavenClaimsConfigResourceTest` fails:** It likely checks for specific config keys. Update the test to expect `starting-claim-blocks` instead of `default-claim-limit` and the new `accrual` section.

- [ ] **Step 4: Commit**

```bash
git add havenclaims-plugin/src/main/resources/config.yml \
        havenclaims-plugin/src/main/resources/messages.yml
git commit -m "config: rename block limit keys, add accrual section, remove irregular-claims and exponential pricing"
```

---

## Task 13: `HavenClaimsPlugin` Wiring

**Files:**
- Modify: `havenclaims-plugin/.../plugin/HavenClaimsPlugin.java`

- [ ] **Step 1: Update config reads in `onEnable()`**

Change:
```java
// Old:
new LimitService(getConfig().getInt("limits.default-claim-limit", 10), claimLimitRepository)
// New:
new LimitService(getConfig().getInt("limits.starting-claim-blocks", 500), claimLimitRepository)

// Old:
getConfig().getInt("claiming.player-buffer-distance", 3)
// New:
getConfig().getInt("claiming.player-buffer-distance", 16)
// Same for admin-buffer-distance
```

- [ ] **Step 2: Wire `BlockAccrualService`**

Add field:
```java
private BlockAccrualService blockAccrualService;
```

In `onEnable()`, after `limitService` is created:
```java
if (getConfig().getBoolean("limits.accrual.enabled", true)) {
    AfkDetector afkDetector = HavenCoreAfkDetector.create();
    blockAccrualService = new BlockAccrualService(
            limitService,
            afkDetector,
            getConfig().getInt("limits.accrual.blocks-per-interval", 10),
            getConfig().getInt("limits.accrual.max-blocks", 50000),
            getConfig().getString("limits.accrual.afk.mode", "reduced"),
            getConfig().getDouble("limits.accrual.afk.rate-multiplier", 0.5)
    );
    long intervalSeconds = getConfig().getLong("limits.accrual.interval-seconds", 60L);
    blockAccrualService.start(this, intervalSeconds * 20L);
}
```

Add imports:
```java
import com.invisiblespiders.havenclaims.plugin.accrual.AfkDetector;
import com.invisiblespiders.havenclaims.plugin.accrual.BlockAccrualService;
import com.invisiblespiders.havenclaims.plugin.accrual.HavenCoreAfkDetector;
```

- [ ] **Step 3: Wire `OverLimitConfirmService`**

Add field:
```java
private OverLimitConfirmService overLimitConfirmService;
```

In `onEnable()`, after `claimCostService`:
```java
overLimitConfirmService = new OverLimitConfirmService(
        getConfig().getInt("limits.over-limit.confirm-timeout-seconds", 60));
```

Pass `overLimitConfirmService` to `ClaimsCommand` constructor (add as last parameter).

- [ ] **Step 4: Update `performReload()` config keys**

```java
// Old:
limitService.reload(getConfig().getInt("limits.default-claim-limit", 10));
// New:
limitService.reload(getConfig().getInt("limits.starting-claim-blocks", 500));

// Old:
getConfig().getInt("claiming.player-buffer-distance", 3)
// New:
getConfig().getInt("claiming.player-buffer-distance", 16)
```

Also reload `overLimitConfirmService` if needed (currently stateless timeout — recreate or add `reload(int)` method).

- [ ] **Step 5: Update `onDisable()` to stop `blockAccrualService`**

```java
if (blockAccrualService != null) {
    blockAccrualService.stop();
    blockAccrualService = null;
}
```

Add before the existing `chunkBorderVisualService.clearAll()` block.

- [ ] **Step 6: Run all tests**

Run: `.\gradlew test`
Expected: PASS — BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/HavenClaimsPlugin.java
git commit -m "feat: wire BlockAccrualService, OverLimitConfirmService, and update config keys in HavenClaimsPlugin"
```

---

## Task 14: Cleanup — Remove Old Shim Methods

**Files:**
- Modify: `havenclaims-plugin/.../plugin/claim/Claim.java` (remove `claimChunks()` shim)
- Modify: `havenclaims-plugin/.../plugin/claim/ClaimService.java` (remove `expandRectangle()`, `isWithinChunkBuffer()`)
- Modify: any remaining callers

- [ ] **Step 1: Audit remaining callers of `claimChunks()` and `expandRectangle()`**

Run:
```bash
grep -rn "claimChunks()\|expandRectangle\|isWithinChunkBuffer" havenclaims-plugin/src/main/java/
```

Expected: Only references should be in `Claim.java` itself (the shim body) and `ClaimService.java`. If any additional callers appear, update them first to use `overlappingChunks()` or the block methods.

- [ ] **Step 2: Remove deprecated shim from `Claim`**

Delete the `claimChunks()` method from `Claim.java`.

- [ ] **Step 3: Remove old `ClaimService` methods**

Delete `expandRectangle()` and `isWithinChunkBuffer()` from `ClaimService.java`.

- [ ] **Step 4: Run all tests**

Run: `.\gradlew test`
Expected: PASS — BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/claim/Claim.java \
        havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/claim/ClaimService.java
git commit -m "cleanup: remove deprecated claimChunks() shim and old chunk-based ClaimService methods"
```

---

## Self-Review Checklist

After writing the plan, verify spec coverage:

| Spec Requirement | Task |
|---|---|
| Claim tool highlights block rectangle | Tasks 5, 7 |
| "Position 1 set." / "Position 2 set. X claim blocks required." | Task 7 |
| Buffer distance in blocks (default 16) | Tasks 6, 12, 13 |
| `allow-irregular-claims` removed | Task 12 |
| `starting-claim-blocks` default 500 | Tasks 12, 13 |
| Over-limit flat pricing + YES/NO prompt | Tasks 4, 10, 11 |
| Block accrual + AFK detection | Tasks 9, 13 |
| `ClaimRegion` record | Task 1 |
| `BlockPos` record | Task 1 |
| V5 SQL migration | Task 1 |
| `Claim` model swap | Task 6 |
| `ClaimIndex` from `overlappingChunks()` | Task 6 |
| `SqlClaimRepository` region storage | Task 6 |
| `ClaimCreationService` block buffer | Task 6 |
| `HavenClaimsLimitService` renamed | Task 3 |
| `ClaimCostQuote/Config/Service` blocks | Task 4 |
| `ChunkBorderPlanner.planRectangle()` | Task 5 |
| Block-exact protection check | Task 8 |
| `confirm-purchase` command | Task 11 |
| `config.yml` + `messages.yml` updates | Task 12 |
| `HavenClaimsPlugin` wiring | Task 13 |
