# Permissions

Core permissions:

- `havenclaims.use`
- `havenclaims.claim`
- `havenclaims.gui`
- `havenclaims.member.manage`

Normal players use `havenclaims.claim` to enter claim mode, select chunks, preview costs, create claims, cancel selections, and confirm same-name merge flows.

Admin permissions:

- `havenclaims.admin`
- `havenclaims.admin.claim.create`
- `havenclaims.admin.claim.edit`
- `havenclaims.admin.claim.delete`
- `havenclaims.admin.claim.list`
- `havenclaims.admin.claim.teleport`
- `havenclaims.admin.userclaims.view`
- `havenclaims.admin.userclaims.edit`
- `havenclaims.admin.userclaims.delete`
- `havenclaims.admin.userclaims.teleport`
- `havenclaims.admin.userclaims.transfer`
- `havenclaims.admin.limit`

Claim limits are stored per player in the HavenClaims database and inherit `limits.default-claim-limit` when no override exists.
Use `/claim admin limit` commands or the `HavenClaimsLimitService` API to adjust them.

Flag edit groups are configured in `permissions.yml`.
Group nodes such as `havenclaims.flag.edit.basic` grant child permissions such as `havenclaims.flag.build`.
