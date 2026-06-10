# Claim Creation Flow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first playable claim loop: select chunks, create a saved claim, clear pending selections, and protect claimed chunks immediately.

**Architecture:** Keep the flow split into focused services. `SelectionService` owns pending selections, `ClaimCreationService` validates and creates claims, `ClaimIndex` powers fast protection lookups, and `ClaimRepository` persists claims.

**Tech Stack:** Java 25, Paper API 26.1.2, JUnit 5, AssertJ, Mockito, SQLite JDBC, Gradle Shadow.

---

## File Structure

- Modify `landclaims-plugin/src/main/java/com/nick/landclaims/plugin/selection/SelectionService.java` for completed selections that persist until consumed or cleared.
- Create `landclaims-plugin/src/main/java/com/nick/landclaims/plugin/selection/DoubleCrouchClearService.java` for tick-window clear detection.
- Create `landclaims-plugin/src/main/java/com/nick/landclaims/plugin/claim/ClaimIndex.java` for live lookup by chunk.
- Create `landclaims-plugin/src/main/java/com/nick/landclaims/plugin/claim/ClaimCreationService.java` for validation and creation.
- Modify `landclaims-plugin/src/main/java/com/nick/landclaims/plugin/tool/ClaimToolService.java` for reading and spending charges.
- Modify `landclaims-plugin/src/main/java/com/nick/landclaims/plugin/storage/sql/SqlClaimRepository.java` for save/load methods.
- Modify `landclaims-plugin/src/main/java/com/nick/landclaims/plugin/listener/ClaimToolListener.java` for clear gestures.
- Modify `landclaims-plugin/src/main/java/com/nick/landclaims/plugin/listener/ProtectionListener.java` to use `ClaimIndex`.
- Modify `landclaims-plugin/src/main/java/com/nick/landclaims/plugin/command/ClaimsCommand.java` for `create`, `cancel`, and `info`.
- Modify `landclaims-plugin/src/main/java/com/nick/landclaims/plugin/LandClaimsPlugin.java` for startup wiring.
- Modify `landclaims-plugin/src/main/resources/config.yml` for claim name and selection clear settings.

---

### Task 1: Pending Selections

- [ ] Add tests in `SelectionServiceTest` proving a completed selection remains visible through `pendingSelection(UUID)` until `consumeSelection(UUID)` or `clear(UUID)` removes it.
- [ ] Run `./gradlew.bat :landclaims-plugin:test --tests com.nick.landclaims.plugin.selection.SelectionServiceTest` and confirm the new tests fail because the API is missing.
- [ ] Add `Map<UUID, Set<ClaimChunk>> completedSelections` to `SelectionService`.
- [ ] Add `pendingSelection(UUID)`, `consumeSelection(UUID)`, and `clear(UUID)`.
- [ ] Store the expanded rectangle in `completedSelections` when the second corner is selected.
- [ ] Update `clear(Player)` to delegate to `clear(player.getUniqueId())`.
- [ ] Rerun the targeted test and commit with `git commit -m "Store pending claim selections"`.

### Task 2: Double-Crouch Clear Service

- [ ] Add `DoubleCrouchClearServiceTest` covering a second crouch at exactly `80` ticks returning true and a second crouch at `81` ticks returning false.
- [ ] Run `./gradlew.bat :landclaims-plugin:test --tests com.nick.landclaims.plugin.selection.DoubleCrouchClearServiceTest` and confirm it fails because the service is missing.
- [ ] Create `DoubleCrouchClearService` with constructor `DoubleCrouchClearService(int windowTicks, LongSupplier currentTickSupplier)`.
- [ ] Implement `recordCrouch(UUID)` with a `Map<UUID, Long>` of last crouch ticks.
- [ ] Rerun the targeted test and commit with `git commit -m "Add double crouch selection clearing"`.

### Task 3: Live Claim Index

- [ ] Add `ClaimIndexTest` proving `add(Claim)`, `load(Collection<Claim>)`, `findAt(ClaimChunk)`, and `findAll()` work.
- [ ] Run `./gradlew.bat :landclaims-plugin:test --tests com.nick.landclaims.plugin.claim.ClaimIndexTest` and confirm it fails because `ClaimIndex` is missing.
- [ ] Create `ClaimIndex` backed by `Map<ClaimChunk, Claim>`.
- [ ] Rerun the targeted test and commit with `git commit -m "Add live claim index"`.

### Task 4: SQL Repository

