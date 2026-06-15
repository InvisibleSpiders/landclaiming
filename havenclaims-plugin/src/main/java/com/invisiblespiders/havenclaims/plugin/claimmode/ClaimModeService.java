package com.invisiblespiders.havenclaims.plugin.claimmode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
        List<ClaimModeItemSnapshot> snapshots = snapshotClaimModeSlots(inventory);
        Map<Integer, ItemStack> toolItems = createClaimModeToolItems();
        clearClaimModeSlots(inventory);
        sessions.put(playerId, new ClaimModeSession(playerId, player.getName(), Instant.now(), snapshots));
        placeClaimModeTools(inventory, toolItems);
        return EnterResult.ENTERED;
    }

    public ExitResult exit(Player player, ExitReason reason) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(reason, "reason");
        UUID playerId = player.getUniqueId();
        ClaimModeSession session = sessions.get(playerId);
        if (session == null) {
            return ExitResult.NOT_ACTIVE;
        }

        PlayerInventory inventory = player.getInventory();
        removeClaimModeTools(inventory);

        List<String> restoreResults = new ArrayList<>();
        boolean partial = false;
        boolean recovery = false;
        List<ClaimModeItemSnapshot> remainingSnapshots = new ArrayList<>(session.snapshots());
        sessions.put(playerId, retainedSession(session, remainingSnapshots));
        while (!remainingSnapshots.isEmpty()) {
            ClaimModeItemSnapshot snapshot = remainingSnapshots.get(0);
            RestoreOutcome outcome = restoreSnapshot(inventory, session, snapshot);
            restoreResults.add(snapshot.slot() + "=" + outcome.historyValue());
            partial = partial || outcome.partial();
            recovery = recovery || outcome.recovery();
            remainingSnapshots.remove(0);
            sessions.put(playerId, retainedSession(session, remainingSnapshots));
        }

        appendHistory(session, reason, restoreResults);
        sessions.remove(playerId);
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
                try {
                    exit(player, reason);
                } catch (RuntimeException exception) {
                    // Bulk restore must remain best-effort so one damaged session cannot strand others.
                }
            }
        }
    }

    public Component fallbackMessage() {
        return fallbackMessage;
    }

    private ClaimModeSession retainedSession(ClaimModeSession session, List<ClaimModeItemSnapshot> snapshots) {
        return new ClaimModeSession(session.playerId(), session.playerName(), session.enteredAt(), snapshots);
    }

    private List<ClaimModeItemSnapshot> snapshotClaimModeSlots(PlayerInventory inventory) {
        List<ClaimModeItemSnapshot> snapshots = new ArrayList<>();
        for (int slot = 0; slot <= 8; slot++) {
            snapshots.add(ClaimModeItemSnapshot.from("hotbar-" + slot, inventory.getItem(slot)));
        }
        snapshots.add(ClaimModeItemSnapshot.from("offhand", inventory.getItemInOffHand()));
        return snapshots;
    }

    private Map<Integer, ItemStack> createClaimModeToolItems() {
        Map<Integer, ItemStack> toolItems = new LinkedHashMap<>();
        for (ClaimModeTool tool : toolRegistry.toolsBySlot().values()) {
            toolItems.put(tool.slot(), toolRegistry.createItem(tool.id()));
        }
        return toolItems;
    }

    private void clearClaimModeSlots(PlayerInventory inventory) {
        for (int slot = 0; slot <= 8; slot++) {
            inventory.setItem(slot, null);
        }
        inventory.setItemInOffHand(null);
    }

    private void placeClaimModeTools(PlayerInventory inventory, Map<Integer, ItemStack> toolItems) {
        for (Map.Entry<Integer, ItemStack> entry : toolItems.entrySet()) {
            inventory.setItem(entry.getKey(), entry.getValue());
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
        boolean emergencyPending = false;
        for (ItemStack leftover : leftovers.values()) {
            ClaimModeRecoveryEntry entry = recoveryEntry(session, snapshot.slot(), leftover, "inventory-full");
            try {
                recoveryStore.add(entry);
            } catch (RuntimeException exception) {
                recoveryStore.addPendingOnly(emergencyRecoveryEntry(entry));
                emergencyPending = true;
            }
        }
        return emergencyPending ? RestoreOutcome.EMERGENCY_RECOVERY : RestoreOutcome.RECOVERY;
    }

    private ClaimModeRecoveryEntry recoveryEntry(ClaimModeSession session, String slot, ItemStack item, String reason) {
        return new ClaimModeRecoveryEntry(
                session.playerId(),
                session.playerName(),
                Instant.now(),
                slot,
                ClaimModeItemCodec.summary(item),
                ClaimModeItemCodec.serialize(item),
                reason
        );
    }

    private ClaimModeRecoveryEntry emergencyRecoveryEntry(ClaimModeRecoveryEntry entry) {
        return new ClaimModeRecoveryEntry(
                entry.playerId(),
                entry.playerName(),
                Instant.now(),
                entry.originalSlot(),
                entry.summary(),
                entry.backup(),
                "inventory-full;recovery-log-failed;pending-memory"
        );
    }

    private void appendHistory(ClaimModeSession session, ExitReason reason, List<String> restoreResults) {
        try {
            history.append(session.playerId(), session.playerName(), session.enteredAt(), Instant.now(),
                    reason, session.snapshots(), restoreResults);
        } catch (RuntimeException exception) {
            restoreResults.add("history=recovery-log-failed;restore-complete");
        }
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
        RECOVERY("recovery"),
        EMERGENCY_RECOVERY("recovery-log-failed;pending-memory");

        private final String historyValue;

        RestoreOutcome(String historyValue) {
            this.historyValue = historyValue;
        }

        String historyValue() {
            return historyValue;
        }

        boolean partial() {
            return this == INVENTORY || recovery();
        }

        boolean recovery() {
            return this == RECOVERY || this == EMERGENCY_RECOVERY;
        }
    }
}
