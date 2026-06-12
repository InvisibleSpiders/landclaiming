# Tri-state Claim Flags Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace boolean claim flags with an OFF/VISITORS/ALL tri-state whose meaning is governed per-flag, and replace the chat flag editor with a Paper Dialog UI.

**Architecture:** A new `FlagState` enum and `FlagKind` classify every flag. `ProtectionService` resolves an actor's relationship (owner/manager/member/visitor) and decides allow/deny from the flag's state and kind. Flags persist as a `state` TEXT column (V3 migration preserves current behavior). The editor becomes a Paper Dialog with per-flag click-through cycling, falling back to clickable chat.

**Tech Stack:** Java 25, Paper API 26.1.2 (Dialog API), Gradle (Kotlin DSL), JUnit, SQLite/HikariCP, Adventure.

**Spec:** `docs/superpowers/specs/2026-06-12-tristate-claim-flags-design.md`

**Conventions:**
- Build/test command for everything below: `./gradlew build` (runs all module tests). For a single test class: `./gradlew :landclaims-plugin:test --tests "com.nick.landclaims.plugin.<Class>"`.
- Commit after each task. Branch: `feature/tristate-flags`.

**Known real API names (do not guess):**
- `ClaimFlagResult` is `record ClaimFlagResult(boolean allowed, String messageKey)` with statics `success()` / `denied(String)`. Use `result.allowed()` and `result.messageKey()` (a plain `String`, NOT `Optional`).
- `ClaimIndex` exposes `add`, `replace`, `remove(UUID)`, `findAt(ClaimChunk)`, `findAll()`. There is **no** `findById`. To read a claim's current state after a write, recompute it (see A6 `nextState`) rather than re-querying the index.
- `FlagRegistry.createDefault()`, `definition(String)`, `keys()`, `definitions()` already exist; `defaultValue` is renamed to `defaultState` in A3.

**Existing tests will break.** Several existing test files reference `Map<String,Boolean>` flags, `defaultValue`, `setFlag`/`toggleFlag`, or boolean flag assertions and MUST be updated as part of the tasks that change those APIs (not left for the final build). Affected files, by task:
- A3 (`defaultValue`→`defaultState`): existing `flag/FlagRegistryTest.java` — extend in place (do not create a second file).
- A4 (type change): `claim/ClaimTest.java`, `claim/ClaimCreationServiceTest.java`, `claim/ClaimDenyServiceTest.java`, `claim/ClaimMemberServiceTest.java`, `admin/AdminClaimServiceTest.java`, `entity/EntityControlServiceTest.java`, `api/BukkitLandClaimsApiTest.java`, `access/ClaimEntryGuardTest.java`, `ui/ClaimMenuServiceTest.java`, `command/ClaimsCommandAdminTest.java`, `recipe/ClaimToolRecipeConfigTest.java` — change any `Map.of("flag", true/false)` to `FlagState`, any `getOrDefault(..., false)` to `FlagState.OFF`.
- A5: existing `protection/ProtectionServiceTest.java` and `listener/ProtectionListenerTest.java` — replace boolean expectations with the tri-state matrix.
- A6: existing `flag/ClaimFlagServiceTest.java` — replace `setFlag`/`toggleFlag` cases with `setFlagState`/`cycleFlag`.
- A8: existing `storage/sql/SqlClaimRepositoryTest.java` (reuse its DataSource+migration harness) and `storage/sql/LandClaimsMigrationResourceTest.java` (may assert the migration list — add V3).
For each, run the file's test after editing and fix until green. When a task says "create or extend," if the file already exists, **extend it**.

---

## Phase A — Model, protection, storage, command (headless)

### Task A1: `FlagState` enum

**Files:**
- Create: `landclaims-api/src/main/java/com/nick/landclaims/api/flag/FlagState.java`
- Test: `landclaims-api/src/test/java/com/nick/landclaims/api/flag/FlagStateTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.nick.landclaims.api.flag;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FlagStateTest {
    @Test
    void hasThreeStatesInDeclaredOrder() {
        assertEquals(3, FlagState.values().length);
        assertEquals(FlagState.OFF, FlagState.values()[0]);
        assertEquals(FlagState.VISITORS, FlagState.values()[1]);
        assertEquals(FlagState.ALL, FlagState.values()[2]);
    }
}
```

- [ ] **Step 2: Run test, verify it fails**

Run: `./gradlew :landclaims-api:test --tests "com.nick.landclaims.api.flag.FlagStateTest"`
Expected: FAIL — `FlagState` does not exist (compile error).

> If `landclaims-api` has no `src/test` yet, also confirm the module's `build.gradle.kts` has JUnit on the test classpath; mirror `landclaims-plugin/build.gradle.kts` test deps if missing. Add only if the test fails to compile for lack of JUnit.

- [ ] **Step 3: Create the enum**

```java
package com.nick.landclaims.api.flag;

public enum FlagState {
    OFF,
    VISITORS,
    ALL
}
```

- [ ] **Step 4: Run test, verify it passes**

Run: `./gradlew :landclaims-api:test --tests "com.nick.landclaims.api.flag.FlagStateTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add landclaims-api/src/main/java/com/nick/landclaims/api/flag/FlagState.java landclaims-api/src/test/java/com/nick/landclaims/api/flag/FlagStateTest.java
git commit -m "feat(api): add FlagState tri-state enum"
```

---

### Task A2: `FlagKind` enum with cycle logic

**Files:**
- Create: `landclaims-api/src/main/java/com/nick/landclaims/api/flag/FlagKind.java`
- Test: `landclaims-api/src/test/java/com/nick/landclaims/api/flag/FlagKindTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.nick.landclaims.api.flag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class FlagKindTest {
    @Test
    void playerActionCyclesThroughAllThreeStates() {
        assertEquals(List.of(FlagState.OFF, FlagState.VISITORS, FlagState.ALL),
                FlagKind.PLAYER_ACTION.cycle());
        assertEquals(FlagState.VISITORS, FlagKind.PLAYER_ACTION.next(FlagState.OFF));
        assertEquals(FlagState.ALL, FlagKind.PLAYER_ACTION.next(FlagState.VISITORS));
        assertEquals(FlagState.OFF, FlagKind.PLAYER_ACTION.next(FlagState.ALL));
        assertTrue(FlagKind.PLAYER_ACTION.supports(FlagState.VISITORS));
    }

    @Test
    void worldEffectSkipsVisitors() {
        assertEquals(List.of(FlagState.OFF, FlagState.ALL), FlagKind.WORLD_EFFECT.cycle());
        assertEquals(FlagState.ALL, FlagKind.WORLD_EFFECT.next(FlagState.OFF));
        assertEquals(FlagState.OFF, FlagKind.WORLD_EFFECT.next(FlagState.ALL));
        assertFalse(FlagKind.WORLD_EFFECT.supports(FlagState.VISITORS));
    }

    @Test
    void nextFromUnsupportedStateReturnsFirst() {
        // A WORLD_EFFECT flag should never hold VISITORS, but be defensive.
        assertEquals(FlagState.OFF, FlagKind.WORLD_EFFECT.next(FlagState.VISITORS));
    }
}
```

- [ ] **Step 2: Run test, verify it fails**

Run: `./gradlew :landclaims-api:test --tests "com.nick.landclaims.api.flag.FlagKindTest"`
Expected: FAIL — `FlagKind` does not exist.

- [ ] **Step 3: Create the enum**

