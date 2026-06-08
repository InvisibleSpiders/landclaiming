package com.nick.landclaims.plugin.api;

import com.nick.landclaims.api.LandClaimsApi;
import com.nick.landclaims.api.claim.ClaimView;
import com.nick.landclaims.api.protection.ClaimProtectionResult;
import com.nick.landclaims.plugin.claim.Claim;
import com.nick.landclaims.plugin.claim.ClaimChunk;
import com.nick.landclaims.plugin.claim.ClaimIndex;
import com.nick.landclaims.plugin.claim.OwnerType;
import com.nick.landclaims.plugin.protection.ProtectionService;
import com.nick.landclaims.plugin.storage.ClaimRepository;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class BukkitLandClaimsApi implements LandClaimsApi {
    private static final String BYPASS_PERMISSION = "landclaims.bypass.protection";

    private final ClaimRepository claimRepository;
    private final ClaimIndex claimIndex;
    private final ProtectionService protectionService;

    public BukkitLandClaimsApi(
            ClaimRepository claimRepository,
            ClaimIndex claimIndex,
            ProtectionService protectionService
    ) {
        this.claimRepository = Objects.requireNonNull(claimRepository, "claimRepository");
        this.claimIndex = Objects.requireNonNull(claimIndex, "claimIndex");
        this.protectionService = Objects.requireNonNull(protectionService, "protectionService");
    }

    @Override
    public Optional<ClaimView> getClaimAt(Location location) {
        Objects.requireNonNull(location, "location");
        return claimIndex.findAt(claimChunk(location))
                .map(ClaimView.class::cast);
    }

    @Override
    public Optional<ClaimView> getClaimById(UUID claimId) {
        Objects.requireNonNull(claimId, "claimId");
        return claimRepository.findClaimById(claimId)
                .map(ClaimView.class::cast);
    }

    @Override
    public List<ClaimView> getClaimsOwnedBy(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return claimRepository.findClaimsByOwner(OwnerType.PLAYER, playerId).stream()
                .map(ClaimView.class::cast)
                .toList();
    }

    @Override
    public boolean canBuild(Player player, Location location) {
        return canInteract(player, location, "build");
    }

    @Override
    public boolean canInteract(Player player, Location location, String actionKey) {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(actionKey, "actionKey");

        Optional<Claim> claim = claimIndex.findAt(claimChunk(location));
        if (claim.isEmpty()) {
            return true;
        }

        if (hasBypass(player, actionKey)) {
            return true;
        }

        UUID actorUuid = player == null ? null : player.getUniqueId();
        return protectionService.checkClaimFlag(claim.orElseThrow(), actorUuid, actionKey) == ClaimProtectionResult.ALLOW;
    }

    private boolean hasBypass(Player player, String actionKey) {
        return player != null
                && (player.hasPermission(BYPASS_PERMISSION) || player.hasPermission(BYPASS_PERMISSION + "." + actionKey));
    }

    private ClaimChunk claimChunk(Location location) {
        World world = Objects.requireNonNull(location.getWorld(), "location world");
        Chunk chunk = location.getChunk();
        return new ClaimChunk(world.getUID(), chunk.getX(), chunk.getZ());
    }
}
