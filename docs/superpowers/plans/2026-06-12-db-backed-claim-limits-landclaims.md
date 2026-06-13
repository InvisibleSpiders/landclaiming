# DB-Backed Claim Limits — LandClaims (Phase A) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace permission-based chunk limits with a per-player DB-backed integer limit, expose it as a `LandClaimsLimitService` API, and add admin commands to adjust limits directly.

**Architecture:** A new `ClaimLimitRepository`/`SqlClaimLimitRepository` stores one row per player; `LimitService` is rewritten to implement `LandClaimsLimitService` (API in `landclaims-api`) using that repository with a config-default fallback. `ClaimCostService` drops its `permissions` param. `/claim admin limit` commands let admins set/add/remove/get limits directly. All tables go through the existing `HavenDataSource` migration runner.

**Tech Stack:** Java 25, Paper API 26.1.2, Gradle Kotlin DSL, JUnit 5, AssertJ, SQLite (tests) / HavenDataSource (runtime).

**Spec:** `docs/superpowers/specs/2026-06-12-db-backed-claim-limits-design.md`

**Build/test command:** `.\gradlew build` (all modules). Single class: `.\gradlew :landclaims-plugin:test --tests "com.nick.landclaims.plugin.<ClassName>"`.

**Conventions:**
- Branch: create `feature/db-claim-limits` from `master`.
- Commit after every task.
- All new plugin classes use `Objects.requireNonNull` for every constructor parameter.
- Test DB harness: copy the `applyMigrations` / `applySql` pattern from `SqlClaimRepositoryTest` (it reads `migrations.index` and applies each SQL file in order — your V4 migration will be picked up automatically once added to the index).

---

## File Map

| Action | File | Purpose |
|---|---|---|
| Create | `landclaims-plugin/src/main/resources/db/migrations/landclaims/V4__claim_player_limits.sql` | New table |
| Modify | `landclaims-plugin/src/main/resources/db/migrations/landclaims/migrations.index` | Register V4 |
| Create | `landclaims-api/src/main/java/com/nick/landclaims/api/limit/LandClaimsLimitService.java` | Public API interface |
| Create | `landclaims-plugin/src/main/java/com/nick/landclaims/plugin/limit/ClaimLimitRepository.java` | Repository interface |
| Create | `landclaims-plugin/src/main/java/com/nick/landclaims/plugin/limit/SqlClaimLimitRepository.java` | SQLite implementation |
| Modify | `landclaims-plugin/src/main/java/com/nick/landclaims/plugin/limit/LimitService.java` | Implement `LandClaimsLimitService`, drop permission map |
| Modify | `landclaims-plugin/src/main/java/com/nick/landclaims/plugin/limit/ClaimCostService.java` | Drop `permissions` param |
| Modify | `landclaims-plugin/src/main/java/com/nick/landclaims/plugin/LandClaimsPlugin.java` | Wire new repo/service, register API, remove permission-limit loading |
| Modify | `landclaims-plugin/src/main/java/com/nick/landclaims/plugin/command/ClaimsCommand.java` | Add `limit` admin subcommand, update cost call sites |
| Modify | `landclaims-plugin/src/main/resources/messages.yml` | Add `admin.limit.*` messages |
| Modify | `landclaims-plugin/src/main/resources/permissions.yml` | Remove `limits:` section, add `landclaims.admin.limit` |
| Create | `landclaims-plugin/src/test/java/com/nick/landclaims/plugin/limit/SqlClaimLimitRepositoryTest.java` | Repository DB tests |
| Modify | `landclaims-plugin/src/test/java/com/nick/landclaims/plugin/limit/LimitServiceTest.java` | Rewrite for new API |
| Modify | `landclaims-plugin/src/test/java/com/nick/landclaims/plugin/limit/ClaimCostServiceTest.java` | Drop `permissions` from call sites |

---

## Task A1: V4 migration + ClaimLimitRepository