```java
package com.nick.landclaims.api.flag;

import java.util.List;

public enum FlagKind {
    PLAYER_ACTION(List.of(FlagState.OFF, FlagState.VISITORS, FlagState.ALL)),
    WORLD_EFFECT(List.of(FlagState.OFF, FlagState.ALL));

    private final List<FlagState> cycle;

    FlagKind(List<FlagState> cycle) {
        this.cycle = List.copyOf(cycle);
    }

    public List<FlagState> cycle() {
        return cycle;
    }

    public boolean supports(FlagState state) {
        return cycle.contains(state);
    }

    public FlagState next(FlagState current) {
        int index = cycle.indexOf(current);
        if (index < 0) {
            return cycle.get(0);
        }
        return cycle.get((index + 1) % cycle.size());
    }
}
```

- [ ] **Step 4: Run test, verify it passes**

Run: `./gradlew :landclaims-api:test --tests "com.nick.landclaims.api.flag.FlagKindTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add landclaims-api/src/main/java/com/nick/landclaims/api/flag/FlagKind.java landclaims-api/src/test/java/com/nick/landclaims/api/flag/FlagKindTest.java
git commit -m "feat(api): add FlagKind with state cycle logic"
```

---

### Task A3: Rewrite `ClaimFlagDefinition` + `FlagRegistry`

**Files:**
- Modify: `landclaims-api/src/main/java/com/nick/landclaims/api/flag/ClaimFlagDefinition.java` (full rewrite)
- Modify: `landclaims-plugin/src/main/java/com/nick/landclaims/plugin/flag/FlagRegistry.java`
- Test: `landclaims-plugin/src/test/java/com/nick/landclaims/plugin/flag/FlagRegistryTest.java` (create or extend)

- [ ] **Step 1: Write the failing test**

```java
package com.nick.landclaims.plugin.flag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nick.landclaims.api.flag.ClaimFlagDefinition;
import com.nick.landclaims.api.flag.FlagKind;
import com.nick.landclaims.api.flag.FlagState;
import org.junit.jupiter.api.Test;

class FlagRegistryTest {
    private final FlagRegistry registry = FlagRegistry.createDefault();

    @Test
    void containerAccessIsPlayerActionOwnerExemptVisitorsDefault() {
        ClaimFlagDefinition def = registry.definition("container_access").orElseThrow();
        assertEquals(FlagKind.PLAYER_ACTION, def.kind());
        assertTrue(def.ownerExempt());
        assertEquals(FlagState.VISITORS, def.defaultState());
    }

    @Test
    void entityDamageDefaultsToAll() {
        assertEquals(FlagState.ALL, registry.definition("entity_damage").orElseThrow().defaultState());
    }

    @Test
    void explosionDamageIsWorldEffectNotOwnerExemptOffDefault() {
        ClaimFlagDefinition def = registry.definition("explosion_damage").orElseThrow();
        assertEquals(FlagKind.WORLD_EFFECT, def.kind());
        assertFalse(def.ownerExempt());
        assertEquals(FlagState.OFF, def.defaultState());
    }

    @Test
    void pistonProtectionDefaultsToAll() {
        assertEquals(FlagState.ALL, registry.definition("piston_protection").orElseThrow().defaultState());
    }

    @Test
    void defaultStateLookupFallsBackToOff() {
        assertEquals(FlagState.OFF, registry.defaultState("nonexistent_flag"));
    }
}
```

- [ ] **Step 2: Run test, verify it fails**

Run: `./gradlew :landclaims-plugin:test --tests "com.nick.landclaims.plugin.flag.FlagRegistryTest"`
Expected: FAIL — `kind()`, `defaultState()`, `registry.defaultState(...)` do not exist.

- [ ] **Step 3a: Rewrite `ClaimFlagDefinition`**

Replace the entire file with:

```java
package com.nick.landclaims.api.flag;

public record ClaimFlagDefinition(
        String key,
        String category,
        String label,
        String description,
        FlagKind kind,
        boolean ownerExempt,
        FlagState defaultState,
        String editPermission
) {
    public ClaimFlagDefinition {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Flag key cannot be blank.");
        }
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("Flag category cannot be blank.");
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("Flag label cannot be blank.");
        }
        if (description == null) {
            throw new IllegalArgumentException("Flag description cannot be null.");
        }
        if (kind == null) {
            throw new IllegalArgumentException("Flag kind cannot be null.");
        }
        if (defaultState == null) {
            throw new IllegalArgumentException("Flag defaultState cannot be null.");
        }
        if (!kind.supports(defaultState)) {
            throw new IllegalArgumentException(
                    "Flag " + key + " kind " + kind + " does not support default state " + defaultState);
        }
    }
}
```

- [ ] **Step 3b: Rewrite `FlagRegistry.createDefault()`, the `flag(...)` helper, and `defaultValue` → `defaultState`**

In `FlagRegistry.java`:

Replace the `createDefault()` body's definition set with (kind, ownerExempt, defaultState per the spec classification table):

```java
    public static FlagRegistry createDefault() {
        return new FlagRegistry(Set.of(
                action("build", "Access", "Build", "Allow non-members to place blocks.", FlagState.VISITORS),
                action("break", "Access", "Break", "Allow non-members to break blocks.", FlagState.VISITORS),
                action("interact", "Access", "Interact", "Allow generic block interaction.", FlagState.VISITORS),
                action("container_access", "Access", "Containers", "Allow chest, barrel, furnace, and hopper access.", FlagState.VISITORS),
                action("door_access", "Access", "Doors & Gates", "Allow doors, trapdoors, and fence gates.", FlagState.VISITORS),
                action("switch_access", "Access", "Switches", "Allow buttons, levers, and pressure plates.", FlagState.VISITORS),
                action("redstone_access", "Access", "Redstone Use", "Allow repeater and comparator interaction.", FlagState.VISITORS),
                action("entity_damage", "Entity", "Entity Damage", "Allow non-members to damage entities here.", FlagState.ALL),
                action("crop_trample", "Entity", "Crop Trample", "Allow farmland trampling in this claim.", FlagState.ALL),
                action("item_pickup", "Items", "Item Pickup", "Allow non-members to pick up items here.", FlagState.ALL),
                action("item_drop", "Items", "Item Drop", "Allow non-members to drop items here.", FlagState.ALL),
                world("piston_protection", "Protection", "Piston Protection", "Block piston movement touching this claim.", FlagState.ALL),
                world("fluid_flow", "Environment", "Fluid Flow", "Allow water and lava to flow into this claim.", FlagState.OFF),
                world("explosion_damage", "Environment", "Explosion Damage", "Allow explosions to damage claimed blocks.", FlagState.OFF),
                world("fire_spread", "Environment", "Fire Spread", "Allow fire to spread into this claim.", FlagState.OFF),
                world("mob_griefing", "Environment", "Mob Griefing", "Allow entity block changes in this claim.", FlagState.OFF),
                world("remove_hostile_entities", "Entity Control", "Remove Hostiles", "Removes hostile mobs unless named or tamed.", FlagState.OFF),
                world("remove_passive_entities", "Entity Control", "Remove Passives", "Removes passive mobs unless named or tamed.", FlagState.OFF)
        ));
    }
```

Replace the `defaultValue` method:

```java
    public FlagState defaultState(String key) {
        return definition(key)
                .map(ClaimFlagDefinition::defaultState)
                .orElse(FlagState.OFF);
    }
```

