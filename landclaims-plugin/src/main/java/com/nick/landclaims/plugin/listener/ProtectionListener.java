package com.nick.landclaims.plugin.listener;

import com.nick.landclaims.api.protection.ClaimProtectionResult;
import com.nick.landclaims.plugin.claim.Claim;
import com.nick.landclaims.plugin.claim.ClaimChunk;
import com.nick.landclaims.plugin.claim.ClaimIndex;
import com.nick.landclaims.plugin.message.MessageService;
import com.nick.landclaims.plugin.protection.ProtectionService;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerDropItemEvent;

public final class ProtectionListener implements Listener {
    private static final String BYPASS_PERMISSION = "landclaims.bypass.protection";

    private final ProtectionService protectionService;
    private final ClaimIndex claimIndex;
    private final MessageService messageService;

    public ProtectionListener(ProtectionService protectionService, ClaimIndex claimIndex) {
        this(protectionService, claimIndex, null);
    }

    public ProtectionListener(ProtectionService protectionService, ClaimIndex claimIndex, MessageService messageService) {
        this.protectionService = Objects.requireNonNull(protectionService, "protectionService");
        this.claimIndex = Objects.requireNonNull(claimIndex, "claimIndex");
        this.messageService = messageService;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (isDeniedWithMessage(event.getBlock(), event.getPlayer(), "break")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (isDeniedWithMessage(event.getBlock(), event.getPlayer(), "build")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) {
            return;
        }

        String flagKey = event.getAction() == Action.PHYSICAL && clickedBlock.getType() == Material.FARMLAND
                ? "crop_trample"
                : resolveInteractFlag(clickedBlock.getType());
        if (isDeniedWithMessage(clickedBlock, event.getPlayer(), flagKey)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPistonExtend(BlockPistonExtendEvent event) {
        if (pistonTouchesProtectedClaim(event.getBlocks(), event.getDirection())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPistonRetract(BlockPistonRetractEvent event) {
        if (pistonTouchesProtectedClaim(event.getBlocks(), event.getDirection())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        removeProtectedBlocks(event.blockList(), "explosion_damage");
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        removeProtectedBlocks(event.blockList(), "explosion_damage");
    }

    @EventHandler
    public void onBlockSpread(BlockSpreadEvent event) {
        if (isDenied(event.getBlock(), null, permission -> false, "fire_spread")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockFromTo(BlockFromToEvent event) {
        if (isDenied(event.getToBlock(), null, permission -> false, "fluid_flow")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (isDenied(event.getBlock(), actorUuid(event.getEntity()), permission -> false, "mob_griefing")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityInteract(EntityInteractEvent event) {
        if (event.getBlock().getType() != Material.FARMLAND) {
            return;
        }

        if (isDenied(event.getBlock(), actorUuid(event.getEntity()), permission -> false, "crop_trample")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Entity damagedEntity = event.getEntity();
        UUID actorUuid = actorUuid(event.getDamager());
        Predicate<String> permissionCheck = event.getDamager() instanceof Player player ? player::hasPermission : permission -> false;
        if (isDenied(damagedEntity.getLocation().getBlock(), actorUuid, permissionCheck, "entity_damage")) {
            event.setCancelled(true);
            if (event.getDamager() instanceof Player player) {
                claimAt(damagedEntity.getLocation().getBlock()).ifPresent(claim -> sendDenied(player, claim));
            }
        }
    }

    @EventHandler
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        Entity entity = event.getEntity();
        UUID actorUuid = actorUuid(entity);
        Predicate<String> permissionCheck = entity instanceof Player player ? player::hasPermission : permission -> false;
        if (isDenied(event.getItem().getLocation().getBlock(), actorUuid, permissionCheck, "item_pickup")) {
            event.setCancelled(true);
            if (entity instanceof Player player) {
                claimAt(event.getItem().getLocation().getBlock()).ifPresent(claim -> sendDenied(player, claim));
            }
        }
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (isDeniedWithMessage(event.getPlayer().getLocation().getBlock(), event.getPlayer(), "item_drop")) {
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

    private boolean isDeniedWithMessage(Block block, Player player, String flagKey) {
        Optional<Claim> claim = claimAt(block);
        if (claim.isEmpty()) {
            return false;
        }

        boolean denied = checkProtection(claimChunk(block), player.getUniqueId(), player::hasPermission, flagKey)
                .filter(result -> result != ClaimProtectionResult.ALLOW)
                .isPresent();
        if (denied) {
            sendDenied(player, claim.orElseThrow());
        }
        return denied;
    }

    private boolean isDenied(Block block, UUID actorUuid, Predicate<String> permissionCheck, String flagKey) {
        ClaimChunk claimChunk = claimChunk(block);
        return checkProtection(claimChunk, actorUuid, permissionCheck, flagKey)
                .filter(result -> result != ClaimProtectionResult.ALLOW)
                .isPresent();
    }

    private Optional<Claim> claimAt(Block block) {
        return claimIndex.findAt(claimChunk(block));
    }

    private ClaimChunk claimChunk(Block block) {
        Chunk chunk = block.getChunk();
        return new ClaimChunk(block.getWorld().getUID(), chunk.getX(), chunk.getZ());
    }

    private boolean pistonTouchesProtectedClaim(java.util.List<Block> movedBlocks, org.bukkit.block.BlockFace direction) {
        for (Block movedBlock : movedBlocks) {
            if (isDenied(movedBlock, null, permission -> false, "piston_protection")
                    || isDenied(movedBlock.getRelative(direction), null, permission -> false, "piston_protection")) {
                return true;
            }
        }
        return false;
    }

    private void removeProtectedBlocks(java.util.List<Block> blocks, String flagKey) {
        Iterator<Block> iterator = blocks.iterator();
        while (iterator.hasNext()) {
            if (isDenied(iterator.next(), null, permission -> false, flagKey)) {
                iterator.remove();
            }
        }
    }

    private UUID actorUuid(Entity entity) {
        if (entity instanceof Player player) {
            return player.getUniqueId();
        }
        if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player.getUniqueId();
        }
        return null;
    }

    private void sendDenied(Player player, Claim claim) {
        if (messageService == null) {
            return;
        }
        player.sendMessage(messageService.render("claim.denied", Map.of("claim_name", claim.name())));
    }

    static String resolveInteractFlag(Material material) {
        if (Tag.DOORS.isTagged(material) || Tag.TRAPDOORS.isTagged(material) || Tag.FENCE_GATES.isTagged(material)) {
            return "door_access";
        }
        if (Tag.BUTTONS.isTagged(material) || Tag.PRESSURE_PLATES.isTagged(material)) {
            return "switch_access";
        }
        if (material == Material.REPEATER || material == Material.COMPARATOR) {
            return "redstone_access";
        }
        if (isContainerMaterial(material)) {
            return "container_access";
        }
        return "interact";
    }

    private static boolean isContainerMaterial(Material material) {
        return switch (material) {
            case BARREL,
                    BLAST_FURNACE,
                    BREWING_STAND,
                    CHEST,
                    CHISELED_BOOKSHELF,
                    DISPENSER,
                    DROPPER,
                    ENDER_CHEST,
                    FURNACE,
                    HOPPER,
                    JUKEBOX,
                    LECTERN,
                    SHULKER_BOX,
                    SMOKER,
                    TRAPPED_CHEST -> true;
            default -> material.name().endsWith("_SHULKER_BOX");
        };
    }
}
