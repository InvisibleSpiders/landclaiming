# Block Claims and Claim Depth Upgrades Design

Date: 2026-06-14

Repos: LandClaims, HavenVault

## Purpose

LandClaims currently stores and protects claims as sets of chunks. That makes selection simple, but it also makes the player experience coarse: claims are always 16x16 columns, claim limits are expressed in chunks, and HavenVault upgrades can only sell more chunks.

The next model should use GriefPrevention-style block claims: each claim is a full-height or configured-height 2D rectangle in X/Z space, with per-claim vertical protection bounds. HavenVault should continue to own purchase flows, pricing, requirements, and dialogs, while LandClaims owns claim geometry, protection checks, storage, migrations, and the API that applies claim changes.

## Goals

1. Replace chunk-based claim ownership with block-area 2D rectangles.
2. Preserve simple player UX: two-corner selection, rectangular claim, protection from a lower Y to an upper Y.
3. Let server config define the default vertical protection range.
4. Let individual claims upgrade their protected depth through HavenVault.
5. Convert player claim allowance from chunks to claim blocks.
6. Keep protection lookups fast by maintaining an internal chunk-spatial index even though storage is block-based.
7. Migrate existing chunk claims without silently losing player-owned land.

## Non-Goals

- No arbitrary polygon claims.
- No player-managed 3D cuboids in the first block-claim release.
- No HavenVault-side claim geometry logic.
- No per-world pricing formulas in the first pass unless already supported by HavenVault config patterns.

## Claim Model

LandClaims should store each claim as one or more rectangular regions. A region is full-width in X/Z and protected only inside its configured Y bounds.

Recommended region fields:

- `claim_id`
- `world_id`
- `min_x`
- `min_z`
- `max_x`
- `max_z`
- `protected_min_y`
- `protected_max_y`

Coordinates are inclusive block coordinates. A single simple claim has one region. Multiple regions are reserved for migration and future advanced cases, but new player-created claims should start as one rectangle.

The public API should stop exposing only chunk views for new integrations. It can keep legacy chunk views temporarily for compatibility, but new API consumers should use block regions and claim area.

## Vertical Protection

Config defines the default vertical bounds for new claims. Per-world overrides should be supported because Overworld, Nether, End, and resource worlds often need different policy.

Example:

```yaml
claims:
  vertical-protection:
    default:
      min-y: 32
      max-y: world_max
    worlds:
      world_nether:
        min-y: world_min
        max-y: world_max
      resource_world:
        enabled: false
```

`world_min` and `world_max` resolve from Bukkit world bounds. If `enabled: false`, player claims cannot be created in that world unless admin bypass logic later allows it.

Protection checks should be:

```text
protected if:
  same world
  x between min_x and max_x
  z between min_z and max_z
  y between protected_min_y and protected_max_y
```

This allows public deep mining below a server-chosen Y while still protecting bases, farms, storage, and surface builds.

## Claim Allowance

The allowance unit becomes claim blocks, meaning X/Z area:

```text
area = (max_x - min_x + 1) * (max_z - min_z + 1)
```

Chunk limits migrate by multiplying by 256:

- `1 chunk = 256 claim blocks`
- `10 chunks = 2,560 claim blocks`
- `20 chunks = 5,120 claim blocks`

LandClaims should expose a renamed allowance API for new consumers:

```java
interface LandClaimsAllowanceService {
    int getBlockLimit(UUID playerId);
    void setBlockLimit(UUID playerId, int blocks);
    void addBlocks(UUID playerId, int blocks);
    void removeBlocks(UUID playerId, int blocks);
}
```

The current `LandClaimsLimitService` can remain as a compatibility adapter during migration, but HavenVault should move to the block allowance API when claim-block upgrades are implemented.

## Per-Claim Depth Upgrades

LandClaims should expose a claim upgrade service for HavenVault:

```java
interface LandClaimsUpgradeService {
    List<ClaimUpgradeTarget> getUpgradeableClaims(UUID playerId);
    Optional<ClaimVerticalProtection> getVerticalProtection(UUID claimId);
    ClaimUpgradeResult expandClaimDepth(UUID playerId, UUID claimId, int blocksDown);
    ClaimUpgradeResult setClaimDepth(UUID playerId, UUID claimId, int protectedMinY);
}
```

LandClaims validates ownership/manager rights, world bounds, existing claim state, and whether the requested depth is actually an upgrade. HavenVault handles price, requirements, payment, refund-on-failure, and dialogs.

Recommended DTOs:

```java
record ClaimUpgradeTarget(UUID claimId, String name, UUID worldId, int areaBlocks,
                          int protectedMinY, int protectedMaxY) {}

record ClaimVerticalProtection(UUID claimId, int protectedMinY, int protectedMaxY,
                               int worldMinY, int worldMaxY) {}
```

Depth upgrades should never expand beyond the world's minimum Y. A claim already at world bottom should appear as owned or unavailable in HavenVault.

## HavenVault Flow

HavenVault should split claim upgrades into two categories:

1. **Claim Block Allowance**
   - Player-wide upgrades.
   - Replaces `+N chunks` with `+N claim blocks`.
   - Uses `LandClaimsAllowanceService`.

