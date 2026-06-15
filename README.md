# HavenClaims

HavenClaims is a Paper land claiming plugin that protects land by chunks. It uses a charged golden hoe claim tool, configurable flags, player and admin claims, SQLite or MySQL/MariaDB storage, MiniMessage messages, a public Bukkit service API, and optional economy over-limit claiming.

This branch is an MVP foundation. The current build includes claim-tool selection, optional-name claim creation, same-name merge confirmation, claim cost previews, member and deny management, dialog-backed claim menus, clickable flag editing with descriptions, enforced claim protection flags, admin claim commands, advanced entity-control flags, and the public `HavenClaimsApi` service for other plugins.

## Requirements

- Paper 26.1.2
- Java 25
- Optional: VaultUnlocked for paid over-limit claiming
- Optional: LuckPerms for permission management

## Development Notes

Feature PRs should follow the [development checklist](docs/development-checklist.md) so commands, permissions, config, messages, migrations, and README notes stay in sync.

## Install

1. Download the latest `HavenClaims` jar from GitHub Releases.
2. Place the jar in your Paper server `plugins` folder.
3. Restart the server.
4. Edit generated files in `plugins/HavenClaims/`.
5. Restart the server after configuration changes, or use `/claim admin reload` for supported runtime-reloadable settings.

## Quick Start

- Run `/claim tool` to receive the claim tool.
- Right-click two chunks with the tool to record selection corners.
- Run `/claim cost` to preview allowance and over-limit cost.
- Run `/claim create [name]` to preview and create the claim.
- Run `/claim` to open your claims dashboard from anywhere.
- Run `/claim menu` while in a claim, or `/claim menu <claim-id>`, to manage a specific claim.
- Run `/claim flags` while in a claim, or `/claim flags <claim-id>`, to open clickable flag controls.
- Run `/claim viewborder` to show the current selection, claim, or chunk border.
- Switch away from the claim tool or double crouch within the configured window to clear an active selection.

## Persistence

Created claims are saved through the configured SQL repository when `/claim create` succeeds. On server startup, HavenClaims loads saved claims into the in-memory claim index, so claims persist through restarts as long as the configured database is retained.

## Commands

Base command: `/claim`. Aliases: `/claims`, `/lc`.

