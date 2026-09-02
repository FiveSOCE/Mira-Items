package com.mira.items.store;

import com.mira.items.MiraItemsPlugin;
import com.mira.items.model.MiraItemDefinition;
import com.mira.items.model.MiraItemDefinitions;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class ItemStateStore {
    private final MiraItemsPlugin plugin;
    private final File file;
    private final YamlConfiguration data;

    public ItemStateStore(MiraItemsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "state.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        initializeDefaults();
    }

    private void initializeDefaults() {
        boolean changed = false;
        for (MiraItemDefinition definition : MiraItemDefinitions.all()) {
            String base = base(definition.id());
            if (!data.contains(base + ".enabled")) {
                data.set(base + ".enabled", true);
                changed = true;
            }
            if (!data.contains(base + ".limit")) {
                data.set(base + ".limit", definition.defaultLimit());
                changed = true;
            }
        }
        if (changed) save();
    }

    public synchronized boolean enabled(String itemId) {
        return data.getBoolean(base(itemId) + ".enabled", true);
    }

    public synchronized void setEnabled(String itemId, boolean enabled) {
        data.set(base(itemId) + ".enabled", enabled);
        save();
    }

    public synchronized int limit(String itemId) {
        return data.getInt(base(itemId) + ".limit", -1);
    }

    public synchronized void setLimit(String itemId, int limit) {
        data.set(base(itemId) + ".limit", limit);
        save();
    }

    public synchronized int issuedCount(String itemId) {
        ConfigurationSection section = data.getConfigurationSection(base(itemId) + ".issued");
        return section == null ? 0 : section.getKeys(false).size();
    }

    public synchronized boolean canIssue(String itemId) {
        int limit = limit(itemId);
        return limit < 0 || issuedCount(itemId) < limit;
    }

    public synchronized Optional<IssuedRecord> issue(MiraItemDefinition definition, Player owner, String date) {
        if (!enabled(definition.id()) || !canIssue(definition.id())) return Optional.empty();

        UUID issueId = UUID.randomUUID();
        String path = issuedPath(definition.id(), issueId);
        data.set(path + ".owner-uuid", owner.getUniqueId().toString());
        data.set(path + ".owner-name", owner.getName());
        data.set(path + ".date", date);
        save();
        return Optional.of(new IssuedRecord(issueId, owner.getUniqueId(), owner.getName(), date));
    }

    public synchronized Optional<IssuedRecord> record(String itemId, UUID issueId) {
        String path = issuedPath(itemId, issueId);
        String ownerUuid = data.getString(path + ".owner-uuid");
        String ownerName = data.getString(path + ".owner-name");
        String date = data.getString(path + ".date");
        if (ownerUuid == null || ownerName == null || date == null) return Optional.empty();
        try {
            return Optional.of(new IssuedRecord(issueId, UUID.fromString(ownerUuid), ownerName, date));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    public synchronized List<IssuedRecord> records(String itemId) {
        List<IssuedRecord> records = new ArrayList<>();
        ConfigurationSection section = data.getConfigurationSection(base(itemId) + ".issued");
        if (section == null) return records;
        for (String key : section.getKeys(false)) {
            try {
                record(itemId, UUID.fromString(key)).ifPresent(records::add);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return records;
    }

    public synchronized boolean removeIssue(String itemId, UUID issueId) {
        String path = issuedPath(itemId, issueId);
        if (!data.contains(path)) return false;
        data.set(path, null);
        save();
        return true;
    }

    public synchronized int reset(String itemId) {
        int count = issuedCount(itemId);
        data.set(base(itemId) + ".issued", null);
        save();
        return count;
    }

    private String base(String itemId) {
        return "items." + itemId;
    }

    private String issuedPath(String itemId, UUID issueId) {
        return base(itemId) + ".issued." + issueId;
    }

    private void save() {
        try {
            file.getParentFile().mkdirs();
            data.save(file);
        } catch (IOException error) {
            plugin.getLogger().severe("Could not save state.yml: " + error.getMessage());
            throw new IllegalStateException("Could not save MiraItems state", error);
        }
    }

    public record IssuedRecord(UUID issueId, UUID ownerId, String ownerName, String date) {
    }
}
