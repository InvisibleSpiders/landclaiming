# Block Claims Phase 1 Design

Date: 2026-06-15

Repo: HavenClaims

## Purpose

Replace the chunk-based claim system with block-exact rectangular claims. Players select two block corners with the claim tool; the resulting X/Z rectangle is stored and protected at block precision. Claim allowances, costs, and accrual all use block area (claim blocks) rather than chunk counts.

This phase delivers six player-facing improvements:

1. Claim tool highlights the selected block rectangle, not full chunks.
2. Selection messages read "Position 1 set." and "Position 2 set. X claim blocks required."
3. Buffer distance is configured in blocks, not chunks.
4. `allow-irregular-claims` is removed; rectangular block claims replace irregular chunk sets.
5. `default-claim-limit` is replaced by `starting-claim-blocks`.
6. Over-limit prompts read "You're short by X claim blocks. Purchase missing blocks? [YES] [NO]" with clickable chat text.

Block-exact Y-axis protection (depth upgrades) is out of scope and handled in a future phase.

## Data Model

### `ClaimRegion`

New record replacing `Set<ClaimChunk>` as the spatial representation of a claim.

```java
record ClaimRegion(UUID worldId, int minX, int minZ, int maxX, int maxZ) {
    int area() { return (maxX - minX + 1) * (maxZ - minZ + 1); }
    boolean containsBlock(int blockX, int blockZ) {
        return blockX >= minX && blockX <= maxX && blockZ >= minZ && blockZ <= maxZ;
    }
    Set<ClaimChunk> overlappingChunks() { /* iterate chunk range */ }
}
```

### `BlockPos`

Transient selection record used only inside `SelectionService`.

```java
record BlockPos(UUID worldId, int blockX, int blockZ) {}
```

### `Claim`

`Set<ClaimChunk> claimChunks` field removed. `ClaimRegion region` added. All area and limit calculations use `region.area()`. `ClaimIndex` population uses `region.overlappingChunks()`.

### `ClaimIndex`

Interface unchanged (`findAt(ClaimChunk)`, `findAll()`). Internally still `Map<ClaimChunk, Claim>`, populated from `claim.region().overlappingChunks()` on add/replace. Callers needing block-exact bounds access `claim.region()` after chunk lookup.

## Storage

### V5 Migration

```sql
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

No live data exists, so no rollback path is required. Fresh installs produce correct schema directly.

### `SqlClaimRepository`

- `saveClaim()` writes one row to `claim_block_regions`.
- `loadAll()` joins `claims` with `claim_block_regions` and constructs `Claim` with `ClaimRegion`.
- `replaceClaims()` updates the single region row.
- All chunk-related SQL removed.

### `HavenClaimsLimitService` API

Methods renamed for block semantics:

| Old | New |
|---|---|
| `getLimit(UUID)` | `getBlockLimit(UUID)` |
| `setLimit(UUID, int)` | `setBlockLimit(UUID, int)` |
| `addChunks(UUID, int)` | `addBlocks(UUID, int)` |
| `removeChunks(UUID, int)` | `removeBlocks(UUID, int)` |

`LimitService.defaultLimit` is now in blocks. Default: `500` (configurable via `limits.starting-claim-blocks`).

## Selection & Tool

### `SelectionService`

- `firstCorners`: `Map<UUID, BlockPos>` (was `Map<UUID, ClaimChunk>`)
- `completedSelections`: `Map<UUID, ClaimRegion>` (was `Map<UUID, Set<ClaimChunk>>`)
- `select(Player, Block)` records the clicked block's exact X/Z; does not snap to chunk.
- Returns `Optional<ClaimRegion>` on second click: `(worldId, min(x1,x2), min(z1,z2), max(x1,x2), max(z1,z2))`.

### `ClaimToolListener`

- First click → send "Position 1 set." → show 1-block point highlight at the clicked block.
- Second click → compute `ClaimRegion` → send "Position 2 set. `<area>` claim blocks required." → show exact block rectangle border.

### `ClaimService`

- `blockRectangle(BlockPos p1, BlockPos p2) → ClaimRegion` replaces `expandRectangle()`.
- `isWithinBlockBuffer(ClaimRegion proposed, ClaimRegion existing, int bufferBlocks) → boolean` — nearest-edge Chebyshev distance in blocks between the two regions.

### `ClaimCreationService`

- Accepts `ClaimRegion` instead of `Set<ClaimChunk>`.
- Buffer validation uses `isWithinBlockBuffer()`.

## Protection

### Block-Exact Check

1. Chunk lookup: `claimIndex.findAt(new ClaimChunk(worldId, blockX >> 4, blockZ >> 4))` as before.
2. If a claim is found: additionally verify `claim.region().containsBlock(blockX, blockZ)`.
3. Y-axis: unchecked in this phase (full-height protection).

`ProtectionService` and `ProtectionListener` updated to apply the block-exact check. The chunk-spatial index remains the fast first pass; the region check is a cheap integer comparison.

## Visuals

### `ChunkBorderPlanner`

New overload:

```java
static ChunkBorderPlan planRectangle(ClaimRegion region, ChunkGroundHeightProvider heightProvider,
                                     BorderColor color, int durationTicks)
