package com.invisiblespiders.havenclaims.plugin.claimmode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.invisiblespiders.havenclaims.plugin.claim.ClaimChunk;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimService;
import com.invisiblespiders.havenclaims.plugin.listener.ClaimToolListener;
import com.invisiblespiders.havenclaims.plugin.message.MessageService;
import com.invisiblespiders.havenclaims.plugin.selection.SelectionService;
import com.invisiblespiders.havenclaims.plugin.tool.ClaimToolService;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

@SuppressWarnings({"deprecation", "removal"})
class ClaimModeListenerTest {
    private static final NamespacedKey TOOL_KEY = new NamespacedKey("havenclaims", "claim_mode_tool");
    private static final Component FALLBACK = Component.text("claim mode fallback");

    @Test
    void claimModeInteractPreemptsLegacyClaimToolListener() throws Exception {
        Method claimModeInteract = ClaimModeListener.class.getDeclaredMethod("onInteract", PlayerInteractEvent.class);
        Method claimModeSwap = ClaimModeListener.class.getDeclaredMethod("onSwap", PlayerSwapHandItemsEvent.class);
        Method legacyInteract = ClaimToolListener.class.getDeclaredMethod("onPlayerInteract", PlayerInteractEvent.class);
        Method legacySwap = ClaimToolListener.class.getDeclaredMethod("onPlayerSwapHandItems", PlayerSwapHandItemsEvent.class);

        EventHandler claimModeHandler = claimModeInteract.getAnnotation(EventHandler.class);
        EventHandler claimModeSwapHandler = claimModeSwap.getAnnotation(EventHandler.class);
        EventHandler legacyHandler = legacyInteract.getAnnotation(EventHandler.class);
        EventHandler legacySwapHandler = legacySwap.getAnnotation(EventHandler.class);

        assertThat(claimModeHandler.priority()).isEqualTo(EventPriority.LOWEST);
        assertThat(claimModeSwapHandler.priority()).isEqualTo(EventPriority.LOWEST);
        assertThat(legacyHandler.ignoreCancelled()).isTrue();
        assertThat(legacySwapHandler.ignoreCancelled()).isTrue();
    }

    @Test
    void blocksConfiguredCommandForActivePlayerAndUsesMessageServicePlaceholder() {
        Fixture fixture = fixture(true);
        MessageService messages = new MessageService(Map.of(
                "claim-mode.blocked-command", "Blocked <command>"
        ));
        ClaimModeListener listener = listener(fixture.service(), messages);
        PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(fixture.player(), "/storage open", Set.of());

        listener.onCommand(event);

        assertThat(event.isCancelled()).isTrue();
        verify(fixture.player()).sendMessage(Component.text("Blocked storage"));
    }

    @Test
    void leavesCommandForInactivePlayerAlone() {
        Fixture fixture = fixture(false);
        ClaimModeListener listener = listener(fixture.service());
        PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(fixture.player(), "/storage open", Set.of());

        listener.onCommand(event);

        assertThat(event.isCancelled()).isFalse();
        verify(fixture.player(), never()).sendMessage(any(Component.class));
    }

    @Test
    void blocksDropPickupAndSwapForActivePlayers() {
        Fixture fixture = fixture(true);
        ClaimModeListener listener = listener(fixture.service());

        PlayerDropItemEvent drop = new PlayerDropItemEvent(fixture.player(), mock(Item.class));
        EntityPickupItemEvent pickup = new EntityPickupItemEvent(fixture.player(), mock(Item.class), 0);
        PlayerSwapHandItemsEvent swap = new PlayerSwapHandItemsEvent(
                fixture.player(),
                item(Material.STONE),
                item(Material.SHIELD)
        );

        listener.onDrop(drop);
        listener.onPickup(pickup);
        listener.onSwap(swap);

        assertThat(drop.isCancelled()).isTrue();
        assertThat(pickup.isCancelled()).isTrue();
        assertThat(swap.isCancelled()).isTrue();
    }

    @Test
    void ignoresPickupFromNonPlayerEntity() {
        Fixture fixture = fixture(true);
        ClaimModeListener listener = listener(fixture.service());
        EntityPickupItemEvent pickup = new EntityPickupItemEvent(mock(org.bukkit.entity.Zombie.class), mock(Item.class), 0);

        listener.onPickup(pickup);

        assertThat(pickup.isCancelled()).isFalse();
    }

