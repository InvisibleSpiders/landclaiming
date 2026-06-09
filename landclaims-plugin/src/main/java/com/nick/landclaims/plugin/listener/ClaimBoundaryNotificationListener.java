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
    private final boolean enabled;
    private final boolean enterEnabled;
    private final boolean exitEnabled;
    private final Delivery delivery;
    private final Map<UUID, UUID> currentClaimIds = new java.util.HashMap<>();

    public ClaimBoundaryNotificationListener(
            ClaimIndex claimIndex,
            MessageService messageService,
            boolean enabled,
            boolean enterEnabled,
            boolean exitEnabled,
            String deliveryMode
    ) {
        this.claimIndex = Objects.requireNonNull(claimIndex, "claimIndex");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
        this.enabled = enabled;
        this.enterEnabled = enterEnabled;
        this.exitEnabled = exitEnabled;
        this.delivery = Delivery.from(deliveryMode);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!enabled || !movedChunk(event.getFrom(), event.getTo())) {
            return;
        }

        Player player = event.getPlayer();
        Optional<Claim> claim = claimAt(event.getTo());
        UUID previousClaimId = currentClaimIds.get(player.getUniqueId());
        UUID nextClaimId = claim.map(Claim::id).orElse(null);
        if (Objects.equals(previousClaimId, nextClaimId)) {
            return;
        }

        if (previousClaimId != null && exitEnabled) {
            send(player, "claim.boundary.exit", Map.of());
        }
        if (claim.isPresent() && enterEnabled) {
            Claim foundClaim = claim.orElseThrow();
            send(player, "claim.boundary.enter", Map.of(
                    "claim_name", foundClaim.name(),
                    "owner_type", foundClaim.owner().name().toLowerCase()
            ));
        }

        if (nextClaimId == null) {
            currentClaimIds.remove(player.getUniqueId());
        } else {
            currentClaimIds.put(player.getUniqueId(), nextClaimId);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        currentClaimIds.remove(event.getPlayer().getUniqueId());
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

    private void send(Player player, String messageKey, Map<String, String> placeholders) {
        Component message = messageService.render(messageKey, placeholders);
        if (delivery == Delivery.CHAT || delivery == Delivery.BOTH) {
            player.sendMessage(message);
        }
        if (delivery == Delivery.ACTION_BAR || delivery == Delivery.BOTH) {
            player.sendActionBar(message);
        }
    }

    private enum Delivery {
        CHAT,
        ACTION_BAR,
        BOTH;

        private static Delivery from(String value) {
            if ("chat".equalsIgnoreCase(value)) {
                return CHAT;
            }
            if ("both".equalsIgnoreCase(value)) {
                return BOTH;
            }
            return ACTION_BAR;
        }
    }
}