```

Draws exactly four edges of the block rectangle. No interior chunk boundaries rendered.

### `ChunkBorderVisualService`

New overload `showSelection(Player, ClaimRegion, BorderColor)` backed by `planRectangle`. Existing `Set<ClaimChunk>` overload kept for `/claim viewborder` on existing claims (draws the region rectangle).

## Limits & Costs

### `ClaimCostQuote`

All fields renamed from `*Chunks` to `*Blocks`: `allowedBlocks`, `existingBlocks`, `selectedBlocks`, `proposedTotalBlocks`, `overageBlocks`, `cost`.

### `ClaimCostService`

`quotePlayerClaim()` computes existing block area as the sum of `claim.region().area()` across the player's claims. Selected area = `selectedRegion.area()`.

### `ClaimCostConfig`

Exponential pricing mode removed. Flat per-block pricing only.

| Key | Default |
|---|---|
| `limits.over-limit.enabled` | `true` |
| `limits.over-limit.flat-cost-per-block` | `0.10` |
| `limits.over-limit.confirm-timeout-seconds` | `60` |

`ClaimCostMessageService` placeholder names updated from `*_chunks` to `*_blocks`.

## Over-Limit UX

When `/claim create` is run and `overageBlocks > 0`:

1. If `over-limit.enabled: false` → deny with existing message.
2. If enabled → send clickable chat prompt:
   ```
   You're short by 150 claim blocks. Purchase missing blocks? [YES] [NO]
   ```
   - `[YES]` click event runs `/claim confirm-purchase`
   - `[NO]` click event runs `/claim cancel`
3. A `PendingOverLimitPurchase` is stored in memory per player, containing the pending `ClaimRegion`, computed cost, and expiry timestamp.
4. `/claim confirm-purchase` — validates the pending purchase is not expired, charges the player, creates the claim, clears the pending entry.
5. Pending entry expires after `confirm-timeout-seconds`. Expired attempts send "Purchase confirmation expired."

No Dialog API used for this prompt. Clickable chat text only.

## Block Accrual

### `BlockAccrualService`

Repeating `BukkitRunnable` that runs every `accrual.interval-seconds`. On each tick:

1. Iterate all online players.
2. Check AFK status via `AfkDetector.isAfk(UUID)`.
3. Grant blocks:
   - Not AFK: grant `blocks-per-interval`.
   - AFK + mode `reduced`: grant `floor(blocks-per-interval * rate-multiplier)`.
   - AFK + mode `zero`: grant nothing.
4. If `max-blocks > 0`: cap the player's total at `max-blocks`.

### `AfkDetector`

```java
interface AfkDetector {
    boolean isAfk(UUID playerId);
}
```

- `HavenCoreAfkDetector` — queries HavenCore via `ServicesManager`.
- `NoopAfkDetector` — returns `false` (full rate always); used when HavenCore is absent.

### Config

```yaml
limits:
  accrual:
    enabled: true
    blocks-per-interval: 10
    interval-seconds: 60
    max-blocks: 50000
    afk:
      mode: reduced       # reduced or zero
      rate-multiplier: 0.5