2. **Selected Claim Depth**
   - Per-claim upgrades.
   - Uses `LandClaimsUpgradeService`.
   - Requires a selected claim ID.

Suggested dialog paths:

- `/vault upgrades` shows normal bank/vault upgrades and player-wide claim block allowance upgrades.
- LandClaims claim menu shows `Upgrade Claim`.
- `Upgrade Claim` opens HavenVault with `claim_id` context.
- HavenVault selected-claim dialog shows the current depth and configured depth upgrade buttons.

Example buttons:

- `Protect 16 blocks deeper - 500`
- `Protect to Y0 - 1,250`
- `Protect to world bottom - 3,000`

HavenVault should continue to use its existing locked/available button styling. A depth button is locked when the player cannot afford it, when LandClaims is absent, when the claim ID is invalid, or when the claim is already at or beyond the target depth.

## Config Shape

HavenVault claim upgrade config should evolve from chunk wording to allowance/depth wording:

```yaml
claim-upgrades:
  enabled: true
  allowance:
    currency: money
    base-cost: 500.0
    cost-multiplier: 1.1
    default-limit-blocks: 2560
    bulk-options:
      - blocks: 500
        discount: 0
      - blocks: 2500
        discount: 0.10
  depth:
    currency: money
    options:
      - id: deeper_16
        label: "Protect 16 blocks deeper"
        blocks-down: 16
        cost: 500.0
      - id: to_y0
        label: "Protect to Y0"
        target-min-y: 0
        cost: 1250.0
      - id: to_world_bottom
        label: "Protect to world bottom"
        target-min-y: world_min
        cost: 3000.0
```

This separates player-wide limit economics from per-claim depth economics and avoids making every claim depth purchase scale with total player allowance.

## Migration

LandClaims migration should convert existing chunk claims to rectangular regions.

For each existing claim:

1. Group contiguous chunks into rectangles where possible.
2. If all chunks form one rectangle, create one block region covering the exact chunk area:
   - `min_x = min_chunk_x * 16`
   - `max_x = max_chunk_x * 16 + 15`
   - same for Z
3. If chunks are irregular, create multiple block regions rather than one large bounding rectangle. This avoids silently granting land that was not previously claimed.
4. Set `protected_min_y` and `protected_max_y` from migration defaults.
5. Migrate player limits to claim-block limits by multiplying by 256.

Irregular multi-region claims can remain as migrated legacy claims. New creation can still require one rectangular region until a future multi-region UX exists.

## Protection and Indexing

Storage should be block-region based. Runtime lookup should maintain an index keyed by world and chunk coordinate:

```text
world_id -> chunk_x/chunk_z -> claim region ids that overlap that chunk
```

When checking a block location, LandClaims first gets candidate regions from the chunk index, then checks exact X/Z/Y bounds. This keeps common protection checks close to current chunk lookup performance while allowing block-precise boundaries.

## Visuals and Selection

Selection remains two-corner. The claim tool should select block coordinates instead of chunk coordinates.

Visual border behavior:

- Preview exact X/Z rectangle border.
- Continue to draw at ground level by sampling terrain.
- For large claims, cap or batch display entities if needed.
- Existing `/claim viewborder` should show the exact block rectangle for the current or selected claim.

The UI should display:

- Area in claim blocks.
- Current allowance and remaining blocks.
- Protected depth range, such as `Y32 to Y320`.

## Compatibility

Short-term compatibility:

- Keep current chunk APIs and chunk commands where possible.
- Mark chunk limit API as legacy once block allowance API exists.
- HavenVault can support both APIs during a transition:
  - Prefer block allowance/depth services.
  - Fall back to chunk limit service only on older LandClaims.

Long-term compatibility:

- Remove chunk-specific public API in a major version only after HavenVault and other integrations have migrated.

## Testing

LandClaims tests:

- Block region overlap and containment.
- Protection checks respect X/Z and Y bounds.
- Deep mining below `protected_min_y` is allowed.
- Claim creation charges exact block area.
- Existing chunk claims migrate to exact block rectangles.
- Irregular chunk claims migrate to multiple regions without expanded ownership.
- Spatial index returns candidates correctly at claim edges.
- Vertical depth upgrade rejects non-owner, invalid claim, and already-maxed claim.

HavenVault tests:

- Claim allowance pricing uses blocks, not chunks.
- Claim depth purchase shows locked/available states consistently.
- Purchase calls LandClaims upgrade service with the selected claim ID.
- Payment is refunded if LandClaims upgrade application fails.
- LandClaims absent hides or disables claim upgrade sections cleanly.
- Existing chunk upgrade config migrates or produces a clear admin warning.

## Rollout Plan

1. LandClaims API and data model design PR.
2. LandClaims block-region storage and migration PR.
3. LandClaims protection, selection, visuals, and menus PR.
4. HavenVault claim-block allowance upgrade PR.
5. HavenVault per-claim depth upgrade PR.
6. LandClaims `Upgrade Claim` routing button PR.

Each PR should be independently testable. The cross-plugin behavior should be verified on a server with both plugins installed before replacing the current chunk upgrade flow in production.