**Files:**
- Create: `landclaims-plugin/src/main/resources/db/migrations/landclaims/V4__claim_player_limits.sql`
- Modify: `landclaims-plugin/src/main/resources/db/migrations/landclaims/migrations.index`
- Create: `landclaims-plugin/src/main/java/com/nick/landclaims/plugin/limit/ClaimLimitRepository.java`
- Create: `landclaims-plugin/src/main/java/com/nick/landclaims/plugin/limit/SqlClaimLimitRepository.java`
- Test: `landclaims-plugin/src/test/java/com/nick/landclaims/plugin/limit/SqlClaimLimitRepositoryTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.nick.landclaims.plugin.limit;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.OptionalInt;
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

    private static SqlClaimLimitRepository repo(@TempDir Path tmp) throws Exception {
        SQLiteDataSource ds = new SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + tmp.resolve("test.db"));
        applyMigrations(ds);
        return new SqlClaimLimitRepository(ds);
    }

    private static void applyMigrations(DataSource dataSource) throws Exception {
        try (InputStream indexIn = SqlClaimLimitRepositoryTest.class.getClassLoader()
                .getResourceAsStream("db/migrations/landclaims/migrations.index");
             Connection connection = dataSource.getConnection()) {
            String index = new String(indexIn.readAllBytes(), StandardCharsets.UTF_8);
            for (String line : index.split("\\R")) {
                String migration = line.trim();
                if (migration.isEmpty() || migration.startsWith("#")) continue;
                try (InputStream migIn = SqlClaimLimitRepositoryTest.class.getClassLoader()
                        .getResourceAsStream("db/migrations/landclaims/" + migration)) {
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
```

- [ ] **Step 2: Run test, verify it fails**

Run: `.\gradlew :landclaims-plugin:test --tests "com.nick.landclaims.plugin.limit.SqlClaimLimitRepositoryTest"`
Expected: FAIL — compile error, `SqlClaimLimitRepository` does not exist.

- [ ] **Step 3a: Create the SQL migration**

`landclaims-plugin/src/main/resources/db/migrations/landclaims/V4__claim_player_limits.sql`:
```sql
CREATE TABLE IF NOT EXISTS claim_player_limits (
    player_uuid TEXT NOT NULL PRIMARY KEY,
    chunk_limit  INTEGER NOT NULL CHECK (chunk_limit >= 1)
);
```

- [ ] **Step 3b: Register in index**

Append to `migrations.index`:
```
V4__claim_player_limits.sql
```

- [ ] **Step 3c: Create the repository interface**

`landclaims-plugin/src/main/java/com/nick/landclaims/plugin/limit/ClaimLimitRepository.java`:
```java
package com.nick.landclaims.plugin.limit;

import java.util.OptionalInt;
import java.util.UUID;

public interface ClaimLimitRepository {
    OptionalInt getLimit(UUID playerId);
    void setLimit(UUID playerId, int limit);
}
```

- [ ] **Step 3d: Create the SQL implementation**

`landclaims-plugin/src/main/java/com/nick/landclaims/plugin/limit/SqlClaimLimitRepository.java`:
```java
package com.nick.landclaims.plugin.limit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.UUID;
import javax.sql.DataSource;

public final class SqlClaimLimitRepository implements ClaimLimitRepository {
    private final DataSource dataSource;

    public SqlClaimLimitRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public OptionalInt getLimit(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        String sql = "SELECT chunk_limit FROM claim_player_limits WHERE player_uuid = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return OptionalInt.of(rs.getInt("chunk_limit"));
                }
                return OptionalInt.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to get player limit.", e);
        }
    }

    @Override
    public void setLimit(UUID playerId, int limit) {
        Objects.requireNonNull(playerId, "playerId");
        String sql = "INSERT OR REPLACE INTO claim_player_limits (player_uuid, chunk_limit) VALUES (?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerId.toString());
            stmt.setInt(2, limit);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to set player limit.", e);
        }
    }
}
```

- [ ] **Step 4: Run test, verify it passes**

Run: `.\gradlew :landclaims-plugin:test --tests "com.nick.landclaims.plugin.limit.SqlClaimLimitRepositoryTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add landclaims-plugin/src/main/resources/db/migrations/landclaims/V4__claim_player_limits.sql
git add landclaims-plugin/src/main/resources/db/migrations/landclaims/migrations.index
git add landclaims-plugin/src/main/java/com/nick/landclaims/plugin/limit/ClaimLimitRepository.java
git add landclaims-plugin/src/main/java/com/nick/landclaims/plugin/limit/SqlClaimLimitRepository.java
git add landclaims-plugin/src/test/java/com/nick/landclaims/plugin/limit/SqlClaimLimitRepositoryTest.java
git commit -m "feat(db): V4 migration and ClaimLimitRepository"
```

