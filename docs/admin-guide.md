# Admin Guide

Admin claims are server-owned claims for spawn, arenas, roads, shops, and event spaces. They use the same chunk storage and protection flags as player claims, but their owner type is `ADMIN` and they are not tied to a player UUID.

## Commands

| Command | Permission | Description |
| --- | --- | --- |
| `/claim admin create <name>` | `havenclaims.admin.claim.create` | Creates an admin claim from the current completed claim-mode selection. |
| `/claim admin list` | `havenclaims.admin.claim.list` | Lists admin claim names, chunk counts, and UUIDs. |
| `/claim admin delete <claim-id>` | `havenclaims.admin.claim.delete` | Deletes an admin claim by UUID. |
| `/claim admin teleport <claim-id>` | `havenclaims.admin.claim.teleport` | Teleports to the center of the first chunk in an admin claim. |
| `/claim admin userclaims list <player\|uuid>` | `havenclaims.admin.userclaims.view` | Lists a player's claim names, chunk counts, and UUIDs from anywhere. |
| `/claim admin userclaims view <claim-id>` | `havenclaims.admin.userclaims.view` | Shows player claim name, owner, and chunk count by UUID. |
| `/claim admin userclaims delete <claim-id>` | `havenclaims.admin.userclaims.delete` | Deletes a player claim by UUID. |
| `/claim admin userclaims teleport <claim-id>` | `havenclaims.admin.userclaims.teleport` | Teleports to the center of the first chunk in a player claim. |
| `/claim admin userclaims transfer <claim-id> <player\|uuid>` | `havenclaims.admin.userclaims.transfer` | Transfers player claim ownership. Names must be online; UUIDs are accepted. |
| `/claim admin userclaims flag list <claim-id>` | `havenclaims.admin.userclaims.edit` | Lists flags for a player claim by UUID. |
| `/claim admin userclaims flag set <claim-id> <flag> <off\|visitors\|all>` | `havenclaims.admin.userclaims.edit` | Sets a player claim flag by UUID. |
| `/claim admin userclaims flag cycle <claim-id> <flag>` | `havenclaims.admin.userclaims.edit` | Cycles a player claim flag by UUID. |
| `/claim admin userclaims member list <claim-id>` | `havenclaims.admin.userclaims.edit` | Lists members for a player claim by UUID. |
| `/claim admin userclaims member add <claim-id> <player\|uuid> [member\|manager]` | `havenclaims.admin.userclaims.edit` | Adds or updates a player claim member by UUID. Names must be online; UUIDs are accepted. |
| `/claim admin userclaims member remove <claim-id> <player\|uuid>` | `havenclaims.admin.userclaims.edit` | Removes a player claim member by UUID or known player name. |

Admin claim creation does not charge economy currency. It uses the current claim-mode selection and rejects blank names, empty selections, and overlaps with any existing claim.

## Still Planned

- `/claim admin mode`
- `/claim admin edit <claim-id>`
- Future claim audit logging.