Replace the private `flag(...)` helper with two helpers:

```java
    private static ClaimFlagDefinition action(
            String key, String category, String label, String description, FlagState defaultState) {
        return define(key, category, label, description, FlagKind.PLAYER_ACTION, true, defaultState);
    }

    private static ClaimFlagDefinition world(
            String key, String category, String label, String description, FlagState defaultState) {
        return define(key, category, label, description, FlagKind.WORLD_EFFECT, false, defaultState);
    }

    private static ClaimFlagDefinition define(
            String key, String category, String label, String description,
            FlagKind kind, boolean ownerExempt, FlagState defaultState) {
        return new ClaimFlagDefinition(
                key, category, label, description, kind, ownerExempt, defaultState,
                "landclaims.flag." + key);
    }
```

Add imports to `FlagRegistry.java`:

```java
import com.nick.landclaims.api.flag.FlagKind;
import com.nick.landclaims.api.flag.FlagState;
```

> Note: the project will NOT fully compile yet — `ClaimFlagService` and `ProtectionService` still call `defaultValue(...)`. They are fixed in A4/A5/A6. Run only the targeted module compile for the registry/api here.

- [ ] **Step 4: Run the api + registry tests**

Run: `./gradlew :landclaims-api:compileJava :landclaims-plugin:test --tests "com.nick.landclaims.plugin.flag.FlagRegistryTest"`
Expected: api compiles; FlagRegistryTest PASS. (`landclaims-plugin` full build still red — expected, fixed in later tasks.)

- [ ] **Step 5: Commit**

```bash
git add landclaims-api/src/main/java/com/nick/landclaims/api/flag/ClaimFlagDefinition.java landclaims-plugin/src/main/java/com/nick/landclaims/plugin/flag/FlagRegistry.java landclaims-plugin/src/test/java/com/nick/landclaims/plugin/flag/FlagRegistryTest.java
git commit -m "feat: classify flags by kind, ownerExempt, and default state"
```

---

### Task A4: Migrate domain + API flag value type to `FlagState`

This task changes `Map<String,Boolean>` → `Map<String,FlagState>` across the domain and fixes every mechanical consumer so the project compiles again. Protection logic (A5), flag service (A6), and storage (A7/A8) are intentionally left for later tasks; here we only keep types coherent and behavior equivalent.

