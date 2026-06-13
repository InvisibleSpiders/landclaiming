# Permissions

Core permissions:

- `landclaims.use`
- `landclaims.claim`
- `landclaims.gui`
- `landclaims.tool.use`
- `landclaims.tool.craft`
- `landclaims.tool.recharge`
- `landclaims.member.manage`

Admin permissions:

- `landclaims.admin`
- `landclaims.admin.claim.create`
- `landclaims.admin.claim.edit`
- `landclaims.admin.claim.delete`
- `landclaims.admin.claim.list`
- `landclaims.admin.claim.teleport`
- `landclaims.admin.userclaims.view`
- `landclaims.admin.userclaims.edit`
- `landclaims.admin.userclaims.delete`
- `landclaims.admin.userclaims.teleport`
- `landclaims.admin.userclaims.transfer`
- `landclaims.admin.limit`

Claim limits are stored per player in the LandClaims database and inherit `limits.default-claim-limit` when no override exists.
Use `/claim admin limit` commands or the `LandClaimsLimitService` API to adjust them.

Flag edit groups are configured in `permissions.yml`.
Group nodes such as `landclaims.flag.edit.basic` grant child permissions such as `landclaims.flag.build`.