---

## Task A2: LandClaimsLimitService API + LimitService rewrite

**Files:**
- Create: `landclaims-api/src/main/java/com/nick/landclaims/api/limit/LandClaimsLimitService.java`
- Modify: `landclaims-plugin/src/main/java/com/nick/landclaims/plugin/limit/LimitService.java`
- Modify: `landclaims-plugin/src/test/java/com/nick/landclaims/plugin/limit/LimitServiceTest.java`

- [ ] **Step 1: Write the failing test**

Replace the entire contents of `LimitServiceTest.java`:

```java
package com.nick.landclaims.plugin.limit;

import static org.assertj.core.api.Assertions.assertThat;

import com.nick.landclaims.api.limit.LandClaimsLimitService;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LimitServiceTest {
    private static final int DEFAULT = 10;

    private LimitService serviceWith(UUID player, int stored) {
        ClaimLimitRepository repo = new ClaimLimitRepository() {
            @Override public OptionalInt getLimit(UUID id) {
                return id.equals(player) ? OptionalInt.of(stored) : OptionalInt.empty();
            }
            @Override public void setLimit(UUID id, int limit) {}
        };
        return new LimitService(DEFAULT, repo);
    }

    private LimitService emptyService() {
        return new LimitService(DEFAULT, new ClaimLimitRepository() {
            private int stored = -1;
            private UUID storedId = null;
            @Override public OptionalInt getLimit(UUID id) {
                return storedId != null && storedId.equals(id) ? OptionalInt.of(stored) : OptionalInt.empty();
            }
            @Override public void setLimit(UUID id, int limit) { storedId = id; stored = limit; }
        });
    }

    @Test
    void getLimitReturnsDatabaseValueWhenPresent() {
        UUID player = UUID.randomUUID();
        assertThat(serviceWith(player, 25).getLimit(player)).isEqualTo(25);
    }

    @Test
    void getLimitFallsBackToDefaultWhenNoRecord() {
        assertThat(serviceWith(UUID.randomUUID(), 25).getLimit(UUID.randomUUID())).isEqualTo(DEFAULT);
    }

    @Test
    void setLimitWritesToRepository() {
        LimitService service = emptyService();
        UUID player = UUID.randomUUID();
        service.setLimit(player, 20);
        assertThat(service.getLimit(player)).isEqualTo(20);
    }

    @Test
    void addChunksIncreasesLimit() {
        LimitService service = emptyService();
        UUID player = UUID.randomUUID();
        service.setLimit(player, 10);
        service.addChunks(player, 5);
        assertThat(service.getLimit(player)).isEqualTo(15);
    }

    @Test
    void removeChunksDecreasesLimitWithFloorAtOne() {
        LimitService service = emptyService();
        UUID player = UUID.randomUUID();
        service.setLimit(player, 3);
        service.removeChunks(player, 10);
        assertThat(service.getLimit(player)).isEqualTo(1);
    }

    @Test
    void overageChunksNeverReturnsNegative() {
        LimitService service = emptyService();
        assertThat(service.overageChunks(14, 10)).isEqualTo(4);
        assertThat(service.overageChunks(8, 10)).isZero();
    }

    @Test
    void flatOverLimitCostChargesPerChunk() {
        assertThat(LimitService.flatOverLimitCost(3, 250.0)).isEqualTo(750.0);
    }

    @Test
    void exponentialOverLimitCostScalesEachChunk() {
        assertThat(LimitService.exponentialOverLimitCost(3, 250.0, 1.25)).isEqualTo(953.125);
    }
}
```

- [ ] **Step 2: Run test, verify it fails**

Run: `.\gradlew :landclaims-plugin:test --tests "com.nick.landclaims.plugin.limit.LimitServiceTest"`
Expected: FAIL — `LandClaimsLimitService` does not exist, `LimitService` constructor mismatch.

- [ ] **Step 3a: Create `LandClaimsLimitService` in landclaims-api**

