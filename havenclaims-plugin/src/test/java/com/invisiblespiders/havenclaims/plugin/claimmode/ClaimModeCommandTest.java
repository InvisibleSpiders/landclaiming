package com.invisiblespiders.havenclaims.plugin.claimmode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.invisiblespiders.havenclaims.plugin.message.MessageService;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClaimModeCommandTest {
    private static final NamespacedKey TOOL_KEY = new NamespacedKey("havenclaims", "claim_mode_tool");

    @TempDir
    Path tempDir;

    @Test
    void actionParsingAcceptsAliasesAndDefaultsToToggle() {
        assertThat(ClaimModeAction.from(new String[]{})).isEqualTo(ClaimModeAction.TOGGLE);
        assertThat(ClaimModeAction.from(new String[]{"on"})).isEqualTo(ClaimModeAction.ON);
        assertThat(ClaimModeAction.from(new String[]{"enable"})).isEqualTo(ClaimModeAction.ON);
        assertThat(ClaimModeAction.from(new String[]{"start"})).isEqualTo(ClaimModeAction.ON);
        assertThat(ClaimModeAction.from(new String[]{"off"})).isEqualTo(ClaimModeAction.OFF);
        assertThat(ClaimModeAction.from(new String[]{"disable"})).isEqualTo(ClaimModeAction.OFF);
        assertThat(ClaimModeAction.from(new String[]{"stop"})).isEqualTo(ClaimModeAction.OFF);
        assertThat(ClaimModeAction.from(new String[]{"exit"})).isEqualTo(ClaimModeAction.OFF);
        assertThat(ClaimModeAction.from(new String[]{"surprise"})).isEqualTo(ClaimModeAction.TOGGLE);
    }

    @Test
    void nonPlayerSenderGetsPlayerOnlyMessage() {
        CommandSender sender = mock(CommandSender.class);
        List<Component> messages = captureMessages(sender);
        ClaimModeCommand command = command(service(true));

        command.onCommand(sender, mock(Command.class), "claimmode", new String[]{});

        assertThat(plain(messages)).containsExactly("Only players can use HavenClaims commands.");
    }

    @Test
    void onEntersInactivePlayerAndReportsAlreadyActiveWithoutExiting() {
        PlayerFixture fixture = playerFixture();
        ClaimModeService service = service(true);
        ClaimModeCommand command = command(service);

        command.execute(fixture.player(), ClaimModeAction.ON);
        command.execute(fixture.player(), ClaimModeAction.ON);

        assertThat(service.isInClaimMode(fixture.playerId())).isTrue();
        assertThat(plain(fixture.messages())).containsExactly(
                "Claim mode enabled.",
                "You are already in claim mode."
        );
    }

    @Test
    void onReportsAlreadyActiveWhenActiveSessionOutlivesDisabledConfig() {
        PlayerFixture fixture = playerFixture();
        ClaimModeService service = service(true);
        ClaimModeCommand command = command(service);
        command.execute(fixture.player(), ClaimModeAction.ON);
        service.reload(new ClaimModeConfig(false, 5, List.of("storage"), List.of("claimmode", "cm", "claim")));

        command.execute(fixture.player(), ClaimModeAction.ON);

        assertThat(service.isInClaimMode(fixture.playerId())).isTrue();
        assertThat(plain(fixture.messages())).containsExactly(
                "Claim mode enabled.",
                "You are already in claim mode."
        );
    }

    @Test
    void offExitsActivePlayerAndReportsNotActiveWithoutEntering() {
        PlayerFixture fixture = playerFixture();
        ClaimModeService service = service(true);
        ClaimModeCommand command = command(service);

        command.execute(fixture.player(), ClaimModeAction.OFF);
        command.execute(fixture.player(), ClaimModeAction.ON);
        command.execute(fixture.player(), ClaimModeAction.OFF);

        assertThat(service.isInClaimMode(fixture.playerId())).isFalse();
        assertThat(plain(fixture.messages())).containsExactly(
                "You are not in claim mode.",
                "Claim mode enabled.",
                "Claim mode disabled."
        );
    }

    @Test
    void toggleEntersInactiveAndExitsActivePlayer() {
        PlayerFixture fixture = playerFixture();
        ClaimModeService service = service(true);
        ClaimModeCommand command = command(service);

        command.execute(fixture.player(), ClaimModeAction.TOGGLE);
        assertThat(service.isInClaimMode(fixture.playerId())).isTrue();

        command.execute(fixture.player(), ClaimModeAction.TOGGLE);

        assertThat(service.isInClaimMode(fixture.playerId())).isFalse();
        assertThat(plain(fixture.messages())).containsExactly(
                "Claim mode enabled.",
                "Claim mode disabled."
        );
    }

    @Test
    void enterGuardResultsUseConfiguredMessages() {
        PlayerFixture disabled = playerFixture();
        ClaimModeCommand disabledCommand = command(service(false));

        disabledCommand.execute(disabled.player(), ClaimModeAction.ON);

        assertThat(plain(disabled.messages())).containsExactly("Claim mode is disabled.");

        PlayerFixture noPermission = playerFixture();
        when(noPermission.player().hasPermission(ClaimModeService.CLAIM_PERMISSION)).thenReturn(false);
        ClaimModeCommand noPermissionCommand = command(service(true));

        noPermissionCommand.execute(noPermission.player(), ClaimModeAction.ON);

        assertThat(plain(noPermission.messages())).containsExactly("You do not have permission to create claims.");
    }

    @Test
    void tabCompletesModeActions() {
        ClaimModeCommand command = command(service(true));

        assertThat(command.onTabComplete(mock(CommandSender.class), mock(Command.class), "claimmode", new String[]{"o"}))
                .containsExactly("on", "off");
        assertThat(command.onTabComplete(mock(CommandSender.class), mock(Command.class), "claimmode", new String[]{"t"}))
                .containsExactly("toggle");
        assertThat(command.onTabComplete(mock(CommandSender.class), mock(Command.class), "claimmode", new String[]{"on", ""}))
                .isEmpty();
    }

    private ClaimModeCommand command(ClaimModeService service) {
        return new ClaimModeCommand(service, messages());
    }

    private ClaimModeService service(boolean enabled) {
        return new ClaimModeService(
                new ClaimModeConfig(enabled, 5, List.of("storage"), List.of("claimmode", "cm", "claim")),
                registry(),
                new ClaimModeSessionHistory(tempDir, 5),
                new ClaimModeRecoveryStore(tempDir),
                Component.text("claim mode fallback")
        );
    }

    private ClaimModeToolRegistry registry() {
        return new ClaimModeToolRegistry(TOOL_KEY, List.of(
                new ClaimModeTool("claim", 0, () -> claimModeItem(Material.GOLDEN_HOE), true, "", (player, event) -> {}),
                new ClaimModeTool("exit", 8, () -> claimModeItem(Material.BARRIER), true, "", (player, event) -> {})
        ));
    }

    private PlayerFixture playerFixture() {
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        UUID playerId = UUID.randomUUID();
        List<Component> messages = captureMessages(player);
        Map<Integer, ItemStack> slots = new HashMap<>();
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getName()).thenReturn("Alice");
        when(player.getInventory()).thenReturn(inventory);
        when(player.hasPermission(ClaimModeService.CLAIM_PERMISSION)).thenReturn(true);
        when(inventory.getItem(anyInt())).thenAnswer(invocation -> slots.get(invocation.getArgument(0)));
        doAnswer(invocation -> {
            slots.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(inventory).setItem(anyInt(), any());
        when(inventory.addItem(any(ItemStack.class))).thenReturn(new HashMap<>());
        return new PlayerFixture(player, playerId, messages);
    }

    private static ItemStack claimModeItem(Material material) {
        ItemStack template = mock(ItemStack.class);
        ItemStack itemStack = mock(ItemStack.class);
        ItemMeta itemMeta = mock(ItemMeta.class);
        PersistentDataContainer persistentDataContainer = mock(PersistentDataContainer.class);
        Map<NamespacedKey, String> values = new HashMap<>();

        when(template.clone()).thenReturn(itemStack);
        when(itemStack.getType()).thenReturn(material);
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

    private static MessageService messages() {
        return new MessageService(Map.of(
                "command.player-only", "<red>Only players can use HavenClaims commands.",
                "command.claim.no-permission", "<red>You do not have permission to create claims.",
                "claim-mode.entered", "<green>Claim mode enabled.",
                "claim-mode.exited", "<yellow>Claim mode disabled.",
                "claim-mode.already-active", "<yellow>You are already in claim mode.",
                "claim-mode.not-active", "<yellow>You are not in claim mode.",
                "claim-mode.disabled", "<red>Claim mode is disabled."
        ));
    }

    private static List<Component> captureMessages(CommandSender sender) {
        List<Component> messages = new ArrayList<>();
        doAnswer(invocation -> {
            messages.add(invocation.getArgument(0));
            return null;
        }).when(sender).sendMessage(any(Component.class));
        return messages;
    }

    private static List<String> plain(List<Component> messages) {
        return messages.stream()
                .map(PlainTextComponentSerializer.plainText()::serialize)
                .toList();
    }

    private record PlayerFixture(Player player, UUID playerId, List<Component> messages) {
    }
}
