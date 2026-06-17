package com.invisiblespiders.havenclaims.plugin.listener;

import com.invisiblespiders.havenclaims.api.protection.ClaimProtectionResult;
import com.invisiblespiders.havenclaims.plugin.claim.Claim;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimChunk;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimIndex;
import com.invisiblespiders.havenclaims.plugin.message.MessageService;
import com.invisiblespiders.havenclaims.plugin.protection.ProtectionService;
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
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public final class ProtectionListener implements Listener {
    private static final String BYPASS_PERMISSION = "havenclaims.bypass.protection";

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
                : resolveInteractFlag(clickedBlock);
        if (isDeniedWithMessage(clickedBlock, event.getPlayer(), flagKey)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPistonExtend(BlockPistonExtendEvent event) {
        if (pistonTouchesProtectedClaim(event.getBlock(), event.getBlocks(), event.getDirection(), true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPistonRetract(BlockPistonRetractEvent event) {
        if (pistonTouchesProtectedClaim(event.getBlock(), event.getBlocks(), event.getDirection().getOppositeFace(), false)) {
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
        Block toBlock = event.getBlock();
        if (isDeniedEnteringClaimExact(
                claimChunk(toBlock), toBlock.getX(), toBlock.getZ(),
                claimChunk(event.getSource()),
                "fire_spread")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockFromTo(BlockFromToEvent event) {
        Block toBlock = event.getToBlock();
        // Use exact destination block coordinates rather than chunk-min-corner approximation
        // to correctly guard partial-chunk claims.
        if (isDeniedEnteringClaimExact(
                claimChunk(toBlock), toBlock.getX(), toBlock.getZ(),
                claimChunk(event.getBlock()),
                "fluid_flow")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockRedstone(BlockRedstoneEvent event) {
        // Only care about blocks becoming powered (new > old)
        if (event.getNewCurrent() <= event.getOldCurrent()) {
            return;
        }
        Block block = event.getBlock();
        ClaimChunk destChunk = claimChunk(block);
        Optional<Claim> destClaim = claimIndex.findAt(destChunk);
        if (destClaim.isEmpty()) {
            return;
        }
        // Block-exact containment: ignore if block is outside the claim's selected region
        if (!destClaim.get().region().containsBlock(block.getX(), block.getZ())) {
            return;
        }
        // Check whether the flag is enabled for this claim
        Optional<ClaimProtectionResult> result = checkProtection(
                destChunk, block.getX(), block.getZ(), null, permission -> false, "redstone_signal");
        if (result.filter(r -> r != ClaimProtectionResult.ALLOW).isPresent()) {
            event.setNewCurrent(event.getOldCurrent());
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
            int blockX,
            int blockZ,
            UUID actorUuid,
            Predicate<String> permissionCheck,
            String flagKey
    ) {
        Objects.requireNonNull(claimChunk, "claimChunk");
        Objects.requireNonNull(permissionCheck, "permissionCheck");
        Objects.requireNonNull(flagKey, "flagKey");

        Optional<Claim> claimOpt = claimIndex.findAt(claimChunk);
        if (claimOpt.isEmpty()) {
            return Optional.empty();
        }
        Claim claim = claimOpt.get();

        // Block-exact containment check (chunk lookup is the fast first pass; region is exact)
        if (!claim.region().containsBlock(blockX, blockZ)) {
            return Optional.empty();
        }

        if (permissionCheck.test(BYPASS_PERMISSION) || permissionCheck.test(BYPASS_PERMISSION + "." + flagKey)) {
            return Optional.of(ClaimProtectionResult.ALLOW);
        }

        return Optional.of(protectionService.checkClaimFlag(claim, actorUuid, flagKey));
    }

    private boolean isDeniedWithMessage(Block block, Player player, String flagKey) {
        int blockX = block.getX();
        int blockZ = block.getZ();
        Optional<ClaimProtectionResult> result = checkProtection(
                claimChunk(block), blockX, blockZ, player.getUniqueId(), player::hasPermission, flagKey);
        boolean denied = result.filter(r -> r != ClaimProtectionResult.ALLOW).isPresent();
        if (denied) {
            claimAt(block).ifPresent(claim -> sendDenied(player, claim));
        }
        return denied;
    }

    private boolean isDenied(Block block, UUID actorUuid, Predicate<String> permissionCheck, String flagKey) {
        return isDenied(claimChunk(block), block.getX(), block.getZ(), actorUuid, permissionCheck, flagKey);
    }

    private boolean isDenied(ClaimChunk claimChunk, UUID actorUuid, Predicate<String> permissionCheck, String flagKey) {
        // Use chunk min-corner only as a legacy test overload; production paths pass exact coordinates.
        return isDenied(claimChunk, claimChunk.chunkX() * 16, claimChunk.chunkZ() * 16, actorUuid, permissionCheck, flagKey);
    }

    private boolean isDenied(ClaimChunk claimChunk, int blockX, int blockZ, UUID actorUuid, Predicate<String> permissionCheck, String flagKey) {
        return checkProtection(claimChunk, blockX, blockZ, actorUuid, permissionCheck, flagKey)
                .filter(result -> result != ClaimProtectionResult.ALLOW)
                .isPresent();
    }

    /** Fluid/fire-spread variant that uses exact destination coordinates for partial-chunk claim accuracy. */
    boolean isDeniedEnteringClaimExact(
            ClaimChunk destinationChunk, int blockX, int blockZ,
            ClaimChunk sourceChunk,
            String flagKey) {
        Optional<Claim> destinationClaim = claimIndex.findAt(destinationChunk);
        if (destinationClaim.isEmpty()) {
            return false;
        }
        Optional<Claim> sourceClaim = claimIndex.findAt(sourceChunk);
        if (sourceClaim.isPresent() && sourceClaim.orElseThrow().id().equals(destinationClaim.orElseThrow().id())) {
            return false;
        }
        return checkProtection(destinationChunk, blockX, blockZ, null, permission -> false, flagKey)
                .filter(result -> result != ClaimProtectionResult.ALLOW)
                .isPresent();
    }

    private Optional<Claim> claimAt(Block block) {
        return claimIndex.findAt(claimChunk(block))
                .filter(claim -> claim.region().containsBlock(block.getX(), block.getZ()));
    }

    private ClaimChunk claimChunk(Block block) {
        Chunk chunk = block.getChunk();
        return new ClaimChunk(block.getWorld().getUID(), chunk.getX(), chunk.getZ());
    }

    private boolean pistonTouchesProtectedClaim(
            Block pistonBlock,
            java.util.List<Block> movedBlocks,
            BlockFace movementDirection,
            boolean includePistonHeadDestination
    ) {
        // Check each source block with exact coordinates
        for (Block source : movedBlocks) {
            if (isDenied(claimChunk(source), source.getX(), source.getZ(), null, permission -> false, "piston_protection")) {
                return true;
            }
        }
        // Check each destination block with exact coordinates
        for (Block source : movedBlocks) {
            Block dest = source.getRelative(movementDirection);
            if (isDenied(claimChunk(dest), dest.getX(), dest.getZ(), null, permission -> false, "piston_protection")) {
                return true;
            }
        }
        if (includePistonHeadDestination) {
            Block pistonDest = pistonBlock.getRelative(movementDirection);
            if (isDenied(claimChunk(pistonDest), pistonDest.getX(), pistonDest.getZ(), null, permission -> false, "piston_protection")) {
                return true;
            }
        }
        return false;
    }

    // null actorUuid signals "no player actor" — isDenied skips owner/member checks and
    // treats the interaction as coming from an untrusted non-player source.
    boolean pistonTouchesProtectedClaim(java.util.List<ClaimChunk> sourceChunks, java.util.List<ClaimChunk> destinationChunks) {
        for (ClaimChunk sourceChunk : sourceChunks) {
            if (isDenied(sourceChunk, null, permission -> false, "piston_protection")) {
                return true;
            }
        }
        for (ClaimChunk destinationChunk : destinationChunks) {
            if (isDenied(destinationChunk, null, permission -> false, "piston_protection")) {
                return true;
            }
        }
        return false;
    }

    boolean isDeniedEnteringClaim(ClaimChunk sourceChunk, ClaimChunk destinationChunk, String flagKey) {
        Optional<Claim> destinationClaim = claimIndex.findAt(destinationChunk);
        if (destinationClaim.isEmpty()) {
            return false;
        }
        Optional<Claim> sourceClaim = claimIndex.findAt(sourceChunk);
        if (sourceClaim.isPresent() && sourceClaim.orElseThrow().id().equals(destinationClaim.orElseThrow().id())) {
            return false;
        }
        return isDenied(destinationChunk, null, permission -> false, flagKey);
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

    // Maps the interacted block to the most-specific protection flag so each flag can be toggled independently.
    static String resolveInteractFlag(Block block) {
        Material material = block.getType();
        if (Tag.DOORS.isTagged(material) || Tag.TRAPDOORS.isTagged(material) || Tag.FENCE_GATES.isTagged(material)) {
            return "door_access";
        }
        if (Tag.BUTTONS.isTagged(material) || Tag.PRESSURE_PLATES.isTagged(material)) {
            return "switch_access";
        }
        if (material == Material.REPEATER || material == Material.COMPARATOR) {
            return "redstone_access";
        }
        BlockState state = block.getState();
        if (state instanceof Container || material == Material.ENDER_CHEST) {
            return "container_access";
        }
        return "interact";
    }
}