`landclaims-api/src/main/java/com/nick/landclaims/api/limit/LandClaimsLimitService.java`:
```java
package com.nick.landclaims.api.limit;

import java.util.UUID;

public interface LandClaimsLimitService {
    int getLimit(UUID playerId);
    void setLimit(UUID playerId, int limit);
    void addChunks(UUID playerId, int chunks);
    void removeChunks(UUID playerId, int chunks);
}
```

- [ ] **Step 3b: Rewrite `LimitService`**

Replace the entire file:
```java
package com.nick.landclaims.plugin.limit;

import com.nick.landclaims.api.limit.LandClaimsLimitService;
import java.util.Objects;
import java.util.UUID;

public final class LimitService implements LandClaimsLimitService {
    private final int defaultLimit;
    private final ClaimLimitRepository repository;

    public LimitService(int defaultLimit, ClaimLimitRepository repository) {
        this.defaultLimit = defaultLimit;
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public int getLimit(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return repository.getLimit(playerId).orElse(defaultLimit);
    }

    @Override
    public void setLimit(UUID playerId, int limit) {
        Objects.requireNonNull(playerId, "playerId");
        if (limit < 1) throw new IllegalArgumentException("limit must be >= 1");
        repository.setLimit(playerId, limit);
    }

    @Override
    public void addChunks(UUID playerId, int chunks) {
        Objects.requireNonNull(playerId, "playerId");
        if (chunks < 1) throw new IllegalArgumentException("chunks must be >= 1");
        repository.setLimit(playerId, getLimit(playerId) + chunks);
    }

    @Override
    public void removeChunks(UUID playerId, int chunks) {
        Objects.requireNonNull(playerId, "playerId");
        if (chunks < 1) throw new IllegalArgumentException("chunks must be >= 1");
        repository.setLimit(playerId, Math.max(1, getLimit(playerId) - chunks));
    }

    public int overageChunks(int proposedTotalChunks, int allowedChunks) {
        return Math.max(0, proposedTotalChunks - allowedChunks);
    }

    public static double flatOverLimitCost(int overageChunks, double costPerChunk) {
        return Math.max(0, overageChunks) * Math.max(0.0, costPerChunk);
    }

    public static double exponentialOverLimitCost(int overageChunks, double baseCost, double multiplier) {
        int n = Math.max(0, overageChunks);
        double base = Math.max(0.0, baseCost);
        double mult = Math.max(0.0, multiplier);
        double total = 0.0;
        for (int i = 0; i < n; i++) {
            total += base * Math.pow(mult, i);
        }
        return total;
    }
}
```

- [ ] **Step 4: Run test, verify it passes**

Run: `.\gradlew :landclaims-plugin:test --tests "com.nick.landclaims.plugin.limit.LimitServiceTest"`
Expected: PASS (8 tests).

- [ ] **Step 5: Commit**

```bash
git add landclaims-api/src/main/java/com/nick/landclaims/api/limit/LandClaimsLimitService.java
git add landclaims-plugin/src/main/java/com/nick/landclaims/plugin/limit/LimitService.java
git add landclaims-plugin/src/test/java/com/nick/landclaims/plugin/limit/LimitServiceTest.java
git commit -m "feat(api): LandClaimsLimitService interface and DB-backed LimitService"
```

---

## Task A3: Update ClaimCostService + all call sites

`ClaimCostService.quotePlayerClaim` currently takes `(UUID ownerId, Set<String> permissions, Set<ClaimChunk> selectedChunks)` and calls `limitService.resolveLimit(permissions)`. After this task it takes `(UUID ownerId, Set<ClaimChunk> selectedChunks)` and calls `limitService.getLimit(ownerId)`.

**Files:**
- Modify: `landclaims-plugin/src/main/java/com/nick/landclaims/plugin/limit/ClaimCostService.java`
- Modify: `landclaims-plugin/src/main/java/com/nick/landclaims/plugin/command/ClaimsCommand.java` (call sites)
- Modify: `landclaims-plugin/src/test/java/com/nick/landclaims/plugin/limit/ClaimCostServiceTest.java`

- [ ] **Step 1: Write the failing test**

