package com.mira.items.model;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;

import java.util.List;
import java.util.Map;

public record MiraItemDefinition(
        String id,
        String displayName,
        List<String> aliases,
        List<String> lorePrefix,
        Material material,
        Map<Enchantment, Integer> enchants,
        int defaultLimit,
        MiraAbility ability
) {
    public boolean unlimited() {
        return defaultLimit < 0;
    }
}