**Files:**
- Modify: `landclaims-api/src/main/java/com/nick/landclaims/api/claim/ClaimView.java`
- Modify: `landclaims-plugin/src/main/java/com/nick/landclaims/plugin/claim/Claim.java`
- Modify: `landclaims-plugin/src/main/java/com/nick/landclaims/plugin/claim/ClaimCreationService.java`
- Modify: `landclaims-plugin/src/main/java/com/nick/landclaims/plugin/admin/AdminClaimService.java`
- Modify: `landclaims-plugin/src/main/java/com/nick/landclaims/plugin/entity/EntityControlService.java`
- (Compile-only pass-throughs — no logic change: `ClaimDenyService`, `ClaimMemberService`, `ClaimMenuService` rebuild `Claim` with `claim.flags()` and won't need edits beyond type inference.)
- Test: `landclaims-plugin/src/test/java/com/nick/landclaims/plugin/claim/ClaimFlagTypeTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.nick.landclaims.plugin.claim;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nick.landclaims.api.flag.FlagState;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClaimFlagTypeTest {
    @Test
    void claimStoresFlagStates() {
        Claim claim = new Claim(
                UUID.randomUUID(), "Home", OwnerType.PLAYER, UUID.randomUUID(), UUID.randomUUID(),
                Set.of(new ClaimChunk(UUID.randomUUID(), 0, 0)),
                Map.of("container_access", FlagState.VISITORS),
                Instant.now(), Instant.now());
        assertEquals(FlagState.VISITORS, claim.flags().get("container_access"));
    }
}
```

- [ ] **Step 2: Run test, verify it fails**

Run: `./gradlew :landclaims-plugin:test --tests "com.nick.landclaims.plugin.claim.ClaimFlagTypeTest"`
Expected: FAIL — `Claim` flags is `Map<String,Boolean>`, won't accept `FlagState`.

- [ ] **Step 3a: `ClaimView.flags()` type**

In `ClaimView.java`, change import and signature:

```java
import com.nick.landclaims.api.flag.FlagState;
```
```java
    Map<String, FlagState> flags();
```

- [ ] **Step 3b: `Claim` record**

In `Claim.java`:
- Add import: `import com.nick.landclaims.api.flag.FlagState;`
- Change the record component `Map<String, Boolean> flags` → `Map<String, FlagState> flags`.
- Change BOTH convenience constructors' `Map<String, Boolean> flags` parameter → `Map<String, FlagState> flags`.

- [ ] **Step 3c: `ClaimCreationService` default + merge**

In `ClaimCreationService.java`:
- Add imports: `import com.nick.landclaims.api.flag.FlagState;`
- Change `defaultFlags()` return type and body:

```java
    private Map<String, FlagState> defaultFlags() {
        return flagRegistry.keys().stream()
                .collect(Collectors.toUnmodifiableMap(key -> key, flagRegistry::defaultState));
    }
```

- In `persistClaim`, change the merge accumulator type:

```java
            Map<String, FlagState> mergedFlags = new HashMap<>(defaultFlags());
```

(`mergedFlags.putAll(mergeTarget.flags())` now type-checks against `Map<String,FlagState>`.)

- [ ] **Step 3d: `AdminClaimService` default**

In `AdminClaimService.java`:
- Add import: `import com.nick.landclaims.api.flag.FlagState;`
- Change `defaultFlags()`:

```java
    private Map<String, FlagState> defaultFlags() {
        return flagRegistry.keys().stream()
                .collect(Collectors.toUnmodifiableMap(key -> key, flagRegistry::defaultState));
    }
```

(`transferPlayerClaim` passes `claim.flags()` straight through — no change.)

- [ ] **Step 3e: `EntityControlService` world-effect reads**

In `EntityControlService.java`, the two reads currently use `getOrDefault(KEY, false)`. Change to treat any non-OFF state as enabled:

```java
            return claim.flags().getOrDefault(REMOVE_HOSTILE_FLAG, FlagState.OFF) != FlagState.OFF;
```
```java
            return claim.flags().getOrDefault(REMOVE_PASSIVE_FLAG, FlagState.OFF) != FlagState.OFF;
```

Add import: `import com.nick.landclaims.api.flag.FlagState;`

- [ ] **Step 3f: Sweep for remaining compile errors**

Run: `./gradlew :landclaims-plugin:compileJava 2>&1 | grep -E "error:|\.java:"`

`ClaimFlagService.java`, `ProtectionService.java`, and `SqlClaimRepository.java` will still show errors — those are fixed in A5–A8. For ANY OTHER file that errors here (e.g. an unexpected `.flags()` consumer), apply the same mechanical type change (`Boolean` → `FlagState`, `false` → `FlagState.OFF`). Do not change protection/flag-service/repository logic yet.

> The remaining-error allowlist after this step is exactly: `ClaimFlagService.java`, `ProtectionService.java`, `SqlClaimRepository.java`. If anything else still errors, fix it before moving on.

- [ ] **Step 4: Run the type test**

The full plugin module won't compile until A8, so run the api compile and confirm the only remaining plugin errors are the three allowlisted files:

Run: `./gradlew :landclaims-api:build`
Expected: api builds and its tests (FlagStateTest, FlagKindTest) pass.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor: migrate claim flag value type to FlagState"
```

---

### Task A5: Rewrite `ProtectionService`

**Files:**
- Modify: `landclaims-plugin/src/main/java/com/nick/landclaims/plugin/protection/ProtectionService.java` (full rewrite)
- Test: `landclaims-plugin/src/test/java/com/nick/landclaims/plugin/protection/ProtectionServiceTest.java` (create or extend)

- [ ] **Step 1: Write the failing test**

```java
package com.nick.landclaims.plugin.protection;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nick.landclaims.api.protection.ClaimProtectionResult;
import com.nick.landclaims.plugin.claim.Claim;
import com.nick.landclaims.plugin.claim.ClaimChunk;
import com.nick.landclaims.plugin.claim.ClaimMember;
import com.nick.landclaims.plugin.claim.ClaimRole;
import com.nick.landclaims.plugin.claim.OwnerType;
import com.nick.landclaims.plugin.flag.FlagRegistry;
import com.nick.landclaims.api.flag.FlagState;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProtectionServiceTest {
    private final ProtectionService service = new ProtectionService(FlagRegistry.createDefault());
    private final UUID owner = UUID.randomUUID();
    private final UUID manager = UUID.randomUUID();
    private final UUID member = UUID.randomUUID();
    private final UUID visitor = UUID.randomUUID();

    private Claim claimWith(String flag, FlagState state) {
        return new Claim(
                UUID.randomUUID(), "C", OwnerType.PLAYER, owner, UUID.randomUUID(),
                Set.of(new ClaimChunk(UUID.randomUUID(), 0, 0)),
                Map.of(flag, state),
                Set.of(new ClaimMember(manager, ClaimRole.MANAGER), new ClaimMember(member, ClaimRole.MEMBER)),
                Set.of(), Instant.now(), Instant.now());
    }

    private ClaimProtectionResult check(Claim c, UUID actor, String flag) {
        return service.checkClaimFlag(c, actor, flag);
    }

    @Test
    void playerActionOffAllowsEveryone() {
        Claim c = claimWith("container_access", FlagState.OFF);
        assertEquals(ClaimProtectionResult.ALLOW, check(c, visitor, "container_access"));
    }

    @Test
    void playerActionVisitorsDeniesOnlyVisitor() {
        Claim c = claimWith("container_access", FlagState.VISITORS);
        assertEquals(ClaimProtectionResult.ALLOW, check(c, owner, "container_access"));
        assertEquals(ClaimProtectionResult.ALLOW, check(c, manager, "container_access"));
        assertEquals(ClaimProtectionResult.ALLOW, check(c, member, "container_access"));
        assertEquals(ClaimProtectionResult.DENY_WITH_MESSAGE, check(c, visitor, "container_access"));
        assertEquals(ClaimProtectionResult.DENY_WITH_MESSAGE, check(c, null, "container_access"));
    }

    @Test
    void playerActionAllAllowsOwnerAndManagerOnly() {
        Claim c = claimWith("container_access", FlagState.ALL);
        assertEquals(ClaimProtectionResult.ALLOW, check(c, owner, "container_access"));
        assertEquals(ClaimProtectionResult.ALLOW, check(c, manager, "container_access"));
        assertEquals(ClaimProtectionResult.DENY_WITH_MESSAGE, check(c, member, "container_access"));
        assertEquals(ClaimProtectionResult.DENY_WITH_MESSAGE, check(c, visitor, "container_access"));
    }

    @Test
    void worldEffectIgnoresActorAndOwner() {
        // explosion_damage: OFF = explosions denied, ALL = allowed. ownerExempt=false.
        Claim off = claimWith("explosion_damage", FlagState.OFF);
        assertEquals(ClaimProtectionResult.DENY_WITH_MESSAGE, check(off, owner, "explosion_damage"));
        Claim all = claimWith("explosion_damage", FlagState.ALL);
        assertEquals(ClaimProtectionResult.ALLOW, check(all, owner, "explosion_damage"));
    }

    @Test
    void pistonProtectionInversionPreserved() {
        Claim protectedClaim = claimWith("piston_protection", FlagState.ALL);
        assertEquals(ClaimProtectionResult.DENY_WITH_MESSAGE, check(protectedClaim, null, "piston_protection"));
        Claim unprotected = claimWith("piston_protection", FlagState.OFF);
        assertEquals(ClaimProtectionResult.ALLOW, check(unprotected, null, "piston_protection"));
    }
}
```

- [ ] **Step 2: Run test, verify it fails**

Run: `./gradlew :landclaims-plugin:test --tests "com.nick.landclaims.plugin.protection.ProtectionServiceTest"`
Expected: FAIL to compile (old `ProtectionService` references `defaultValue`, returns boolean logic).

- [ ] **Step 3: Rewrite `ProtectionService`**

Replace the whole file:

```java
package com.nick.landclaims.plugin.protection;

import com.nick.landclaims.api.claim.ClaimView;
import com.nick.landclaims.api.flag.ClaimFlagDefinition;
import com.nick.landclaims.api.flag.FlagKind;
import com.nick.landclaims.api.flag.FlagState;
import com.nick.landclaims.api.protection.ClaimProtectionResult;
import com.nick.landclaims.plugin.claim.Claim;
import com.nick.landclaims.plugin.claim.ClaimRole;
import com.nick.landclaims.plugin.flag.FlagRegistry;
import java.util.Objects;
import java.util.UUID;

public final class ProtectionService {
    private final FlagRegistry flagRegistry;

    public ProtectionService(FlagRegistry flagRegistry) {
        this.flagRegistry = Objects.requireNonNull(flagRegistry, "flagRegistry");
    }

    public ClaimProtectionResult checkClaimFlag(ClaimView claim, UUID actorUuid, String flagKey) {
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(flagKey, "flagKey");

        FlagState state = claim.flags().getOrDefault(flagKey, flagRegistry.defaultState(flagKey));
        ClaimFlagDefinition definition = flagRegistry.definition(flagKey).orElse(null);
        FlagKind kind = definition == null ? FlagKind.PLAYER_ACTION : definition.kind();

        if (kind == FlagKind.WORLD_EFFECT) {
            return worldEffectResult(flagKey, state);
        }
        return playerActionResult(claim, actorUuid, definition, state);
    }

    private ClaimProtectionResult worldEffectResult(String flagKey, FlagState state) {
        boolean enabled = state != FlagState.OFF;
        if ("piston_protection".equals(flagKey)) {
            return enabled ? ClaimProtectionResult.DENY_WITH_MESSAGE : ClaimProtectionResult.ALLOW;
        }
        return enabled ? ClaimProtectionResult.ALLOW : ClaimProtectionResult.DENY_WITH_MESSAGE;
    }

    private ClaimProtectionResult playerActionResult(
            ClaimView claim, UUID actorUuid, ClaimFlagDefinition definition, FlagState state) {
        if (state == FlagState.OFF) {
            return ClaimProtectionResult.ALLOW;
        }
        boolean ownerExempt = definition == null || definition.ownerExempt();
        Relationship relationship = relationship(claim, actorUuid);
        return switch (state) {
            case VISITORS -> relationship == Relationship.VISITOR
                    ? ClaimProtectionResult.DENY_WITH_MESSAGE
                    : ClaimProtectionResult.ALLOW;
            case ALL -> (ownerExempt && relationship.isTrusted())
                    ? ClaimProtectionResult.ALLOW
                    : ClaimProtectionResult.DENY_WITH_MESSAGE;
            default -> ClaimProtectionResult.ALLOW;
        };
    }

    private enum Relationship {
        OWNER, MANAGER, MEMBER, VISITOR;

        boolean isTrusted() {
            return this == OWNER || this == MANAGER;
        }
    }

    private Relationship relationship(ClaimView claim, UUID actorUuid) {
        if (actorUuid == null) {
            return Relationship.VISITOR;
        }
        if (actorUuid.equals(claim.ownerUuid())) {
            return Relationship.OWNER;
        }
        if (!(claim instanceof Claim landClaim)) {
            return Relationship.VISITOR;
        }
        return landClaim.members().stream()
                .filter(member -> member.memberUuid().equals(actorUuid))
                .findFirst()
                .map(member -> member.role() == ClaimRole.MANAGER ? Relationship.MANAGER : Relationship.MEMBER)
                .orElse(Relationship.VISITOR);
    }
}
```

- [ ] **Step 4: Run test, verify it passes**

Run: `./gradlew :landclaims-plugin:test --tests "com.nick.landclaims.plugin.protection.ProtectionServiceTest"`
Expected: PASS once A6/A8 also compile. If `ClaimFlagService`/`SqlClaimRepository` still break the module compile, proceed to A6 then re-run; the test is correct as written.

- [ ] **Step 5: Commit**

```bash
git add landclaims-plugin/src/main/java/com/nick/landclaims/plugin/protection/ProtectionService.java landclaims-plugin/src/test/java/com/nick/landclaims/plugin/protection/ProtectionServiceTest.java
git commit -m "feat: tri-state-aware protection with relationship resolution"
```

---

### Task A6: `ClaimFlagService` set/cycle + `ClaimFlagRow`

**Files:**
- Modify: `landclaims-plugin/src/main/java/com/nick/landclaims/plugin/flag/ClaimFlagRow.java`
- Modify: `landclaims-plugin/src/main/java/com/nick/landclaims/plugin/flag/ClaimFlagService.java`
- Test: `landclaims-plugin/src/test/java/com/nick/landclaims/plugin/flag/ClaimFlagServiceTest.java` (create or extend)

- [ ] **Step 1: Write the failing test**

```java
package com.nick.landclaims.plugin.flag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nick.landclaims.api.flag.FlagState;
import com.nick.landclaims.plugin.claim.Claim;
import com.nick.landclaims.plugin.claim.ClaimChunk;
import com.nick.landclaims.plugin.claim.ClaimIndex;
import com.nick.landclaims.plugin.claim.OwnerType;
import com.nick.landclaims.plugin.storage.ClaimRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClaimFlagServiceTest {
    private final UUID owner = UUID.randomUUID();

    private Claim claim(Map<String, FlagState> flags) {
        return new Claim(UUID.randomUUID(), "C", OwnerType.PLAYER, owner, UUID.randomUUID(),
                Set.of(new ClaimChunk(UUID.randomUUID(), 0, 0)), flags, Set.of(), Set.of(),
                Instant.now(), Instant.now());
    }

    private ClaimFlagService service(List<Claim> saved) {
        ClaimRepository repo = new InMemoryRepo(saved);
        return new ClaimFlagService(repo, new ClaimIndex(), FlagRegistry.createDefault());
    }

    @Test
    void cyclePlayerActionAdvancesOffToVisitors() {
        List<Claim> saved = new ArrayList<>();
        Claim c = claim(Map.of("container_access", FlagState.OFF));
        ClaimFlagResult result = service(saved).cycleFlag(owner, c, "container_access", perm -> true);
        assertTrue(result.allowed());
        assertEquals(FlagState.VISITORS, saved.get(saved.size() - 1).flags().get("container_access"));
    }

    @Test
    void cycleWorldEffectSkipsVisitors() {
        List<Claim> saved = new ArrayList<>();
        Claim c = claim(Map.of("explosion_damage", FlagState.OFF));
        service(saved).cycleFlag(owner, c, "explosion_damage", perm -> true);
        assertEquals(FlagState.ALL, saved.get(saved.size() - 1).flags().get("explosion_damage"));
    }

    @Test
    void setVisitorsOnWorldEffectIsRejected() {
        ClaimFlagResult result = service(new ArrayList<>())
                .setFlagState(owner, claim(Map.of()), "explosion_damage", FlagState.VISITORS, perm -> true);
        assertFalse(result.allowed());
    }

    @Test
    void nextStateComputesWithoutWriting() {
        ClaimFlagService service = service(new ArrayList<>());
        assertEquals(FlagState.VISITORS,
                service.nextState(claim(Map.of("container_access", FlagState.OFF)), "container_access"));
        assertEquals(FlagState.ALL,
                service.nextState(claim(Map.of("explosion_damage", FlagState.OFF)), "explosion_damage"));
    }

    // Minimal in-memory ClaimRepository capturing saved claims.
    private static final class InMemoryRepo implements ClaimRepository {
        private final List<Claim> saved;
        InMemoryRepo(List<Claim> saved) { this.saved = saved; }
        @Override public void saveClaim(Claim claim) { saved.add(claim); }
        @Override public void replaceClaims(Claim c, List<UUID> d) { saved.add(c); }
        @Override public void deleteClaim(UUID id) {}
        @Override public Optional<Claim> findClaimAt(UUID w, int x, int z) { return Optional.empty(); }
        @Override public Optional<Claim> findClaimById(UUID id) { return Optional.empty(); }
        @Override public List<Claim> findClaimsByOwner(OwnerType t, UUID o) { return List.of(); }
        @Override public List<Claim> findAllClaims() { return List.of(); }
    }
}
```

> Before writing, open `ClaimRepository.java` to match the `InMemoryRepo` override list to the actual interface (it currently has the 7 methods shown). `ClaimIndex` has a public no-arg-usable constructor (`new ClaimIndex()` with `load(...)`); confirm against the class.

- [ ] **Step 2: Run test, verify it fails**

Run: `./gradlew :landclaims-plugin:test --tests "com.nick.landclaims.plugin.flag.ClaimFlagServiceTest"`
Expected: FAIL — `cycleFlag`/`setFlagState` not defined.

- [ ] **Step 3a: `ClaimFlagRow`**

Replace its fields `boolean enabled` with `FlagState state` and add `FlagKind kind`:

```java
package com.nick.landclaims.plugin.flag;

import com.nick.landclaims.api.flag.FlagKind;
import com.nick.landclaims.api.flag.FlagState;

public record ClaimFlagRow(
        String key,
        String category,
        String label,
        String description,
        FlagKind kind,
        FlagState state,
        String editPermission
) {
}
```

- [ ] **Step 3b: `ClaimFlagService`**

In `ClaimFlagService.java`:
- Add imports: `import com.nick.landclaims.api.flag.FlagState;`
- Replace `setFlag(...)` with `setFlagState(...)`:

```java
    public ClaimFlagResult setFlagState(
            UUID actorId,
            Claim claim,
            String flagKey,
            FlagState state,
            Predicate<String> permissionCheck
    ) {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(flagKey, "flagKey");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(permissionCheck, "permissionCheck");

        if (!actorId.equals(claim.ownerUuid()) && !isManager(actorId, claim)) {
            return ClaimFlagResult.denied("claim.flag.not-owner");
        }

        ClaimFlagDefinition definition = flagRegistry.definition(flagKey).orElse(null);
        if (definition == null) {
            return ClaimFlagResult.denied("claim.flag.unknown");
        }
        if (!definition.kind().supports(state)) {
            return ClaimFlagResult.denied("claim.flag.invalid-state");
        }
        if (!permissionCheck.test(definition.editPermission())) {
            return ClaimFlagResult.denied("claim.flag.no-permission");
        }

        Map<String, FlagState> flags = new HashMap<>(claim.flags());
        flags.put(definition.key(), state);
        Claim updatedClaim = new Claim(
                claim.id(), claim.name(), claim.owner(), claim.ownerUuid(), claim.worldId(),
                claim.claimChunks(), flags, claim.members(), claim.deniedPlayers(),
                claim.createdAt(), Instant.now());
        claimRepository.saveClaim(updatedClaim);
        claimIndex.replace(updatedClaim);
        return ClaimFlagResult.success();
    }
```

- Replace `toggleFlag(...)` with `cycleFlag(...)`:

```java
    public ClaimFlagResult cycleFlag(
            UUID actorId,
            Claim claim,
            String flagKey,
            Predicate<String> permissionCheck
    ) {
        Objects.requireNonNull(flagKey, "flagKey");
        ClaimFlagDefinition definition = flagRegistry.definition(flagKey).orElse(null);
        if (definition == null) {
            return ClaimFlagResult.denied("claim.flag.unknown");
        }
        FlagState next = nextState(claim, definition.key());
        return setFlagState(actorId, claim, definition.key(), next, permissionCheck);
    }
```

- Add a pure helper used by both `cycleFlag` and the command (to message the resulting state without re-querying the index):

```java
    public FlagState nextState(Claim claim, String flagKey) {
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(flagKey, "flagKey");
        ClaimFlagDefinition definition = flagRegistry.definition(flagKey).orElse(null);
        if (definition == null) {
            return FlagState.OFF;
        }
        FlagState current = claim.flags().getOrDefault(definition.key(), definition.defaultState());
        return definition.kind().next(current);
    }
```

- Update `listFlags(...)` to populate `kind` and `state`:

```java
                .map(definition -> new ClaimFlagRow(
                        definition.key(),
                        definition.category(),
                        definition.label(),
                        definition.description(),
                        definition.kind(),
                        claim.flags().getOrDefault(definition.key(), definition.defaultState()),
                        definition.editPermission()))
```

> `ClaimFlagResult` accessor is `allowed()` and `messageKey()` returns a `String` (not Optional) — used in A9.

- [ ] **Step 4: Run test, verify it passes**

Run: `./gradlew :landclaims-plugin:test --tests "com.nick.landclaims.plugin.flag.ClaimFlagServiceTest"`
Expected: PASS (module must compile — needs A8 for `SqlClaimRepository`; if still red there, finish A7/A8 then re-run).

- [ ] **Step 5: Commit**

```bash
git add landclaims-plugin/src/main/java/com/nick/landclaims/plugin/flag/ClaimFlagRow.java landclaims-plugin/src/main/java/com/nick/landclaims/plugin/flag/ClaimFlagService.java landclaims-plugin/src/test/java/com/nick/landclaims/plugin/flag/ClaimFlagServiceTest.java
git commit -m "feat: flag set/cycle operations over FlagState"
```

---

### Task A7: V3 migration SQL + index

**Files:**
- Create: `landclaims-plugin/src/main/resources/db/migrations/landclaims/V3__claim_flag_states.sql`
- Modify: `landclaims-plugin/src/main/resources/db/migrations/landclaims/migrations.index`

- [ ] **Step 1: Create the migration**

```sql
-- Tri-state flag values. Add a state column and backfill from the legacy enabled flag
-- so existing claims keep their current behavior. The enabled column is retained (now
-- nullable in effect; still written by the plugin for rollback safety).
ALTER TABLE claim_flags ADD COLUMN state TEXT;

UPDATE claim_flags
SET state = CASE
    WHEN flag_key IN ('build','break','interact','container_access','door_access','switch_access','redstone_access')
        THEN CASE WHEN enabled = 1 THEN 'OFF' ELSE 'VISITORS' END
    WHEN flag_key IN ('entity_damage','item_pickup','item_drop','crop_trample')
        THEN CASE WHEN enabled = 1 THEN 'OFF' ELSE 'ALL' END
    WHEN flag_key IN ('piston_protection','fluid_flow','explosion_damage','fire_spread','mob_griefing','remove_hostile_entities','remove_passive_entities')
        THEN CASE WHEN enabled = 1 THEN 'ALL' ELSE 'OFF' END
    ELSE CASE WHEN enabled = 1 THEN 'ALL' ELSE 'OFF' END
END
WHERE state IS NULL;
```

- [ ] **Step 2: Register in the index**

Append to `migrations.index` (after `V2__claim_denied_players.sql`):

```
V3__claim_flag_states.sql
```

- [ ] **Step 3: Commit**

```bash
git add landclaims-plugin/src/main/resources/db/migrations/landclaims/V3__claim_flag_states.sql landclaims-plugin/src/main/resources/db/migrations/landclaims/migrations.index
git commit -m "feat(db): V3 migration backfilling flag state column"
```

---

### Task A8: `SqlClaimRepository` reads/writes `state`

**Files:**
- Modify: `landclaims-plugin/src/main/java/com/nick/landclaims/plugin/storage/sql/SqlClaimRepository.java`
- Test: extend the **existing** `landclaims-plugin/src/test/java/com/nick/landclaims/plugin/storage/sql/SqlClaimRepositoryTest.java` (it already builds a migrated DataSource-backed repository — reuse that exact setup; do not invent a new harness).

- [ ] **Step 1: Write the failing test**

> Open `SqlClaimRepositoryTest.java` first and copy its setup pattern (how it constructs the repository over a migrated in-memory DB). Add this test method into that class, adapting `newRepository()` to whatever the existing helper/field is actually named.

```java
    @Test
    void roundTripsFlagState() {
        SqlClaimRepository repo = newRepository(); // reuse existing harness helper/field
        UUID world = UUID.randomUUID();
        Claim claim = new Claim(UUID.randomUUID(), "C", OwnerType.PLAYER, UUID.randomUUID(), world,
                Set.of(new ClaimChunk(world, 1, 2)),
                Map.of("container_access", FlagState.ALL, "explosion_damage", FlagState.OFF),
                Set.of(), Set.of(), Instant.now(), Instant.now());
        repo.saveClaim(claim);

        Claim loaded = repo.findClaimById(claim.id()).orElseThrow();
        assertEquals(FlagState.ALL, loaded.flags().get("container_access"));
        assertEquals(FlagState.OFF, loaded.flags().get("explosion_damage"));
    }
```

Add `import com.nick.landclaims.api.flag.FlagState;` to the test class. The existing harness already applies `migrations.index` (now including V3 from A7), so the `state` column exists.

- [ ] **Step 2: Run test, verify it fails**

Run: `./gradlew :landclaims-plugin:test --tests "com.nick.landclaims.plugin.storage.sql.SqlClaimRepositoryStateTest"`
Expected: FAIL — repository still writes/reads `enabled` as boolean.

- [ ] **Step 3a: `insertFlags` writes state (+ legacy enabled)**

`claim_flags.enabled` is `NOT NULL`, so keep writing it (derived) alongside `state`:

```java
    private void insertFlags(Connection connection, Claim claim) throws SQLException {
        String sql = "INSERT INTO claim_flags (claim_id, flag_key, enabled, state) VALUES (?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (Map.Entry<String, FlagState> flag : claim.flags().entrySet()) {
                statement.setString(1, claim.id().toString());
                statement.setString(2, flag.getKey());
                statement.setInt(3, flag.getValue() == FlagState.OFF ? 0 : 1);
                statement.setString(4, flag.getValue().name());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }
```

- [ ] **Step 3b: `loadFlags` reads state**

```java
    private Map<String, FlagState> loadFlags(Connection connection, UUID claimId) throws SQLException {
        Map<String, FlagState> flags = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT flag_key, state FROM claim_flags WHERE claim_id = ?"
        )) {
            statement.setString(1, claimId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    flags.put(resultSet.getString("flag_key"), parseState(resultSet.getString("state")));
                }
            }
        }
        return Map.copyOf(flags);
    }
