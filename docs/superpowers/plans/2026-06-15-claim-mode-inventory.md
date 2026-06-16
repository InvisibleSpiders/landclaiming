# Claim Mode Inventory Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build safe `/claimmode` inventory sessions that store player hotbar/offhand items, provide ephemeral claim-mode tools, guard item/economy interactions, and retire the permanent craftable claim tool.

**Architecture:** Add a focused `claimmode` package containing session state, tool identity, lifecycle, audit, command guarding, and listeners. `HavenClaimsPlugin` wires these services once on enable, restores active sessions on disable, and exposes `/claimmode`, `/cm`, and `/claim mode`. Existing selection logic remains, but claim creation no longer requires or spends permanent tool charges.

**Tech Stack:** Java 21, Paper/Bukkit API, Gradle, JUnit 5, Mockito, AssertJ, MiniMessage/Adventure components, Bukkit `PersistentDataContainer`, Bukkit item serialization.

---

## Scope Check

This plan implements the first claim-mode project only:

- `/claimmode`, `/cm`, and `/claim mode`
- hotbar/offhand snapshot and restore
- claim-mode tool registry
- disabled subclaim placeholder
- command and inventory guards
- audit log with serialized item backups
- internal pending recovery store
- removal of permanent craftable claim tool flow

It does not implement lock/key tools, hologram tools, full subclaims, HavenCore `/rewards` integration, hard-crash session recovery, block-claim accrual, or block-area geometry.

## File Structure

Create:

- `havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeAction.java`  
  Enum for explicit toggle actions.
- `havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeConfig.java`  
  Reads `claim-mode` settings from config.
- `havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeCommand.java`  
  Handles `/claimmode` and `/cm`.
- `havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeCommandGuard.java`  
  Blocks configured item/economy/storage commands while active.
- `havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeItemCodec.java`  
  Base64 serialize/deserialize helpers and item summaries.
- `havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeItemSnapshot.java`  
  Stores one slot/offhand item plus summary and backup.
- `havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeListener.java`  
  Guards inventory, pickup/drop, swap-hand, vanilla item use, logout, and death.
- `havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeRecoveryEntry.java`  
  Data model for unrecovered items.
- `havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeRecoveryStore.java`  
  In-memory/file-backed pending recovery writer.
- `havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeSession.java`  
  Active per-player session state.
- `havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeService.java`  
  Enters/exits claim mode, restores snapshots, and exposes active state.
- `havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeSessionHistory.java`  
  Writes and trims `logs/claimmode-history.log`.
- `havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeTool.java`  
  Tool contract.
- `havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeToolRegistry.java`  
  Registers tools and resolves tagged items.
- `havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/claimmode/StandardClaimModeTools.java`  
  Initial claim, subclaim, menu, and exit tools.

Modify:

- `havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/HavenClaimsPlugin.java`  
  Wire claim-mode services, commands, listeners, config reload, and disable restore. Remove recipe registration.
- `havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/command/ClaimsCommand.java`  
  Add `/claim mode`; remove `/claim tool`; remove held-tool and charge checks from create flow.
- `havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/listener/ClaimToolListener.java`  
  Treat claim-mode claim tools as valid selection tools and avoid old charge semantics.
- `havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/tool/ClaimToolService.java`  
  Convert to ephemeral claim-mode tool identity or keep as compatibility helper without charges.
- `havenclaims-plugin/src/main/resources/config.yml`  
  Add `claim-mode` config; remove old recipe/tool guidance where present.
- `havenclaims-plugin/src/main/resources/messages.yml`  
  Add claim-mode messages; remove old permanent tool command messaging.
- `havenclaims-plugin/src/main/resources/plugin.yml`  
  Add `claimmode` command with `cm` alias; remove craft/recharge permissions.
- `README.md`, `docs/admin-guide.md`, `docs/configuration.md`, `docs/permissions.md`  
  Document claim mode as the standard path and remove craftable-tool instructions.

Delete:

- `havenclaims-plugin/src/main/resources/recipes.yml`
- `havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/recipe/ClaimToolRecipeConfig.java`
- `havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/recipe/ClaimToolRecipeService.java`
- `havenclaims-plugin/src/test/java/com/invisiblespiders/havenclaims/plugin/recipe/ClaimToolRecipeConfigTest.java`

Tests:

- Create `havenclaims-plugin/src/test/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeConfigTest.java`
- Create `havenclaims-plugin/src/test/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeCommandGuardTest.java`
- Create `havenclaims-plugin/src/test/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeItemCodecTest.java`
- Create `havenclaims-plugin/src/test/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeServiceTest.java`
- Create `havenclaims-plugin/src/test/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeToolRegistryTest.java`
- Create `havenclaims-plugin/src/test/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeListenerTest.java`
- Modify `havenclaims-plugin/src/test/java/com/invisiblespiders/havenclaims/plugin/command/ClaimsCommandPermissionTest.java`
- Modify `havenclaims-plugin/src/test/java/com/invisiblespiders/havenclaims/plugin/tool/ClaimToolServiceTest.java`

---

### Task 1: Claim Mode Config And Metadata

**Files:**
- Create: `havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeConfig.java`
- Test: `havenclaims-plugin/src/test/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeConfigTest.java`
- Modify: `havenclaims-plugin/src/main/resources/config.yml`
- Modify: `havenclaims-plugin/src/main/resources/plugin.yml`
- Modify: `havenclaims-plugin/src/main/resources/messages.yml`

- [ ] **Step 1: Write failing config tests**

Create `havenclaims-plugin/src/test/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeConfigTest.java`:

```java
package com.invisiblespiders.havenclaims.plugin.claimmode;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ClaimModeConfigTest {
    @Test
    void readsConfiguredClaimModeValues() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("claim-mode.enabled", true);
        configuration.set("claim-mode.history-per-player", 3);
        configuration.set("claim-mode.blocked-commands", List.of("storage", "pay", "minecraft:give"));
        configuration.set("claim-mode.allowed-commands", List.of("claimmode", "cm", "claim"));

        ClaimModeConfig config = ClaimModeConfig.from(configuration);

        assertThat(config.enabled()).isTrue();
        assertThat(config.historyPerPlayer()).isEqualTo(3);
        assertThat(config.blockedCommands()).containsExactly("storage", "pay", "give");
        assertThat(config.allowedCommands()).containsExactly("claimmode", "cm", "claim");
    }

    @Test
    void suppliesSafeDefaults() {
        ClaimModeConfig config = ClaimModeConfig.from(new YamlConfiguration());

        assertThat(config.enabled()).isTrue();
        assertThat(config.historyPerPlayer()).isEqualTo(5);
        assertThat(config.blockedCommands()).contains("storage", "vault", "shop", "auction", "ah", "trade", "pay", "sell", "buy", "kit", "mail");
        assertThat(config.allowedCommands()).contains("claimmode", "cm", "claim");
    }
}
```

- [ ] **Step 2: Run config tests and verify failure**

Run:

```powershell
.\gradlew.bat :havenclaims-plugin:test --tests "com.invisiblespiders.havenclaims.plugin.claimmode.ClaimModeConfigTest"
```

Expected: FAIL because `ClaimModeConfig` does not exist.

- [ ] **Step 3: Implement config record**

Create `havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeConfig.java`:

```java
package com.invisiblespiders.havenclaims.plugin.claimmode;

import java.util.List;
import java.util.Locale;
import org.bukkit.configuration.file.FileConfiguration;

public record ClaimModeConfig(
        boolean enabled,
        int historyPerPlayer,
        List<String> blockedCommands,
        List<String> allowedCommands
) {
    private static final List<String> DEFAULT_BLOCKED_COMMANDS = List.of(
            "storage", "vault", "shop", "auction", "ah", "trade", "pay", "sell", "buy", "kit", "mail");
    private static final List<String> DEFAULT_ALLOWED_COMMANDS = List.of("claimmode", "cm", "claim");

    public ClaimModeConfig {
        historyPerPlayer = Math.max(1, historyPerPlayer);
        blockedCommands = normalizeList(blockedCommands);
        allowedCommands = normalizeList(allowedCommands);
    }

    public static ClaimModeConfig from(FileConfiguration configuration) {
        boolean enabled = configuration.getBoolean("claim-mode.enabled", true);
        int historyPerPlayer = configuration.getInt("claim-mode.history-per-player", 5);
        List<String> blocked = configuration.getStringList("claim-mode.blocked-commands");
        List<String> allowed = configuration.getStringList("claim-mode.allowed-commands");
        return new ClaimModeConfig(
                enabled,
                historyPerPlayer,
                blocked.isEmpty() ? DEFAULT_BLOCKED_COMMANDS : blocked,
                allowed.isEmpty() ? DEFAULT_ALLOWED_COMMANDS : allowed
        );
    }

    static String normalizeCommandLabel(String label) {
        String normalized = label == null ? "" : label.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        int namespaceSeparator = normalized.indexOf(':');
        if (namespaceSeparator >= 0 && namespaceSeparator + 1 < normalized.length()) {
            normalized = normalized.substring(namespaceSeparator + 1);
        }
        int firstSpace = normalized.indexOf(' ');
        if (firstSpace >= 0) {
            normalized = normalized.substring(0, firstSpace);
        }
        return normalized;
    }

    private static List<String> normalizeList(List<String> commands) {
        return commands.stream()
                .map(ClaimModeConfig::normalizeCommandLabel)
                .filter(command -> !command.isBlank())
                .distinct()
                .toList();
    }
}
```