- [ ] Extend `SqlClaimRepositoryTest` to save a claim with two chunks and two flags, then load it with `findClaimAt`, `findClaimById`, `findClaimsByOwner`, and `findAllClaims`.
- [ ] Run `./gradlew.bat :landclaims-plugin:test --tests com.nick.landclaims.plugin.storage.sql.SqlClaimRepositoryTest` and confirm it fails with `UnsupportedOperationException`.
- [ ] Implement `saveClaim(Claim)` in a transaction by replacing existing claim rows, inserting the claim, chunks, and flags.
- [ ] Implement read methods with prepared statements and helpers for chunk/flag loading.
- [ ] Rerun the targeted test and commit with `git commit -m "Implement SQL claim repository"`.

### Task 5: Tool Charge Spending

- [ ] Add `ClaimToolServiceTest` proving `currentCharges(ItemStack)` reads charges and `spendCharges(ItemStack, int)` spends only when enough charges remain.
- [ ] Run `./gradlew.bat :landclaims-plugin:test --tests com.nick.landclaims.plugin.tool.ClaimToolServiceTest` and confirm the new APIs are missing.
- [ ] Add `currentCharges`, `maxCharges`, and `spendCharges`.
- [ ] Extract lore rendering so charge counts update after spending.
- [ ] Rerun the targeted test and commit with `git commit -m "Spend claim tool charges"`.

### Task 6: Claim Creation Service

- [ ] Add `ClaimCreationServiceTest` covering success, invalid name, overlap, player buffer, and admin buffer rejection.
- [ ] Run `./gradlew.bat :landclaims-plugin:test --tests com.nick.landclaims.plugin.claim.ClaimCreationServiceTest` and confirm it fails because the service is missing.
- [ ] Create `ClaimCreationService` using `ClaimRepository`, `ClaimIndex`, `ClaimService`, and `FlagRegistry`.
- [ ] Implement `createPlayerClaim(UUID ownerUuid, String name, Set<ClaimChunk> chunks)`.
- [ ] Validate names, empty selections, overlaps, player buffer, and admin buffer.
- [ ] On success, create a `Claim` with default flags, save it, add it to the index, and return `ClaimValidationResult.allowed()`.
- [ ] Rerun the targeted test and commit with `git commit -m "Create claims from selections"`.

### Task 7: Commands And Clear Gestures

- [ ] Add config defaults for `claiming.max-name-length`, `selection.clear-on-tool-switch`, and `selection.double-crouch-clear.window-ticks: 80`.
- [ ] Add listener tests for clearing when switching away from a claim tool.
- [ ] Run `./gradlew.bat :landclaims-plugin:test --tests com.nick.landclaims.plugin.listener.ClaimToolListenerTest` and confirm the helper is missing.
- [ ] Extend `ClaimToolListener` with `PlayerItemHeldEvent` and `PlayerToggleSneakEvent`.
- [ ] Update `ClaimsCommand` to support `/claim create <name>`, `/claim cancel`, and `/claim info`.
- [ ] Ensure `/claim create` spends charges only after `ClaimCreationService` succeeds and then consumes the selection.
- [ ] Rerun targeted listener tests and commit with `git commit -m "Add claim commands and selection clearing"`.

### Task 8: Startup Wiring And Protection

- [ ] Update `ProtectionListenerTest` to pass a `ClaimIndex` and assert indexed locked claims deny non-owners.
- [ ] Run `./gradlew.bat :landclaims-plugin:test --tests com.nick.landclaims.plugin.listener.ProtectionListenerTest` and confirm constructor/API failure.
- [ ] Update `ProtectionListener` to use `ClaimIndex` instead of an internal map.
- [ ] Wire `SqlClaimRepository`, `ClaimIndex`, `ClaimCreationService`, `DoubleCrouchClearService`, listeners, and command in `LandClaimsPlugin`.
- [ ] Load existing claims into the index during startup.
- [ ] Rerun targeted protection tests and commit with `git commit -m "Wire claims into protection"`.

### Task 9: Verification And PR

- [ ] Run `./gradlew.bat build` and confirm `BUILD SUCCESSFUL`.
- [ ] Confirm `landclaims-plugin/build/libs/LandClaims-1.0.0-SNAPSHOT.jar` exists.
- [ ] Run `git status -sb` and confirm only local artifacts are untracked.
- [ ] Push `feat/claim-creation-flow`.
- [ ] Open a PR against `master`.
- [ ] Watch GitHub Actions and confirm the `LandClaims` artifact uploads.

---

## Self-Review

- Spec coverage: persistent selections, command creation, cancel/info, 80-tick double crouch, tool-switch clearing, storage, indexing, and protection are covered.
- Stub scan: no unresolved markers or unspecified implementation steps remain.
- Type consistency: services and methods are introduced before later tasks depend on them.