```

- [ ] **Step 3c: `bulkLoadFlags` reads state**

Change its SQL column list and put:

```java
        String sql = "SELECT claim_id, flag_key, state FROM claim_flags WHERE claim_id IN ("
                + inClausePlaceholders(claimIds.size()) + ")";
```
```java
                    result.computeIfAbsent(claimId, key -> new HashMap<>())
                            .put(resultSet.getString("flag_key"), parseState(resultSet.getString("state")));
```

Change the bulk map type to `Map<UUID, Map<String, FlagState>>` and the `mapClaims` local `flagsByClaim` accordingly (and the `getOrDefault(row.id(), Map.of())` line still type-checks).

- [ ] **Step 3d: Add `parseState` helper + import**

Add import `import com.nick.landclaims.api.flag.FlagState;` and:

```java
    private FlagState parseState(String value) {
        if (value == null) {
            return FlagState.OFF;
        }
        try {
            return FlagState.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return FlagState.OFF;
        }
    }
```

Update the `mapClaims` assembly and any `Map<String,Boolean>` references in this file to `Map<String,FlagState>`.

- [ ] **Step 4: Run test, then full build**

Run: `./gradlew :landclaims-plugin:test --tests "com.nick.landclaims.plugin.storage.sql.SqlClaimRepositoryStateTest"`
Expected: PASS.

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL — the whole module compiles now; A5 and A6 tests also green.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(db): persist and load flag state column"
```

