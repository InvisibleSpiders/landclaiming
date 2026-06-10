# LandClaims

LandClaims is a Paper land claiming plugin that protects land by chunks. It uses a charged golden hoe claim tool, configurable flags, player and admin claims, SQLite or MySQL/MariaDB storage, MiniMessage messages, a public Bukkit service API, and optional economy over-limit claiming.

This branch is an MVP foundation. The current build includes claim-tool selection, claim creation, same-name merge confirmation, claim cost previews, member commands, clickable flag editing with descriptions, a claim menu shell, enforced claim protection flags, admin claim commands, advanced entity-control flags, and the public `LandClaimsApi` service for other plugins.

## Requirements

- Paper 26.1.2
- Java 25
- Optional: VaultUnlocked for paid over-limit claiming
- Optional: LuckPerms for permission management

## Development Notes

Feature PRs should follow the [development checklist](docs/development-checklist.md) so commands, permissions, config, messages, migrations, and README notes stay in sync.

## Install

1. Download the latest `LandClaims` jar from GitHub Releases.
2. Place the jar in your Paper server `plugins` folder.
3. Restart the server.
4. Edit generated files in `plugins/LandClaims/`.
5. Restart the server after configuration changes. Runtime reload via `/claim admin reload` is planned but not complete in this build.

## Quick Start

- Run `/claim tool` to receive the claim tool.
- Right-click two chunks with the tool to record selection corners.
- Run `/claim cost` to preview allowance and over-limit cost.
- Run `/claim create <name>` to create the claim.
- Run `/claim menu` while standing in a claim to open the current claim management shell.
- Run `/claim flags` while standing in a claim to open clickable flag toggles.
- Run `/claim viewborder` to show the current selection, claim, or chunk border.
- Switch away from the claim tool or double crouch within the configured window to clear an active selection.

## Persistence

Created claims are saved through the configured SQL repository when `/claim create` succeeds. On server startup, LandClaims loads saved claims into the in-memory claim index, so claims persist through restarts as long as the configured database is retained.

## Commands

Base command: `/claim`. Aliases: `/claims`, `/lc`.

| Command | Permission | Description |
| --- | --- | --- |
| `/claim` | `landclaims.use` | Shows the command help output. |
| `/claim tool` | `landclaims.tool.use` | Gives the configured claim tool. The default item is a charged golden hoe. |
| `/claim create <name>` | `landclaims.claim` | Creates a player claim from the current completed two-corner selection. |
| `/claim cost` | `landclaims.claim` | Previews selected chunks, allowance, over-limit chunks, and cost. `/claim quote` is also accepted. |
| `/claim menu` | `landclaims.gui` | Opens the claim management menu for the claim at the player's current chunk. |
| `/claim flags` | `landclaims.gui` | Opens the clickable flag editor for the claim at the player's current chunk. |
| `/claim viewborder` | `landclaims.use` | Shows the pending selection border, the current claim border, or the current chunk border. |
| `/claim flag list` | `landclaims.gui` | Opens the same flag editor as `/claim flags`. |
| `/claim flag set <flag> <true\|false>` | Claim owner plus `landclaims.flag.<flag>` | Sets a claim flag directly. Also accepts `on/off` and `yes/no`. |
| `/claim flag toggle <flag>` | Claim owner plus `landclaims.flag.<flag>` | Toggles a claim flag and redraws the flag editor. |
| `/claim member list` | Any player in claim | Lists members for the claim at the player's current chunk. |
| `/claim member add <player> [member\|manager]` | Claim owner or manager | Adds an online player to the claim. Managers can only add `member` role entries. |
| `/claim member remove <player>` | Claim owner or manager | Removes an existing claim member. Managers cannot remove other managers. |
| `/claim deny <player\|uuid>` | Claim owner or manager plus `landclaims.deny.manage` | Denies a player from entering the claim at the player's current chunk. Names must be online; UUIDs are accepted. |
| `/claim undeny <player\|uuid>` | Claim owner or manager plus `landclaims.deny.manage` | Removes a player from the current claim's denied-entry list. |
| `/claim denied` | `landclaims.deny.manage` | Lists players denied from entering the current claim. |
| `/claim admin create <name>` | `landclaims.admin.claim.create` | Creates a server-owned admin claim from the current completed selection. |
| `/claim admin list` | `landclaims.admin.claim.list` | Lists server-owned admin claims and their IDs. |
| `/claim admin delete <claim-id>` | `landclaims.admin.claim.delete` | Deletes a server-owned admin claim by UUID. |
| `/claim admin teleport <claim-id>` | `landclaims.admin.claim.teleport` | Teleports to the center of an admin claim's first chunk. |
| `/claim info` | `landclaims.use` | Shows claim name, owner type, chunk count, and whether the player owns the claim. |
| `/claim cancel` | `landclaims.claim` | Clears the player's pending first corner or completed claim selection. |
| `/claim mergeconfirm` | `landclaims.claim` | Confirms a pending same-name adjacent claim merge. Usually clicked from chat. |
| `/claim mergecancel` | `landclaims.claim` | Cancels a pending same-name adjacent claim merge. Usually clicked from chat. |

## Shortcuts

| Shortcut | Permission | Description |
| --- | --- | --- |
| Right-click with claim tool | `landclaims.tool.use` | Selects claim corners by chunk and shows a temporary glowing border around the selected chunk or completed selection. |
| Switch away from claim tool | `landclaims.tool.use` | Clears a pending first corner or completed selection and removes its border when `selection.clear-on-tool-switch` is enabled. |
| Double crouch | `landclaims.tool.use` | Clears a pending first corner or completed selection and removes its border when `selection.double-crouch-clear.enabled` is true. No message is sent when there is no selection to clear. |
| Sneak + swap hand | `landclaims.gui` | Opens `/claim menu` when the claim tool is involved. |

