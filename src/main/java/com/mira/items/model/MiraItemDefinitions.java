package com.mira.items.model;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class MiraItemDefinitions {
    private static final Map<String, MiraItemDefinition> BY_ID = new LinkedHashMap<>();
    private static final Map<String, MiraItemDefinition> BY_ALIAS = new LinkedHashMap<>();

    static {
        register(new MiraItemDefinition("pyro_axe", "&4Pyro Axe", List.of("pyro", "pyroaxe", "pyro_axe"),
                List.of("&4Run For The Hills", "&f"), Material.NETHERITE_AXE,
                Map.of(Enchantment.SHARPNESS, 5, Enchantment.FIRE_ASPECT, 2), -1, MiraAbility.PYRO.name()));
        register(new MiraItemDefinition("excalibur", "Excalibur", List.of("excalibur"),
                List.of("&6Thy Might Of King Arthur", "&f"), Material.GOLDEN_SWORD,
                Map.of(Enchantment.SHARPNESS, 10, Enchantment.INFINITY, 10), 2, MiraAbility.EXCALIBUR.name()));
        register(new MiraItemDefinition("lochaber_axe", "&aLochaber Axe", List.of("lochaber", "lochaberaxe", "lochaber_axe"),
                List.of("&aCome Closer!", "&f"), Material.DIAMOND_AXE,
                Map.of(Enchantment.SHARPNESS, 10), 5, MiraAbility.LOCHABER.name()));
        register(new MiraItemDefinition("empower", "&1Empower!", List.of("empower", "empower!"),
                List.of("&9Empower Thy Ally!", "&f"), Material.GOAT_HORN,
                Map.of(Enchantment.UNBREAKING, 10), 10, MiraAbility.EMPOWER.name()));
    }

    private MiraItemDefinitions() { }

    public static synchronized boolean register(MiraItemDefinition definition) {
        if (definition == null || definition.id() == null || definition.id().isBlank()) return false;
        String id = normalizeId(definition.id());
        if (BY_ID.containsKey(id)) return false;
        BY_ID.put(id, definition);
        index(definition);
        return true;
    }

    public static synchronized boolean replace(MiraItemDefinition definition) {
        if (definition == null || definition.id() == null || definition.id().isBlank()) return false;
        unregister(definition.id());
        return register(definition);
    }

    public static synchronized boolean unregister(String id) {
        MiraItemDefinition removed = BY_ID.remove(normalizeId(id));
        if (removed == null) return false;
        rebuildAliases();
        return true;
    }

    private static void index(MiraItemDefinition definition) {
        BY_ALIAS.put(normalize(definition.id()), definition);
        BY_ALIAS.put(normalize(definition.displayName()), definition);
        for (String alias : definition.aliases()) BY_ALIAS.put(normalize(alias), definition);
    }

    private static void rebuildAliases() {
        BY_ALIAS.clear();
        BY_ID.values().forEach(MiraItemDefinitions::index);
    }

    public static Optional<MiraItemDefinition> find(String input) {
        if (input == null) return Optional.empty();
        return Optional.ofNullable(BY_ALIAS.get(normalize(input)));
    }

    public static Optional<MiraItemDefinition> byId(String id) { return Optional.ofNullable(BY_ID.get(normalizeId(id))); }
    public static Collection<MiraItemDefinition> all() { return List.copyOf(BY_ID.values()); }

    private static String normalizeId(String input) {
        return input == null ? "" : input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_").replaceAll("^_|_$", "");
    }

    public static String normalize(String input) {
        String stripped = input.replaceAll("(?i)&[0-9A-FK-ORX]", "");
        return stripped.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
