# LandClaims

LandClaims is a Paper land claiming plugin that protects land by chunks. It uses a charged golden hoe claim tool, configurable flags, player and admin claims, SQLite or MySQL/MariaDB storage, MiniMessage messages, a public Bukkit service API, and optional economy over-limit claiming.

This branch is an MVP foundation. The current build includes claim-tool selection, claim creation, same-name merge confirmation, claim cost previews, member commands, clickable flag editing, a claim menu shell, core block protection, and the public `LandClaimsApi` service for other plugins.

## Requirements

- Paper 26.1.2
- Java 25
- Optional: VaultUnlocked for paid over-limit claiming
- Optional: LuckPerms for permission management

## Install

1. Download the latest `LandClaims` jar from GitHub Releases.
2. Place the jar in your Paper server `plugins` folder.
3. Restart the server.
4. Edit generated files in `plugins/LandClaims/`.
5. Restart the server after configuration changes. Runtime reload via `/claims admin reload` is planned but not complete in this build.

## Quick Start

- Run `/claims tool` to receive the claim tool.
- Right-click two chunks with the tool to record selection corners.
- Run `/claims cost` to preview allowance and over-limit cost.
- Run `/claims create <name>` to create the claim.
- Run `/claims menu` while standing in a claim to open the current claim management shell.
- Run `/claims flags` while standing in a claim to open clickable flag toggles.
- Run `/claims viewborder` to show the current selection, claim, or chunk border.
- Switch away from the claim tool or double crouch within the configured window to clear an active selection.

## Commands

Base command: `/claims`. Aliases: `/claim`, `/lc`.

| Command | Permission | Description |
| --- | --- | --- |
| `/claims` | `landclaims.use` | Shows the command help output. |
| `/claims tool` | `landclaims.tool.use` | Gives the configured claim tool. The default item is a charged golden hoe. |
| `/claims create <name>` | `landclaims.claim` | Creates a player claim from the current completed two-corner selection. |
| `/claims cost` | `landclaims.claim` | Previews selected chunks, allowance, over-limit chunks, and cost. `/claims quote` is also accepted. |
| `/claims menu` | `landclaims.gui` | Opens the claim management menu for the claim at the player's current chunk. |
| `/claims flags` | `landclaims.gui` | Opens the clickable flag editor for the claim at the player's current chunk. |
| `/claims viewborder` | `landclaims.use` | Shows the pending selection border, the current claim border, or the current chunk border. |
| `/claims flag list` | `landclaims.gui` | Opens the same flag editor as `/claims flags`. |
| `/claims flag set <flag> <true\|false>` | Claim owner plus `landclaims.flag.<flag>` | Sets a claim flag directly. Also accepts `on/off` and `yes/no`. |
| `/claims flag toggle <flag>` | Claim owner plus `landclaims.flag.<flag>` | Toggles a claim flag and redraws the flag editor. |
| `/claims member list` | Claim owner | Lists members for the claim at the player's current chunk. |
| `/claims member add <player> [member\|manager]` | Claim owner | Adds a member or manager to the claim at the player's current chunk. |
| `/claims member remove <player>` | Claim owner | Removes a member from the claim at the player's current chunk. |
| `/claims info` | `landclaims.use` | Shows claim name, owner type, chunk count, and whether the player owns the claim. |
| `/claims cancel` | `landclaims.claim` | Clears the player's pending first corner or completed claim selection. |
| `/claims mergeconfirm` | `landclaims.claim` | Confirms a pending same-name adjacent claim merge. Usually clicked from chat. |
| `/claims mergecancel` | `landclaims.claim` | Cancels a pending same-name adjacent claim merge. Usually clicked from chat. |

## Shortcuts

| Shortcut | Permission | Description |
| --- | --- | --- |
| Right-click with claim tool | `landclaims.tool.use` | Selects claim corners by chunk and shows a temporary glowing border around the selected chunk or completed selection. |
| Switch away from claim tool | `landclaims.tool.use` | Clears a pending first corner or completed selection and removes its border when `selection.clear-on-tool-switch` is enabled. |
| Double crouch | `landclaims.tool.use` | Clears a pending first corner or completed selection and removes its border when `selection.double-crouch-clear.enabled` is true. No message is sent when there is no selection to clear. |
| Sneak + swap hand | `landclaims.gui` | Opens `/claims menu` when the claim tool is involved. |

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
| `landclaims.limit.default` | Registered from `permissions.yml` | Grants the default configured chunk allowance, currently 10. |
| `landclaims.limit.member` | Registered from `permissions.yml` | Grants the member configured chunk allowance, currently 25. |
| `landclaims.limit.vip` | Registered from `permissions.yml` | Grants the VIP configured chunk allowance, currently 75. |
| `landclaims.limit.elite` | Registered from `permissions.yml` | Grants the elite configured chunk allowance, currently 150. |
| `landclaims.flag.<flag>` | Server permission system | Allows the claim owner to edit a specific flag, such as `landclaims.flag.build` or `landclaims.flag.item_drop`. |
| `landclaims.bypass.claim-limit` | `op` | Bypasses claim allowance limits. |
| `landclaims.bypass.claim-buffer` | `op` | Bypasses configured claim buffer distance checks. |
| `landclaims.bypass.protection` | `op` | Bypasses all protection checks. |
| `landclaims.bypass.protection.<flag>` | `op` by convention | Bypasses one protection flag check through the public API and internal protection checks. |
| `landclaims.admin` | `op` | Parent permission for planned admin claim and user-claim tools. |
| `landclaims.admin.claim.create` | child of `landclaims.admin` | Planned admin claim creation. |
| `landclaims.admin.claim.edit` | child of `landclaims.admin` | Planned admin claim editing. |
| `landclaims.admin.claim.delete` | child of `landclaims.admin` | Planned admin claim deletion. |
| `landclaims.admin.claim.list` | child of `landclaims.admin` | Planned admin claim listing. |
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
| `container_access` | access | `false` | Reserved for chest and container access. |
| `door_access` | access | `false` | Reserved for door and gate access. |
| `switch_access` | access | `false` | Reserved for button, lever, and pressure plate access. |
| `redstone_access` | access | `false` | Reserved for redstone interaction. |
| `piston_protection` | protection | `true` | Reserved for piston movement protection. |
| `fluid_flow` | environment | `false` | Reserved for water and lava flow protection. |
| `explosion_damage` | environment | `false` | Reserved for explosion damage protection. |
| `fire_spread` | environment | `false` | Reserved for fire spread protection. |
| `mob_griefing` | environment | `false` | Reserved for mob grief behavior. |
| `crop_trample` | entity | `false` | Reserved for crop trampling. |
| `entity_damage` | entity | `false` | Reserved for damaging entities in claims. |
| `item_pickup` | item | `false` | Reserved for item pickup in claims. |
| `item_drop` | item | `false` | Reserved for item drops in claims and external plugin checks. |

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
