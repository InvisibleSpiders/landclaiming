package com.invisiblespiders.havenclaims.plugin.claimmode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public final class ClaimModeService {
    public static final String CLAIM_PERMISSION = "havenclaims.claim";

    public enum EnterResult { ENTERED, DISABLED, NO_PERMISSION, ALREADY_ACTIVE }
    public enum ExitResult { RESTORED, PARTIAL, RECOVERY, NOT_ACTIVE }
    public enum ExitReason { MANUAL, LOGOUT, DEATH, PLUGIN_DISABLE, RESTORE_FAILURE }

    private ClaimModeConfig config;
    private final ClaimModeToolRegistry toolRegistry;
    private final ClaimModeSessionHistory history;
    private final ClaimModeRecoveryStore recoveryStore;
    private final Component fallbackMessage;
    private final Map<UUID, ClaimModeSession> sessions = new HashMap<>();

    public ClaimModeService(ClaimModeConfig config, ClaimModeToolRegistry toolRegistry,
                            ClaimModeSessionHistory history, ClaimModeRecoveryStore recoveryStore,
                            Component fallbackMessage) {
        this.config = Objects.requireNonNull(config, "config");
        this.toolRegistry = Objects.requireNonNull(toolRegistry, "toolRegistry");
        this.history = Objects.requireNonNull(history, "history");
        this.recoveryStore = Objects.requireNonNull(recoveryStore, "recoveryStore");
        this.fallbackMessage = Objects.requireNonNull(fallbackMessage, "fallbackMessage");
    }

    public void reload(ClaimModeConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public boolean isInClaimMode(UUID playerId) {
        return sessions.containsKey(playerId);
    }

    public ClaimModeToolRegistry toolRegistry() {
        return toolRegistry;
    }

    public EnterResult enter(Player player) {
        Objects.requireNonNull(player, "player");
        if (!config.enabled()) {
            return EnterResult.DISABLED;
        }
        if (!player.hasPermission(CLAIM_PERMISSION)) {
            return EnterResult.NO_PERMISSION;
        }

        UUID playerId = player.getUniqueId();
        if (sessions.containsKey(playerId)) {
            return EnterResult.ALREADY_ACTIVE;
        }

        PlayerInventory inventory = player.getInventory();
        List<ClaimModeItemSnapshot> snapshots = snapshotAndClearClaimModeSlots(inventory);
        placeClaimModeTools(inventory);
        sessions.put(playerId, new ClaimModeSession(playerId, player.getName(), Instant.now(), snapshots));
        return EnterResult.ENTERED;
    }

    public ExitResult exit(Player player, ExitReason reason) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(reason, "reason");
        ClaimModeSession session = sessions.remove(player.getUniqueId());
        if (session == null) {
            return ExitResult.NOT_ACTIVE;
        }

        PlayerInventory inventory = player.getInventory();
        removeClaimModeTools(inventory);

        List<String> restoreResults = new ArrayList<>();
        boolean partial = false;
        boolean recovery = false;
        for (ClaimModeItemSnapshot snapshot : session.snapshots()) {
            RestoreOutcome outcome = restoreSnapshot(inventory, session, snapshot);
            restoreResults.add(snapshot.slot() + "=" + outcome.historyValue());
            partial = partial || outcome == RestoreOutcome.INVENTORY || outcome == RestoreOutcome.RECOVERY;
            recovery = recovery || outcome == RestoreOutcome.RECOVERY;
        }

        history.append(session.playerId(), session.playerName(), session.enteredAt(), Instant.now(),
                reason, session.snapshots(), restoreResults);
        if (recovery) {
            return ExitResult.RECOVERY;
        }
        return partial ? ExitResult.PARTIAL : ExitResult.RESTORED;
    }

    public void restoreAll(Iterable<? extends Player> players, ExitReason reason) {
        Objects.requireNonNull(players, "players");
        Objects.requireNonNull(reason, "reason");
        for (Player player : players) {
            if (player != null && isInClaimMode(player.getUniqueId())) {
                exit(player, reason);
            }
        }
    }

    public Component fallbackMessage() {
        return fallbackMessage;
    }

    private List<ClaimModeItemSnapshot> snapshotAndClearClaimModeSlots(PlayerInventory inventory) {
        List<ClaimModeItemSnapshot> snapshots = new ArrayList<>();
        for (int slot = 0; slot <= 8; slot++) {
            snapshots.add(ClaimModeItemSnapshot.from("hotbar-" + slot, inventory.getItem(slot)));
            inventory.setItem(slot, null);
        }
        snapshots.add(ClaimModeItemSnapshot.from("offhand", inventory.getItemInOffHand()));
        inventory.setItemInOffHand(null);
        return snapshots;
    }

    private void placeClaimModeTools(PlayerInventory inventory) {
        for (ClaimModeTool tool : toolRegistry.toolsBySlot().values()) {
            inventory.setItem(tool.slot(), toolRegistry.createItem(tool.id()));
        }
    }

    private void removeClaimModeTools(PlayerInventory inventory) {
        for (int slot = 0; slot <= 8; slot++) {
            if (toolRegistry.isClaimModeTool(inventory.getItem(slot))) {
                inventory.setItem(slot, null);
            }
        }
        if (toolRegistry.isClaimModeTool(inventory.getItemInOffHand())) {
            inventory.setItemInOffHand(null);
        }
    }

    private RestoreOutcome restoreSnapshot(PlayerInventory inventory, ClaimModeSession session,
                                           ClaimModeItemSnapshot snapshot) {
        if (snapshot.empty()) {
            return RestoreOutcome.EMPTY;
        }

        ItemStack item = snapshot.restoreItem();
        if (isEmpty(item)) {
            return RestoreOutcome.EMPTY;
        }
        if (restoreExact(inventory, snapshot.slot(), item)) {
            return RestoreOutcome.EXACT;
        }

        Map<Integer, ItemStack> leftovers = inventory.addItem(item);
        if (leftovers.isEmpty()) {
            return RestoreOutcome.INVENTORY;
        }
        for (ItemStack leftover : leftovers.values()) {
            recoveryStore.add(new ClaimModeRecoveryEntry(
                    session.playerId(),
                    session.playerName(),
                    Instant.now(),
                    snapshot.slot(),
                    ClaimModeItemCodec.summary(leftover),
                    ClaimModeItemCodec.serialize(leftover),
                    "inventory-full"
            ));
        }
        return RestoreOutcome.RECOVERY;
    }

    private boolean restoreExact(PlayerInventory inventory, String slot, ItemStack item) {
        if (slot.startsWith("hotbar-")) {
            int hotbarSlot = Integer.parseInt(slot.substring("hotbar-".length()));
            ItemStack current = inventory.getItem(hotbarSlot);
            if (canOverwriteForRestore(current)) {
                inventory.setItem(hotbarSlot, item);
                return true;
            }
            return false;
        }
        if ("offhand".equals(slot)) {
            ItemStack current = inventory.getItemInOffHand();
            if (canOverwriteForRestore(current)) {
                inventory.setItemInOffHand(item);
                return true;
            }
        }
        return false;
    }

    private boolean canOverwriteForRestore(ItemStack item) {
        return isEmpty(item) || toolRegistry.isClaimModeTool(item);
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.getType() == Material.AIR;
    }

    private enum RestoreOutcome {
        EMPTY("empty"),
        EXACT("exact"),
        INVENTORY("inventory"),
        RECOVERY("recovery");

        private final String historyValue;

        RestoreOutcome(String historyValue) {
            this.historyValue = historyValue;
        }

        String historyValue() {
            return historyValue;
        }
    }
}
