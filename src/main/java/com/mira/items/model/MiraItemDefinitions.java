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
        register(new MiraItemDefinition(
                "pyro_axe",
                "&4Pyro Axe",
                List.of("pyro", "pyroaxe", "pyro_axe"),
                List.of("&4Run For The Hills", "&f"),
                Material.NETHERITE_AXE,
                Map.of(
                        Enchantment.SHARPNESS, 5,
                        Enchantment.FIRE_ASPECT, 2
                ),
                -1,
                MiraAbility.PYRO
        ));

        register(new MiraItemDefinition(
                "excalibur",
                "Excalibur",
                List.of("excalibur"),
                List.of("&6Thy Might Of King Arthur", "&f"),
                Material.GOLDEN_SWORD,
                Map.of(
                        Enchantment.SHARPNESS, 10,
                        Enchantment.INFINITY, 10
                ),
                2,
                MiraAbility.EXCALIBUR
        ));

        register(new MiraItemDefinition(
                "lochaber_axe",
                "&aLochaber Axe",
                List.of("lochaber", "lochaberaxe", "lochaber_axe"),
                List.of("&aCome Closer!", "&f"),
                Material.DIAMOND_AXE,
                Map.of(Enchantment.SHARPNESS, 10),
                5,
                MiraAbility.LOCHABER
        ));

        register(new MiraItemDefinition(
                "empower",
                "&1Empower!",
                List.of("empower", "empower!"),
                List.of("&9Empower Thy Ally!", "&f"),
                Material.GOAT_HORN,
                Map.of(Enchantment.UNBREAKING, 10),
                10,
                MiraAbility.EMPOWER
        ));
    }

    private MiraItemDefinitions() {
    }

    private static void register(MiraItemDefinition definition) {
        BY_ID.put(definition.id(), definition);
        BY_ALIAS.put(normalize(definition.id()), definition);
        BY_ALIAS.put(normalize(definition.displayName()), definition);
        for (String alias : definition.aliases()) {
            BY_ALIAS.put(normalize(alias), definition);
        }
    }

    public static Optional<MiraItemDefinition> find(String input) {
        if (input == null) return Optional.empty();
        return Optional.ofNullable(BY_ALIAS.get(normalize(input)));
    }

    public static Optional<MiraItemDefinition> byId(String id) {
        return Optional.ofNullable(BY_ID.get(id));
    }

    public static Collection<MiraItemDefinition> all() {
        return List.copyOf(BY_ID.values());
    }

    public static String normalize(String input) {
        String stripped = input.replaceAll("(?i)&[0-9A-FK-ORX]", "");
        return stripped.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