Replace `ClaimCostServiceTest.java` (the two tests, adapted for the new signature):
```java
package com.nick.landclaims.plugin.limit;

import static org.assertj.core.api.Assertions.assertThat;

import com.nick.landclaims.plugin.claim.Claim;
import com.nick.landclaims.plugin.claim.ClaimChunk;
import com.nick.landclaims.plugin.claim.ClaimIndex;
import com.nick.landclaims.plugin.claim.OwnerType;
import java.time.Instant;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClaimCostServiceTest {
    private static LimitService limitOf(int limit) {
        return new LimitService(limit, id -> OptionalInt.empty());
    }

    private static LimitService limitOf(UUID player, int limit) {
        return new LimitService(99, id -> id.equals(player) ? OptionalInt.of(limit) : OptionalInt.empty());
    }

    @Test
    void quoteIncludesExistingPlayerChunksWhenPricingOverLimitSelection() {
        ClaimIndex claimIndex = new ClaimIndex();
        UUID ownerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        claimIndex.add(claim(ownerId, worldId, Set.of(
                new ClaimChunk(worldId, 0, 0),
                new ClaimChunk(worldId, 1, 0)
        )));
        ClaimCostService service = new ClaimCostService(
                claimIndex,
                limitOf(3),
                new ClaimCostConfig(true, ClaimCostConfig.PricingMode.FLAT, 100.0, 100.0, 2.0)
        );

        ClaimCostQuote quote = service.quotePlayerClaim(
                ownerId,
                Set.of(new ClaimChunk(worldId, 2, 0), new ClaimChunk(worldId, 3, 0))
        );

        assertThat(quote.allowedChunks()).isEqualTo(3);
        assertThat(quote.existingChunks()).isEqualTo(2);
        assertThat(quote.proposedTotalChunks()).isEqualTo(4);
        assertThat(quote.overageChunks()).isEqualTo(1);
        assertThat(quote.cost()).isEqualTo(100.0);
    }

    @Test
    void quoteUsesPlayerDBLimitNotDefault() {
        UUID ownerId = UUID.randomUUID();
        ClaimCostService service = new ClaimCostService(
                new ClaimIndex(),
                limitOf(ownerId, 10),
                new ClaimCostConfig(true, ClaimCostConfig.PricingMode.FLAT, 100.0, 100.0, 2.0)
        );

        ClaimCostQuote quote = service.quotePlayerClaim(
                ownerId,
                Set.of(new ClaimChunk(UUID.randomUUID(), 0, 0), new ClaimChunk(UUID.randomUUID(), 1, 0))
        );

        assertThat(quote.allowedChunks()).isEqualTo(10);
        assertThat(quote.overageChunks()).isZero();
    }

    private static Claim claim(UUID ownerId, UUID worldId, Set<ClaimChunk> chunks) {
        Instant now = Instant.parse("2026-06-07T00:00:00Z");
        return new Claim(UUID.randomUUID(), "Existing", OwnerType.PLAYER, ownerId, worldId, chunks, Map.of(), now, now);
    }
}
```

> Note: `new LimitService(limit, id -> OptionalInt.empty())` works because `ClaimLimitRepository` is a functional interface (single abstract method). If the compiler rejects the lambda, use an anonymous class as shown in `LimitServiceTest`.

- [ ] **Step 2: Run test, verify it fails**

Run: `.\gradlew :landclaims-plugin:test --tests "com.nick.landclaims.plugin.limit.ClaimCostServiceTest"`
Expected: FAIL — `quotePlayerClaim` has wrong arity.

- [ ] **Step 3: Update `ClaimCostService`**

Replace `quotePlayerClaim`:
```java
public ClaimCostQuote quotePlayerClaim(UUID ownerId, Set<ClaimChunk> selectedChunks) {
    Objects.requireNonNull(ownerId, "ownerId");
    Objects.requireNonNull(selectedChunks, "selectedChunks");

    int allowedChunks = limitService.getLimit(ownerId);
    int existingChunks = claimIndex.findAll().stream()
            .filter(claim -> claim.owner() == OwnerType.PLAYER && ownerId.equals(claim.ownerUuid()))
            .mapToInt(claim -> claim.claimChunks().size())
            .sum();
    int proposedTotalChunks = existingChunks + selectedChunks.size();
    int overageChunks = limitService.overageChunks(proposedTotalChunks, allowedChunks);
    return new ClaimCostQuote(
            allowedChunks,
            existingChunks,
            selectedChunks.size(),
            proposedTotalChunks,
            overageChunks,
            claimCostConfig.priceOverage(overageChunks)
    );
}
```

