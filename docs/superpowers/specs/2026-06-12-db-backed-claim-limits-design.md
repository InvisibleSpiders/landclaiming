# DB-Backed Claim Limits + HavenVault Chunk Upgrades — Design

Date: 2026-06-12
Status: Approved (pending spec review)
Repos: HavenClaims (primary), HavenVault (upgrade UI)

## Problem

Claim chunk limits are permission-node–based today. `LimitService.resolveLimit(Set<String> permissions)` scans the player's permission set for the highest `havenclaims.limit.*` value. This makes per-player upgrades impossible without a permission plugin, prevents runtime adjustment, and cannot integrate with HavenVault's purchase system.

## Goals

1. Store per-player chunk limits in the database (HavenCore-integrated, same `HavenDataSource` as existing HavenClaims tables).
2. Replace permission-based limit resolution with DB lookup + `default-claim-limit` config fallback.
3. Expose `HavenClaimsLimitService` via Bukkit `ServicesManager` so other plugins (HavenVault) can read and modify limits.
4. Add `/claim admin limit` commands for direct admin control.
5. Add a chunk upgrade purchase flow in HavenVault with exponential pricing, true-sum bulk options, and configurable currency.

## Non-goals

- No claim storage system (separate spec).
- No change to over-limit economy charging (that flow is unchanged; the limit value just comes from a different source).
- No UI in HavenClaims for player self-service upgrades (that lives in HavenVault).

## Architecture

HavenClaims owns the data and the API contract. HavenVault owns the purchase UI, pricing logic, and upgrade effects. HavenVault soft-depends on HavenClaims; HavenClaims has no dependency on HavenVault.

---

## 1. HavenClaims — Data model (V4 migration)

New migration `V4__claim_player_limits.sql` under `db/migrations/havenclaims/` (picked up by the existing HavenCore migration runner):

```sql
CREATE TABLE IF NOT EXISTS claim_player_limits (
    player_uuid TEXT NOT NULL PRIMARY KEY,
    chunk_limit  INTEGER NOT NULL CHECK (chunk_limit >= 1)
);
```

One row per player with an explicit limit. No row = inherit `default-claim-limit` from `config.yml`. `migrations.index` updated to include V4.

---

## 2. HavenClaims — Repository

**`ClaimLimitRepository`** (`havenclaims-plugin`, uses `HavenDataSource`-provided `DataSource`):

| Method | SQL |
|---|---|
| `setLimit(UUID, int)` | `INSERT OR REPLACE INTO claim_player_limits (player_uuid, chunk_limit) VALUES (?, ?)` |
| `addChunks(UUID, int)` | read current (or default), write `max(1, current + delta)` |
| `removeChunks(UUID, int)` | read current (or default), write `max(1, current - delta)` |
| `getLimit(UUID)` → `OptionalInt` | `SELECT chunk_limit FROM claim_player_limits WHERE player_uuid = ?`; empty if no row |

`addChunks` and `removeChunks` run in a single connection transaction (read + write) to avoid races.

---

## 3. HavenClaims — API interface

**`HavenClaimsLimitService`** in `havenclaims-api`:

```java
public interface HavenClaimsLimitService {
    int getLimit(UUID playerId);
    void setLimit(UUID playerId, int limit);
    void addChunks(UUID playerId, int chunks);
    void removeChunks(UUID playerId, int chunks);
}
```

- `getLimit` always returns a value: DB record if present, otherwise `default-claim-limit` from config.
- All methods validate `chunks >= 1`; `removeChunks` floors result at 1.
- Registered on plugin enable: `getServer().getServicesManager().register(HavenClaimsLimitService.class, impl, this, ServicePriority.Normal)`.

---

## 4. HavenClaims — LimitService rewrite

`LimitService` drops the `permissionLimits` map and `resolveLimit(Set<String>)` signature entirely.

New signature: `resolveLimit(UUID playerId)` — delegates to `HavenClaimsLimitService.getLimit(playerId)`.

`HavenClaimsPlugin` no longer reads `permissions.yml` limit nodes or passes them to `LimitService`. The `limits.use-permission-limits` config key is removed. The `default-claim-limit` key remains as the universal fallback.

**Migration note for server operators:** Servers using permission groups for VIP limits (e.g. `havenclaims.limit.vip = 25`) must use `/claim admin limit set` to apply those values per-player after upgrading. The old permission nodes are silently ignored after this change.

---

## 5. HavenClaims — Admin commands

All under `/claim admin limit`, permission `havenclaims.admin.limit` (OP default).

| Command | Effect |
|---|---|
| `/claim admin limit set <player\|uuid> <amount>` | Upsert exact limit |
| `/claim admin limit add <player\|uuid> <amount>` | Increase by delta (no cap) |
| `/claim admin limit remove <player\|uuid> <amount>` | Decrease by delta, floor 1 |
| `/claim admin limit get <player\|uuid>` | Show effective limit + source (db or config default) |

