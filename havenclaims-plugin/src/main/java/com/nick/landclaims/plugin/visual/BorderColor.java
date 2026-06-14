package com.nick.landclaims.plugin.visual;

import org.bukkit.Color;
import org.bukkit.Material;

public enum BorderColor {
    GREEN(Color.LIME, Material.LIME_STAINED_GLASS, "green"),
    RED(Color.RED, Material.RED_STAINED_GLASS, "red"),
    YELLOW(Color.YELLOW, Material.YELLOW_STAINED_GLASS, "yellow"),
    AQUA(Color.AQUA, Material.LIGHT_BLUE_STAINED_GLASS, "aqua"),
    GOLD(Color.ORANGE, Material.ORANGE_STAINED_GLASS, "gold");

    private final Color bukkitColor;
    private final Material displayMaterial;
    private final String messageName;

    BorderColor(Color bukkitColor, Material displayMaterial, String messageName) {
        this.bukkitColor = bukkitColor;
        this.displayMaterial = displayMaterial;
        this.messageName = messageName;
    }

    public Color bukkitColor() {
        return bukkitColor;
    }

    public Material displayMaterial() {
        return displayMaterial;
    }

    public String messageName() {
        return messageName;
    }
}