## Selection Behavior

- After two points are selected, another right-click replaces the second point and recalculates from the original first point.
- Clicking one of your own claimed chunks warns you and can use that claim as an expansion anchor. Existing owned chunks are removed from the pending selection so only new chunks are claimed.
- Clicking another player's claimed chunk warns you and does not set a selection point.

## Visuals

Chunk borders use temporary glowing `BlockDisplay` entities. There is no native Paper chunk glow API, so LandClaims draws display-entity line segments for the viewing player and removes them on timeout when configured, selection clear, logout, plugin disable, or replacement.

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
    duration-ticks: 0
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

Claim owners and managers can deny specific players from entering a claim. Denied players are pushed back to their last allowed location when they cross into the claim. If they are already inside a denied claim, LandClaims sends them to the nearest bordering unclaimed chunk it can find.

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
| `landclaims.use` | `true` | Allows basic LandClaims usage and help/info access. |
| `landclaims.claim` | `true` | Allows player claim creation, cost previews, selection cancellation, and merge confirmation flows. |
| `landclaims.gui` | `true` | Allows opening claim menus and flag editor views. |
| `landclaims.tool.use` | `true` | Allows receiving and using the claim tool. |
| `landclaims.tool.craft` | `true` | Allows crafting the claim tool when the recipe is enabled. |
| `landclaims.tool.recharge` | `true` | Reserved for claim tool recharge flows. |
| `landclaims.member.manage` | `true` | Reserved for broader member management permission gating. Current member commands are owner-gated. |
| `landclaims.deny.manage` | `true` | Allows using denied-entry management commands. Claim ownership or manager role is still required. |
| `landclaims.limit.default` | Registered from `permissions.yml` | Grants the default configured chunk allowance, currently 10. |
| `landclaims.limit.member` | Registered from `permissions.yml` | Grants the member configured chunk allowance, currently 25. |
| `landclaims.limit.vip` | Registered from `permissions.yml` | Grants the VIP configured chunk allowance, currently 75. |
| `landclaims.limit.elite` | Registered from `permissions.yml` | Grants the elite configured chunk allowance, currently 150. |
| `landclaims.flag.<flag>` | Server permission system | Allows the claim owner to edit a specific flag, such as `landclaims.flag.build` or `landclaims.flag.item_drop`. |
| `landclaims.bypass.claim-limit` | `op` | Bypasses claim allowance limits. |
| `landclaims.bypass.claim-buffer` | `op` | Bypasses configured claim buffer distance checks. |
| `landclaims.bypass.protection` | `op` | Bypasses all protection checks. |
| `landclaims.bypass.protection.<flag>` | `op` by convention | Bypasses one protection flag check through the public API and internal protection checks. |
| `landclaims.bypass.entry-deny` | `op` | Allows staff to enter claims even when listed as denied. |
| `landclaims.admin` | `op` | Parent permission for admin claim and planned user-claim tools. |
| `landclaims.admin.claim.create` | child of `landclaims.admin` | Allows creating server-owned admin claims. |
| `landclaims.admin.claim.edit` | child of `landclaims.admin` | Reserved for admin claim editing flows beyond creation/deletion. |
| `landclaims.admin.claim.delete` | child of `landclaims.admin` | Allows deleting server-owned admin claims. |
| `landclaims.admin.claim.list` | child of `landclaims.admin` | Allows listing server-owned admin claims. |
| `landclaims.admin.claim.teleport` | child of `landclaims.admin` | Allows teleporting to server-owned admin claims. |
| `landclaims.admin.userclaims.view` | child of `landclaims.admin` | Planned user claim browsing. |
| `landclaims.admin.userclaims.edit` | child of `landclaims.admin` | Planned user claim editing. |
| `landclaims.admin.userclaims.delete` | child of `landclaims.admin` | Planned user claim deletion. |
| `landclaims.admin.userclaims.teleport` | child of `landclaims.admin` | Planned teleporting to user claims. |
| `landclaims.admin.userclaims.transfer` | child of `landclaims.admin` | Planned claim ownership transfer. |
| `landclaims.admin.reload` | child of `landclaims.admin` | Planned runtime reload command. |

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

Advanced entity-control flags are claim-level customizations. They are off by default, can be toggled from `/claim flags`, and are designed to become part of the future claim upgrade UI.

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
- Plugin display/custom names, including rarity labels from other plugins, do not count as name-tag protection unless the entity was actually marked by LandClaims during a name-tag interaction.
- In the flag editor, entity-control flags show `REMOVING` or `KEEPING` so owners can tell at a glance what the claim will do.

## Plugin API

LandClaims registers `com.nick.landclaims.api.LandClaimsApi` with Bukkit's `ServicesManager`.

Other plugins can retrieve it after LandClaims enables:

```java
RegisteredServiceProvider<LandClaimsApi> provider =
        getServer().getServicesManager().getRegistration(LandClaimsApi.class);
LandClaimsApi landClaims = provider == null ? null : provider.getProvider();
```

Useful calls:

```java
landClaims.getClaimAt(location);
landClaims.getClaimsOwnedBy(playerUuid);
landClaims.canBuild(player, location);
landClaims.canInteract(player, location, "item_drop");
landClaims.canInteract(player, location, "mob_griefing");
```
