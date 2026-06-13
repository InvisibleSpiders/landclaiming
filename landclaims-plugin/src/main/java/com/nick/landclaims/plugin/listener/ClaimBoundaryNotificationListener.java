package com.nick.landclaims.plugin.listener;

import com.nick.landclaims.plugin.claim.Claim;
import com.nick.landclaims.plugin.claim.ClaimChunk;
import com.nick.landclaims.plugin.claim.ClaimIndex;
import com.nick.landclaims.plugin.message.MessageService;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class ClaimBoundaryNotificationListener implements Listener {
    private final ClaimIndex claimIndex;
    private final MessageService messageService;
    private boolean enabled;
    private boolean enterEnabled;
    private boolean exitEnabled;
    private BoundaryMessageDelivery enterDelivery;
    private BoundaryMessageDelivery exitDelivery;
    private final Map<UUID, Claim> currentClaims = new java.util.HashMap<>();

    public ClaimBoundaryNotificationListener(
            ClaimIndex claimIndex,
            MessageService messageService,
            boolean enabled,
            boolean enterEnabled,
            boolean exitEnabled,
            String deliveryMode
    ) {
        this(claimIndex, messageService, enabled, enterEnabled, exitEnabled, deliveryMode, deliveryMode, deliveryMode);
    }

    public ClaimBoundaryNotificationListener(
            ClaimIndex claimIndex,
            MessageService messageService,
            boolean enabled,
            boolean enterEnabled,
            boolean exitEnabled,
            String deliveryMode,
            String enterDeliveryMode,
            String exitDeliveryMode
    ) {
        this.claimIndex = Objects.requireNonNull(claimIndex, "claimIndex");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
        this.enabled = enabled;
        this.enterEnabled = enterEnabled;
        this.exitEnabled = exitEnabled;
        BoundaryMessageDelivery defaultDelivery = BoundaryMessageDelivery.from(deliveryMode, BoundaryMessageDelivery.ACTION_BAR);
        this.enterDelivery = BoundaryMessageDelivery.from(enterDeliveryMode, defaultDelivery);
        this.exitDelivery = BoundaryMessageDelivery.from(exitDeliveryMode, defaultDelivery);
    }

    public void reload(boolean newEnabled, boolean newEnterEnabled, boolean newExitEnabled,
            String deliveryMode, String enterDeliveryMode, String exitDeliveryMode) {
        this.enabled = newEnabled;
        this.enterEnabled = newEnterEnabled;
        this.exitEnabled = newExitEnabled;
        BoundaryMessageDelivery defaultDelivery = BoundaryMessageDelivery.from(
                deliveryMode, BoundaryMessageDelivery.ACTION_BAR);
        this.enterDelivery = BoundaryMessageDelivery.from(enterDeliveryMode, defaultDelivery);
        this.exitDelivery = BoundaryMessageDelivery.from(exitDeliveryMode, defaultDelivery);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!enabled || !movedChunk(event.getFrom(), event.getTo())) {
            return;
        }

        Player player = event.getPlayer();
        Optional<Claim> claim = claimAt(event.getTo());
        Claim previousClaim = currentClaims.get(player.getUniqueId());
        UUID previousClaimId = previousClaim == null ? null : previousClaim.id();
        UUID nextClaimId = claim.map(Claim::id).orElse(null);
        if (Objects.equals(previousClaimId, nextClaimId)) {
            return;
        }

        if (previousClaim != null && exitEnabled) {
            send(player, exitDelivery, "claim.boundary.exit", placeholders(previousClaim));
        }
        if (claim.isPresent() && enterEnabled) {
            send(player, enterDelivery, "claim.boundary.enter", placeholders(claim.orElseThrow()));
        }

        if (nextClaimId == null) {
            currentClaims.remove(player.getUniqueId());
        } else {
            currentClaims.put(player.getUniqueId(), claim.orElseThrow());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        currentClaims.remove(event.getPlayer().getUniqueId());
    }

    private Optional<Claim> claimAt(Location location) {
        Chunk chunk = location.getChunk();
        return claimIndex.findAt(new ClaimChunk(location.getWorld().getUID(), chunk.getX(), chunk.getZ()));
    }

    private boolean movedChunk(Location from, Location to) {
        return from.getWorld() != null
                && to.getWorld() != null
                && (!from.getWorld().equals(to.getWorld())
                || from.getChunk().getX() != to.getChunk().getX()
                || from.getChunk().getZ() != to.getChunk().getZ());
    }

    private Map<String, String> placeholders(Claim claim) {
        return Map.of(
                "claim_name", claim.name(),
                "owner_type", claim.owner().name().toLowerCase(),
                "chunk_count", String.valueOf(claim.claimChunks().size())
        );
    }

    private void send(
            Player player,
            BoundaryMessageDelivery delivery,
            String messageKey,
            Map<String, String> placeholders
    ) {
        Component message = messageService.render(messageKey, placeholders);
        if (delivery.sendsChat()) {
            player.sendMessage(message);
        }
        if (delivery.sendsActionBar()) {
            player.sendActionBar(message);
        }
    }
}
