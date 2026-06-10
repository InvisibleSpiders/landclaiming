# Claim Creation Flow Design

## Purpose

The next playable milestone is the core claim loop: select chunks with the claim tool, turn that selection into a saved claim, and have protection checks use that claim immediately. This keeps the plugin useful in-game before the richer dialog and inventory GUI layers are added.

## Player Flow

Players use the configured claim tool to right-click two chunks. The selection expands to every chunk inside the rectangle and remains stored for that player until it is claimed or deliberately cleared. The player then runs `/claim create <name>` to create a player-owned claim from the stored selection.

Players can clear a pending selection in three ways:

- `/claim cancel`
- Switching away from the claim tool, if selection-clear-on-tool-switch is enabled.
- Double crouching within a configurable tick window.

The double-crouch window will be configured in ticks because Minecraft server input is processed on ticks. The default will be `80` ticks, or about `4` seconds.

## Commands

`/claim` shows the current useful commands and avoids stub-only messaging.

`/claim tool` gives the configured claim tool.

`/claim create <name>` creates a claim from the player's pending selection. If there is no pending selection, the player receives a clear message telling them to select two chunks first.

`/claim cancel` clears the player's pending selection.

`/claim info` shows the claim at the player's current chunk, including name, owner, chunk count, and whether the player is owner/admin.

## Claim Rules

The first implementation creates player-owned claims only. Admin claim creation and editing user claims will remain separate admin flows.

Claim creation must reject:

- Empty or overly long names.
- Selections larger than the claim tool's available charges.
- Chunks already claimed by anyone.
- Player claims inside the configured buffer distance from another player's claim.
- Player claims inside the configured admin-claim buffer distance from an admin claim.

When a claim is created, the tool spends one charge per selected chunk. If claim creation fails, charges are not spent and the selection remains.

## Storage And Indexing

`ClaimRepository` will become functional for saving claims and reading them by chunk, id, owner, and all claims. SQLite is the first backend to become fully usable; the SQL implementation should stay compatible with MySQL/MariaDB syntax where practical.

On plugin startup, the repository initializes schema and loads all claims into a live `ClaimIndex`. The protection listener uses the index instead of its current empty map. After `/claim create`, the new claim is saved and added to the live index without requiring a restart.

## Protection Behavior

Created claims use the default locked-down flag preset. For this milestone, block break, block place, and basic interaction protections are enough to prove the loop. The existing bypass permissions continue to work.

Owners are allowed by the protection service where owner checks already exist. Non-owners are denied for locked flags unless they have bypass permission. More detailed member/chunk-specific access will build on this storage/index foundation later.

## Configuration

Add or extend config entries for:

- claim name maximum length
- selection clear on tool switch
- double-crouch selection clear enabled
- double-crouch clear window in ticks

Existing claim buffer distance settings remain authoritative.

## Testing

Unit tests will cover:

- Selection persists until claimed or cleared.
- Double-crouch detection only clears within the configured window.
- Claim creation rejects overlapping chunks and buffer violations.
- Successful claim creation saves, indexes, and spends charges.
- Protection checks find newly created claims through the live index.
- SQL repository can save and load claims with chunks and flags.

The final verification will run the full Gradle build and produce a new shaded `LandClaims` jar.
