package com.mira.items.service;

import com.mira.items.MiraItemsPlugin;
import com.mira.items.model.MiraAbility;
import com.mira.items.model.MiraItemDefinition;
import com.mira.items.model.MiraItemDefinitions;
import com.mira.items.store.ItemStateStore;
import com.mira.items.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.MusicInstrument;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MusicInstrumentMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class MiraItemService {
    private final MiraItemsPlugin plugin;
    private final ItemStateStore state;
    private final CustomItemRegistryService registry;
    private final NamespacedKey itemIdKey;
    private final NamespacedKey issueIdKey;
    private final NamespacedKey ownerUuidKey;
    private final NamespacedKey ownerNameKey;
    private final NamespacedKey issuedDateKey;
    private final NamespacedKey signatureKey;
    private final NamespacedKey renameValueKey;
    private final NamespacedKey renameSignatureKey;
    private final String secret;

    public MiraItemService(MiraItemsPlugin plugin, ItemStateStore state, CustomItemRegistryService registry) {
        this.plugin = plugin;
        this.state = state;
        this.registry = registry;
        this.itemIdKey = new NamespacedKey(plugin, "item_id");
        this.issueIdKey = new NamespacedKey(plugin, "issue_id");
        this.ownerUuidKey = new NamespacedKey(plugin, "owner_uuid");
        this.ownerNameKey = new NamespacedKey(plugin, "owner_name");
        this.issuedDateKey = new NamespacedKey(plugin, "issued_date");
        this.signatureKey = new NamespacedKey(plugin, "signature");
        this.renameValueKey = new NamespacedKey(plugin, "custom_name");
        this.renameSignatureKey = new NamespacedKey(plugin, "custom_name_signature");
        this.secret = ensureSecret();
    }

    public Optional<ItemStack> issue(Player owner, MiraItemDefinition definition) {
        if (!registry.active(definition.id())) return Optional.empty();
        String date = LocalDate.now(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(plugin.getConfig().getString("date-format", "dd/MM/yyyy")));
        Optional<ItemStateStore.IssuedRecord> recordOptional = state.issue(definition, owner, date);
        if (recordOptional.isEmpty()) return Optional.empty();
        ItemStateStore.IssuedRecord record = recordOptional.get();
        try {
            ItemStack item = new ItemStack(definition.material());
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Text.component(resolve(definition.displayName(), definition, record.ownerName(), record.date())));
            meta.lore(expectedLore(definition, record.ownerName(), record.date()));
            definition.enchants().forEach((enchantment, level) -> meta.addEnchant(enchantment, level, true));
            if (definition.ability(MiraAbility.EMPOWER)) {
                if (!(meta instanceof MusicInstrumentMeta instrumentMeta)) throw new IllegalStateException("GOAT_HORN did not expose MusicInstrumentMeta");
                instrumentMeta.setInstrument(MusicInstrument.YEARN_GOAT_HORN);
            }
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(itemIdKey, PersistentDataType.STRING, definition.id());
            pdc.set(issueIdKey, PersistentDataType.STRING, record.issueId().toString());
            pdc.set(ownerUuidKey, PersistentDataType.STRING, record.ownerId().toString());
            pdc.set(ownerNameKey, PersistentDataType.STRING, record.ownerName());
            pdc.set(issuedDateKey, PersistentDataType.STRING, record.date());
            pdc.set(signatureKey, PersistentDataType.STRING, signature(definition.id(), record.issueId(), record.ownerId(), record.ownerName(), record.date()));
            item.setItemMeta(meta);
            return Optional.of(item);
        } catch (RuntimeException error) {
            state.removeIssue(definition.id(), record.issueId());
            throw error;
        }
    }

    public boolean give(Player owner, MiraItemDefinition definition) {
        Optional<ItemStack> itemOptional = issue(owner, definition);
        if (itemOptional.isEmpty()) return false;
        owner.getInventory().addItem(itemOptional.get()).values().forEach(leftover -> owner.getWorld().dropItemNaturally(owner.getLocation(), leftover));
        return true;
    }

    public Optional<MiraItemDefinition> identify(ItemStack item) { return identify(item, true); }

    public Optional<MiraItemDefinition> identify(ItemStack item, boolean invalidateOnFailure) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return Optional.empty();
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String itemId = pdc.get(itemIdKey, PersistentDataType.STRING);
        if (itemId == null) return Optional.empty();
        if (!registry.active(itemId)) {
            if (invalidateOnFailure) stripBacking(item);
            return Optional.empty();
        }
        Optional<MiraItemDefinition> definitionOptional = MiraItemDefinitions.byId(itemId);
        if (definitionOptional.isEmpty()) {
            if (invalidateOnFailure) stripBacking(item);
            return Optional.empty();
        }
        MiraItemDefinition definition = definitionOptional.get();
        String issueText = pdc.get(issueIdKey, PersistentDataType.STRING);
        String ownerUuidText = pdc.get(ownerUuidKey, PersistentDataType.STRING);
        String ownerName = pdc.get(ownerNameKey, PersistentDataType.STRING);
        String date = pdc.get(issuedDateKey, PersistentDataType.STRING);
        String storedSignature = pdc.get(signatureKey, PersistentDataType.STRING);
        boolean valid = item.getType() == definition.material() && issueText != null && ownerUuidText != null && ownerName != null && date != null && storedSignature != null;
        UUID issueId = null; UUID ownerId = null;
        if (valid) {
            try { issueId = UUID.fromString(issueText); ownerId = UUID.fromString(ownerUuidText); }
            catch (IllegalArgumentException error) { valid = false; }
        }
        if (valid) {
            ItemStateStore.IssuedRecord record = state.record(itemId, issueId).orElse(null);
            valid = record != null && record.ownerId().equals(ownerId) && record.ownerName().equals(ownerName) && record.date().equals(date)
                    && storedSignature.equals(signature(itemId, issueId, ownerId, ownerName, date));
        }
        if (valid) {
            String customName = pdc.get(renameValueKey, PersistentDataType.STRING);
            String customSignature = pdc.get(renameSignatureKey, PersistentDataType.STRING);
            Component expectedName;
            if (customName != null) {
                valid = customSignature != null && customSignature.equals(renameSignature(
                        itemId, issueId, ownerId, customName));
                expectedName = Text.component(customName);
            } else {
                valid = customSignature == null;
                expectedName = Text.component(resolve(definition.displayName(), definition, ownerName, date));
            }
            valid = valid && meta.displayName() != null && meta.displayName().equals(expectedName)
                    && meta.lore() != null && meta.lore().equals(expectedLore(definition, ownerName, date));
        }
        if (valid && definition.ability(MiraAbility.EMPOWER)) {
            valid = meta instanceof MusicInstrumentMeta instrumentMeta && MusicInstrument.YEARN_GOAT_HORN.equals(instrumentMeta.getInstrument());
        }
        if (valid) valid = pdc.getKeys().stream().noneMatch(key -> key.getNamespace().equals("miraenchantments") && key.getKey().startsWith("enchant_"));
        if (!valid) {
            if (invalidateOnFailure) stripBacking(item);
            return Optional.empty();
        }
        return Optional.of(definition);
    }

    public boolean claimed(ItemStack item) {
        return item != null && !item.getType().isAir() && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(itemIdKey, PersistentDataType.STRING);
    }

    public Optional<UUID> issueId(ItemStack item) {
        if (identify(item).isEmpty()) return Optional.empty();
        return issueIdNonMutating(item);
    }

    public Optional<UUID> issueIdNonMutating(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return Optional.empty();
        String value = item.getItemMeta().getPersistentDataContainer().get(issueIdKey, PersistentDataType.STRING);
        if (value == null) return Optional.empty();
        try { return Optional.of(UUID.fromString(value)); } catch (IllegalArgumentException ignored) { return Optional.empty(); }
    }

    public Inspection inspect(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return Inspection.EMPTY;
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String itemId = pdc.get(itemIdKey, PersistentDataType.STRING);
        if (itemId == null) return Inspection.EMPTY;
        UUID issueId = issueIdNonMutating(item).orElse(null);
        String ownerName = pdc.get(ownerNameKey, PersistentDataType.STRING);
        String ownerUuid = pdc.get(ownerUuidKey, PersistentDataType.STRING);
        String date = pdc.get(issuedDateKey, PersistentDataType.STRING);
        MiraItemDefinition definition = MiraItemDefinitions.byId(itemId).orElse(null);
        boolean activeDefinition = definition != null && registry.active(itemId);
        boolean valid = identify(item, false).isPresent();
        boolean backed = issueId != null && state.record(itemId, issueId).isPresent();
        return new Inspection(true, valid, backed, activeDefinition, itemId, issueId,
                ownerName == null ? "" : ownerName, ownerUuid == null ? "" : ownerUuid,
                date == null ? "" : date, definition == null ? "UNKNOWN" : definition.abilityId(),
                registry.expiresAt(itemId).orElse(null));
    }

    /**
     * Refreshes canonical name/lore/signature for an already-valid issued item.
     * Invalid or unbacked items are never repaired by this method.
     */
    public boolean migrateCanonical(ItemStack item) {
        MiraItemDefinition definition = identify(item, false).orElse(null);
        if (definition == null) return false;
        UUID issueId = issueIdNonMutating(item).orElse(null);
        if (issueId == null) return false;
        ItemStateStore.IssuedRecord record = state.record(definition.id(), issueId).orElse(null);
        if (record == null) return false;

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String customName = pdc.get(renameValueKey, PersistentDataType.STRING);
        String customNameSignature = pdc.get(renameSignatureKey, PersistentDataType.STRING);
        boolean signedCustomName = customName != null && customNameSignature != null
                && customNameSignature.equals(renameSignature(definition.id(), issueId, record.ownerId(), customName));
        meta.displayName(signedCustomName
                ? Text.component(customName)
                : Text.component(resolve(definition.displayName(), definition, record.ownerName(), record.date())));
        if (!signedCustomName) {
            pdc.remove(renameValueKey);
            pdc.remove(renameSignatureKey);
        }
        meta.lore(expectedLore(definition, record.ownerName(), record.date()));
        pdc.set(itemIdKey, PersistentDataType.STRING, definition.id());
        pdc.set(issueIdKey, PersistentDataType.STRING, issueId.toString());
        pdc.set(ownerUuidKey, PersistentDataType.STRING, record.ownerId().toString());
        pdc.set(ownerNameKey, PersistentDataType.STRING, record.ownerName());
        pdc.set(issuedDateKey, PersistentDataType.STRING, record.date());
        pdc.set(signatureKey, PersistentDataType.STRING,
                signature(definition.id(), issueId, record.ownerId(), record.ownerName(), record.date()));
        item.setItemMeta(meta);
        return identify(item, false).isPresent();
    }

    /**
     * Changes only the display name. Issued MiraItems receive a second signed rename overlay so
     * their original issuance identity remains verifiable; unrelated item metadata/PDC is untouched.
     */
    public boolean renamePreservingIdentity(ItemStack item, String legacyName) {
        if (item == null || item.getType().isAir() || legacyName == null || legacyName.isBlank()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        if (claimed(item)) {
            MiraItemDefinition definition = identify(item, false).orElse(null);
            UUID issueId = issueIdNonMutating(item).orElse(null);
            if (definition == null || issueId == null) return false;
            ItemStateStore.IssuedRecord record = state.record(definition.id(), issueId).orElse(null);
            if (record == null) return false;

            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(renameValueKey, PersistentDataType.STRING, legacyName);
            pdc.set(renameSignatureKey, PersistentDataType.STRING,
                    renameSignature(definition.id(), issueId, record.ownerId(), legacyName));
            meta.displayName(Text.component(legacyName));
            item.setItemMeta(meta);
            return identify(item, false).isPresent();
        }

        meta.displayName(Text.component(legacyName));
        item.setItemMeta(meta);
        return true;
    }

    public int sanitizeInventory(Player player) {
        int invalidated = 0;
        for (ItemStack item : player.getInventory().getContents()) if (claimed(item) && identify(item).isEmpty()) invalidated++;
        return invalidated;
    }

    public void stripBacking(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.remove(itemIdKey); pdc.remove(issueIdKey); pdc.remove(ownerUuidKey); pdc.remove(ownerNameKey); pdc.remove(issuedDateKey); pdc.remove(signatureKey);
        pdc.remove(renameValueKey); pdc.remove(renameSignatureKey);
        item.setItemMeta(meta);
    }

    private List<Component> expectedLore(MiraItemDefinition definition, String ownerName, String date) {
        List<Component> lore = new ArrayList<>();
        for (String line : definition.lorePrefix()) lore.add(Text.component(resolve(line, definition, ownerName, date)));
        lore.add(Text.component("&8Owner: &6" + ownerName));
        lore.add(Text.component("&8Date: &6" + date + "."));
        registry.expiresAt(definition.id()).ifPresent(expiry -> lore.add(Text.component("&8Event Ends: &6" + expiry)));
        return List.copyOf(lore);
    }

    private String resolve(String text, MiraItemDefinition definition, String ownerName, String date) {
        String expiry = registry.expiresAt(definition.id()).map(Object::toString).orElse("Never");
        return (text == null ? "" : text)
                .replace("%owner%", ownerName)
                .replace("%player%", ownerName)
                .replace("%date%", date)
                .replace("%item_id%", definition.id())
                .replace("%event_expires%", expiry);
    }

    private String ensureSecret() {
        String configured = plugin.getConfig().getString("security.secret", "").trim();
        if (!configured.isEmpty()) return configured;
        String generated = UUID.randomUUID() + "-" + UUID.randomUUID();
        plugin.getConfig().set("security.secret", generated); plugin.saveConfig(); return generated;
    }

    public record Inspection(boolean claimed, boolean valid, boolean backed, boolean activeDefinition,
                             String itemId, UUID issueId, String ownerName, String ownerUuid,
                             String issuedDate, String abilityId, java.time.Instant eventExpiry) {
        public static final Inspection EMPTY = new Inspection(false, false, false, false, "", null, "", "", "", "NONE", null);
    }

    private String renameSignature(String itemId, UUID issueId, UUID ownerId, String customName) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String payload = "rename|" + itemId + "|" + issueId + "|" + ownerId + "|" + customName + "|" + secret;
            return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException("Unable to sign MiraItem rename", error);
        }
    }

    private String signature(String itemId, UUID issueId, UUID ownerId, String ownerName, String date) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String payload = itemId + "|" + issueId + "|" + ownerId + "|" + ownerName + "|" + date + "|" + secret;
            return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) { throw new IllegalStateException("Unable to sign MiraItem", error); }
    }
}
