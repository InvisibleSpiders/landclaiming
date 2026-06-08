package com.nick.landclaims.plugin.visual;

import org.bukkit.Color;
import org.bukkit.Material;

public enum BorderColor {
    GREEN(Color.LIME, Material.LIME_STAINED_GLASS),
    RED(Color.RED, Material.RED_STAINED_GLASS),
    YELLOW(Color.YELLOW, Material.YELLOW_STAINED_GLASS),
    AQUA(Color.AQUA, Material.LIGHT_BLUE_STAINED_GLASS),
    GOLD(Color.ORANGE, Material.ORANGE_STAINED_GLASS);

    private final Color bukkitColor;
    private final Material displayMaterial;

    BorderColor(Color bukkitColor, Material displayMaterial) {
        this.bukkitColor = bukkitColor;
        this.displayMaterial = displayMaterial;
    }

    public Color bukkitColor() {
        return bukkitColor;
    }

    public Material displayMaterial() {
        return displayMaterial;
    }
}