- [ ] **Step 4: Add config defaults**

Modify `havenclaims-plugin/src/main/resources/config.yml` by adding this section after `ui:`:

```yaml
claim-mode:
  enabled: true
  history-per-player: 5
  allowed-commands:
    - claimmode
    - cm
    - claim
  blocked-commands:
    - storage
    - vault
    - shop
    - auction
    - ah
    - trade
    - pay
    - sell
    - buy
    - kit
    - mail
```

- [ ] **Step 5: Update plugin metadata**

Modify `havenclaims-plugin/src/main/resources/plugin.yml`:

```yaml
commands:
  claim:
    description: Opens HavenClaims commands and menus.
    usage: /claim
    aliases: [claims, lc]
  claimmode:
    description: Toggles HavenClaims claim mode.
    usage: /claimmode [on|off|toggle]
    aliases: [cm]
```

Remove these permission nodes:

```yaml
  havenclaims.tool.craft:
    description: Allows crafting the claim tool.
    default: true
  havenclaims.tool.recharge:
    description: Allows recharging the claim tool.
    default: true
```

Keep `havenclaims.tool.use` for now because existing selection checks use it; later tasks can route player-facing claim mode entry through `havenclaims.claim`.

- [ ] **Step 6: Add messages**

Modify `havenclaims-plugin/src/main/resources/messages.yml` by adding:

```yaml
claim-mode:
  entered: "<green>Claim mode enabled. Your hotbar and offhand are stored safely."
  exited: "<yellow>Claim mode disabled. Your stored items were restored."
  already-active: "<yellow>You are already in claim mode."
  not-active: "<yellow>You are not in claim mode."
  disabled: "<red>Claim mode is disabled."
  blocked-command: "<red>Exit claim mode before using <yellow>/<command></yellow>."
  blocked-inventory: "<red>Exit claim mode before moving hotbar items."
  blocked-drop: "<red>Exit claim mode before dropping items."
  blocked-pickup: "<red>Exit claim mode before picking up items."
  blocked-swap: "<red>Exit claim mode before swapping hands."
  subclaim-coming-soon: "<yellow>Subclaims are coming soon."
  restore-partial: "<yellow>Some stored items were moved to your inventory because their original slots were unavailable."
  restore-recovery: "<red>Some stored items could not be restored and were moved to claim mode recovery. Contact staff."
  restore-failed: "<red>Claim mode restore failed. Contact staff immediately."
  menu-help: "<gold>Claim Mode:</gold> <gray>Use the claim tool to select land, slot 8 to exit."
```

- [ ] **Step 7: Re-run config tests**

Run:

```powershell
.\gradlew.bat :havenclaims-plugin:test --tests "com.invisiblespiders.havenclaims.plugin.claimmode.ClaimModeConfigTest"
```

Expected: PASS.

- [ ] **Step 8: Commit**

```powershell
git add havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeConfig.java `
        havenclaims-plugin/src/test/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeConfigTest.java `
        havenclaims-plugin/src/main/resources/config.yml `
        havenclaims-plugin/src/main/resources/plugin.yml `
        havenclaims-plugin/src/main/resources/messages.yml
git commit -m "feat: add claim mode configuration"
```

---

### Task 2: Claim Mode Tool Identity And Registry

**Files:**
- Create: `havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeTool.java`
- Create: `havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeToolRegistry.java`
- Create: `havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/claimmode/StandardClaimModeTools.java`
- Test: `havenclaims-plugin/src/test/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeToolRegistryTest.java`
- Modify: `havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/tool/ClaimToolService.java`
- Test: `havenclaims-plugin/src/test/java/com/invisiblespiders/havenclaims/plugin/tool/ClaimToolServiceTest.java`

- [ ] **Step 1: Write failing registry tests**

Create `havenclaims-plugin/src/test/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeToolRegistryTest.java`:

```java
package com.invisiblespiders.havenclaims.plugin.claimmode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;

class ClaimModeToolRegistryTest {
    private static final NamespacedKey TOOL_KEY = new NamespacedKey("havenclaims", "claim_mode_tool");

    @Test
    void tagsAndResolvesRegisteredToolItems() {
        ClaimModeTool claimTool = new ClaimModeTool(
                "claim",
                0,
                () -> new ItemStack(Material.GOLDEN_HOE),
                true,
                "",
                (player, event) -> {}
        );
        ClaimModeToolRegistry registry = new ClaimModeToolRegistry(TOOL_KEY, List.of(claimTool));

        ItemStack item = registry.createItem("claim");

        assertThat(registry.resolve(item)).contains(claimTool);
        assertThat(registry.isClaimModeTool(item)).isTrue();
    }

    @Test
    void rejectsDuplicateToolSlots() {
        ClaimModeTool first = new ClaimModeTool("first", 0, () -> new ItemStack(Material.STICK), true, "", (player, event) -> {});
        ClaimModeTool second = new ClaimModeTool("second", 0, () -> new ItemStack(Material.BLAZE_ROD), true, "", (player, event) -> {});

        assertThatThrownBy(() -> new ClaimModeToolRegistry(TOOL_KEY, List.of(first, second)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("slot");
    }

    @Test
    void standardToolsUseExpectedSlots() {
        ClaimModeToolRegistry registry = StandardClaimModeTools.createRegistry(TOOL_KEY);

        assertThat(registry.toolsBySlot().keySet()).containsExactlyInAnyOrder(0, 1, 7, 8);
        assertThat(registry.toolsBySlot().get(1).enabled()).isFalse();
    }
}
```

- [ ] **Step 2: Run registry tests and verify failure**

Run:

```powershell
.\gradlew.bat :havenclaims-plugin:test --tests "com.invisiblespiders.havenclaims.plugin.claimmode.ClaimModeToolRegistryTest"
```

Expected: FAIL because registry types do not exist.

- [ ] **Step 3: Add tool contract**

Create `havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeTool.java`:

```java
package com.invisiblespiders.havenclaims.plugin.claimmode;

import java.util.Objects;
import java.util.function.Supplier;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public record ClaimModeTool(
        String id,
        int slot,
        Supplier<ItemStack> itemFactory,
        boolean enabled,
        String disabledMessageKey,
        ClaimModeToolHandler handler
) {
    public ClaimModeTool {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (slot < 0 || slot > 8) {
            throw new IllegalArgumentException("slot must be in hotbar range 0-8");
        }
        itemFactory = Objects.requireNonNull(itemFactory, "itemFactory");
        disabledMessageKey = disabledMessageKey == null ? "" : disabledMessageKey;
        handler = Objects.requireNonNull(handler, "handler");
    }

    @FunctionalInterface
    public interface ClaimModeToolHandler {
        void handle(Player player, PlayerInteractEvent event);
    }
}
```

- [ ] **Step 4: Add registry**

Create `havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeToolRegistry.java`:

```java
package com.invisiblespiders.havenclaims.plugin.claimmode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class ClaimModeToolRegistry {
    private final NamespacedKey toolKey;
    private final Map<String, ClaimModeTool> toolsById;
    private final Map<Integer, ClaimModeTool> toolsBySlot;

    public ClaimModeToolRegistry(NamespacedKey toolKey, List<ClaimModeTool> tools) {
        this.toolKey = toolKey;
        Map<String, ClaimModeTool> byId = new LinkedHashMap<>();
        Map<Integer, ClaimModeTool> bySlot = new LinkedHashMap<>();
        for (ClaimModeTool tool : tools) {
            if (byId.putIfAbsent(tool.id(), tool) != null) {
                throw new IllegalArgumentException("Duplicate claim mode tool id: " + tool.id());
            }
            if (bySlot.putIfAbsent(tool.slot(), tool) != null) {
                throw new IllegalArgumentException("Duplicate claim mode tool slot: " + tool.slot());
            }
        }
        this.toolsById = Map.copyOf(byId);
        this.toolsBySlot = Map.copyOf(bySlot);
    }

    public Map<Integer, ClaimModeTool> toolsBySlot() {
        return toolsBySlot;
    }

    public ItemStack createItem(String id) {
        ClaimModeTool tool = toolsById.get(id);
        if (tool == null) {
            throw new IllegalArgumentException("Unknown claim mode tool: " + id);
        }
        ItemStack item = tool.itemFactory().get();
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(toolKey, PersistentDataType.STRING, id);
        item.setItemMeta(meta);
        return item;
    }

    public Optional<ClaimModeTool> resolve(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return Optional.empty();
        }
        String id = item.getItemMeta().getPersistentDataContainer().get(toolKey, PersistentDataType.STRING);
        return Optional.ofNullable(toolsById.get(id));
    }

    public boolean isClaimModeTool(ItemStack item) {
        return resolve(item).isPresent();
    }
}
```

- [ ] **Step 5: Add standard tools**

Create `havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/claimmode/StandardClaimModeTools.java`:

```java
package com.invisiblespiders.havenclaims.plugin.claimmode;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class StandardClaimModeTools {
    private StandardClaimModeTools() {
    }

    public static ClaimModeToolRegistry createRegistry(NamespacedKey toolKey) {
        return new ClaimModeToolRegistry(toolKey, List.of(
                new ClaimModeTool("claim", 0, StandardClaimModeTools::claimTool, true, "", (player, event) -> {}),
                new ClaimModeTool("subclaim", 1, StandardClaimModeTools::subclaimTool, false, "claim-mode.subclaim-coming-soon", (player, event) -> {}),
                new ClaimModeTool("menu", 7, StandardClaimModeTools::menuTool, true, "", (player, event) -> player.performCommand("claim menu")),
                new ClaimModeTool("exit", 8, StandardClaimModeTools::exitTool, true, "", (player, event) -> player.performCommand("claimmode off"))
        ));
    }

    private static ItemStack claimTool() {
        return named(Material.GOLDEN_HOE, Component.text("Claim Tool", NamedTextColor.GOLD),
                List.of(Component.text("Select claim corners.", NamedTextColor.GRAY)));
    }

    private static ItemStack subclaimTool() {
        return named(Material.IRON_SHOVEL, Component.text("Subclaim Tool", NamedTextColor.YELLOW),
                List.of(Component.text("Coming soon.", NamedTextColor.GRAY)));
    }

    private static ItemStack menuTool() {
        return named(Material.BOOK, Component.text("Claim Mode Menu", NamedTextColor.AQUA),
                List.of(Component.text("Open claim options.", NamedTextColor.GRAY)));
    }

    private static ItemStack exitTool() {
        return named(Material.BARRIER, Component.text("Exit Claim Mode", NamedTextColor.RED),
                List.of(Component.text("Restore your stored items.", NamedTextColor.GRAY)));
    }

    private static ItemStack named(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
```

- [ ] **Step 6: Convert `ClaimToolService` to recognize mode tools**

Modify `havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/tool/ClaimToolService.java`:

```java
public class ClaimToolService {
    private final NamespacedKey currentChargesKey;
    private final NamespacedKey maxChargesKey;
    private ClaimModeToolRegistry claimModeToolRegistry;

    public void setClaimModeToolRegistry(ClaimModeToolRegistry claimModeToolRegistry) {
        this.claimModeToolRegistry = claimModeToolRegistry;
    }

    public ItemStack createClaimModeTool() {
        if (claimModeToolRegistry == null) {
            return createClaimTool(1);
        }
        return claimModeToolRegistry.createItem("claim");
    }

    public boolean isClaimTool(ItemStack itemStack) {
        if (claimModeToolRegistry != null && claimModeToolRegistry.resolve(itemStack)
                .map(tool -> tool.id().equals("claim"))
                .orElse(false)) {
            return true;
        }
        if (itemStack == null || !itemStack.hasItemMeta()) {
            return false;
        }
        return itemStack.getItemMeta()
                .getPersistentDataContainer()
                .has(currentChargesKey, PersistentDataType.INTEGER);
    }
}
```

Keep existing charge methods temporarily; Task 7 removes their command use after claim mode is wired.

- [ ] **Step 7: Add/update tool tests**

Modify `havenclaims-plugin/src/test/java/com/invisiblespiders/havenclaims/plugin/tool/ClaimToolServiceTest.java` with:

```java
@Test
void recognizesClaimModeClaimTool() {
    ClaimToolService service = new ClaimToolService("havenclaims");
    ClaimModeToolRegistry registry = StandardClaimModeTools.createRegistry(
            new NamespacedKey("havenclaims", "claim_mode_tool")
    );
    service.setClaimModeToolRegistry(registry);

    assertThat(service.isClaimTool(registry.createItem("claim"))).isTrue();
    assertThat(service.isClaimTool(registry.createItem("menu"))).isFalse();
}
```

- [ ] **Step 8: Run targeted tests**

Run:

```powershell
.\gradlew.bat :havenclaims-plugin:test --tests "com.invisiblespiders.havenclaims.plugin.claimmode.ClaimModeToolRegistryTest" --tests "com.invisiblespiders.havenclaims.plugin.tool.ClaimToolServiceTest"
```

Expected: PASS.

- [ ] **Step 9: Commit**

```powershell
git add havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/claimmode `
        havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/tool/ClaimToolService.java `
        havenclaims-plugin/src/test/java/com/invisiblespiders/havenclaims/plugin/claimmode `
        havenclaims-plugin/src/test/java/com/invisiblespiders/havenclaims/plugin/tool/ClaimToolServiceTest.java
git commit -m "feat: add claim mode tool registry"
```

---

### Task 3: Item Serialization, Audit History, And Recovery Store

**Files:**
- Create: `havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeItemCodec.java`
- Create: `havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeItemSnapshot.java`
- Create: `havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeRecoveryEntry.java`
- Create: `havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeRecoveryStore.java`
- Create: `havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeSessionHistory.java`
- Test: `havenclaims-plugin/src/test/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeItemCodecTest.java`

- [ ] **Step 1: Write failing codec tests**

Create `havenclaims-plugin/src/test/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeItemCodecTest.java`:

```java
package com.invisiblespiders.havenclaims.plugin.claimmode;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;

class ClaimModeItemCodecTest {
    @Test
    void serializesAndRestoresItemStack() {
        ItemStack item = new ItemStack(Material.DIAMOND_SWORD, 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("Trusty");
        meta.setLore(List.of("audit me"));
        meta.addEnchant(Enchantment.SHARPNESS, 3, true);
        ((Damageable) meta).setDamage(12);
        item.setItemMeta(meta);

        String backup = ClaimModeItemCodec.serialize(item);
        ItemStack restored = ClaimModeItemCodec.deserialize(backup);

        assertThat(restored.getType()).isEqualTo(Material.DIAMOND_SWORD);
        assertThat(restored.getAmount()).isEqualTo(1);
        assertThat(restored.getItemMeta().getDisplayName()).isEqualTo("Trusty");
        assertThat(restored.getItemMeta().getEnchantLevel(Enchantment.SHARPNESS)).isEqualTo(3);
        assertThat(((Damageable) restored.getItemMeta()).getDamage()).isEqualTo(12);
    }

    @Test
    void createsHumanReadableSummary() {
        ItemStack item = new ItemStack(Material.GOLDEN_APPLE, 2);

        String summary = ClaimModeItemCodec.summary(item);

        assertThat(summary).contains("GOLDEN_APPLE");
        assertThat(summary).contains("amount=2");
    }
}
```

- [ ] **Step 2: Run codec tests and verify failure**

Run:

```powershell
.\gradlew.bat :havenclaims-plugin:test --tests "com.invisiblespiders.havenclaims.plugin.claimmode.ClaimModeItemCodecTest"
```

Expected: FAIL because `ClaimModeItemCodec` does not exist.

- [ ] **Step 3: Add item codec**

Create `havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeItemCodec.java`:

```java
package com.invisiblespiders.havenclaims.plugin.claimmode;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

public final class ClaimModeItemCodec {
    private ClaimModeItemCodec() {
    }