```

## Config Changes

Full `config.yml` diff:

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
      mode: reduced
      rate-multiplier: 0.5
  over-limit:
    enabled: true
    flat-cost-per-block: 0.10
    confirm-timeout-seconds: 60
```

## Message Changes

```yaml
claim:
  tool:
    first-corner-selected: "<yellow>Position 1 set."
    selection-complete: "<green>Position 2 set. <yellow><area></yellow> claim blocks required."
  blocked-near-other: "<red>You cannot claim within <yellow><distance></yellow> blocks of another player's land."
  over-limit-prompt: "<yellow>You're short by <red><shortage></red> claim blocks. Purchase missing blocks? <green>[YES]</green> <red>[NO]</red>"
  over-limit-expired: "<red>Purchase confirmation expired."
  over-limit-confirmed: "<green>Charged <yellow><cost></yellow>. Claim created."
  over-limit-cost: "<yellow>This claim exceeds your allowance by <over_blocks> blocks and will cost <green><cost></green>."
```

All existing `*_chunks` / `chunk_count` placeholders renamed to `*_blocks` / `block_count`.

## Components Touched

| Component | Change |
|---|---|
| `ClaimRegion` | New |
| `BlockPos` | New |
| `BlockAccrualService` | New |
| `AfkDetector` / `HavenCoreAfkDetector` / `NoopAfkDetector` | New |
| `PendingOverLimitPurchase` | New |
| `Claim` | Remove `claimChunks`, add `region` |
| `ClaimIndex` | Populate from `region.overlappingChunks()` |
| `SelectionService` | Block-coord corners, `ClaimRegion` result |
| `ClaimService` | `blockRectangle()`, `isWithinBlockBuffer()` |
| `ClaimCreationService` | Accept `ClaimRegion`, block buffer |
| `ClaimCostQuote` | Rename `*Chunks` → `*Blocks` |
| `ClaimCostService` | Block-area totals |
| `ClaimCostConfig` | Flat-per-block only, `confirm-timeout-seconds` |
| `ClaimCostMessageService` | Updated placeholders |
| `LimitService` | `defaultLimit` in blocks |
| `HavenClaimsLimitService` | Renamed API methods |
| `SqlClaimRepository` | `claim_block_regions` table |
| `ChunkBorderPlanner` | `planRectangle()` overload |
| `ChunkBorderVisualService` | `ClaimRegion` overload |
| `ClaimToolListener` | Block selection, new messages, over-limit prompt |
| `ProtectionService` / `ProtectionListener` | Block-exact containment check |
| `ClaimsCommand` | `/claim confirm-purchase` sub-command |
| `config.yml` | Renames, removals, new accrual section |
| `messages.yml` | Updated keys and placeholders |
| V5 migration SQL | Drop chunks, create regions |

## Testing

- `ClaimRegion`: area, containsBlock edge cases, overlappingChunks covers all touching chunks
- `SelectionService`: block corners snap to exact coords, cross-world resets correctly
- `ClaimService.blockRectangle()`: corners in any order produce same region
- `ClaimService.isWithinBlockBuffer()`: edge-to-edge distance, adjacent = 0, diagonal
- `ClaimCreationService`: block buffer validation accepts own claims, rejects others within range
- `ClaimIndex`: adding block-region claim indexes all overlapping chunks; exact-edge chunks included
- `ProtectionService`: block inside region is protected; block on exact boundary is protected; block outside region in same chunk is not protected
- `ClaimCostService`: existing area sums all player regions; overage computes correctly
- `ClaimCostConfig`: flat-per-block pricing; over-limit disabled returns 0
- `BlockAccrualService`: grants correct amount; AFK reduced rate floors; AFK zero grants nothing; max-blocks cap respected
- `AfkDetector`: HavenCore absent falls back to noop
- Over-limit flow: pending entry stored on prompt; confirm before expiry creates claim; confirm after expiry rejects; NO clears pending
- `SqlClaimRepository`: save/load round-trip preserves all region fields
- V5 migration: produces correct schema on a fresh DB