    @Test
    void blocksContainerRightClickForActivePlayerWithoutTool() {
        Fixture fixture = fixture(true);
        ClaimModeListener listener = listener(fixture.service());
        Block chest = mock(Block.class);
        when(chest.getState()).thenReturn(mock(Container.class));
        PlayerInteractEvent event = new PlayerInteractEvent(
                fixture.player(),
                Action.RIGHT_CLICK_BLOCK,
                null,
                chest,
                BlockFace.UP
        );

        listener.onInteract(event);

        assertThat(event.isCancelled()).isTrue();
        assertThat(event.useInteractedBlock()).isEqualTo(Event.Result.DENY);
        assertThat(event.useItemInHand()).isEqualTo(Event.Result.DENY);
    }

    @Test
    void leavesContainerRightClickForInactivePlayerAlone() {
        Fixture fixture = fixture(false);
        ClaimModeListener listener = listener(fixture.service());
        Block chest = mock(Block.class);
        when(chest.getState()).thenReturn(mock(Container.class));
        PlayerInteractEvent event = new PlayerInteractEvent(
                fixture.player(),
                Action.RIGHT_CLICK_BLOCK,
                null,
                chest,
                BlockFace.UP
        );

        listener.onInteract(event);

        assertThat(event.isCancelled()).isFalse();
    }

