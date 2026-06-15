package com.invisiblespiders.havenclaims.plugin.claimmode;

import java.util.List;
import java.util.Objects;
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
        return createRegistry(toolKey, (player, event) -> {});
    }

    public static ClaimModeToolRegistry createRegistry(
            NamespacedKey toolKey,
            ClaimModeTool.ClaimModeToolHandler claimHandler
    ) {
        ClaimModeTool.ClaimModeToolHandler handler = Objects.requireNonNull(claimHandler, "claimHandler");
        return new ClaimModeToolRegistry(toolKey, List.of(
                new ClaimModeTool("claim", 0, StandardClaimModeTools::claimTool, true, "", handler),
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