---

### Task A9: Command + messages for tri-state

**Files:**
- Modify: `landclaims-plugin/src/main/java/com/nick/landclaims/plugin/command/ClaimsCommand.java`
- Modify: `landclaims-plugin/src/main/resources/messages.yml`
- Test: extend `landclaims-plugin/src/test/java/.../command/` if a `ClaimsCommand` test exists; otherwise rely on the service-level tests above and do a manual verification step.

- [ ] **Step 1: Update messages**

In `messages.yml` under `claim.flag:` replace the boolean-oriented keys:

```yaml
  flag:
    usage: "<red>Usage: /claim flag <list|set|cycle> <flag> [off|visitors|all]"
    not-owner: "<red>Only the claim owner or a manager can edit flags."
    unknown: "<red>Unknown claim flag: <yellow><flag></yellow>"
    no-permission: "<red>You do not have permission to edit <yellow><flag></yellow>."
    invalid-state: "<red>Flag state must be <yellow>off</yellow>, <yellow>visitors</yellow>, or <yellow>all</yellow> (visitors is not valid for this flag)."
    set: "<green>Set <yellow><flag></yellow> to <yellow><state></yellow>."
    cycled: "<green><yellow><flag></yellow> is now <yellow><state></yellow>."
    list-header: "<gold>Claim flags:"
    list-entry: "<gray>- <yellow><label></yellow> <dark_gray>(<flag>, <category>)</dark_gray>: <white><state></white> <dark_gray>- <description></dark_gray>"
```