    public static String serialize(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return "";
        }
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             BukkitObjectOutputStream output = new BukkitObjectOutputStream(bytes)) {
            output.writeObject(item);
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to serialize item stack", exception);
        }
    }

    public static ItemStack deserialize(String backup) {
        if (backup == null || backup.isBlank()) {
            return null;
        }
        byte[] bytes = Base64.getDecoder().decode(backup);
        try (BukkitObjectInputStream input = new BukkitObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (ItemStack) input.readObject();
        } catch (IOException | ClassNotFoundException exception) {
            throw new IllegalStateException("Failed to deserialize item stack", exception);
        }
    }

    public static String summary(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return "empty";
        }
        ItemMeta meta = item.getItemMeta();
        String name = meta != null && meta.hasDisplayName() ? meta.getDisplayName() : "";
        int damage = meta instanceof Damageable damageable ? damageable.getDamage() : 0;
        Map<String, Integer> enchants = new TreeMap<>();
        if (meta != null) {
            for (Map.Entry<Enchantment, Integer> entry : meta.getEnchants().entrySet()) {
                enchants.put(entry.getKey().getKey().toString(), entry.getValue());
            }
        }
        return "type=" + item.getType()
                + ", amount=" + item.getAmount()
                + ", damage=" + damage
                + ", name=" + name
                + ", enchants=" + enchants;
    }
}
```

- [ ] **Step 4: Add snapshot and recovery records**

Create `havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeItemSnapshot.java`:

```java
package com.invisiblespiders.havenclaims.plugin.claimmode;

import org.bukkit.inventory.ItemStack;

public record ClaimModeItemSnapshot(
        String slot,
        String summary,
        String backup
) {
    public static ClaimModeItemSnapshot from(String slot, ItemStack item) {
        return new ClaimModeItemSnapshot(slot, ClaimModeItemCodec.summary(item), ClaimModeItemCodec.serialize(item));
    }

    public ItemStack restoreItem() {
        return ClaimModeItemCodec.deserialize(backup);
    }

    public boolean empty() {
        return backup == null || backup.isBlank();
    }
}
```

Create `havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeRecoveryEntry.java`:

```java
package com.invisiblespiders.havenclaims.plugin.claimmode;

import java.time.Instant;
import java.util.UUID;

