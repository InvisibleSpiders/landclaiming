# Admin Guide

Admin claims are server-owned claims for spawn, arenas, roads, shops, and event spaces.

Admin management is scaffolded in this MVP foundation, but the command flows are not complete in the current build. Today, `/claim tool` is available for claim-tool selection testing, and most other `/claim` paths return the coming-soon menu message.

Planned admin commands:

- `/claim admin mode`
- `/claim admin create <name>`
- `/claim admin list`
- `/claim admin list <player>`
- `/claim admin view <claim-id>`
- `/claim admin teleport <claim-id>`
- `/claim admin edit <claim-id>`
- `/claim admin delete <claim-id>`
- `/claim admin reload`

Planned admin edits to user claims will be written to the claim audit log.