Under `claim.flag-editor:` update the row to show the next state:

```yaml
  flag-editor:
    title: "<gold>Flags for <yellow><claim_name></yellow>"
    row: "<gray>- <yellow><label></yellow> <dark_gray>(<category>)</dark_gray>: <white><state></white> <green>[Click: <next_state>]</green> <dark_gray>- <description></dark_gray>"
```

- [ ] **Step 2: Update the flag subcommand handling in `ClaimsCommand`**

Locate the `flag` subcommand dispatch (the handler around the existing `set`/`toggle`/`list` for `/claim flag` and the admin `userclaims flag` path; `ADMIN_USERCLAIM_FLAG_SUGGESTIONS` currently lists `list|set|toggle`). Apply:

- Change tab-completion list:

```java
    private static final List<String> ADMIN_USERCLAIM_FLAG_SUGGESTIONS = List.of("list", "set", "cycle");
```

- Where the player flag command parses `set <flag> <true|false>`, parse a `FlagState` instead:

```java
        // /claim flag set <flag> <off|visitors|all>
        FlagState desired;
        try {
            desired = FlagState.valueOf(stateArg.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            player.sendMessage(message("claim.flag.invalid-state"));
            return true;
        }
        ClaimFlagResult result = claimFlagService.setFlagState(
                player.getUniqueId(), claim, flagKey, desired, player::hasPermission);
        if (!result.allowed()) {
            player.sendMessage(message(result.messageKey().isEmpty() ? "claim.flag.unknown" : result.messageKey()));
            return true;
        }
        player.sendMessage(message("claim.flag.set", Map.of("flag", flagKey, "state", desired.name())));
        return true;
```

- Replace the `toggle` branch with `cycle`. Compute the resulting state **before** cycling (the held `claim` snapshot is pre-write), via the `nextState` helper:

```java
        // /claim flag cycle <flag>
        FlagState resulting = claimFlagService.nextState(claim, flagKey);
        ClaimFlagResult result = claimFlagService.cycleFlag(
                player.getUniqueId(), claim, flagKey, player::hasPermission);
        if (!result.allowed()) {
            player.sendMessage(message(result.messageKey().isEmpty() ? "claim.flag.unknown" : result.messageKey()));
            return true;
        }
        player.sendMessage(message("claim.flag.cycled", Map.of(
                "flag", flagKey,
                "state", resulting.name())));
        return true;
```

> Add `import com.nick.landclaims.api.flag.FlagState;`. `nextState` returns the state cycle would apply, so messaging it pre-write is correct (cycle uses the same computation).

- Update the `list` rendering to use `row.state().name()` (from the updated `ClaimFlagRow`).

- [ ] **Step 3: Build + manual smoke**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

