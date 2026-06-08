package com.nick.landclaims.plugin.listener;

import com.nick.landclaims.api.protection.ClaimProtectionResult;
import com.nick.landclaims.plugin.claim.Claim;
import com.nick.landclaims.plugin.claim.ClaimChunk;
import com.nick.landclaims.plugin.claim.ClaimIndex;
import com.nick.landclaims.plugin.protection.ProtectionService;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public final class ProtectionListener implements Listener {
    private static final String BYPASS_PERMISSION = "landclaims.bypass.protection";

    private final ProtectionService protectionService;
    private final ClaimIndex claimIndex;

    public ProtectionListener(ProtectionService protectionService, ClaimIndex claimIndex) {
        this.protectionService = Objects.requireNonNull(protectionService, "protectionService");
        this.claimIndex = Objects.requireNonNull(claimIndex, "claimIndex");
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (isDenied(event.getBlock(), event.getPlayer(), "break")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (isDenied(event.getBlock(), event.getPlayer(), "build")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) {
            return;
        }

        if (isDenied(clickedBlock, event.getPlayer(), "interact")) {
            event.setCancelled(true);
        }
    }

    Optional<ClaimProtectionResult> checkProtection(
            ClaimChunk claimChunk,
            UUID actorUuid,
            Predicate<String> permissionCheck,
            String flagKey
    ) {
        Objects.requireNonNull(claimChunk, "claimChunk");
        Objects.requireNonNull(permissionCheck, "permissionCheck");
        Objects.requireNonNull(flagKey, "flagKey");

        Optional<Claim> claim = claimIndex.findAt(claimChunk);
        if (claim.isEmpty()) {
            return Optional.empty();
        }

        if (permissionCheck.test(BYPASS_PERMISSION) || permissionCheck.test(BYPASS_PERMISSION + "." + flagKey)) {
            return Optional.of(ClaimProtectionResult.ALLOW);
        }

        return Optional.of(protectionService.checkClaimFlag(claim.orElseThrow(), actorUuid, flagKey));
    }

    private boolean isDenied(Block block, Player player, String flagKey) {
        ClaimChunk claimChunk = claimChunk(block);
        return checkProtection(claimChunk, player.getUniqueId(), player::hasPermission, flagKey)
                .filter(result -> result != ClaimProtectionResult.ALLOW)
                .isPresent();
    }

    private ClaimChunk claimChunk(Block block) {
        Chunk chunk = block.getChunk();
        return new ClaimChunk(block.getWorld().getUID(), chunk.getX(), chunk.getZ());
    }
}
