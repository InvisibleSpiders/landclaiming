# Development Checklist

Use this checklist for each HavenClaims feature PR so user-facing changes stay discoverable.

## Required Updates

- Update `README.md` when commands, permissions, flags, config, storage behavior, API behavior, or player-visible workflows change.
- Update `messages.yml` whenever a new player-facing message is added or an existing message key changes.
- Update `plugin.yml` and `permissions.yml` together when adding or changing permissions so permission editors can discover the nodes.
- Add SQL migrations and migration tests for storage changes.
- Add focused tests for the behavior being changed before implementation where practical.

## PR Notes

Each PR should include:

- What changed.
- Why it changed.
- How players or admins use it.
- Validation commands that were run.
- Any manual server testing that should happen after merge.
