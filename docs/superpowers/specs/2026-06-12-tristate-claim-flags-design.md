# Tri-state claim flags + Paper Dialog editor — Design

Date: 2026-06-12
Status: Approved (pending spec review)

## Problem

Claim flags are boolean today (`claim_flags.enabled` 0/1, `ClaimView.flags()` →
`Map<String,Boolean>`). A flag like `container_access` can only be "allow non-members"
or not. Owners cannot express "lock this for visitors but allow my members" separately
from "lock this for everyone but me." The flag editor is also chat text with clickable
run-command lines, not a real GUI.

## Goals

1. Replace the boolean flag value with a tri-state: **OFF / VISITORS / ALL**, with the
   exact meaning of each state determined per-flag by the flag's kind.
2. Let the owner restrict members (ALL) while still acting freely themselves, except for
   flags where even the owner is bound (e.g. mob/explosion damage).
3. Replace the flag editor with a real Paper **Dialog** UI where each flag's state cycles
   independently on click. No chest GUI.
4. Preserve all existing in-game behavior on migration — no server sees a behavior change
   from upgrading until an owner deliberately changes a flag.

## Non-goals

- No change to member/manager/deny management, claim creation, economy, or visuals.
- No new flags. Same flag set, new value model.

## 1. Value model (landclaims-api)

New enum:

```java
public enum FlagState { OFF, VISITORS, ALL }
```

New enum:

```java
public enum FlagKind { PLAYER_ACTION, WORLD_EFFECT }
```

`ClaimFlagDefinition` changes:

- Remove `boolean defaultValue`.
- Add `FlagKind kind`.
- Add `boolean ownerExempt`.
- Add `FlagState defaultState`.

`FlagKind` governs which states are valid:

- `PLAYER_ACTION` → OFF, VISITORS, ALL (full cycle).
- `WORLD_EFFECT` → OFF, ALL only (VISITORS is invalid; never offered in the editor and
  rejected by `setFlagState`).

### Flag classification

| Flag key | kind | ownerExempt | defaultState |
|---|---|---|---|
| build | PLAYER_ACTION | true | VISITORS |
| break | PLAYER_ACTION | true | VISITORS |
| interact | PLAYER_ACTION | true | VISITORS |
| container_access | PLAYER_ACTION | true | VISITORS |
| door_access | PLAYER_ACTION | true | VISITORS |
| switch_access | PLAYER_ACTION | true | VISITORS |
| redstone_access | PLAYER_ACTION | true | VISITORS |
| entity_damage | PLAYER_ACTION | true | ALL |
| item_pickup | PLAYER_ACTION | true | ALL |
| item_drop | PLAYER_ACTION | true | ALL |
| crop_trample | PLAYER_ACTION | true | ALL |
| piston_protection | WORLD_EFFECT | false | ALL |
| fluid_flow | WORLD_EFFECT | false | OFF |
| explosion_damage | WORLD_EFFECT | false | OFF |
| fire_spread | WORLD_EFFECT | false | OFF |
| mob_griefing | WORLD_EFFECT | false | OFF |
| remove_hostile_entities | WORLD_EFFECT | false | OFF |
| remove_passive_entities | WORLD_EFFECT | false | OFF |