Manual (optional, on a test server): `/claim flag set container_access visitors`, `/claim flag cycle container_access`, `/claim flag list` — confirm states display and persist across relog.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "feat: tri-state flag command surface and messages"
```

---

## Phase B — Paper Dialog editor

### Task B1: `ClaimFlagEditorService` + `ClaimFlagEditorRow` tri-state labels

**Files:**
- Modify: `landclaims-plugin/src/main/java/com/nick/landclaims/plugin/ui/ClaimFlagEditorRow.java`
- Modify: `landclaims-plugin/src/main/java/com/nick/landclaims/plugin/ui/ClaimFlagEditorService.java`
- Test: `landclaims-plugin/src/test/java/com/nick/landclaims/plugin/ui/ClaimFlagEditorServiceTest.java` (create or extend)

- [ ] **Step 1: Write the failing test**

```java
package com.nick.landclaims.plugin.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nick.landclaims.api.flag.FlagKind;
import com.nick.landclaims.api.flag.FlagState;
import com.nick.landclaims.plugin.flag.ClaimFlagRow;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClaimFlagEditorServiceTest {
    private final ClaimFlagEditorService service = new ClaimFlagEditorService();

    @Test
    void playerActionRowShowsCurrentAndNextState() {
        ClaimFlagRow row = new ClaimFlagRow("container_access", "Access", "Containers",
                "desc", FlagKind.PLAYER_ACTION, FlagState.VISITORS, "landclaims.flag.container_access");
        ClaimFlagEditor editor = service.buildEditor("Home", List.of(row));
        ClaimFlagEditorRow built = editor.rows().get(0);
        assertEquals("VISITORS", built.stateLabel());
        assertEquals("ALL", built.nextStateLabel());
        assertEquals("/claim flag cycle container_access", built.toggleCommand());
    }

    @Test
    void worldEffectRowCyclesOffToAll() {
        ClaimFlagRow row = new ClaimFlagRow("explosion_damage", "Environment", "Explosion Damage",
                "desc", FlagKind.WORLD_EFFECT, FlagState.OFF, "landclaims.flag.explosion_damage");
        ClaimFlagEditorRow built = service.buildEditor("Home", List.of(row)).rows().get(0);
        assertEquals("OFF", built.stateLabel());
        assertEquals("ALL", built.nextStateLabel());
    }
}
```

- [ ] **Step 2: Run test, verify it fails**

Run: `./gradlew :landclaims-plugin:test --tests "com.nick.landclaims.plugin.ui.ClaimFlagEditorServiceTest"`
Expected: FAIL — `buildEditor` signature/labels operate on boolean.

- [ ] **Step 3a: `ClaimFlagEditorRow`** — keep its fields as-is (`stateLabel`, `nextStateLabel`, `toggleCommand`); no structural change needed.

- [ ] **Step 3b: Rewrite `ClaimFlagEditorService`**

```java
package com.nick.landclaims.plugin.ui;

import com.nick.landclaims.api.flag.FlagState;
import com.nick.landclaims.plugin.flag.ClaimFlagRow;
import java.util.List;
import java.util.Objects;

public final class ClaimFlagEditorService {
    public ClaimFlagEditor buildEditor(String claimName, List<ClaimFlagRow> flags) {
        Objects.requireNonNull(claimName, "claimName");
        Objects.requireNonNull(flags, "flags");

        return new ClaimFlagEditor(
                claimName,
                flags.stream()
                        .map(flag -> {
                            FlagState next = flag.kind().next(flag.state());
                            return new ClaimFlagEditorRow(
                                    flag.key(),
                                    flag.category(),
                                    flag.label(),
                                    flag.description(),
                                    flag.state().name(),
                                    next.name(),
                                    "/claim flag cycle " + flag.key());
                        })
                        .toList());
    }
}
```

- [ ] **Step 4: Run test, verify it passes**

Run: `./gradlew :landclaims-plugin:test --tests "com.nick.landclaims.plugin.ui.ClaimFlagEditorServiceTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add landclaims-plugin/src/main/java/com/nick/landclaims/plugin/ui/ClaimFlagEditorService.java landclaims-plugin/src/test/java/com/nick/landclaims/plugin/ui/ClaimFlagEditorServiceTest.java
git commit -m "feat(ui): tri-state flag editor rows"
```

---

### Task B2: Paper Dialog flag editor + chat fallback

**Files:**
- Modify: `landclaims-plugin/src/main/java/com/nick/landclaims/plugin/ui/DialogService.java`
- Reference: Paper Dialog API docs (`io.papermc.paper.dialog.Dialog`, `io.papermc.paper.registry.data.dialog.DialogBase`, `ActionButton`, `DialogAction`). Confirm exact factory names against paper-api 26.1.2 source — the API stabilized post-1.21.6 and method names may differ slightly from older snapshots.

- [ ] **Step 1: Add a `prefer-dialogs` flag to `DialogService`**

`DialogService` is constructed in `LandClaimsPlugin`. Pass the `config.yml` `prefer-dialogs` value (already read elsewhere — grep `prefer-dialogs`) into a new constructor field `boolean preferDialogs`. If the constructor changes, update the single construction site in `LandClaimsPlugin`.

- [ ] **Step 2: Implement the dialog editor**

Replace `openFlagEditor` so it builds a real Dialog when `preferDialogs` and the runtime supports it, else delegates to the existing chat rendering (extract the current chat body into `openFlagEditorChat`).

```java
    public void openFlagEditor(Player player, ClaimFlagEditor editor, MessageService messageService) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(editor, "editor");
        Objects.requireNonNull(messageService, "messageService");

        if (preferDialogs && tryOpenDialog(player, editor)) {
            return;
        }
        openFlagEditorChat(player, editor, messageService);
    }
```

`tryOpenDialog` builds a Dialog with one `ActionButton` per `editor.rows()`, button label `"<label> — <stateLabel>"`, tooltip = description, click action running `/claim flag cycle <key>` (the row's `toggleCommand`). On any `LinkageError`/`NoClassDefFoundError`/`NoSuchMethodError` (server without Dialog support) return `false` so the caller falls back to chat.

```java
    private boolean tryOpenDialog(Player player, ClaimFlagEditor editor) {
        try {
            // Build using io.papermc.paper.dialog.Dialog + DialogBase with one ActionButton
            // per flag row. Each button's action runs the row.toggleCommand() and the client
            // reopens the dialog. Group buttons in the order provided (already category-sorted).
            // player.showDialog(dialog);
            return buildAndShowDialog(player, editor);
        } catch (LinkageError | RuntimeException error) {
            return false;
        }
    }
```

> `buildAndShowDialog` contains the version-specific Paper Dialog construction. Implement it against the paper-api 26.1.2 Dialog API: create `DialogBase` with the title `Flags for <claimName>`, a body, and an `ActionButton` list mapped from `editor.rows()` where each button uses `DialogAction.staticAction(ClickEvent.runCommand(row.toggleCommand()))` (or the current equivalent), then `player.showDialog(Dialog.create(...))`. Keep all Paper Dialog imports confined to `DialogService`.

- [ ] **Step 3: Build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Manual verification (test server)**

With `prefer-dialogs: true`: open the claim menu → flags → confirm a popup dialog with one button per flag; clicking a button cycles its state and reopens the dialog. Set `prefer-dialogs: false` → confirm the clickable-chat editor renders the same states and the cycle command works.

- [ ] **Step 5: Commit**

```bash
git add landclaims-plugin/src/main/java/com/nick/landclaims/plugin/ui/DialogService.java landclaims-plugin/src/main/java/com/nick/landclaims/plugin/LandClaimsPlugin.java
git commit -m "feat(ui): Paper Dialog flag editor with chat fallback"
```

---

## Final verification

- [ ] Run the full build and test suite:

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL, all tests green.

- [ ] Confirm spec coverage: tri-state model (A1–A4), per-flag kind/ownerExempt (A3), protection matrix incl. manager-as-owner and world-effect owner-binding (A5), migration preserving behavior (A7–A8), command surface (A9), Dialog editor + fallback (B1–B2).

- [ ] Open a PR from `feature/tristate-flags` once Phase A (and optionally Phase B) is complete.