    @Test
    void blocksHotbarNumberKeyAndDragInventoryInteractions() {
        Fixture fixture = fixture(true);
        ClaimModeListener listener = listener(fixture.service());

        InventoryClickEvent hotbarClick = clickEvent(fixture.player(), 0, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        InventoryClickEvent numberKey = clickEvent(fixture.player(), 18, ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP);
        InventoryClickEvent hotbarAction = clickEvent(
                fixture.player(),
                18,
                ClickType.LEFT,
                InventoryAction.valueOf("HOTBAR_MOVE_AND_READD")
        );
        InventoryDragEvent hotbarDrag = dragEvent(fixture.player(), Map.of(36, item(Material.STONE)), Set.of(36));
        InventoryDragEvent offhandDrag = dragEvent(fixture.player(), Map.of(45, item(Material.STONE)));

        listener.onInventoryClick(hotbarClick);
        listener.onInventoryClick(numberKey);
        listener.onInventoryClick(hotbarAction);
        listener.onInventoryDrag(hotbarDrag);
        listener.onInventoryDrag(offhandDrag);

        assertThat(hotbarClick.isCancelled()).isTrue();
        assertThat(numberKey.isCancelled()).isTrue();
        assertThat(hotbarAction.isCancelled()).isTrue();
        assertThat(hotbarDrag.isCancelled()).isTrue();
        assertThat(offhandDrag.isCancelled()).isTrue();
    }

    @Test
    void blocksOffhandGuiSwap() {
        Fixture fixture = fixture(true);
        ClaimModeListener listener = listener(fixture.service());
        InventoryClickEvent swapOffhandClick = clickEvent(
                fixture.player(),
                20,
                ClickType.SWAP_OFFHAND,
                InventoryAction.PICKUP_ALL,
                40
        );

        listener.onInventoryClick(swapOffhandClick);

        assertThat(swapOffhandClick.isCancelled()).isTrue();
    }

    @Test
    void blocksTopInventoryClicksAndMovesForActivePlayers() {
        Fixture fixture = fixture(true);
        ClaimModeListener listener = listener(fixture.service());
        InventoryClickEvent topClick = topInventoryClick(fixture.player(), 0, InventoryAction.PICKUP_ALL);
        InventoryClickEvent bottomShiftMove = bottomInventoryClick(
                fixture.player(),
                20,
                ClickType.SHIFT_LEFT,
                InventoryAction.MOVE_TO_OTHER_INVENTORY
        );
        InventoryDragEvent topDrag = topInventoryDrag(fixture.player(), Map.of(0, item(Material.STONE)));

        listener.onInventoryClick(topClick);
        listener.onInventoryClick(bottomShiftMove);
        listener.onInventoryDrag(topDrag);

        verify(topClick).setCancelled(true);
        verify(bottomShiftMove).setCancelled(true);
        assertThat(topDrag.isCancelled()).isTrue();
    }

    @Test
    void leavesTopInventoryClicksForInactivePlayersAlone() {
        Fixture fixture = fixture(false);
        ClaimModeListener listener = listener(fixture.service());
        InventoryClickEvent topClick = topInventoryClick(fixture.player(), 0, InventoryAction.PICKUP_ALL);

        listener.onInventoryClick(topClick);

        verify(topClick, never()).setCancelled(true);
    }

    @Test
    void doesNotMistakePlayerInventoryRawSlotsZeroThroughEightForHotbar() {
        Fixture fixture = fixture(true);
        ClaimModeListener listener = listener(fixture.service());
        InventoryClickEvent playerInventoryRawSlot = playerInventoryClick(
                fixture.player(),
                5,
                ClickType.LEFT,
                InventoryAction.PICKUP_ALL
        );
        InventoryDragEvent playerInventoryDrag = dragEvent(fixture.player(), Map.of(5, item(Material.STONE)));

        listener.onInventoryClick(playerInventoryRawSlot);
        listener.onInventoryDrag(playerInventoryDrag);

        verify(playerInventoryRawSlot, never()).setCancelled(true);
        assertThat(playerInventoryDrag.isCancelled()).isFalse();
    }

    @Test
    void cancelsTopInventoryRawSlotsZeroThroughEightBecauseTheyAreExternalInventory() {
        Fixture fixture = fixture(true);
        ClaimModeListener listener = listener(fixture.service());
        InventoryClickEvent topRawSlot = topInventoryClick(fixture.player(), 5, InventoryAction.PICKUP_ALL);

        listener.onInventoryClick(topRawSlot);

        verify(topRawSlot).setCancelled(true);
    }

    @Test
    void blocksTaggedClaimModeToolInventoryMovesOutsideHotbar() {
        Fixture fixture = fixture(true);
        ClaimModeListener listener = listener(fixture.service());
        ItemStack tool = fixture.registry().createItem("claim");
        InventoryClickEvent currentItemClick = clickEvent(fixture.player(), tool, null);
        InventoryClickEvent cursorClick = clickEvent(fixture.player(), null, tool);
        InventoryDragEvent drag = dragEvent(fixture.player(), Map.of(20, tool));

        listener.onInventoryClick(currentItemClick);
        listener.onInventoryClick(cursorClick);
        listener.onInventoryDrag(drag);

        verify(currentItemClick).setCancelled(true);
        verify(cursorClick).setCancelled(true);
        assertThat(drag.isCancelled()).isTrue();
    }

    @Test
    void interactWithEnabledToolCancelsBaseActionAndInvokesHandler() {
        AtomicBoolean handled = new AtomicBoolean(false);
        Fixture fixture = fixture(true, new ClaimModeTool(
                "menu",
                7,
                ClaimModeListenerTest::claimModeItem,
                true,
                "",
                (player, event) -> handled.set(true)
        ));
        ClaimModeListener listener = listener(fixture.service());
        PlayerInteractEvent event = new PlayerInteractEvent(
                fixture.player(),
                Action.RIGHT_CLICK_AIR,
                fixture.registry().createItem("menu"),
                null,
                BlockFace.SELF
        );

        listener.onInteract(event);

        assertThat(event.isCancelled()).isTrue();
        assertThat(event.useItemInHand()).isEqualTo(Event.Result.DENY);
        assertThat(handled).isTrue();
    }

    @Test
    void claimToolInvokesLegacySelectionHandler() {
        SelectionService selectionService = new SelectionService(new ClaimService());
        ClaimToolService claimToolService = mock(ClaimToolService.class);
        ClaimToolListener claimToolListener = new ClaimToolListener(claimToolService, selectionService);
        Fixture fixture = fixture(true, new ClaimModeTool(
                "claim",
                0,
                ClaimModeListenerTest::claimModeItem,
                true,
                "",
                (player, event) -> claimToolListener.handleClaimToolSelection(event)
        ));
        UUID worldId = UUID.randomUUID();
        Chunk firstChunk = chunk(worldId, 0, 0);
        Chunk secondChunk = chunk(worldId, 1, 0);
        Block firstBlock = block(firstChunk);
        Block secondBlock = block(secondChunk);
        Location location = mock(Location.class);
        when(fixture.player().getLocation()).thenReturn(location);
        when(location.getChunk()).thenReturn(firstChunk, secondChunk);
        when(fixture.player().hasPermission("havenclaims.tool.use")).thenReturn(true);
        when(fixture.player().getEffectivePermissions()).thenReturn(Set.of());
        ItemStack tool = fixture.registry().createItem("claim");
        ClaimModeListener claimModeListener = listener(fixture.service());
        when(claimToolService.isClaimTool(tool)).thenReturn(true);

        PlayerInteractEvent first = interact(fixture.player(), tool, firstBlock);
        claimModeListener.onInteract(first);
        assertThat(first.isCancelled()).isTrue();
        assertThat(first.useInteractedBlock()).isEqualTo(Event.Result.DENY);
        assertThat(first.useItemInHand()).isEqualTo(Event.Result.DENY);

        PlayerInteractEvent second = interact(fixture.player(), tool, secondBlock);
        claimModeListener.onInteract(second);

        assertThat(selectionService.pendingSelection(fixture.playerId()))
                .contains(Set.of(new ClaimChunk(worldId, 0, 0), new ClaimChunk(worldId, 1, 0)));
    }

    @Test
    void interactWithDisabledToolCancelsBaseActionAndSendsDisabledMessage() {
        Fixture fixture = fixture(true, new ClaimModeTool(
                "subclaim",
                1,
                ClaimModeListenerTest::claimModeItem,
                false,
                "claim-mode.subclaim-coming-soon",
                (player, event) -> {}
        ));
        MessageService messages = new MessageService(Map.of(
                "claim-mode.subclaim-coming-soon", "Subclaim disabled"
        ));
        ClaimModeListener listener = listener(fixture.service(), messages);
        PlayerInteractEvent event = new PlayerInteractEvent(
                fixture.player(),
                Action.RIGHT_CLICK_AIR,
                fixture.registry().createItem("subclaim"),
                null,
                BlockFace.SELF
        );

        listener.onInteract(event);

        assertThat(event.isCancelled()).isTrue();
        verify(fixture.player()).sendMessage(Component.text("Subclaim disabled"));
    }

    @Test
    void blockBreakAndEntityDamageCancelForActivePlayers() {
        Fixture fixture = fixture(true);
        ClaimModeListener listener = listener(fixture.service());
        BlockBreakEvent breakEvent = new BlockBreakEvent(mock(Block.class), fixture.player());
        EntityDamageByEntityEvent damageEvent = mock(EntityDamageByEntityEvent.class);
        when(damageEvent.getDamager()).thenReturn(fixture.player());

        listener.onBlockBreak(breakEvent);
        listener.onEntityDamage(damageEvent);

        assertThat(breakEvent.isCancelled()).isTrue();
        verify(damageEvent).setCancelled(true);
    }

    @Test
    void quitExitsClaimModeWithLogoutReason() {
        Fixture fixture = fixture(true);
        ClaimModeListener listener = listener(fixture.service());
        PlayerQuitEvent event = new PlayerQuitEvent(
                fixture.player(),
                Component.empty(),
                PlayerQuitEvent.QuitReason.DISCONNECTED
        );

        listener.onQuit(event);

        verify(fixture.service()).exit(fixture.player(), ClaimModeService.ExitReason.LOGOUT);
    }

    private static ClaimModeListener listener(ClaimModeService service) {
        return listener(service, null);
    }

    private static ClaimModeListener listener(ClaimModeService service, MessageService messageService) {
        return new ClaimModeListener(
                service,
                new ClaimModeCommandGuard(new ClaimModeConfig(
                        true,
                        5,
                        List.of("storage", "pay"),
                        List.of("claimmode", "cm", "claim")
                )),
                messageService
        );
    }

    private static Fixture fixture(boolean active) {
        return fixture(active, new ClaimModeTool(
                "claim",
                0,
                ClaimModeListenerTest::claimModeItem,
                true,
                "",
                (player, event) -> {}
        ));
    }

    private static Fixture fixture(boolean active, ClaimModeTool tool) {
        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        ClaimModeToolRegistry registry = new ClaimModeToolRegistry(TOOL_KEY, List.of(tool));
        ClaimModeService service = mock(ClaimModeService.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(service.isInClaimMode(playerId)).thenReturn(active);
        when(service.toolRegistry()).thenReturn(registry);
        when(service.fallbackMessage()).thenReturn(FALLBACK);
        return new Fixture(player, playerId, service, registry);
    }

    private static InventoryClickEvent clickEvent(
            Player player,
            int rawSlot,
            ClickType clickType,
            InventoryAction action
    ) {
        return clickEvent(player, rawSlot, clickType, action, clickType == ClickType.NUMBER_KEY ? 0 : -1);
    }

    private static InventoryClickEvent clickEvent(
            Player player,
            int rawSlot,
            ClickType clickType,
            InventoryAction action,
            int hotbarButton
    ) {
        InventoryClickEvent event = new InventoryClickEvent(
                view(player),
                rawSlot >= 0 && rawSlot <= 8 ? InventoryType.SlotType.QUICKBAR : InventoryType.SlotType.CONTAINER,
                rawSlot,
                clickType,
                action,
                hotbarButton
        );
        return event;
    }

    private static PlayerInteractEvent interact(Player player, ItemStack item, Block block) {
        return new PlayerInteractEvent(
                player,
                Action.RIGHT_CLICK_BLOCK,
                item,
                block,
                BlockFace.UP,
                EquipmentSlot.HAND
        );
    }

    private static Block block(Chunk chunk) {
        Block block = mock(Block.class);
        when(block.getChunk()).thenReturn(chunk);
        return block;
    }

    private static Chunk chunk(UUID worldId, int chunkX, int chunkZ) {
        World world = mock(World.class);
        Chunk chunk = mock(Chunk.class);
        when(world.getUID()).thenReturn(worldId);
        when(chunk.getWorld()).thenReturn(world);
        when(chunk.getX()).thenReturn(chunkX);
        when(chunk.getZ()).thenReturn(chunkZ);
        return chunk;
    }

    private static InventoryClickEvent playerInventoryClick(
            Player player,
            int rawSlot,
            ClickType clickType,
            InventoryAction action
    ) {
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        InventoryView view = view(player);
        Inventory bottom = view.getBottomInventory();
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getView()).thenReturn(view);
        when(event.getClickedInventory()).thenReturn(bottom);
        when(event.getSlot()).thenReturn(rawSlot);
        when(event.getRawSlot()).thenReturn(rawSlot);
        when(event.getClick()).thenReturn(clickType);
        when(event.getAction()).thenReturn(action);
        when(event.getHotbarButton()).thenReturn(-1);
        when(event.getSlotType()).thenReturn(InventoryType.SlotType.CONTAINER);
        return event;
    }

    private static InventoryClickEvent topInventoryClick(Player player, int rawSlot, InventoryAction action) {
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        InventoryView view = topInventoryView(player);
        Inventory top = view.getTopInventory();
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getView()).thenReturn(view);
        when(event.getClickedInventory()).thenReturn(top);
        when(event.getSlot()).thenReturn(rawSlot);
        when(event.getRawSlot()).thenReturn(rawSlot);
        when(event.getClick()).thenReturn(ClickType.LEFT);
        when(event.getAction()).thenReturn(action);
        when(event.getHotbarButton()).thenReturn(-1);
        when(event.getSlotType()).thenReturn(InventoryType.SlotType.CONTAINER);
        return event;
    }