Remove the `import java.util.Set;` for `Set<String>` if it becomes unused (keep `Set` for `Set<ClaimChunk>`).

- [ ] **Step 4: Fix call sites in ClaimsCommand**

In `ClaimsCommand.java`, find every call to `claimCostService.quotePlayerClaim(...)`. The current call passes a permissions set built from `player.getEffectivePermissions()`. Remove the permissions argument:

```java
// Before:
Set<String> permissions = player.getEffectivePermissions().stream()
        .filter(PermissionAttachmentInfo::getValue)
        .map(PermissionAttachmentInfo::getPermission)
        .collect(Collectors.toSet());
ClaimCostQuote quote = claimCostService.quotePlayerClaim(player.getUniqueId(), permissions, selectedChunks);

// After:
ClaimCostQuote quote = claimCostService.quotePlayerClaim(player.getUniqueId(), selectedChunks);
```

Remove the now-unused `PermissionAttachmentInfo` import if it is no longer referenced anywhere else in the file (grep for other uses first).

- [ ] **Step 5: Run full build**

Run: `.\gradlew build`
Expected: BUILD SUCCESSFUL — the project must fully compile. Fix any remaining `quotePlayerClaim` call site that still passes a permissions argument.

- [ ] **Step 6: Commit**

```bash
git add landclaims-plugin/src/main/java/com/nick/landclaims/plugin/limit/ClaimCostService.java
git add landclaims-plugin/src/main/java/com/nick/landclaims/plugin/command/ClaimsCommand.java
git add landclaims-plugin/src/test/java/com/nick/landclaims/plugin/limit/ClaimCostServiceTest.java
git commit -m "refactor: drop permissions param from ClaimCostService.quotePlayerClaim"
```

---

## Task A4: Wire LimitService in LandClaimsPlugin + register API

**Files:**
- Modify: `landclaims-plugin/src/main/java/com/nick/landclaims/plugin/LandClaimsPlugin.java`

No test file — this is wiring only; existing integration tests cover the compile path.

- [ ] **Step 1: Update `onEnable` in `LandClaimsPlugin`**

**Add import:**
```java
import com.nick.landclaims.api.limit.LandClaimsLimitService;
import com.nick.landclaims.plugin.limit.ClaimLimitRepository;
import com.nick.landclaims.plugin.limit.SqlClaimLimitRepository;
```

**Replace the `LimitService` construction block.** The current code (around line 82–91):
```java
Map<String, Integer> limitPermissions = loadLimitPermissions(loadYamlResource("permissions.yml"));
PermissionBankService permissionBankService = new PermissionBankService(getServer().getPluginManager());
permissionBankService.registerLimitPermissions(limitPermissions);
...
LimitService limitService = new LimitService(
        getConfig().getInt("limits.default-claim-limit", limitPermissions.getOrDefault("landclaims.limit.default", 10)),
        limitPermissions
);
```

Replace with:
```java
PermissionBankService permissionBankService = new PermissionBankService(getServer().getPluginManager());
...
HavenDataSource havenDataSource = HavenAPI.get(HavenDataSource.class);
havenDataSource.registerMigrations("landclaims", "db/migrations/landclaims", getClass().getClassLoader());
DataSource dataSource = havenDataSource.getDataSource();
ClaimLimitRepository claimLimitRepository = new SqlClaimLimitRepository(dataSource);
LimitService limitService = new LimitService(
        getConfig().getInt("limits.default-claim-limit", 10),
        claimLimitRepository
);
getServer().getServicesManager().register(
        LandClaimsLimitService.class, limitService, this, ServicePriority.Normal);
```

> The `createClaimRepository()` private method also calls `HavenAPI.get(HavenDataSource.class)` and `registerMigrations`. Move the `registerMigrations` + `getDataSource()` call to `onEnable` (shared between claim repo and limit repo), or extract a `getHavenDataSource()` helper. Do NOT call `registerMigrations` twice with the same namespace — merge into one call before both repository constructions.

