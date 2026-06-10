# Admin Guide

Admin claims are server-owned claims for spawn, arenas, roads, shops, and event spaces. They use the same chunk storage and protection flags as player claims, but their owner type is `ADMIN` and they are not tied to a player UUID.

## Commands

| Command | Permission | Description |
| --- | --- | --- |
| `/claim admin create <name>` | `landclaims.admin.claim.create` | Creates an admin claim from the current completed claim-tool selection. |
| `/claim admin list` | `landclaims.admin.claim.list` | Lists admin claim names, chunk counts, and UUIDs. |
| `/claim admin delete <claim-id>` | `landclaims.admin.claim.delete` | Deletes an admin claim by UUID. |
| `/claim admin teleport <claim-id>` | `landclaims.admin.claim.teleport` | Teleports to the center of the first chunk in an admin claim. |
| `/claim admin userclaims list <player\|uuid>` | `landclaims.admin.userclaims.view` | Lists a player's claim names, chunk counts, and UUIDs from anywhere. |
| `/claim admin userclaims view <claim-id>` | `landclaims.admin.userclaims.view` | Shows player claim name, owner, and chunk count by UUID. |
| `/claim admin userclaims delete <claim-id>` | `landclaims.admin.userclaims.delete` | Deletes a player claim by UUID. |
| `/claim admin userclaims teleport <claim-id>` | `landclaims.admin.userclaims.teleport` | Teleports to the center of the first chunk in a player claim. |
| `/claim admin userclaims transfer <claim-id> <player\|uuid>` | `landclaims.admin.userclaims.transfer` | Transfers player claim ownership. Names must be online; UUIDs are accepted. |

Admin claim creation does not charge economy currency or spend claim-tool charges. It rejects blank names, empty selections, and overlaps with any existing claim.

## Still Planned

- `/claim admin mode`
- `/claim admin edit <claim-id>`
- `/claim admin reload`
- Advanced admin edits to player claims and future claim audit logging.