public record ClaimModeRecoveryEntry(
        UUID playerId,
        String playerName,
        Instant timestamp,
        String originalSlot,
        String summary,
        String backup,
        String reason
) {
}
```

- [ ] **Step 5: Add recovery store**

Create `havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeRecoveryStore.java`:

```java
package com.invisiblespiders.havenclaims.plugin.claimmode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ClaimModeRecoveryStore {
    private final Path recoveryFile;
    private final List<ClaimModeRecoveryEntry> pendingEntries = new ArrayList<>();

    public ClaimModeRecoveryStore(Path dataFolder) {
        this.recoveryFile = dataFolder.resolve("claimmode-recovery.log");
    }

    public void add(ClaimModeRecoveryEntry entry) {
        pendingEntries.add(entry);
        append(entry);
    }

    public List<ClaimModeRecoveryEntry> pendingFor(UUID playerId) {
        return pendingEntries.stream()
                .filter(entry -> entry.playerId().equals(playerId))
                .toList();
    }

    private void append(ClaimModeRecoveryEntry entry) {
        try {
            Files.createDirectories(recoveryFile.getParent());
            Files.writeString(
                    recoveryFile,
                    entry.timestamp() + " player=" + entry.playerName()
                            + " uuid=" + entry.playerId()
                            + " slot=" + entry.originalSlot()
                            + " reason=" + entry.reason()
                            + " summary=\"" + entry.summary() + "\""
                            + " backup=" + entry.backup()
                            + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write claim mode recovery entry", exception);
        }
    }
}
```

- [ ] **Step 6: Add session history writer**

Create `havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeSessionHistory.java`:

```java
package com.invisiblespiders.havenclaims.plugin.claimmode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ClaimModeSessionHistory {
    private final Path historyFile;
    private final int historyPerPlayer;

    public ClaimModeSessionHistory(Path dataFolder, int historyPerPlayer) {
        this.historyFile = dataFolder.resolve("logs").resolve("claimmode-history.log");
        this.historyPerPlayer = Math.max(1, historyPerPlayer);
    }

    public void append(UUID playerId, String playerName, Instant enteredAt, Instant exitedAt,
                       ClaimModeService.ExitReason reason, List<ClaimModeItemSnapshot> snapshots,
                       List<String> restoreResults) {
        StringBuilder builder = new StringBuilder();
        builder.append("session player=").append(playerName)
                .append(" uuid=").append(playerId)
                .append(" entered=").append(enteredAt)
                .append(" exited=").append(exitedAt)
                .append(" reason=").append(reason)
                .append(System.lineSeparator());
        for (ClaimModeItemSnapshot snapshot : snapshots) {
            builder.append("  item slot=").append(snapshot.slot())
                    .append(" summary=\"").append(snapshot.summary()).append("\"")
                    .append(" backup=").append(snapshot.backup())
                    .append(System.lineSeparator());
        }
        for (String result : restoreResults) {
            builder.append("  restore ").append(result).append(System.lineSeparator());
        }
        builder.append("end-session").append(System.lineSeparator());
        writeAndTrim(builder.toString(), playerId);
    }

    private void writeAndTrim(String entry, UUID playerId) {
        try {
            Files.createDirectories(historyFile.getParent());
            Files.writeString(historyFile, entry, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            List<String> lines = Files.readAllLines(historyFile, StandardCharsets.UTF_8);
            int sessionsForPlayer = 0;
            int trimBeforeLine = -1;
            for (int i = lines.size() - 1; i >= 0; i--) {
                if (lines.get(i).startsWith("session ") && lines.get(i).contains("uuid=" + playerId)) {
                    sessionsForPlayer++;
                    if (sessionsForPlayer > historyPerPlayer) {
                        trimBeforeLine = i;
                        break;
                    }
                }
            }
            if (trimBeforeLine >= 0) {
                Files.write(historyFile, lines.subList(0, trimBeforeLine), StandardCharsets.UTF_8);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write claim mode history", exception);
        }
    }
}
```

- [ ] **Step 7: Run codec tests**

Run:

```powershell
.\gradlew.bat :havenclaims-plugin:test --tests "com.invisiblespiders.havenclaims.plugin.claimmode.ClaimModeItemCodecTest"
```

Expected: PASS.

- [ ] **Step 8: Commit**

```powershell
git add havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeItemCodec.java `
        havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeItemSnapshot.java `
        havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeRecoveryEntry.java `
        havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeRecoveryStore.java `
        havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeSessionHistory.java `
        havenclaims-plugin/src/test/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeItemCodecTest.java
git commit -m "feat: add claim mode item audit backups"
```

---

### Task 4: Claim Mode Session Lifecycle

**Files:**
- Create: `havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeSession.java`
- Create: `havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeService.java`
- Test: `havenclaims-plugin/src/test/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeServiceTest.java`

- [ ] **Step 1: Write failing service tests**

Create `havenclaims-plugin/src/test/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeServiceTest.java`:

```java
package com.invisiblespiders.havenclaims.plugin.claimmode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClaimModeServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void enterStoresHotbarAndOffhandThenPlacesTools() {
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getName()).thenReturn("Alice");
        when(player.getInventory()).thenReturn(inventory);
        when(player.hasPermission("havenclaims.claim")).thenReturn(true);
        when(inventory.getItem(0)).thenReturn(new ItemStack(Material.DIAMOND));
        when(inventory.getItemInOffHand()).thenReturn(new ItemStack(Material.SHIELD));

        ClaimModeService service = service();

        ClaimModeService.EnterResult result = service.enter(player);

        assertThat(result).isEqualTo(ClaimModeService.EnterResult.ENTERED);
        assertThat(service.isInClaimMode(playerId)).isTrue();
    }

    @Test
    void exitRemovesSession() {
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getName()).thenReturn("Alice");
        when(player.getInventory()).thenReturn(inventory);
        when(player.hasPermission("havenclaims.claim")).thenReturn(true);

        ClaimModeService service = service();
        service.enter(player);

        ClaimModeService.ExitResult result = service.exit(player, ClaimModeService.ExitReason.MANUAL);

        assertThat(result).isIn(ClaimModeService.ExitResult.RESTORED, ClaimModeService.ExitResult.PARTIAL, ClaimModeService.ExitResult.RECOVERY);
        assertThat(service.isInClaimMode(playerId)).isFalse();
    }

    private ClaimModeService service() {
        ClaimModeToolRegistry registry = new ClaimModeToolRegistry(
                new NamespacedKey("havenclaims", "claim_mode_tool"),
                List.of(new ClaimModeTool("claim", 0, () -> new ItemStack(Material.GOLDEN_HOE), true, "", (player, event) -> {}))
        );
        return new ClaimModeService(
                new ClaimModeConfig(true, 5, List.of("storage"), List.of("claimmode", "cm", "claim")),
                registry,
                new ClaimModeSessionHistory(tempDir, 5),
                new ClaimModeRecoveryStore(tempDir),
                Component.text("message")
        );
    }
}
```

- [ ] **Step 2: Run service tests and verify failure**

Run:

```powershell
.\gradlew.bat :havenclaims-plugin:test --tests "com.invisiblespiders.havenclaims.plugin.claimmode.ClaimModeServiceTest"
```

Expected: FAIL because `ClaimModeService` and `ClaimModeSession` do not exist.

- [ ] **Step 3: Add session record**

Create `havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeSession.java`:

```java
package com.invisiblespiders.havenclaims.plugin.claimmode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ClaimModeSession(
        UUID playerId,
        String playerName,
        Instant enteredAt,
        List<ClaimModeItemSnapshot> snapshots
) {
}
```

- [ ] **Step 4: Add service**

Create `havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeService.java`:

```java
package com.invisiblespiders.havenclaims.plugin.claimmode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public final class ClaimModeService {
    public enum EnterResult { ENTERED, DISABLED, NO_PERMISSION, ALREADY_ACTIVE }
    public enum ExitResult { RESTORED, PARTIAL, RECOVERY, NOT_ACTIVE }
    public enum ExitReason { MANUAL, LOGOUT, DEATH, PLUGIN_DISABLE, RESTORE_FAILURE }

    private static final String CLAIM_PERMISSION = "havenclaims.claim";

    private ClaimModeConfig config;
    private final ClaimModeToolRegistry toolRegistry;
    private final ClaimModeSessionHistory history;
    private final ClaimModeRecoveryStore recoveryStore;
    private final Component fallbackMessage;
    private final Map<UUID, ClaimModeSession> sessions = new HashMap<>();

    public ClaimModeService(
            ClaimModeConfig config,
            ClaimModeToolRegistry toolRegistry,
            ClaimModeSessionHistory history,
            ClaimModeRecoveryStore recoveryStore,
            Component fallbackMessage
    ) {
        this.config = config;
        this.toolRegistry = toolRegistry;
        this.history = history;
        this.recoveryStore = recoveryStore;
        this.fallbackMessage = fallbackMessage;
    }

    public void reload(ClaimModeConfig config) {
        this.config = config;
    }

    public boolean isInClaimMode(UUID playerId) {
        return sessions.containsKey(playerId);
    }

    public ClaimModeToolRegistry toolRegistry() {
        return toolRegistry;
    }

    public EnterResult enter(Player player) {
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
        List<ClaimModeItemSnapshot> snapshots = new ArrayList<>();
        for (int slot = 0; slot <= 8; slot++) {
            snapshots.add(ClaimModeItemSnapshot.from("hotbar-" + slot, inventory.getItem(slot)));
            inventory.setItem(slot, null);
        }
        snapshots.add(ClaimModeItemSnapshot.from("offhand", inventory.getItemInOffHand()));
        inventory.setItemInOffHand(null);
        for (Map.Entry<Integer, ClaimModeTool> entry : toolRegistry.toolsBySlot().entrySet()) {
            inventory.setItem(entry.getKey(), toolRegistry.createItem(entry.getValue().id()));
        }
        sessions.put(playerId, new ClaimModeSession(playerId, player.getName(), Instant.now(), snapshots));
        return EnterResult.ENTERED;
    }

    public ExitResult exit(Player player, ExitReason reason) {
        ClaimModeSession session = sessions.remove(player.getUniqueId());
        if (session == null) {
            return ExitResult.NOT_ACTIVE;
        }
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot <= 8; slot++) {
            ItemStack current = inventory.getItem(slot);
            if (toolRegistry.isClaimModeTool(current)) {
                inventory.setItem(slot, null);
            }
        }
        if (toolRegistry.isClaimModeTool(inventory.getItemInOffHand())) {
            inventory.setItemInOffHand(null);
        }
        List<String> restoreResults = new ArrayList<>();
        boolean partial = false;
        boolean recovery = false;
        for (ClaimModeItemSnapshot snapshot : session.snapshots()) {
            if (snapshot.empty()) {
                restoreResults.add(snapshot.slot() + "=empty");
                continue;
            }
            ItemStack item = snapshot.restoreItem();
            if (item == null || item.getType() == Material.AIR) {
                restoreResults.add(snapshot.slot() + "=empty");
                continue;
            }
            boolean exact = restoreExact(inventory, snapshot.slot(), item);
            if (exact) {
                restoreResults.add(snapshot.slot() + "=exact");
                continue;
            }
            partial = true;
            Map<Integer, ItemStack> leftovers = inventory.addItem(item);
            if (leftovers.isEmpty()) {
                restoreResults.add(snapshot.slot() + "=inventory");
                continue;
            }
            recovery = true;
            for (ItemStack leftover : leftovers.values()) {
                ClaimModeRecoveryEntry entry = new ClaimModeRecoveryEntry(
                        session.playerId(), session.playerName(), Instant.now(), snapshot.slot(),
                        ClaimModeItemCodec.summary(leftover), ClaimModeItemCodec.serialize(leftover), "inventory-full");
                recoveryStore.add(entry);
            }
            restoreResults.add(snapshot.slot() + "=recovery");
        }
        history.append(session.playerId(), session.playerName(), session.enteredAt(), Instant.now(), reason, session.snapshots(), restoreResults);
        if (recovery) {
            return ExitResult.RECOVERY;
        }
        return partial ? ExitResult.PARTIAL : ExitResult.RESTORED;
    }

    public void restoreAll(Iterable<? extends Player> players, ExitReason reason) {
        for (Player player : players) {
            if (isInClaimMode(player.getUniqueId())) {
                exit(player, reason);
            }
        }
    }

    private boolean restoreExact(PlayerInventory inventory, String slot, ItemStack item) {
        if (slot.startsWith("hotbar-")) {
            int hotbarSlot = Integer.parseInt(slot.substring("hotbar-".length()));
            ItemStack current = inventory.getItem(hotbarSlot);
            if (current == null || current.getType() == Material.AIR || toolRegistry.isClaimModeTool(current)) {
                inventory.setItem(hotbarSlot, item);
                return true;
            }
            return false;
        }
        if (slot.equals("offhand")) {
            ItemStack current = inventory.getItemInOffHand();
            if (current == null || current.getType() == Material.AIR || toolRegistry.isClaimModeTool(current)) {
                inventory.setItemInOffHand(item);
                return true;
            }
            return false;
        }
        return false;
    }

    Component fallbackMessage() {
        return fallbackMessage;
    }
}
```

- [ ] **Step 5: Run service tests**

Run:

```powershell
.\gradlew.bat :havenclaims-plugin:test --tests "com.invisiblespiders.havenclaims.plugin.claimmode.ClaimModeServiceTest"
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeSession.java `
        havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeService.java `
        havenclaims-plugin/src/test/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeServiceTest.java
git commit -m "feat: add claim mode session lifecycle"
```

---

### Task 5: Interaction And Command Guards

**Files:**
- Create: `havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeCommandGuard.java`
- Create: `havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeListener.java`
- Test: `havenclaims-plugin/src/test/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeCommandGuardTest.java`
- Test: `havenclaims-plugin/src/test/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeListenerTest.java`

- [ ] **Step 1: Write failing command guard test**

Create `havenclaims-plugin/src/test/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeCommandGuardTest.java`:

```java
package com.invisiblespiders.havenclaims.plugin.claimmode;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ClaimModeCommandGuardTest {
    @Test
    void blocksConfiguredCommandsAndAllowsClaimModeCommands() {
        ClaimModeCommandGuard guard = new ClaimModeCommandGuard(
                new ClaimModeConfig(true, 5, List.of("storage", "pay"), List.of("claimmode", "cm", "claim"))
        );

        assertThat(guard.isBlocked("/storage open")).isTrue();
        assertThat(guard.isBlocked("/minecraft:pay Alice 10")).isTrue();
        assertThat(guard.isBlocked("/claimmode off")).isFalse();
        assertThat(guard.isBlocked("/claim mode")).isFalse();
    }
}
```

- [ ] **Step 2: Implement command guard**

Create `havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeCommandGuard.java`:

```java
package com.invisiblespiders.havenclaims.plugin.claimmode;

public final class ClaimModeCommandGuard {
    private ClaimModeConfig config;

    public ClaimModeCommandGuard(ClaimModeConfig config) {
        this.config = config;
    }

    public void reload(ClaimModeConfig config) {
        this.config = config;
    }

    public boolean isBlocked(String commandLine) {
        String root = ClaimModeConfig.normalizeCommandLabel(commandLine);
        if (config.allowedCommands().contains(root)) {
            return false;
        }
        return config.blockedCommands().contains(root);
    }
}
```

- [ ] **Step 3: Write listener guard tests**

Create `havenclaims-plugin/src/test/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeListenerTest.java`:

```java
package com.invisiblespiders.havenclaims.plugin.claimmode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.junit.jupiter.api.Test;

class ClaimModeListenerTest {
    @Test
    void blocksConfiguredCommandForActivePlayer() {
        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        ClaimModeService service = mock(ClaimModeService.class);
        when(service.isInClaimMode(playerId)).thenReturn(true);
        ClaimModeListener listener = new ClaimModeListener(service,
                new ClaimModeCommandGuard(new ClaimModeConfig(true, 5, java.util.List.of("storage"), java.util.List.of("claimmode", "cm", "claim"))),
                null);
        PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(player, "/storage");

        listener.onCommand(event);

        assertThat(event.isCancelled()).isTrue();
    }

    @Test
    void detectsHotbarClickActions() {
        assertThat(ClaimModeListener.touchesHotbar(0, ClickType.LEFT, InventoryAction.PICKUP_ALL)).isTrue();
        assertThat(ClaimModeListener.touchesHotbar(18, ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP)).isTrue();
        assertThat(ClaimModeListener.touchesHotbar(18, ClickType.LEFT, InventoryAction.PICKUP_ALL)).isFalse();
    }
}
```

- [ ] **Step 4: Implement listener**

Create `havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeListener.java`:

```java
package com.invisiblespiders.havenclaims.plugin.claimmode;

import com.invisiblespiders.havenclaims.plugin.message.MessageService;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

public final class ClaimModeListener implements Listener {
    private final ClaimModeService claimModeService;
    private final ClaimModeCommandGuard commandGuard;
    private final MessageService messageService;

    public ClaimModeListener(ClaimModeService claimModeService, ClaimModeCommandGuard commandGuard, MessageService messageService) {
        this.claimModeService = claimModeService;
        this.commandGuard = commandGuard;
        this.messageService = messageService;
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!claimModeService.isInClaimMode(event.getPlayer().getUniqueId()) || !commandGuard.isBlocked(event.getMessage())) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendMessage(message("claim-mode.blocked-command", Map.of("command", ClaimModeConfig.normalizeCommandLabel(event.getMessage()))));
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (claimModeService.isInClaimMode(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(message("claim-mode.blocked-drop", Map.of()));
        }
    }

    @EventHandler
    public void onPickup(PlayerPickupItemEvent event) {
        if (claimModeService.isInClaimMode(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(message("claim-mode.blocked-pickup", Map.of()));
        }
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (claimModeService.isInClaimMode(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(message("claim-mode.blocked-swap", Map.of()));
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !claimModeService.isInClaimMode(player.getUniqueId())) {
            return;
        }
        if (touchesHotbar(event.getSlot(), event.getClick(), event.getAction())
                || claimModeService.toolRegistry().isClaimModeTool(event.getCurrentItem())
                || claimModeService.toolRegistry().isClaimModeTool(event.getCursor())) {
            event.setCancelled(true);
            player.sendMessage(message("claim-mode.blocked-inventory", Map.of()));
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !claimModeService.isInClaimMode(player.getUniqueId())) {
            return;
        }
        boolean touchesHotbar = event.getRawSlots().stream().anyMatch(slot -> slot >= 36 && slot <= 44);
        if (touchesHotbar || claimModeService.toolRegistry().isClaimModeTool(event.getOldCursor())) {
            event.setCancelled(true);
            player.sendMessage(message("claim-mode.blocked-inventory", Map.of()));
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (claimModeService.isInClaimMode(event.getPlayer().getUniqueId())
                && claimModeService.toolRegistry().isClaimModeTool(item)) {
            event.setCancelled(true);
            claimModeService.toolRegistry().resolve(item).ifPresent(tool -> {
                if (!tool.enabled()) {
                    event.getPlayer().sendMessage(message(tool.disabledMessageKey(), Map.of()));
                } else {
                    tool.handler().handle(event.getPlayer(), event);
                }
            });
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (claimModeService.isInClaimMode(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && claimModeService.isInClaimMode(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        claimModeService.exit(event.getPlayer(), ClaimModeService.ExitReason.LOGOUT);
    }

    static boolean touchesHotbar(int slot, ClickType clickType, InventoryAction action) {
        return (slot >= 0 && slot <= 8)
                || clickType == ClickType.NUMBER_KEY
                || action == InventoryAction.HOTBAR_MOVE_AND_READD
                || action == InventoryAction.HOTBAR_SWAP;
    }

    private net.kyori.adventure.text.Component message(String key, Map<String, String> placeholders) {
        return messageService == null ? claimModeService.fallbackMessage() : messageService.render(key, placeholders);
    }
}
```

If the current Paper API version uses `EntityPickupItemEvent` instead of `PlayerPickupItemEvent`, replace that handler with `EntityPickupItemEvent` and guard `event.getEntity() instanceof Player`.

- [ ] **Step 5: Run guard tests**

Run:

```powershell
.\gradlew.bat :havenclaims-plugin:test --tests "com.invisiblespiders.havenclaims.plugin.claimmode.ClaimModeCommandGuardTest" --tests "com.invisiblespiders.havenclaims.plugin.claimmode.ClaimModeListenerTest"
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeCommandGuard.java `
        havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeListener.java `
        havenclaims-plugin/src/test/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeCommandGuardTest.java `
        havenclaims-plugin/src/test/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeListenerTest.java
git commit -m "feat: guard claim mode interactions"
```

---

### Task 6: Commands And Plugin Wiring

**Files:**
- Create: `havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeAction.java`
- Create: `havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeCommand.java`
- Modify: `havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/HavenClaimsPlugin.java`
- Modify: `havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/command/ClaimsCommand.java`
- Test: `havenclaims-plugin/src/test/java/com/invisiblespiders/havenclaims/plugin/command/ClaimsCommandPermissionTest.java`

- [ ] **Step 1: Add action enum**

Create `havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeAction.java`:

```java
package com.invisiblespiders.havenclaims.plugin.claimmode;

public enum ClaimModeAction {
    ON,
    OFF,
    TOGGLE;

    public static ClaimModeAction from(String[] args) {
        if (args.length == 0) {
            return TOGGLE;
        }
        return switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
            case "on", "enable", "start" -> ON;
            case "off", "disable", "stop", "exit" -> OFF;
            default -> TOGGLE;
        };
    }
}
```

- [ ] **Step 2: Add command executor**

Create `havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeCommand.java`:

```java
package com.invisiblespiders.havenclaims.plugin.claimmode;

import com.invisiblespiders.havenclaims.plugin.message.MessageService;
import java.util.List;
import java.util.Map;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class ClaimModeCommand implements CommandExecutor, TabCompleter {
    private final ClaimModeService claimModeService;
    private final MessageService messageService;

    public ClaimModeCommand(ClaimModeService claimModeService, MessageService messageService) {
        this.claimModeService = claimModeService;
        this.messageService = messageService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageService.render("command.player-only", Map.of()));
            return true;
        }
        return execute(player, ClaimModeAction.from(args));
    }

    public boolean execute(Player player, ClaimModeAction action) {
        if (action == ClaimModeAction.OFF || claimModeService.isInClaimMode(player.getUniqueId())) {
            ClaimModeService.ExitResult result = claimModeService.exit(player, ClaimModeService.ExitReason.MANUAL);
            player.sendMessage(messageService.render(result == ClaimModeService.ExitResult.NOT_ACTIVE
                    ? "claim-mode.not-active" : "claim-mode.exited", Map.of()));
            return true;
        }
        ClaimModeService.EnterResult result = claimModeService.enter(player);
        String key = switch (result) {
            case ENTERED -> "claim-mode.entered";
            case DISABLED -> "claim-mode.disabled";
            case NO_PERMISSION -> "command.claim.no-permission";
            case ALREADY_ACTIVE -> "claim-mode.already-active";
        };
        player.sendMessage(messageService.render(key, Map.of()));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("on", "off", "toggle").stream()
                    .filter(option -> option.startsWith(args[0].toLowerCase(java.util.Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }
}
```

- [ ] **Step 3: Wire in `HavenClaimsPlugin`**

Modify `havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/HavenClaimsPlugin.java`:

Add fields:

```java
private ClaimModeService claimModeService;
private ClaimModeCommandGuard claimModeCommandGuard;
```

After creating `ClaimToolService claimToolService = new ClaimToolService(this);`, create claim mode services after `messageService`:

```java
ClaimModeConfig claimModeConfig = ClaimModeConfig.from(getConfig());
claimModeCommandGuard = new ClaimModeCommandGuard(claimModeConfig);
ClaimModeRecoveryStore claimModeRecoveryStore = new ClaimModeRecoveryStore(getDataFolder().toPath());
ClaimModeSessionHistory claimModeSessionHistory = new ClaimModeSessionHistory(
        getDataFolder().toPath(), claimModeConfig.historyPerPlayer());
ClaimModeToolRegistry claimModeToolRegistry = StandardClaimModeTools.createRegistry(
        new NamespacedKey(this, "claim_mode_tool"));
claimModeService = new ClaimModeService(
        claimModeConfig,
        claimModeToolRegistry,
        claimModeSessionHistory,
        claimModeRecoveryStore,
        Component.text("Claim mode")
);
claimToolService.setClaimModeToolRegistry(claimModeToolRegistry);
```

Register listener after `claimToolListener`:

```java
getServer().getPluginManager().registerEvents(
        new ClaimModeListener(claimModeService, claimModeCommandGuard, messageService), this);
```

Register command after `/claim`:

```java
ClaimModeCommand claimModeCommand = new ClaimModeCommand(claimModeService, messageService);
var claimModePluginCommand = Objects.requireNonNull(getCommand("claimmode"), "claimmode command is not defined in plugin.yml");
claimModePluginCommand.setExecutor(claimModeCommand);
claimModePluginCommand.setTabCompleter(claimModeCommand);
claimsCommand.setClaimModeCommand(claimModeCommand);
```

In `performReload()` add:

```java
ClaimModeConfig claimModeConfig = ClaimModeConfig.from(getConfig());
claimModeService.reload(claimModeConfig);
claimModeCommandGuard.reload(claimModeConfig);
```

In `onDisable()` before clearing services:

```java
if (claimModeService != null) {
    claimModeService.restoreAll(getServer().getOnlinePlayers(), ClaimModeService.ExitReason.PLUGIN_DISABLE);
    claimModeService = null;
}
claimModeCommandGuard = null;
```

Remove:

```java
saveResourceIfMissing("recipes.yml");
new ClaimToolRecipeService(this, claimToolService).register(loadYamlResource("recipes.yml"));
```

Remove imports from `HavenClaimsPlugin`:

```java
import com.invisiblespiders.havenclaims.plugin.recipe.ClaimToolRecipeService;
```

Add imports:

```java
import com.invisiblespiders.havenclaims.plugin.claimmode.ClaimModeCommand;
import com.invisiblespiders.havenclaims.plugin.claimmode.ClaimModeCommandGuard;
import com.invisiblespiders.havenclaims.plugin.claimmode.ClaimModeConfig;
import com.invisiblespiders.havenclaims.plugin.claimmode.ClaimModeListener;
import com.invisiblespiders.havenclaims.plugin.claimmode.ClaimModeRecoveryStore;
import com.invisiblespiders.havenclaims.plugin.claimmode.ClaimModeService;
import com.invisiblespiders.havenclaims.plugin.claimmode.ClaimModeSessionHistory;
import com.invisiblespiders.havenclaims.plugin.claimmode.ClaimModeToolRegistry;
import com.invisiblespiders.havenclaims.plugin.claimmode.StandardClaimModeTools;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
```

- [ ] **Step 4: Add `/claim mode` bridge**

Modify `ClaimsCommand`:

Add field:

```java
private ClaimModeCommand claimModeCommand;
```

Add setter:

```java
public void setClaimModeCommand(ClaimModeCommand claimModeCommand) {
    this.claimModeCommand = claimModeCommand;
}
```

Add `"mode"` to `ROOT_SUGGESTIONS`.

At the top of `onCommand` after player check:

```java
if (args.length >= 1 && args[0].equalsIgnoreCase("mode")) {
    if (claimModeCommand == null) {
        player.sendMessage(message("command.unavailable.claim-creation"));
        return true;
    }
    String[] modeArgs = Arrays.copyOfRange(args, 1, args.length);
    return claimModeCommand.execute(player, ClaimModeAction.from(modeArgs));
}
```

Add imports:

```java
import com.invisiblespiders.havenclaims.plugin.claimmode.ClaimModeAction;
import com.invisiblespiders.havenclaims.plugin.claimmode.ClaimModeCommand;
```

- [ ] **Step 5: Add command permission tests**

Modify `ClaimsCommandPermissionTest` with a focused test:

```java
@Test
void claimModeSubcommandDelegatesToClaimModeCommand() {
    ClaimModeCommand claimModeCommand = mock(ClaimModeCommand.class);
    ClaimsCommand command = new ClaimsCommand(mock(ClaimToolService.class));
    command.setClaimModeCommand(claimModeCommand);
    Player player = mock(Player.class);
    when(player.getUniqueId()).thenReturn(UUID.randomUUID());

    command.onCommand(player, mock(Command.class), "claim", new String[] {"mode", "on"});

    verify(claimModeCommand).execute(eq(player), eq(ClaimModeAction.ON));
}
```

- [ ] **Step 6: Run command tests**

Run:

```powershell
.\gradlew.bat :havenclaims-plugin:test --tests "com.invisiblespiders.havenclaims.plugin.command.ClaimsCommandPermissionTest"
```

Expected: PASS.

- [ ] **Step 7: Commit**

```powershell
git add havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeAction.java `
        havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/claimmode/ClaimModeCommand.java `
        havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/HavenClaimsPlugin.java `
        havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/command/ClaimsCommand.java `
        havenclaims-plugin/src/test/java/com/invisiblespiders/havenclaims/plugin/command/ClaimsCommandPermissionTest.java
git commit -m "feat: wire claim mode commands"
```

---

### Task 7: Retire Permanent Claim Tool Flow

**Files:**
- Modify: `havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/command/ClaimsCommand.java`
- Modify: `havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/listener/ClaimToolListener.java`
- Modify: `havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/tool/ClaimToolService.java`
- Delete: `havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/recipe/ClaimToolRecipeConfig.java`
- Delete: `havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/recipe/ClaimToolRecipeService.java`
- Delete: `havenclaims-plugin/src/main/resources/recipes.yml`
- Delete: `havenclaims-plugin/src/test/java/com/invisiblespiders/havenclaims/plugin/recipe/ClaimToolRecipeConfigTest.java`
- Modify: command/tool tests

- [ ] **Step 1: Remove `/claim tool` command path**

Modify `ClaimsCommand`:

Remove `"tool"` from `ROOT_SUGGESTIONS`.

Remove:

```java
if (args.length == 1 && args[0].equalsIgnoreCase("tool")) {
    return giveTool(player);
}
```

Delete the `giveTool(Player player)` method.

Delete `CLAIM_TOOL_PERMISSION` if it is no longer used in `ClaimsCommand`.

- [ ] **Step 2: Remove held-tool and charge requirements from claim creation**

In `previewCreateClaim`, remove:

```java
ItemStack mainHandItem = player.getInventory().getItemInMainHand();
if (!claimToolService.isClaimTool(mainHandItem)) {
    player.sendMessage(message("claim.hold-tool"));
    return true;
}
if (claimToolService.currentCharges(mainHandItem) < chunks.size()) {
    player.sendMessage(message("claim.not-enough-charges", Map.of(
            "needed", String.valueOf(chunks.size()),
            "available", String.valueOf(claimToolService.currentCharges(mainHandItem))
    )));
    return true;
}
```

In `createClaim`, remove the same held-tool and charge requirement block.

Remove:

```java
claimToolService.spendCharges(mainHandItem, chunks.size());
```

Delete now-unused `ItemStack` import if applicable.

- [ ] **Step 3: Keep selection permission but require claim mode for selection**

Modify `ClaimToolListener.onPlayerInteract` so claim-mode tools are still handled, and permanent tools are no longer required for normal workflow. If a `ClaimModeService` dependency has been added to the listener, add:

```java
if (!claimModeService.isInClaimMode(player.getUniqueId())) {
    return;
}
```

Keep the existing `claimToolService.isClaimTool(itemStack)` check so only the claim-mode claim tool selects.

- [ ] **Step 4: Delete recipe files**

Delete:

```powershell
git rm havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/recipe/ClaimToolRecipeConfig.java
git rm havenclaims-plugin/src/main/java/com/invisiblespiders/havenclaims/plugin/recipe/ClaimToolRecipeService.java
git rm havenclaims-plugin/src/main/resources/recipes.yml
git rm havenclaims-plugin/src/test/java/com/invisiblespiders/havenclaims/plugin/recipe/ClaimToolRecipeConfigTest.java
```

- [ ] **Step 5: Remove old messages and permissions**

Modify `messages.yml`:

Remove:

```yaml
  not-enough-charges: "<red>Your claim tool needs <yellow><needed></yellow> charges, but only has <yellow><available></yellow>."
  hold-tool: "<red>Hold your claim tool to create a claim."
tool:
  name: "<gold>Claiming Hoe</gold>"
  lore:
    - "<gray>Charges: <yellow><charges></yellow>/<yellow><max_charges></yellow>"
    - "<gray>Right-click two chunks to select land.</gray>"
command:
  tool:
    no-permission: "<red>You do not have permission to use the claim tool."
    given: "<green>Claim tool added to your inventory."
```

Change help text:

```yaml
    mode: "<yellow>/claim mode</yellow><gray> - toggles claim mode.</gray>"
```

Use `mode` in `sendHelp` instead of `tool`.

Modify `plugin.yml` by removing:

```yaml
  havenclaims.tool.craft:
  havenclaims.tool.recharge:
```

- [ ] **Step 6: Update tests**

Remove tests expecting `/claim tool` to give an item or charge spending to block creation. Add or adjust tests proving claim creation uses pending selection without held tool checks:

```java
@Test
void createDoesNotRequirePermanentClaimTool() {
    SelectionService selectionService = new SelectionService(new ClaimService());
    Player player = mock(Player.class);
    UUID playerId = UUID.randomUUID();
    when(player.getUniqueId()).thenReturn(playerId);
    when(player.hasPermission("havenclaims.claim")).thenReturn(true);

    selectionService.replacePendingSelection(playerId, Set.of(new ClaimChunk(UUID.randomUUID(), 1, 1)));

    ClaimsCommand command = new ClaimsCommand(
            mock(ClaimToolService.class),
            selectionService,
            mock(ClaimCreationService.class),
            mock(ClaimIndex.class),
            null,
            null,
            new PendingClaimMergeService(),
            new MessageService(Map.of()),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            mock(HavenClaimsLimitService.class),
            null,
            null
    );

    command.onCommand(player, mock(Command.class), "claim", new String[] {"create", "Home"});

    verify(player, never()).sendMessage(argThat(component -> component.toString().contains("hold-tool")));
}
```

Adjust this exact skeleton to existing constructor helpers in the test file.

- [ ] **Step 7: Run command and recipe-adjacent tests**

Run:

```powershell
.\gradlew.bat :havenclaims-plugin:test --tests "com.invisiblespiders.havenclaims.plugin.command.*" --tests "com.invisiblespiders.havenclaims.plugin.tool.*"
```

Expected: PASS.

- [ ] **Step 8: Commit**

```powershell
git add -A
git commit -m "refactor: retire permanent claim tool flow"
```

---

### Task 8: Documentation And Final Verification

**Files:**
- Modify: `README.md`
- Modify: `docs/admin-guide.md`
- Modify: `docs/configuration.md`
- Modify: `docs/permissions.md`
- Modify: `havenclaims-plugin/src/main/resources/messages.yml`
- Modify: `havenclaims-plugin/src/main/resources/plugin.yml`

- [ ] **Step 1: Update README player flow**

In `README.md`, replace claim tool setup instructions with:

```markdown
- Run `/claimmode`, `/cm`, or `/claim mode` to enter claim mode.
- Claim mode stores your hotbar and offhand, gives temporary claim tools, and restores your items when you exit.
- Use the claim tool in slot 0 to select two chunks.
- Use slot 8 or `/claimmode off` to exit claim mode.
```

Replace the command table entry for `/claim tool` with:

```markdown
| `/claimmode`, `/cm`, `/claim mode` | `havenclaims.claim` | Toggles claim mode for claim creation and editing. |
```

Remove `havenclaims.tool.craft` and `havenclaims.tool.recharge` from the permissions table.

- [ ] **Step 2: Update admin and config docs**

In `docs/admin-guide.md`, replace "claim-tool selection" with "claim-mode selection".

In `docs/configuration.md`, remove references to `tool.yml` and `recipes.yml`. Add:

```markdown
- `claim-mode` controls whether claim mode is enabled, how many audit sessions are retained per player, and which commands are blocked while a player's hotbar/offhand are stored.
```

In `docs/permissions.md`, remove `havenclaims.tool.craft` and `havenclaims.tool.recharge`. Explain that normal players use `havenclaims.claim` for claim mode.

- [ ] **Step 3: Run old-surface search**

Run:

```powershell
rg -n "recipes.yml|ClaimToolRecipe|/claim tool|tool\\.craft|tool\\.recharge|not-enough-charges|hold-tool|Charges:" README.md docs havenclaims-plugin/src/main
```

Expected: no matches, except historical docs under `docs/superpowers/` if the team chooses to keep old planning records. If historical docs match, do not edit old dated plans unless they confuse current documentation.

- [ ] **Step 4: Run full verification**

Run:

```powershell
.\gradlew.bat clean test build
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Verify plugin metadata**

Run:

```powershell
tar -xOf havenclaims-plugin\build\libs\HavenClaims-1.7.0-SNAPSHOT.jar plugin.yml
```

Expected output includes:

```yaml
name: HavenClaims
commands:
  claim:
  claimmode:
    aliases: [cm]
permissions:
  havenclaims.claim:
```

Expected output does not include:

```yaml
havenclaims.tool.craft
havenclaims.tool.recharge
```

- [ ] **Step 6: Commit docs and final cleanup**

```powershell
git add README.md docs/admin-guide.md docs/configuration.md docs/permissions.md havenclaims-plugin/src/main/resources/messages.yml havenclaims-plugin/src/main/resources/plugin.yml
git commit -m "docs: document claim mode inventory flow"
```

- [ ] **Step 7: Final status**

Run:

```powershell
git status -sb
```

Expected: clean branch with commits ahead of base.

## Manual Smoke Test

Run after build on a Paper server with HavenCore:

1. Put enchanted/damaged/custom-named items in hotbar slots `0`, `1`, and `8`; put a shield or tool in offhand; wear enchanted armor.
2. Run `/claimmode`.
3. Confirm hotbar and offhand are replaced, armor remains equipped, and main inventory is unchanged.
4. Attempt item pickup, item drop, swap-hand, hotbar click, number-key swap, drag over hotbar, `/storage`, and `/pay`.
5. Confirm each blocked action is cancelled with a message.
6. Use the claim tool to select chunks.
7. Use the subclaim item and confirm coming-soon feedback.
8. Use slot `8` to exit claim mode and confirm exact hotbar/offhand restoration.
9. Repeat entry and exit with `/cm`, `/claimmode off`, and `/claim mode`.
10. Enter claim mode and disconnect. Reconnect and confirm the player is not in claim mode and stored items were restored.
11. Enter claim mode and stop/reload the plugin. Confirm active sessions are restored before disable completes.
12. Inspect `plugins/HavenClaims/logs/claimmode-history.log` and confirm item summaries plus Base64 backups exist for the latest sessions.

## Plan Self-Review

- Spec coverage: entry points, permission model, hotbar/offhand snapshots, restore lifecycle, disabled subclaim tool, tool registry, old craftable-tool removal, item interaction guards, command guard, audit backups, pending recovery, config/messages, tests, docs, and manual smoke testing are covered.
- Placeholder scan: no placeholder sections and no incomplete task instructions.
- Type consistency: the plan consistently uses `ClaimModeConfig`, `ClaimModeService`, `ClaimModeToolRegistry`, `ClaimModeCommand`, `ClaimModeListener`, `ClaimModeCommandGuard`, `ClaimModeItemCodec`, `ClaimModeSessionHistory`, and `ClaimModeRecoveryStore`.