| Command | Permission | Description |
| --- | --- | --- |
| `/claim` | `havenclaims.gui` | Opens the player's claims dashboard. |
| `/claim tool` | `havenclaims.tool.use` | Gives the configured claim tool. The default item is a charged golden hoe. |
| `/claim create [name]` | `havenclaims.claim` | Previews and creates a player claim from the current completed two-corner selection. |
| `/claim cost` | `havenclaims.claim` | Previews selected chunks, allowance, over-limit chunks, and cost. `/claim quote` is also accepted. |
| `/claim menu [claim-id]` | `havenclaims.gui` | Opens the player's claims dashboard, the current claim menu, or a specific owned/managed claim menu by UUID. |
| `/claim flags [claim-id]` | `havenclaims.gui` | Opens the clickable flag editor for the current claim or a specific owned/managed claim by UUID. |
| `/claim viewborder [claim-id]` | `havenclaims.use` | Shows the pending selection border, the current claim border, the current chunk border, or a specific owned/managed claim border by UUID. |
| `/claim flag list` | `havenclaims.gui` | Opens the same flag editor as `/claim flags`. |
| `/claim flag set <flag> <off\|visitors\|all>` | Claim owner or manager plus `havenclaims.flag.<flag>` | Sets a claim flag directly. |
| `/claim flag cycle <flag>` | Claim owner or manager plus `havenclaims.flag.<flag>` | Cycles a claim flag and redraws the flag editor. |
| `/claim member list [claim-id]` | Claim owner or manager | Lists members for the current claim or a specific owned/managed claim by UUID. |
| `/claim member add [claim-id] <player> [member\|manager]` | Claim owner or manager | Adds an online player to the current claim or a specific owned/managed claim by UUID. Managers can only add `member` role entries. |
| `/claim member remove [claim-id] <player>` | Claim owner or manager | Removes an existing member from the current claim or a specific owned/managed claim by UUID. Managers cannot remove other managers. |
| `/claim deny [claim-id] <player\|uuid>` | Claim owner or manager plus `havenclaims.deny.manage` | Denies a player from entering the current claim or a specific owned/managed claim by UUID. Names must be online; UUIDs are accepted. |
| `/claim undeny [claim-id] <player\|uuid>` | Claim owner or manager plus `havenclaims.deny.manage` | Removes a player from the current claim or a specific owned/managed claim's denied-entry list. |
| `/claim denied [claim-id]` | `havenclaims.deny.manage` | Lists players denied from the current claim or a specific owned/managed claim by UUID. |
| `/claim admin create <name>` | `havenclaims.admin.claim.create` | Creates a server-owned admin claim from the current completed selection. |
| `/claim admin list` | `havenclaims.admin.claim.list` | Lists server-owned admin claims and their IDs. |
| `/claim admin delete <claim-id>` | `havenclaims.admin.claim.delete` | Deletes a server-owned admin claim by UUID. |
| `/claim admin teleport <claim-id>` | `havenclaims.admin.claim.teleport` | Teleports to the center of an admin claim's first chunk. |
| `/claim admin userclaims list <player\|uuid>` | `havenclaims.admin.userclaims.view` | Lists a player's claims and IDs from anywhere. |
| `/claim admin userclaims view <claim-id>` | `havenclaims.admin.userclaims.view` | Shows a player claim by UUID from anywhere. |
| `/claim admin userclaims delete <claim-id>` | `havenclaims.admin.userclaims.delete` | Deletes a player claim by UUID. |
| `/claim admin userclaims teleport <claim-id>` | `havenclaims.admin.userclaims.teleport` | Teleports to the center of a player claim's first chunk. |
| `/claim admin userclaims transfer <claim-id> <player\|uuid>` | `havenclaims.admin.userclaims.transfer` | Transfers player claim ownership. Names must be online; UUIDs are accepted. |
| `/claim admin userclaims flag list <claim-id>` | `havenclaims.admin.userclaims.edit` | Lists flags for a player claim by UUID. |
| `/claim admin userclaims flag set <claim-id> <flag> <off\|visitors\|all>` | `havenclaims.admin.userclaims.edit` | Sets a player claim flag by UUID. |
| `/claim admin userclaims flag cycle <claim-id> <flag>` | `havenclaims.admin.userclaims.edit` | Cycles a player claim flag by UUID. |
| `/claim admin userclaims member list <claim-id>` | `havenclaims.admin.userclaims.edit` | Lists members for a player claim by UUID. |
| `/claim admin userclaims member add <claim-id> <player\|uuid> [member\|manager]` | `havenclaims.admin.userclaims.edit` | Adds or updates a player claim member by UUID. Names must be online; UUIDs are accepted. |
| `/claim admin userclaims member remove <claim-id> <player\|uuid>` | `havenclaims.admin.userclaims.edit` | Removes a player claim member by UUID or known player name. |
| `/claim admin limit get <player\|uuid>` | `havenclaims.admin.limit` | Shows a player's effective claim chunk limit. |
| `/claim admin limit set <player\|uuid> <amount>` | `havenclaims.admin.limit` | Sets a player's claim chunk limit. |
| `/claim admin limit add <player\|uuid> <amount>` | `havenclaims.admin.limit` | Adds chunks to a player's claim chunk limit. |
| `/claim admin limit remove <player\|uuid> <amount>` | `havenclaims.admin.limit` | Removes chunks from a player's claim chunk limit, with a floor of 1. |
| `/claim info` | `havenclaims.use` | Shows claim name, owner type, chunk count, and whether the player owns the claim. |
| `/claim cancel` | `havenclaims.claim` | Clears the player's pending first corner or completed claim selection. |
| `/claim mergeconfirm` | `havenclaims.claim` | Confirms a pending same-name adjacent claim merge. Usually clicked from chat. |
| `/claim mergecancel` | `havenclaims.claim` | Cancels a pending same-name adjacent claim merge. Usually clicked from chat. |

## Shortcuts

| Shortcut | Permission | Description |
| --- | --- | --- |
| Right-click with claim tool | `havenclaims.tool.use` | Selects claim corners by chunk and shows a temporary glowing border around the selected chunk or completed selection. |
| Switch away from claim tool | `havenclaims.tool.use` | Clears a pending first corner or completed selection and removes its border when `selection.clear-on-tool-switch` is enabled. |
| Double crouch | `havenclaims.tool.use` | Clears a pending first corner or completed selection and removes its border when `selection.double-crouch-clear.enabled` is true. No message is sent when there is no selection to clear. |
| Sneak + swap hand | `havenclaims.gui` | Opens `/claim menu` when the claim tool is involved. |

## Selection Behavior