**Add to `onDisable`:**
```java
getServer().getServicesManager().unregister(LandClaimsLimitService.class, limitService);
```

Store `limitService` as a field if needed to unregister it. The cleanest way is to hold it as an instance field (like `landClaimsApi`).

- [ ] **Step 2: Remove dead code**

- Delete the `loadLimitPermissions(YamlConfiguration)` private method (no longer called).
- Delete or make no-op `permissionBankService.registerLimitPermissions(limitPermissions)` (the method, if it only registered limit nodes, may now be removable from `PermissionBankService` too — only remove it if nothing else calls it).

- [ ] **Step 3: Run full build**

Run: `.\gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add landclaims-plugin/src/main/java/com/nick/landclaims/plugin/LandClaimsPlugin.java
git commit -m "feat: wire DB-backed LimitService and register LandClaimsLimitService API"
```

---

## Task A5: Admin limit commands

**Files:**
- Modify: `landclaims-plugin/src/main/java/com/nick/landclaims/plugin/command/ClaimsCommand.java`
- Modify: `landclaims-plugin/src/main/resources/messages.yml`
- Modify: `landclaims-plugin/src/main/resources/permissions.yml`

- [ ] **Step 1: Add messages**

In `messages.yml`, add under `admin:`:
```yaml
  limit:
    usage: "<red>Usage: /claim admin limit <set|add|remove|get> <player|uuid> [amount]"
    no-permission: "<red>You do not have permission to manage claim limits."
    invalid-amount: "<red>Amount must be a positive integer."
    player-not-found: "<red>Player <yellow><player></yellow> not found."
    set: "<green>Set claim limit for <yellow><player></yellow> to <yellow><amount></yellow> chunks."
    added: "<green>Added <yellow><amount></yellow> chunks to <yellow><player></yellow>'s limit. New limit: <yellow><new_limit></yellow>."
    removed: "<green>Removed <yellow><amount></yellow> chunks from <yellow><player></yellow>'s limit. New limit: <yellow><new_limit></yellow>."
    get: "<gold>Claim limit for <yellow><player></yellow>: <white><amount></white> chunks <gray>(<source>)</gray>."
```

- [ ] **Step 2: Update permissions.yml**

Remove the entire `limits:` section (lines with `landclaims.limit.default/member/vip/elite`). Add to `admin:`:
```yaml
  landclaims.admin.limit: "Allows staff to view and adjust player claim chunk limits."
```

- [ ] **Step 3: Add `LandClaimsLimitService` to `ClaimsCommand`**

Add field:
```java
private final LandClaimsLimitService claimLimitService;
```

Add `import com.nick.landclaims.api.limit.LandClaimsLimitService;`

Add it as the last parameter in the full constructor (before closing `)`):
```java
LandClaimsLimitService claimLimitService
```
And assign: `this.claimLimitService = Objects.requireNonNull(claimLimitService, "claimLimitService");`

Add `"limit"` to `ADMIN_SUGGESTIONS`:
```java
private static final List<String> ADMIN_SUGGESTIONS = List.of("create", "list", "delete", "teleport", "userclaims", "limit");
```

Add:
```java
private static final List<String> ADMIN_LIMIT_SUGGESTIONS = List.of("set", "add", "remove", "get");
```

- [ ] **Step 4: Implement the limit subcommand handler**

In the `onCommand` admin dispatch block, add a branch for `"limit"`. Following the existing pattern for admin subcommands:

