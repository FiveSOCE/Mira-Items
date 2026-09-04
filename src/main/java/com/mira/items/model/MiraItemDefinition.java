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
        String abilityId
) {
    public MiraItemDefinition {
        abilityId = normalizeAbility(abilityId);
    }

    public boolean unlimited() {
        return defaultLimit < 0;
    }

    public boolean ability(MiraAbility ability) {
        return ability != null && abilityId.equalsIgnoreCase(ability.name());
    }

    private static String normalizeAbility(String value) {
        if (value == null || value.isBlank()) return "NONE";
        return value.trim().toUpperCase(java.util.Locale.ROOT).replaceAll("[^A-Z0-9_]+", "_");
    }
}
