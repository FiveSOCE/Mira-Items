package com.mira.items.service;

import com.mira.items.MiraItemsPlugin;
import com.mira.items.api.MiraItemRegistration;
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
    private final Map<String, Long> starts = new LinkedHashMap<>();
    private final Map<String, Long> expiry = new LinkedHashMap<>();
    private final Map<String, String> eventIds = new LinkedHashMap<>();

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
        return register(registration, "", null, expiresAt);
    }

    public synchronized boolean register(MiraItemRegistration registration, String eventId, Instant startsAt, Instant expiresAt) {
        MiraItemDefinition definition = toDefinition(registration);
        if (!MiraItemDefinitions.register(definition)) return false;
        write(registration, eventId, startsAt, expiresAt);
        String id = definition.id();
        if (startsAt != null) starts.put(id, startsAt.toEpochMilli());
        if (expiresAt != null) expiry.put(id, expiresAt.toEpochMilli());
        if (eventId != null && !eventId.isBlank()) eventIds.put(id, eventId.trim());
        save();
        return true;
    }

    public synchronized boolean unregister(String id) {
        boolean removed = MiraItemDefinitions.unregister(id);
        data.set("items." + normalizeId(id), null);
        starts.remove(normalizeId(id));
        expiry.remove(normalizeId(id));
        eventIds.remove(normalizeId(id));
        save();
        return removed;
    }

    public boolean active(String id) {
        String key = normalizeId(id);
        long now = System.currentTimeMillis();
        Long start = starts.get(key);
        if (start != null && now < start) return false;
        Long end = expiry.get(key);
        if (end == null) return true;
        if (now < end) return true;
        unregister(id);
        return false;
    }

    public Optional<Instant> startsAt(String id) {
        Long value = starts.get(normalizeId(id));
        return value == null ? Optional.empty() : Optional.of(Instant.ofEpochMilli(value));
    }

    public Optional<Instant> expiresAt(String id) {
        Long value = expiry.get(normalizeId(id));
        return value == null ? Optional.empty() : Optional.of(Instant.ofEpochMilli(value));
    }

    public Optional<String> eventId(String id) {
        String value = eventIds.get(normalizeId(id));
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
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
            long startsAt = item.getLong("starts-at", 0L);
            long expiresAt = item.getLong("expires-at", 0L);
            String eventId = item.getString("event-id", "");
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
                if (startsAt > 0L) starts.put(id, startsAt);
                if (expiresAt > 0L) expiry.put(id, expiresAt);
                if (eventId != null && !eventId.isBlank()) eventIds.put(id, eventId);
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
        String abilityId = registration.ability() == null || registration.ability().isBlank()
                ? "NONE"
                : registration.ability();
        return new MiraItemDefinition(
                normalizeId(registration.id()), registration.displayName(),
                registration.aliases() == null ? List.of() : List.copyOf(registration.aliases()),
                registration.lore() == null ? List.of() : List.copyOf(registration.lore()),
                registration.material(), Map.copyOf(enchants), registration.issueLimit(), abilityId);
    }

    private void write(MiraItemRegistration registration, String eventId, Instant startsAt, Instant expiresAt) {
        String base = "items." + normalizeId(registration.id()) + ".";
        data.set(base + "display-name", registration.displayName());
        data.set(base + "aliases", registration.aliases());
        data.set(base + "lore", registration.lore());
        data.set(base + "material", registration.material().name());
        data.set(base + "issue-limit", registration.issueLimit());
        data.set(base + "ability", registration.ability() == null ? "NONE" : registration.ability());
        data.set(base + "event-id", eventId == null || eventId.isBlank() ? null : eventId.trim());
        data.set(base + "starts-at", startsAt == null ? null : startsAt.toEpochMilli());
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