The default states above are chosen to exactly reproduce current default behavior (see
§3 migration table — defaults equal the migration of each flag's old default value).

## 2. Protection semantics (`ProtectionService`)

`checkClaimFlag(ClaimView claim, UUID actor, String flagKey)` is rewritten. The hardcoded
`MEMBER_ACCESS_FLAGS` set is **deleted**; membership now drives the decision directly.

Resolve the actor's relationship to the claim:

- **OWNER** — `actor.equals(claim.ownerUuid())`.
- **MANAGER** — member with role MANAGER.
- **MEMBER** — member with role MEMBER.
- **VISITOR** — anyone else (including `actor == null`).

Resolve `state = claim.flagState(flagKey)` (default `definition.defaultState()`).

### PLAYER_ACTION flags

| state | OWNER | MANAGER | MEMBER | VISITOR |
|---|---|---|---|---|
| OFF | allow | allow | allow | allow |
| VISITORS | allow | allow | allow | **deny** |
| ALL | allow* | allow* | **deny** | **deny** |

`*` Owner and managers are allowed in ALL **only if `ownerExempt == true`**. Every
PLAYER_ACTION flag sets `ownerExempt = true`, so in practice owner+manager always act;
the field exists for completeness and future flags.

Managers are treated as owner-equivalent (per decision: managers are trusted co-owners).

### WORLD_EFFECT flags

No actor relationship applies; environment listeners call with `actor == null`. The
decision is purely `state` + the flag's existing direction:

- `OFF` and `ALL` map to the same allow/deny that the old `false`/`true` produced.
- `ownerExempt == false`, so the owner's own blocks/mobs are affected too. This is the
  "except mob damage" case — `explosion_damage`/`mob_griefing` apply regardless of owner.
- `piston_protection` keeps its inverted return (`ALL` = protected →
  `DENY_WITH_MESSAGE` for piston movement; `OFF` = unprotected → `ALLOW`).

### Admin bypass

`landclaims.bypass.protection` handling is unchanged and remains in the listener layer,
not in `ProtectionService`.

## 3. Storage migration (V3)

New migration `V3__claim_flag_states.sql`:

1. `ALTER TABLE claim_flags ADD COLUMN state TEXT;`
2. Backfill `state` from `enabled` using a `CASE flag_key` mapping:

| Group (flag_key) | old `enabled = 0` | old `enabled = 1` |
|---|---|---|
| build, break, interact, container_access, door_access, switch_access, redstone_access | `VISITORS` | `OFF` |
| entity_damage, item_pickup, item_drop, crop_trample | `ALL` | `OFF` |
| piston_protection, fluid_flow, explosion_damage, fire_spread, mob_griefing, remove_hostile_entities, remove_passive_entities | `OFF` | `ALL` |

   Rationale: member-access actions were "deny non-members" when `false` → `VISITORS`;
   non-member actions had members subject to the flag too → `ALL`; world effects were a
   plain on/off → `ALL`/`OFF`. Any row whose `flag_key` matches none of the groups falls
   back to `CASE WHEN enabled = 1 THEN 'ALL' ELSE 'OFF' END`.
3. The `enabled` column is **retained** (nullable, no longer read) for rollback safety.

`migrations.index` updated to include V3.

`SqlClaimRepository`:

- `insertFlags` writes `state` (the `FlagState.name()`), not `enabled`.
- `loadFlags` and `bulkLoadFlags` read `state` into `Map<String, FlagState>`, defaulting
  an unrecognized/null value to the definition default via the caller.

## 4. Domain & API ripple

`Claim.flags()` and `ClaimView.flags()`: `Map<String,Boolean>` → `Map<String,FlagState>`
(breaking API change, approved).

Touched:

- `Claim` (constructor field type, merge in `ClaimCreationService.persistClaim` unions
  `Map<String,FlagState>` — last-writer-wins per key, same as today).
- `ClaimCreationService.defaultFlags()` / `AdminClaimService.defaultFlags()` produce
  `Map<String,FlagState>` from `definition.defaultState()`.
- `ClaimFlagRow`: `boolean enabled` → `FlagState state` plus `FlagKind kind` (so the
  editor knows the valid cycle).

## 5. Flag service + command

`ClaimFlagService`:

- `setFlagState(actor, claim, key, FlagState, permissionCheck)` replaces `setFlag`.
  Rejects VISITORS for a WORLD_EFFECT flag with `claim.flag.invalid-state`.
- `cycleFlag(actor, claim, key, permissionCheck)` replaces `toggleFlag`; advances to the
  next valid state for the flag's kind (PLAYER_ACTION: OFF→VISITORS→ALL→OFF;
  WORLD_EFFECT: OFF→ALL→OFF).
- Owner/manager edit-permission gate is unchanged.

Command (`/claim flag ...`):

- `/claim flag cycle <key>` — advance one state (used by the dialog buttons).
- `/claim flag set <key> <off|visitors|all>` — set explicitly.
- `/claim flag list` — unchanged listing, now shows the state.
- Admin equivalents under `/claim admin userclaims flag ...` updated the same way.

Messages: `claim.flag.set`, `claim.flag.cycled`, `claim.flag.invalid-state`, and the
`flag-editor.row` placeholders gain a `<state>`/`<next_state>` that render OFF/VISITORS/ALL.

## 6. Dialog UI (`DialogService.openFlagEditor`)

Rewrite to build a Paper Dialog (`io.papermc.paper.dialog.Dialog`,
`io.papermc.paper.registry.data.dialog.*`). paper-api 26.1.2 supports it.

- One `ActionButton` per flag, label `"<Label> — <STATE>"`, tooltip = flag description.
- Click runs `/claim flag cycle <key>` then re-opens the dialog so the player sees the new
  state (independent per-flag cycling).
- Flags grouped/ordered by the existing `CATEGORY_ORDER`.
- WORLD_EFFECT buttons cycle OFF↔ALL; PLAYER_ACTION cycle OFF→VISITORS→ALL.

Fallback: `config.yml prefer-dialogs`. When `false` (or the running server lacks Dialog
support), fall back to the existing clickable-chat editor, which is updated to show the
tri-state and run `/claim flag cycle`.

## 7. Testing

- `ProtectionService`: full decision matrix — {PLAYER_ACTION, WORLD_EFFECT} × {OFF,
  VISITORS, ALL} × {owner, manager, member, visitor, null} × {ownerExempt true/false}.
  Assert `piston_protection` inversion preserved.
- `ClaimFlagService`: cycle ordering per kind; `setFlagState` rejects VISITORS on
  WORLD_EFFECT; owner/permission gates.
- Migration mapping: a repository-level test that inserts legacy rows (simulated old
  `enabled`) and asserts the loaded `FlagState` per the §3 table.
- `ClaimFlagEditorService`: row state/next-state labels per kind.

## Build order (single spec, two-phase plan)

- **Phase A** — value model, protection, storage/migration, domain/API, flag service,
  command. Fully headless and testable; behavior preserved on upgrade.
- **Phase B** — Paper Dialog editor + chat fallback on top of Phase A.

Phase B depends on Phase A.
