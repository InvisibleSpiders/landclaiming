# Claim Mode Inventory Design

Date: 2026-06-15

Repo: HavenClaims

## Goal

Replace the current dialog-style `/claimmode` behavior with a safe claim mode inventory experience. Claim mode becomes the standard way for players to create and modify claims, without requiring a craftable or permanent claim tool.

The first implementation focuses on the claim mode framework and inventory safety. Lock/key tools, hologram creation, and other future tools should plug into the framework later.

## Entry Points And Permissions

The following commands enter or exit claim mode:

- `/claimmode`
- `/claimmode on`
- `/claimmode off`
- `/claimmode toggle`
- `/cm`
- `/claim mode`

All entry points require the normal claim permission, `havenclaims.claim`. No separate player permission is needed because claim mode is the standard claim creation and editing path.

If the player is already in claim mode, the default toggle form exits claim mode.

## Session Lifecycle

`ClaimModeService` owns active sessions keyed by player UUID.

Entering claim mode:

1. Verify `claim-mode.enabled` and `havenclaims.claim`.
2. Refuse entry if the player already has an active claim mode session.
3. Snapshot hotbar slots `0-8` and the offhand item.
4. Clear hotbar slots and offhand.
5. Place registered claim mode tools in configured slots.
6. Record an audit session entry with full item backups.

Exiting claim mode:

1. Remove claim mode tools.
2. Restore the original hotbar and offhand items to their exact slots.
3. If an exact slot cannot be restored, insert the item into the player's main inventory.
4. If insertion still leaves leftovers, store the item in HavenClaims pending recovery.
5. Record the restore result in the audit log.

Logout always exits claim mode before the player is gone. Login always starts outside claim mode. Plugin disable restores every active session. Death handling must prevent claim mode tools from dropping and must restore real stored items through the same restore path.

The initial implementation uses memory-only active sessions. It does not guarantee recovery after a hard JVM or machine crash. The audit backup is intentionally detailed enough for staff to reconstruct disputed items manually.

## Claim Mode Tools

Claim mode tools are registered through a `ClaimModeTool` contract instead of being hardcoded into command logic.

Each tool defines:

- stable ID
- hotbar slot
- item stack factory
- enabled or disabled state
- disabled reason, if present
- interaction handler

Initial toolbelt:

| Slot | Tool | Behavior |
| --- | --- | --- |
| 0 | Claim Tool | Claim selection and future resize flow |
| 1 | Subclaim Tool | Visible disabled item with coming-soon feedback |
| 7 | Claim Mode Menu | Opens help/menu for claim mode actions |
| 8 | Exit Claim Mode | Exits claim mode and restores stored items |

Future tools such as lock/key, hologram creator, claim inspector, member editor, or border toggle should register through the same tool contract.

Claim mode tools are ephemeral plugin items. They must never be usable as normal Minecraft items. For example, if the claim tool uses a golden shovel material, it must not break blocks, create paths, damage entities, or trigger vanilla item behavior.

## Old Claim Tool Removal

The craftable claim tool is removed from the player workflow.

This project should remove or disable:

- claim tool recipe registration
- `/claim tool` as a way to obtain a permanent claim item
- recharge and charge behavior if it only exists for the old craftable tool
- docs and config that tell players to craft or keep a claim tool

Internal item tagging and selection code can be reused for the claim mode tool, but the item must be claim-mode-only.

## Interaction Guards

While a player is in claim mode, HavenClaims blocks interactions that could mix real items with mode tools or let mode tools behave like normal items.

Cancel while active:

- dropping items
- picking up items
- swap-hand key
- moving items into or out of hotbar slots
- number-key swaps involving hotbar slots
- dragging items over hotbar slots
- moving claim mode tools into any inventory
- crafting, anvil, enchanting, grindstone, smithing, merchant, or similar inventory movement if it touches player inventory
- block breaking with claim mode tools
- entity damage with claim mode tools
- path creation, tilling, stripping, scraping, waxing, and similar vanilla item actions
- placing claim mode tools in item frames, containers, or storage

Allowed interactions are routed only through registered claim mode tool handlers.

Armor and the main inventory are not replaced. Armor enchantments and armor behavior should not be affected by claim mode. The offhand is stored and cleared for the duration of claim mode.

## Command Guard

Claim mode blocks commands that can move items, move money, or mutate economy/storage state while the player's hotbar and offhand are stored.

Allowed commands:

- `/claimmode`
- `/cm`
- `/claim mode`
- other explicitly configured safe commands

Blocked commands are configurable. The default list should include common item/economy/storage commands:

