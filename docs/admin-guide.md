# Admin Guide

Admin claims are server-owned claims for spawn, arenas, roads, shops, and event spaces.

Admin management is scaffolded in this MVP foundation, but the command flows are not complete in the current build. Today, `/claims tool` is available for claim-tool selection testing, and most other `/claims` paths return the coming-soon menu message.

Planned admin commands:

- `/claims admin mode`
- `/claims admin create <name>`
- `/claims admin list`
- `/claims admin list <player>`
- `/claims admin view <claim-id>`
- `/claims admin teleport <claim-id>`
- `/claims admin edit <claim-id>`
- `/claims admin delete <claim-id>`
- `/claims admin reload`

Planned admin edits to user claims will be written to the claim audit log.