- After two points are selected, another right-click replaces the second point and recalculates from the original first point.
- Clicking one of your own claimed chunks warns you and can use that claim as an expansion anchor. Existing owned chunks are removed from the pending selection so only new chunks are claimed.
- Clicking another player's claimed chunk warns you and does not set a selection point.

## Visuals

Chunk borders use temporary glowing `BlockDisplay` entities. There is no native Paper chunk glow API, so HavenClaims draws display-entity line segments for the viewing player and removes them on timeout when configured, selection clear, logout, plugin disable, or replacement.

Claim preview border colors:

| Color | Meaning |
| --- | --- |
| Green | Selection or current chunk is claimable without extra action. |
| Red | Selection is blocked by overlap, buffer distance, admin claim, or another player's land. |
| Yellow | Selection borders one of the player's claims and can merge when the claim name matches. |
| Aqua | Selection is claimable but exceeds the player's free allowance and may cost money. |
| Gold | Created claim or existing claim border preview. |

Relevant config:

```yaml
visuals:
  border:
    enabled: true
    duration-ticks: 100
    thickness: 0.08
    view-range: 96.0
```

## Boundary Notifications

Players can receive configurable enter and exit messages when crossing claimed-land boundaries.

```yaml
notifications:
  claim-boundary:
    enabled: true
    delivery: action_bar
    enter:
      enabled: true
      delivery: action_bar
    exit:
      enabled: true
      delivery: action_bar
```

`notifications.claim-boundary.delivery` accepts `action_bar`, `chat`, `both`, or `none`. The `enter.delivery` and `exit.delivery` values override the default for that specific event. Boundary messages support `<claim_name>`, `<owner_type>`, and `<chunk_count>` placeholders.

## Denied Entry

Claim owners and managers can deny specific players from entering a claim. Denied players are pushed back to their last allowed location when they cross into the claim. If they are already inside a denied claim, HavenClaims sends them to the nearest bordering unclaimed chunk it can find.

Relevant config:

```yaml
access-denial:
  enabled: true
  knockback:
    enabled: true
    strength: 0.65
```

## Permissions

| Permission | Default | Description |
| --- | --- | --- |
| `havenclaims.use` | `true` | Allows basic HavenClaims usage and help/info access. |
| `havenclaims.claim` | `true` | Allows player claim creation, cost previews, selection cancellation, and merge confirmation flows. |
| `havenclaims.gui` | `true` | Allows opening claim menus and flag editor views. |
| `havenclaims.tool.use` | `true` | Allows receiving and using the claim tool. |
| `havenclaims.tool.craft` | `true` | Allows crafting the claim tool when the recipe is enabled. |
| `havenclaims.tool.recharge` | `true` | Reserved for claim tool recharge flows. |
| `havenclaims.member.manage` | `true` | Reserved for broader member management permission gating. Current member commands are owner-gated. |
| `havenclaims.deny.manage` | `true` | Allows using denied-entry management commands. Claim ownership or manager role is still required. |
| `havenclaims.flag.<flag>` | Registered from `permissions.yml` flag-edit groups | Allows the claim owner to edit a specific flag, such as `havenclaims.flag.build` or `havenclaims.flag.item_drop`. Group nodes such as `havenclaims.flag.edit.basic` grant their listed child flags. |
| `havenclaims.bypass.claim-limit` | `op` | Bypasses claim allowance limits. |
| `havenclaims.bypass.claim-buffer` | `op` | Bypasses configured claim buffer distance checks. |
| `havenclaims.bypass.protection` | `op` | Bypasses all protection checks. |
| `havenclaims.bypass.protection.<flag>` | `op` by convention | Bypasses one protection flag check through the public API and internal protection checks. |
| `havenclaims.bypass.entry-deny` | `op` | Allows staff to enter claims even when listed as denied. |
| `havenclaims.admin` | `op` | Parent permission for admin claim and user-claim tools. |
| `havenclaims.admin.claim.create` | child of `havenclaims.admin` | Allows creating server-owned admin claims. |
| `havenclaims.admin.claim.edit` | child of `havenclaims.admin` | Reserved for admin claim editing flows beyond creation/deletion. |
| `havenclaims.admin.claim.delete` | child of `havenclaims.admin` | Allows deleting server-owned admin claims. |
| `havenclaims.admin.claim.list` | child of `havenclaims.admin` | Allows listing server-owned admin claims. |
| `havenclaims.admin.claim.teleport` | child of `havenclaims.admin` | Allows teleporting to server-owned admin claims. |
| `havenclaims.admin.userclaims.view` | child of `havenclaims.admin` | Allows browsing player claims from anywhere. |
| `havenclaims.admin.userclaims.edit` | child of `havenclaims.admin` | Allows editing player claim flags and members by UUID. |
| `havenclaims.admin.userclaims.delete` | child of `havenclaims.admin` | Allows deleting player claims by UUID. |
| `havenclaims.admin.userclaims.teleport` | child of `havenclaims.admin` | Allows teleporting to player claims by UUID. |
| `havenclaims.admin.userclaims.transfer` | child of `havenclaims.admin` | Allows transferring player claim ownership by UUID. |
| `havenclaims.admin.limit` | child of `havenclaims.admin` | Allows viewing and changing player claim chunk limits. |
| `havenclaims.admin.reload` | child of `havenclaims.admin` | Allows reloading supported HavenClaims configuration and message settings. |

