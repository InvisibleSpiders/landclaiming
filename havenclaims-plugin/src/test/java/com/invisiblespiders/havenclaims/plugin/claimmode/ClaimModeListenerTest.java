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

import com.invisiblespiders.havenclaims.plugin.message.MessageService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
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
        InventoryDragEvent drag = dragEvent(fixture.player(), Map.of(36, item(Material.STONE)), Set.of(36));

        listener.onInventoryClick(hotbarClick);
        listener.onInventoryClick(numberKey);
        listener.onInventoryClick(hotbarAction);
        listener.onInventoryDrag(drag);

        assertThat(hotbarClick.isCancelled()).isTrue();
        assertThat(numberKey.isCancelled()).isTrue();
        assertThat(hotbarAction.isCancelled()).isTrue();
        assertThat(drag.isCancelled()).isTrue();
        assertThat(ClaimModeListener.touchesHotbar(0, 0, ClickType.LEFT, InventoryAction.PICKUP_ALL)).isTrue();
        assertThat(ClaimModeListener.touchesHotbar(18, 18, ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP)).isTrue();
        assertThat(ClaimModeListener.touchesHotbar(18, 18, ClickType.LEFT, InventoryAction.PICKUP_ALL)).isFalse();
        assertThat(ClaimModeListener.touchesHotbar(40, 40, ClickType.LEFT, InventoryAction.PICKUP_ALL)).isTrue();
    }

    @Test
    void leavesOrdinaryTopInventoryRawSlotNumbersAlone() {
        Fixture fixture = fixture(true);
        ClaimModeListener listener = listener(fixture.service());
        InventoryDragEvent drag = dragEvent(fixture.player(), Map.of(40, item(Material.STONE)));

        listener.onInventoryDrag(drag);

        assertThat(drag.isCancelled()).isFalse();
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
                "claim",
                0,
                ClaimModeListenerTest::claimModeItem,
                true,
                "",
                (player, event) -> handled.set(true)
        ));
        ClaimModeListener listener = listener(fixture.service());
        PlayerInteractEvent event = new PlayerInteractEvent(
                fixture.player(),
                Action.RIGHT_CLICK_AIR,
                fixture.registry().createItem("claim"),
                null,
                BlockFace.SELF
        );

        listener.onInteract(event);

        assertThat(event.isCancelled()).isTrue();
        assertThat(event.useItemInHand()).isEqualTo(Event.Result.DENY);
        assertThat(handled).isTrue();
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
        InventoryClickEvent event = new InventoryClickEvent(
                view(player),
                rawSlot >= 0 && rawSlot <= 8 ? InventoryType.SlotType.QUICKBAR : InventoryType.SlotType.CONTAINER,
                rawSlot,
                clickType,
                action,
                clickType == ClickType.NUMBER_KEY ? 0 : -1
        );
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

    private static InventoryView view(Player player) {
        return view(player, Set.of());
    }

    private static InventoryView view(Player player, Set<Integer> quickbarRawSlots) {
        InventoryView view = mock(InventoryView.class);
        Inventory top = mock(Inventory.class);
        Inventory bottom = mock(Inventory.class);
        when(view.getPlayer()).thenReturn(player);
        when(view.getTopInventory()).thenReturn(top);
        when(view.getBottomInventory()).thenReturn(bottom);
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
            return rawSlot >= 36 && rawSlot <= 45 ? bottom : top;
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
