package com.mira.items.service;

import com.mira.items.MiraItemsPlugin;
import com.mira.items.api.MiraItemRegistration;
import com.mira.items.model.MiraAbility;
import com.mira.items.model.MiraItemDefinition;
import com.mira.items.model.MiraItemDefinitions;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class CustomItemRegistryService {
    private final MiraItemsPlugin plugin;
    private final File file;
    private final YamlConfiguration data;
    private final Map<String, Long> expiry = new LinkedHashMap<>();

    public CustomItemRegistryService(MiraItemsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "custom-items.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        load();
    }

    public synchronized boolean register(MiraItemRegistration registration) {
        return register(registration, null);
    }

    public synchronized boolean register(MiraItemRegistration registration, Instant expiresAt) {
        MiraItemDefinition definition = toDefinition(registration);
        if (!MiraItemDefinitions.register(definition)) return false;
        write(registration, expiresAt);
        if (expiresAt != null) expiry.put(definition.id(), expiresAt.toEpochMilli());
        save();
        return true;
    }

    public synchronized boolean unregister(String id) {
        boolean removed = MiraItemDefinitions.unregister(id);
        data.set("items." + normalizeId(id), null);
        expiry.remove(normalizeId(id));
        save();
        return removed;
    }

    public boolean active(String id) {
        Long end = expiry.get(normalizeId(id));
        if (end == null) return true;
        if (System.currentTimeMillis() < end) return true;
        unregister(id);
        return false;
    }

    public Optional<Instant> expiresAt(String id) {
        Long value = expiry.get(normalizeId(id));
        return value == null ? Optional.empty() : Optional.of(Instant.ofEpochMilli(value));
    }

    public void cleanupExpired() {
        for (String id : List.copyOf(expiry.keySet())) active(id);
    }

    private void load() {
        ConfigurationSection section = data.getConfigurationSection("items");
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            ConfigurationSection item = section.getConfigurationSection(id);
            if (item == null) continue;
            long expiresAt = item.getLong("expires-at", 0L);
            if (expiresAt > 0L && System.currentTimeMillis() >= expiresAt) {
                data.set("items." + id, null);
                continue;
            }
            MiraItemRegistration registration = new MiraItemRegistration(
                    id,
                    item.getString("display-name", id),
                    item.getStringList("aliases"),
                    item.getStringList("lore"),
                    org.bukkit.Material.matchMaterial(item.getString("material", "PAPER")),
                    readEnchantments(item.getConfigurationSection("enchantments")),
                    item.getInt("issue-limit", -1),
                    item.getString("ability", "NONE")
            );
            try {
                MiraItemDefinitions.register(toDefinition(registration));
                if (expiresAt > 0L) expiry.put(id, expiresAt);
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Could not load custom MiraItem " + id + ": " + ex.getMessage());
            }
        }
        save();
    }

    private MiraItemDefinition toDefinition(MiraItemRegistration registration) {
        if (registration == null || registration.material() == null) throw new IllegalArgumentException("Registration/material cannot be null");
        Map<Enchantment, Integer> enchants = new LinkedHashMap<>();
        if (registration.enchantments() != null) {
            registration.enchantments().forEach((name, level) -> {
                Enchantment enchantment = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(name.toLowerCase(Locale.ROOT)));
                if (enchantment != null) enchants.put(enchantment, level);
            });
        }
        MiraAbility ability;
        try { ability = MiraAbility.valueOf((registration.ability() == null ? "NONE" : registration.ability()).toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) { ability = MiraAbility.NONE; }
        return new MiraItemDefinition(
                normalizeId(registration.id()), registration.displayName(),
                registration.aliases() == null ? List.of() : List.copyOf(registration.aliases()),
                registration.lore() == null ? List.of() : List.copyOf(registration.lore()),
                registration.material(), Map.copyOf(enchants), registration.issueLimit(), ability);
    }

    private void write(MiraItemRegistration registration, Instant expiresAt) {
        String base = "items." + normalizeId(registration.id()) + ".";
        data.set(base + "display-name", registration.displayName());
        data.set(base + "aliases", registration.aliases());
        data.set(base + "lore", registration.lore());
        data.set(base + "material", registration.material().name());
        data.set(base + "issue-limit", registration.issueLimit());
        data.set(base + "ability", registration.ability() == null ? "NONE" : registration.ability());
        data.set(base + "expires-at", expiresAt == null ? null : expiresAt.toEpochMilli());
        if (registration.enchantments() != null) registration.enchantments().forEach((key, value) -> data.set(base + "enchantments." + key.toLowerCase(Locale.ROOT), value));
    }

    private Map<String, Integer> readEnchantments(ConfigurationSection section) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (section != null) for (String key : section.getKeys(false)) result.put(key, section.getInt(key));
        return result;
    }

    private String normalizeId(String id) {
        return id == null ? "" : id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_").replaceAll("^_|_$", "");
    }

    private void save() {
        try { data.save(file); }
        catch (IOException ex) { plugin.getLogger().warning("Could not save custom-items.yml: " + ex.getMessage()); }
    }
}