Offline players supported via UUID (same pattern as existing admin commands). `messages.yml` entries follow existing `admin.*` key structure.

---

## 6. HavenVault — Config

New top-level section in HavenVault's `config.yml`:

```yaml
claim-upgrades:
  enabled: true
  base-cost: 500.0
  cost-multiplier: 1.1       # each extra chunk above default costs 10% more than the previous
  currency: money             # money | xp | item
  item-currency:
    material: DIAMOND
    amount: 1
  bulk-options:
    - chunks: 1
    - chunks: 5
      discount: 0.05
    - chunks: 10
      discount: 0.10
    - chunks: 25
      discount: 0.15
```

`claim-upgrades.enabled: false` (or HavenClaims not installed) hides the claim section from the Dialog entirely.

---

## 7. HavenVault — Pricing model

Cost of buying `n` chunks when the player's current limit is `L` and the server's `default-claim-limit` is `D`:

```
cost(L, n) = sum_{i=0}^{n-1} [ base-cost × cost-multiplier ^ (L - D + i) ]
           × (1 - discount)
```

Where `discount = 0` for the single-chunk option. The sum is the true cost of each individual chunk in the batch, so exponential growth applies to every chunk purchased — not just the first. The discount rewards committing to a larger batch.

**Example** (L=10, D=10, base=500, multiplier=1.1, 5-chunk 5% discount):
```
Chunk 11: 500.00
Chunk 12: 550.00
Chunk 13: 605.00
Chunk 14: 665.50
Chunk 15: 732.05
Sum = 3052.55 × 0.95 = 2899.92
```

---

## 8. HavenVault — ClaimChunkUpgradeService

`ClaimChunkUpgradeService` handles the full purchase flow:

1. **Availability check** — `Bukkit.getServicesManager().load(HavenClaimsLimitService.class)`. If null, log once on enable; claim section hidden in Dialog.
2. **Cost computation** — `computeCost(currentLimit, chunks, discount)` using the formula above.
3. **Requirement validation** — check money/XP/items before charging.
4. **Charge** — deduct via `HavenEconomyService`, drain XP, or consume items per `currency` config.
5. **Apply** — call `havenClaimsLimitService.addChunks(playerId, n)`.
6. **Confirm** — send message with new limit.

Charge and apply are not atomic across the two plugins. If `addChunks` throws after charging, the service logs the error and attempts a refund. If the refund also fails, it logs a warning for admin follow-up (same pattern as HavenVault's existing payment error handling).

**`ClaimChunkLimitEffect`** — thin `UpgradeEffect` implementation that delegates to `ClaimChunkUpgradeService.apply(playerId, chunks)` after payment. Keeps the effect chain consistent with `BankCapIncreaseEffect` and `StorageSlotsEffect`.

**`plugin.yml`** in HavenVault:
```yaml
softdepend: [HavenClaims]
```

---

## 9. HavenVault — Dialog UI

Chunk upgrades render as a section in the existing `/vault upgrades` Paper Dialog. Section only appears when `claim-upgrades.enabled: true` and `HavenClaimsLimitService` is available.

Each bulk option is one `ActionButton`:
- Label: `"+5 Chunks — $2,899"` (cost computed fresh at Dialog open from player's current limit)
- Tooltip: current limit, resulting limit, per-chunk cost breakdown
- Click: purchase → Dialog refreshes with updated limit

**Admin commands in HavenVault** (mirror of HavenClaims side, calls `HavenClaimsLimitService`):
```
/vault admin claim limit set <player|uuid> <amount>
/vault admin claim limit add <player|uuid> <amount>
/vault admin claim limit remove <player|uuid> <amount>
```

---

## 10. Testing

**HavenClaims:**
- `ClaimLimitRepositoryTest` — round-trip set/add/remove, floor-at-1 enforcement, fallback when no row
- `LimitServiceTest` — resolves from DB when record exists; falls back to config default when not
- `ClaimAdminLimitCommandTest` — set/add/remove/get with online player, offline UUID, invalid input

**HavenVault:**
- `ClaimChunkPricingTest` — pricing formula for single and bulk tiers, discount math
- `ClaimChunkUpgradeServiceTest` — full purchase flow with mock `HavenClaimsLimitService`; insufficient funds rejection; graceful no-op when HavenClaims absent
- `ClaimChunkLimitEffectTest` — delegates to service, no double-charge

---

## Build order

- **Phase A (HavenClaims):** V4 migration → `ClaimLimitRepository` → `HavenClaimsLimitService` API + impl → `LimitService` rewrite → admin commands. Fully standalone; no HavenVault changes needed.
- **Phase B (HavenVault):** Config loading → `ClaimChunkUpgradeService` → `ClaimChunkLimitEffect` → Dialog integration. Depends on Phase A API being published.

Phase B depends on Phase A.