```java
case "limit" -> {
    if (!sender.hasPermission("landclaims.admin.limit")) {
        sender.sendMessage(message("admin.limit.no-permission"));
        return true;
    }
    if (args.length < 3) {
        sender.sendMessage(message("admin.limit.usage"));
        return true;
    }
    String limitAction = args[2].toLowerCase(java.util.Locale.ROOT);
    if (limitAction.equals("get")) {
        if (args.length < 4) {
            sender.sendMessage(message("admin.limit.usage"));
            return true;
        }
        UUID targetId = resolvePlayerUuid(args[3]);
        if (targetId == null) {
            sender.sendMessage(message("admin.limit.player-not-found", Map.of("player", args[3])));
            return true;
        }
        int limit = claimLimitService.getLimit(targetId);
        String source = /* check if DB record exists — call repo via cast or expose method */
                "db"; // simplified: always show "db or default"
        sender.sendMessage(message("admin.limit.get", Map.of(
                "player", args[3],
                "amount", String.valueOf(limit),
                "source", source)));
        return true;
    }
    if (args.length < 5) {
        sender.sendMessage(message("admin.limit.usage"));
        return true;
    }
    UUID targetId = resolvePlayerUuid(args[3]);
    if (targetId == null) {
        sender.sendMessage(message("admin.limit.player-not-found", Map.of("player", args[3])));
        return true;
    }
    int amount;
    try {
        amount = Integer.parseInt(args[4]);
        if (amount < 1) throw new NumberFormatException();
    } catch (NumberFormatException ex) {
        sender.sendMessage(message("admin.limit.invalid-amount"));
        return true;
    }
    switch (limitAction) {
        case "set" -> {
            claimLimitService.setLimit(targetId, amount);
            sender.sendMessage(message("admin.limit.set", Map.of(
                    "player", args[3], "amount", String.valueOf(amount))));
        }
        case "add" -> {
            claimLimitService.addChunks(targetId, amount);
            sender.sendMessage(message("admin.limit.added", Map.of(
                    "player", args[3],
                    "amount", String.valueOf(amount),
                    "new_limit", String.valueOf(claimLimitService.getLimit(targetId)))));
        }
        case "remove" -> {
            claimLimitService.removeChunks(targetId, amount);
            sender.sendMessage(message("admin.limit.removed", Map.of(
                    "player", args[3],
                    "amount", String.valueOf(amount),
                    "new_limit", String.valueOf(claimLimitService.getLimit(targetId)))));
        }
        default -> sender.sendMessage(message("admin.limit.usage"));
    }
    return true;
}
```

> `resolvePlayerUuid(String)` — look for an existing helper in `ClaimsCommand` that resolves a player name or UUID string to a `UUID` (grep for `UUID.fromString` or `getOfflinePlayer` in the file). If one exists, reuse it. If not, add:
> ```java
> private UUID resolvePlayerUuid(String input) {
>     try {
>         return UUID.fromString(input);
>     } catch (IllegalArgumentException ignored) {}
>     OfflinePlayer offline = getServer().getOfflinePlayerIfCached(input);
>     return offline == null ? null : offline.getUniqueId();
> }
> ```
> Note `ClaimsCommand` doesn't directly hold a `Server` reference — use the `Bukkit.getOfflinePlayer` static or pass `getServer()` from `LandClaimsPlugin`. Check existing admin commands for the pattern already used.

- [ ] **Step 5: Add tab completion for limit**

In `onTabComplete`, add to the admin tab branch:
```java
if (args.length == 3 && args[1].equalsIgnoreCase("admin") && args[2].equalsIgnoreCase("limit")) {
    return filterPrefix(ADMIN_LIMIT_SUGGESTIONS, "");
}
if (args.length == 3 && args[1].equalsIgnoreCase("admin") && !args[2].equalsIgnoreCase("limit")) {
    // existing handling unchanged
}
```

Follow the existing tab-complete pattern for the admin block.

- [ ] **Step 6: Update `LandClaimsPlugin` construction**

Pass `limitService` as the new last argument to `ClaimsCommand`:
```java
ClaimsCommand claimsCommand = new ClaimsCommand(
        ...,
        adminClaimService,
        limitService    // new
);
```

- [ ] **Step 7: Run full build**

Run: `.\gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "feat: /claim admin limit commands and messages"
```

---

## Final verification

- [ ] Run `.\gradlew build` — BUILD SUCCESSFUL, all tests green.
- [ ] Confirm spec coverage: V4 migration (A1), `ClaimLimitRepository` (A1), `LandClaimsLimitService` API (A2), `LimitService` rewrite (A2), `ClaimCostService` signature (A3), ServicesManager registration (A4), admin commands set/add/remove/get (A5), messages + permissions (A5).
- [ ] Phase B plan: `docs/superpowers/plans/2026-06-12-db-backed-claim-limits-havenvault.md`.