```yaml
claim-mode:
  blocked-commands:
    - storage
    - vault
    - shop
    - auction
    - ah
    - trade
    - pay
    - sell
    - buy
    - kit
    - mail
```

The guard should match aliases by root command label after stripping the leading slash and namespace prefix. Blocked commands return a short message telling the player to exit claim mode first.

## Audit History

HavenClaims writes claim mode session history under the plugin folder, for example:

`logs/claimmode-history.log`

The history keeps the most recent `claim-mode.history-per-player` sessions per player. The default is `5`.

Each session record includes:

- player UUID
- last known player name
- enter timestamp
- exit timestamp, when available
- exit reason: manual, logout, death cleanup, plugin disable, restore failure
- hotbar slot and offhand item summaries
- material
- amount
- damage/durability
- display name
- lore
- enchantments
- item flags
- custom model data
- persistent data keys
- serialized Base64 item backup for every stored item
- restore result for every stored item

The file is intended for staff dispute resolution. It should be human-readable enough for quick inspection and complete enough to recreate a missing item manually.

## Pending Recovery

Exact restore is the primary path. Pending recovery is only used when an item cannot be returned to its original slot or inserted into the player's normal inventory.

Pending recovery stores:

- player UUID
- timestamp
- original slot or offhand marker
- serialized Base64 item
- item summary
- reason restore failed

For the first implementation, HavenClaims owns this recovery store. Once HavenCore exposes a clean `/rewards` item-claim API, HavenClaims can bridge pending recovery items into `/rewards` as "Claim Mode Recovery" rewards.

## Configuration

Initial config:

```yaml
claim-mode:
  enabled: true
  history-per-player: 5
  blocked-commands:
    - storage
    - vault
    - shop
    - auction
    - ah
    - trade
    - pay
    - sell
    - buy
    - kit
    - mail
```

Messages should be configurable in `messages.yml`, including:

- entered claim mode
- exited claim mode
- already in claim mode
- not in claim mode
- command blocked while in claim mode
- item pickup/drop blocked while in claim mode
- subclaim tool coming soon
- restore completed
- restore partially recovered
- restore failed and item moved to pending recovery

## Components

`ClaimModeService`

- owns active sessions
- enter and exit lifecycle
- exact restore and fallback restore
- pending recovery handoff
- exposes `isInClaimMode(UUID)`

`ClaimModeSession`

- player UUID and name
- stored hotbar items
- stored offhand item
- enter timestamp
- tool item fingerprints

`ClaimModeTool`

- tool metadata and item creation
- interaction handler
- disabled state

`ClaimModeToolRegistry`

- ordered tool registration
- slot conflict validation
- lookup by tagged item ID

`ClaimModeListener`

- inventory guards
- drop/pickup guards
- swap-hand guard
- vanilla behavior cancellation
- logout/death cleanup

`ClaimModeCommandGuard`

- blocks configured item/economy/storage commands while active

`ClaimModeAuditLog`

- writes session summaries
- includes Base64 item backups
- trims to recent session limit per player

`ClaimModeRecoveryStore`

- stores overflow or failed restore items
- provides a future bridge point for HavenCore `/rewards`

## Testing

Unit and integration-style tests should cover:

- enter snapshots hotbar and offhand only
- enter leaves armor and main inventory untouched
- enter places expected tool items in expected slots
- exit restores exact hotbar and offhand slots
- logout exits and restores session
- plugin disable restores all sessions
- death handling prevents mode tools from dropping
- item drop is cancelled while active
- item pickup is cancelled while active
- swap-hand key is cancelled while active
- hotbar inventory moves are cancelled while active
- configured blocked commands are blocked while active
- allowed claim mode commands still work while active
- claim mode tools do not perform vanilla item behavior
- disabled subclaim tool returns coming-soon feedback
- audit log includes summaries and serialized backups
- audit history trims to configured recent session count
- failed exact restore inserts into normal inventory
- failed insertion writes pending recovery

Manual smoke test:

1. Enter claim mode with valuable enchanted items in hotbar and offhand.
2. Confirm armor remains active and unchanged.
3. Try to drop, pick up, swap hand, open storage commands, move hotbar items, and use tool items as vanilla tools.
4. Exit claim mode and confirm exact item restoration.
5. Repeat with logout and plugin disable.

## Out Of Scope

Not included in this first project:

- lock/key tool behavior
- hologram creator tool behavior
- full subclaim implementation
- HavenCore `/rewards` integration
- hard-crash recovery of in-memory active sessions
- redesign of claim geometry or block-claim accrual

These can be separate projects that build on the claim mode tool registry.
