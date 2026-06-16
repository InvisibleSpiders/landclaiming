package com.invisiblespiders.havenclaims.plugin.accrual;

import com.invisiblespiders.havenclaims.plugin.limit.LimitService;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public final class BlockAccrualService {
    private final LimitService limitService;
    private final AfkDetector afkDetector;
    private final int blocksPerInterval;
    private final int maxBlocks;
    private final String afkMode;     // "reduced" or "zero"
    private final double afkMultiplier;
    private BukkitRunnable task;

    public BlockAccrualService(
            LimitService limitService,
            AfkDetector afkDetector,
            int blocksPerInterval,
            int maxBlocks,
            String afkMode,
            double afkMultiplier
    ) {
        this.limitService = Objects.requireNonNull(limitService, "limitService");
        this.afkDetector = Objects.requireNonNull(afkDetector, "afkDetector");
        this.blocksPerInterval = blocksPerInterval;
        this.maxBlocks = maxBlocks;
        this.afkMode = Objects.requireNonNull(afkMode, "afkMode");
        this.afkMultiplier = afkMultiplier;
    }

    public void start(JavaPlugin plugin, long intervalTicks) {
        if (task != null) task.cancel();
        task = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    accrueFor(player.getUniqueId());
                }
            }
        };
        task.runTaskTimer(plugin, intervalTicks, intervalTicks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    void accrueFor(UUID playerId) {
        boolean afk = afkDetector.isAfk(playerId);
        int grant;
        if (!afk) {
            grant = blocksPerInterval;
        } else if ("zero".equalsIgnoreCase(afkMode)) {
            return;
        } else {
            grant = (int) Math.floor(blocksPerInterval * afkMultiplier);
        }
        if (grant <= 0) return;

        if (maxBlocks > 0) {
            int current = limitService.getBlockLimit(playerId);
            if (current >= maxBlocks) return;
            grant = Math.min(grant, maxBlocks - current);
        }

        limitService.addBlocks(playerId, grant);
    }
}
