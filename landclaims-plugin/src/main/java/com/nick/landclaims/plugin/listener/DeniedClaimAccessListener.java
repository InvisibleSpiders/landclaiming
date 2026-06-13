package com.nick.landclaims.plugin.listener;

import com.nick.landclaims.plugin.access.ClaimEntryDecision;
import com.nick.landclaims.plugin.access.ClaimEntryGuard;
import com.nick.landclaims.plugin.claim.Claim;
import com.nick.landclaims.plugin.claim.ClaimChunk;
import com.nick.landclaims.plugin.claim.ClaimIndex;
import com.nick.landclaims.plugin.message.MessageService;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;

public final class DeniedClaimAccessListener implements Listener {
    private static final String ENTRY_BYPASS_PERMISSION = "landclaims.bypass.entry-deny";

    private final ClaimIndex claimIndex;
    private final ClaimEntryGuard entryGuard;
    private final MessageService messageService;
    private boolean enabled;
    private boolean knockbackEnabled;
    private double knockbackStrength;
    private final Map<UUID, Location> lastAllowedLocations = new java.util.concurrent.ConcurrentHashMap<>();

    public DeniedClaimAccessListener(
            ClaimIndex claimIndex,
            MessageService messageService,
            boolean enabled,
            boolean knockbackEnabled,
            double knockbackStrength
    ) {
        this.claimIndex = Objects.requireNonNull(claimIndex, "claimIndex");
        this.entryGuard = new ClaimEntryGuard(claimIndex);
        this.messageService = Objects.requireNonNull(messageService, "messageService");
        this.enabled = enabled;
        this.knockbackEnabled = knockbackEnabled;
        this.knockbackStrength = knockbackStrength;
    }

    public void reload(boolean newEnabled, boolean newKnockbackEnabled, double newKnockbackStrength) {
        this.enabled = newEnabled;
        this.knockbackEnabled = newKnockbackEnabled;
        this.knockbackStrength = newKnockbackStrength;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!enabled || event.getTo() == null || event.getFrom().getWorld() == null || event.getTo().getWorld() == null) {
            return;
        }

        Player player = event.getPlayer();
        if (player.hasPermission(ENTRY_BYPASS_PERMISSION)) {
            lastAllowedLocations.put(player.getUniqueId(), event.getTo().clone());
            return;
        }

        ClaimChunk fromChunk = claimChunk(event.getFrom());
        ClaimChunk toChunk = claimChunk(event.getTo());
        Optional<Claim> targetClaim = claimIndex.findAt(toChunk);
        boolean deniedAtDestination = targetClaim
                .map(claim -> claim.deniedPlayers().contains(player.getUniqueId()))
                .orElse(false);
        ClaimEntryDecision decision = entryGuard.checkMove(player.getUniqueId(), fromChunk, toChunk, player::hasPermission);

        if (!deniedAtDestination && !decision.denied()) {
            lastAllowedLocations.put(player.getUniqueId(), event.getTo().clone());
            return;
        }

        Location attemptedLocation = event.getTo().clone();
        Location fallback = fallbackLocation(player, event.getFrom(), attemptedLocation, targetClaim.orElse(null));
        event.setTo(fallback);
        if (knockbackEnabled) {
            applyKnockback(player, attemptedLocation, fallback);
        }
        if (targetClaim.isPresent()) {
            player.sendMessage(messageService.render("claim.deny.entry-denied", Map.of(
                    "claim_name", targetClaim.orElseThrow().name()
            )));
        } else {
            player.sendMessage(messageService.render(decision.messageKey(), Map.of()));
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        lastAllowedLocations.remove(event.getPlayer().getUniqueId());
    }

    private Location fallbackLocation(Player player, Location from, Location to, Claim targetClaim) {
        Location lastAllowed = lastAllowedLocations.get(player.getUniqueId());
        if (lastAllowed != null && lastAllowed.getWorld() != null && lastAllowed.getWorld().equals(to.getWorld())) {
            return preserveLook(player, lastAllowed.clone());
        }
        if (!isDenied(player, from)) {
            return preserveLook(player, from.clone());
        }
        if (targetClaim != null) {
            return preserveLook(player, nearestUnclaimedBorderLocation(to, targetClaim));
        }
        return preserveLook(player, from.clone());
    }

    private Location nearestUnclaimedBorderLocation(Location location, Claim claim) {
        World world = Objects.requireNonNull(location.getWorld(), "world");
        ClaimChunk fallbackChunk = entryGuard.nearestUnclaimedBorderChunk(claimChunk(location), claim);
        int blockX = fallbackChunk.chunkX() * 16 + 8;
        int blockZ = fallbackChunk.chunkZ() * 16 + 8;
        int blockY = Math.max(world.getMinHeight() + 1, world.getHighestBlockYAt(blockX, blockZ) + 1);
        return new Location(world, blockX + 0.5D, blockY, blockZ + 0.5D);
    }

    private boolean isDenied(Player player, Location location) {
        return claimIndex.findAt(claimChunk(location))
                .map(claim -> claim.deniedPlayers().contains(player.getUniqueId()))
                .orElse(false);
    }

    private ClaimChunk claimChunk(Location location) {
        Chunk chunk = location.getChunk();
        return new ClaimChunk(location.getWorld().getUID(), chunk.getX(), chunk.getZ());
    }

    private Location preserveLook(Player player, Location location) {
        location.setYaw(player.getLocation().getYaw());
        location.setPitch(player.getLocation().getPitch());
        return location;
    }

    private void applyKnockback(Player player, Location attemptedLocation, Location fallback) {
        Vector direction = fallback.toVector().subtract(attemptedLocation.toVector());
        if (direction.lengthSquared() == 0.0D) {
            direction = player.getLocation().getDirection().multiply(-1);
        }
        player.setVelocity(direction.normalize().multiply(knockbackStrength).setY(0.15D));
    }
}
