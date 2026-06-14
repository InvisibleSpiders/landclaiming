package com.nick.landclaims.api;

import com.nick.landclaims.api.claim.ClaimView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public interface LandClaimsApi {
    Optional<ClaimView> getClaimAt(Location location);

    Optional<ClaimView> getClaimById(UUID claimId);

    List<ClaimView> getClaimsOwnedBy(UUID playerId);

    boolean canBuild(Player player, Location location);

    boolean canInteract(Player player, Location location, String actionKey);
}