    private static InventoryClickEvent bottomInventoryClick(
            Player player,
            int rawSlot,
            ClickType clickType,
            InventoryAction action
    ) {
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        InventoryView view = topInventoryView(player);
        Inventory bottom = view.getBottomInventory();
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getView()).thenReturn(view);
        when(event.getClickedInventory()).thenReturn(bottom);
        when(event.getSlot()).thenReturn(rawSlot);
        when(event.getRawSlot()).thenReturn(rawSlot);
        when(event.getClick()).thenReturn(clickType);
        when(event.getAction()).thenReturn(action);
        when(event.getHotbarButton()).thenReturn(-1);
        when(event.getSlotType()).thenReturn(InventoryType.SlotType.CONTAINER);
        return event;
    }

    private static InventoryClickEvent clickEvent(Player player, ItemStack currentItem, ItemStack cursor) {
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getSlot()).thenReturn(20);
        when(event.getRawSlot()).thenReturn(20);
        when(event.getClick()).thenReturn(ClickType.LEFT);
        when(event.getAction()).thenReturn(InventoryAction.PICKUP_ALL);
        when(event.getSlotType()).thenReturn(InventoryType.SlotType.CONTAINER);
        when(event.getCurrentItem()).thenReturn(currentItem);
        when(event.getCursor()).thenReturn(cursor);
        return event;
    }

    private static InventoryDragEvent dragEvent(Player player, Map<Integer, ItemStack> newItems) {
        return dragEvent(player, newItems, Set.of());
    }

    private static InventoryDragEvent dragEvent(
            Player player,
            Map<Integer, ItemStack> newItems,
            Set<Integer> quickbarRawSlots
    ) {
        return new InventoryDragEvent(
                view(player, quickbarRawSlots),
                item(Material.AIR),
                item(Material.AIR),
                false,
                newItems
        );
    }

    private static InventoryDragEvent topInventoryDrag(Player player, Map<Integer, ItemStack> newItems) {
        return new InventoryDragEvent(
                topInventoryView(player),
                item(Material.AIR),
                item(Material.AIR),
                false,
                newItems
        );
    }

    private static InventoryView view(Player player) {
        return view(player, Set.of());
    }

    private static InventoryView view(Player player, Set<Integer> quickbarRawSlots) {
        InventoryView view = mock(InventoryView.class);
        Inventory inventory = mock(Inventory.class);
        when(view.getPlayer()).thenReturn(player);
        when(view.getTopInventory()).thenReturn(inventory);
        when(view.getBottomInventory()).thenReturn(inventory);
        when(view.getSlotType(anyInt())).thenAnswer(invocation -> {
            int rawSlot = invocation.getArgument(0);
            return quickbarRawSlots.contains(rawSlot)
                    ? InventoryType.SlotType.QUICKBAR
                    : InventoryType.SlotType.CONTAINER;
        });
        when(view.convertSlot(anyInt())).thenAnswer(invocation -> {
            int rawSlot = invocation.getArgument(0);
            if (rawSlot >= 36 && rawSlot <= 44) {
                return rawSlot - 36;
            }
            if (rawSlot == 45) {
                return 40;
            }
            return rawSlot;
        });
        when(view.getInventory(anyInt())).thenAnswer(invocation -> {
            int rawSlot = invocation.getArgument(0);
            return inventory;
        });
        return view;
    }

    private static InventoryView topInventoryView(Player player) {
        InventoryView view = mock(InventoryView.class);
        Inventory top = mock(Inventory.class);
        Inventory bottom = mock(Inventory.class);
        when(view.getPlayer()).thenReturn(player);
        when(view.getTopInventory()).thenReturn(top);
        when(view.getBottomInventory()).thenReturn(bottom);
        when(view.getSlotType(anyInt())).thenReturn(InventoryType.SlotType.CONTAINER);
        when(view.convertSlot(anyInt())).thenAnswer(invocation -> invocation.getArgument(0));
        when(view.getInventory(anyInt())).thenAnswer(invocation -> {
            int rawSlot = invocation.getArgument(0);
            return rawSlot >= 0 && rawSlot <= 26 ? top : bottom;
        });
        return view;
    }

    private static ItemStack claimModeItem() {
        ItemStack template = mock(ItemStack.class);
        ItemStack itemStack = mock(ItemStack.class);
        ItemMeta itemMeta = mock(ItemMeta.class);
        PersistentDataContainer persistentDataContainer = mock(PersistentDataContainer.class);
        Map<NamespacedKey, String> values = new HashMap<>();

        when(template.clone()).thenReturn(itemStack);
        when(itemStack.getType()).thenReturn(Material.GOLDEN_HOE);
        when(itemStack.hasItemMeta()).thenReturn(true);
        when(itemStack.getItemMeta()).thenReturn(itemMeta);
        when(itemMeta.getPersistentDataContainer()).thenReturn(persistentDataContainer);
        when(persistentDataContainer.get(any(NamespacedKey.class), eq(PersistentDataType.STRING)))
                .thenAnswer(invocation -> values.get(invocation.getArgument(0)));
        doAnswer(invocation -> {
            values.put(invocation.getArgument(0), invocation.getArgument(2));
            return null;
        }).when(persistentDataContainer).set(any(NamespacedKey.class), eq(PersistentDataType.STRING), any(String.class));
        return template;
    }

    private static ItemStack item(Material material) {
        ItemStack itemStack = mock(ItemStack.class);
        when(itemStack.getType()).thenReturn(material);
        return itemStack;
    }

    private record Fixture(Player player, UUID playerId, ClaimModeService service, ClaimModeToolRegistry registry) {
    }
}