## Flags

Default flags are locked down unless configured otherwise by the claim owner.

| Flag | Category | Default | Description |
| --- | --- | --- | --- |
| `build` | access | `false` | Controls block placement by non-owners. |
| `break` | access | `false` | Controls block breaking by non-owners. |
| `interact` | access | `false` | Controls generic block interaction by non-owners. |
| `container_access` | access | `false` | Controls chest, barrel, furnace, hopper, shulker, and similar container access. |
| `door_access` | access | `false` | Controls doors, trapdoors, and fence gates. |
| `switch_access` | access | `false` | Controls buttons, levers, and pressure plates. |
| `redstone_access` | access | `false` | Controls repeater and comparator interaction. |
| `piston_protection` | protection | `true` | Controls piston movement touching claimed chunks. |
| `fluid_flow` | environment | `false` | Controls water and lava flowing into claimed chunks. |
| `explosion_damage` | environment | `false` | Controls explosion damage to claimed blocks. |
| `fire_spread` | environment | `false` | Controls fire spread into claimed chunks. |
| `mob_griefing` | environment | `false` | Controls entity block changes such as mob griefing. |
| `crop_trample` | entity | `false` | Controls farmland trampling in claimed chunks. |
| `entity_damage` | entity | `false` | Controls damaging entities in claimed chunks. |
| `remove_hostile_entities` | entity control | `false` | Removes hostile mobs from claimed chunks unless explicitly name-tagged or tamed. |
| `remove_passive_entities` | entity control | `false` | Removes passive mobs from claimed chunks unless explicitly name-tagged or tamed. |
| `item_pickup` | item | `false` | Controls item pickup in claimed chunks. |
| `item_drop` | item | `false` | Controls player item drops in claimed chunks and external plugin checks. |

Protection edge behavior:

- `piston_protection: true` blocks piston movement that would move blocks from, into, or through claimed chunks, including an empty piston head extending into a protected claim.
- `fluid_flow: false` and `fire_spread: false` block flow or spread entering a claim from outside that same claim. Movement within the same claim is not blocked by those boundary checks.

## Advanced Entity Control

Advanced entity-control flags are claim-level customizations. They are off by default, can be cycled from `/claim flags`, and are designed to become part of the future claim upgrade UI.

Relevant config:

```yaml
advanced:
  entity-control:
    enabled: true
    cleanup-interval-ticks: 200
    preserve-named-entities: true
    preserve-tamed-entities: true
```

- Spawned hostile or passive entities are removed immediately when the matching claim flag is enabled.
- Existing entities are checked on the configured cleanup interval.
- Entities explicitly named by a player/admin name tag and tamed entities are preserved by default as a safety guard.
- Plugin display/custom names, including rarity labels from other plugins, do not count as name-tag protection unless the entity was actually marked by HavenClaims during a name-tag interaction.
- In the flag editor, entity-control flags show `REMOVING` or `KEEPING` so owners can tell at a glance what the claim will do.

## Plugin API

HavenClaims registers `com.invisiblespiders.havenclaims.api.HavenClaimsApi` with Bukkit's `ServicesManager`.

Other plugins can retrieve it after HavenClaims enables:

```java
RegisteredServiceProvider<HavenClaimsApi> provider =
        getServer().getServicesManager().getRegistration(HavenClaimsApi.class);
HavenClaimsApi havenClaims = provider == null ? null : provider.getProvider();
```

Useful calls:

```java
havenClaims.getClaimAt(location);
havenClaims.getClaimsOwnedBy(playerUuid);
havenClaims.canBuild(player, location);
havenClaims.canInteract(player, location, "item_drop");
havenClaims.canInteract(player, location, "mob_griefing");
```
