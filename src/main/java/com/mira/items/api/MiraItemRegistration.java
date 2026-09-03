package com.mira.items.api;

import org.bukkit.Material;

import java.util.List;
import java.util.Map;

public record MiraItemRegistration(
        String id,
        String displayName,
        List<String> aliases,
        List<String> lore,
        Material material,
        Map<String, Integer> enchantments,
        int issueLimit,
        String ability
) { }
