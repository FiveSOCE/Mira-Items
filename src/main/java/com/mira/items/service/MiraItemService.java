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
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MusicInstrumentMeta;
import org.bukkit.inventory.meta.components.EquippableComponent;
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
    private static final NamespacedKey PYRO_AXE_MODEL = new NamespacedKey("mira", "pyro_axe");
    private static final NamespacedKey EXCALIBUR_MODEL = new NamespacedKey("mira", "excalibur");
    private static final NamespacedKey LOCHABER_AXE_MODEL = new NamespacedKey("mira", "lochaber_axe");
    private static final NamespacedKey EMPOWER_MODEL = new NamespacedKey("mira", "empower");
    private static final NamespacedKey DARK_RIDER_HELMET_MODEL = new NamespacedKey("mira", "dark_rider_helmet");
    private static final NamespacedKey DARK_RIDER_CHESTPLATE_MODEL = new NamespacedKey("mira", "dark_rider_chestplate");
    private static final NamespacedKey DARK_RIDER_LEGGINGS_MODEL = new NamespacedKey("mira", "dark_rider_leggings");
    private static final NamespacedKey DARK_RIDER_BOOTS_MODEL = new NamespacedKey("mira", "dark_rider_boots");
    private static final NamespacedKey VANILLA_NETHERITE_EQUIPMENT_MODEL = new NamespacedKey("minecraft", "netherite");
    private static final NamespacedKey VOUCHER_RANK_MODEL = new NamespacedKey("mira", "voucher_rank");
    private static final NamespacedKey VOUCHER_PINATA_MODEL = new NamespacedKey("mira", "voucher_pinata");
    private static final NamespacedKey VOUCHER_AIRDROP_MODEL = new NamespacedKey("mira", "voucher_airdrop");
    private static final NamespacedKey VOUCHER_HOME_MODEL = new NamespacedKey("mira", "voucher_home_upgrade");
    private static final NamespacedKey VOUCHER_JELLYLEGS_MODEL = new NamespacedKey("mira", "voucher_jellylegs");
    private static final NamespacedKey VOUCHER_FLY_MODEL = new NamespacedKey("mira", "voucher_fly");
    private static final NamespacedKey VOUCHER_TEMP_KIT_MODEL = new NamespacedKey("mira", "voucher_temp_kit");
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
            applyCanonicalVisuals(meta, definition);
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

        // Item models are derived from the authenticated MiraItem identity, not trusted as identity themselves.
        // This also transparently migrates legitimate pre-resource-pack Pyro Axes when they are first seen.
        boolean visualChanged = false;
        NamespacedKey expectedModel = modelKey(definition);
        if (expectedModel != null && !expectedModel.equals(meta.getItemModel())) {
            meta.setItemModel(expectedModel);
            visualChanged = true;
        }
        NamespacedKey expectedEquipment = equipmentModelKey(definition);
        EquipmentSlot expectedSlot = armorSlot(definition.id());
        if (expectedEquipment != null && expectedSlot != null) {
            EquippableComponent equippable = meta.getEquippable();
            if (!expectedEquipment.equals(equippable.getModel())
                    || expectedSlot != equippable.getSlot()
                    || !equippable.isSwappable()
                    || !equippable.isDispensable()
                    || !equippable.isDamageOnHurt()) {
                configureDarkRiderEquippable(equippable, expectedEquipment, expectedSlot);
                meta.setEquippable(equippable);
                visualChanged = true;
            }
        }
        if (visualChanged) item.setItemMeta(meta);
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
        applyCanonicalVisuals(meta, definition);
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
        for (ItemStack item : player.getInventory().getContents()) {
            if (!claimed(item)) continue;
            Optional<MiraItemDefinition> definition = identify(item);
            if (definition.isEmpty()) {
                invalidated++;
                continue;
            }
            // Always enforce the canonical resource-pack model for every valid claimed item.
            // This keeps legacy/existing vouchers and weapons visually migrated even when
            // their normal gameplay path has not touched them yet.
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                applyCanonicalVisuals(meta, definition.get());
                item.setItemMeta(meta);
            }
        }
        return invalidated;
    }

    public void stripBacking(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String backedItemId = pdc.get(itemIdKey, PersistentDataType.STRING);
        pdc.remove(itemIdKey); pdc.remove(issueIdKey); pdc.remove(ownerUuidKey); pdc.remove(ownerNameKey); pdc.remove(issuedDateKey); pdc.remove(signatureKey);
        pdc.remove(renameValueKey); pdc.remove(renameSignatureKey);
        NamespacedKey itemModel = meta.getItemModel();
        if (itemModel != null && (itemModel.getNamespace().equals("mira") || itemModel.getNamespace().equals("mythicarmor"))) {
            meta.setItemModel(null);
        }
        EquipmentSlot vanillaSlot = armorSlot(backedItemId);
        if (vanillaSlot != null) {
            EquippableComponent equippable = meta.getEquippable();
            configureDarkRiderEquippable(equippable, VANILLA_NETHERITE_EQUIPMENT_MODEL, vanillaSlot);
            meta.setEquippable(equippable);
        }
        item.setItemMeta(meta);
    }

    private NamespacedKey modelKey(MiraItemDefinition definition) {
        String id = definition.id().toLowerCase(java.util.Locale.ROOT);
        if (id.equals("pyro_axe")) return PYRO_AXE_MODEL;
        if (id.equals("excalibur")) return EXCALIBUR_MODEL;
        if (id.equals("lochaber_axe")) return LOCHABER_AXE_MODEL;
        if (id.equals("empower")) return EMPOWER_MODEL;
        if (id.equals("dark_rider_helmet")) return darkRiderHelmetItemModel();
        if (id.equals("dark_rider_chestplate")) return DARK_RIDER_CHESTPLATE_MODEL;
        if (id.equals("dark_rider_leggings")) return DARK_RIDER_LEGGINGS_MODEL;
        if (id.equals("dark_rider_boots")) return DARK_RIDER_BOOTS_MODEL;
        if (id.startsWith("voucher_rank_")) return VOUCHER_RANK_MODEL;
        if (id.startsWith("voucher_kit_")) return VOUCHER_TEMP_KIT_MODEL;
        if (id.startsWith("voucher_home_")) return VOUCHER_HOME_MODEL;
        if (id.equals("voucher_jellylegs")) return VOUCHER_JELLYLEGS_MODEL;
        if (id.equals("voucher_fly")) return VOUCHER_FLY_MODEL;
        if (id.equals("voucher_airdrop")) return VOUCHER_AIRDROP_MODEL;
        if (id.equals("voucher_pinata")) return VOUCHER_PINATA_MODEL;
        return null;
    }

    private NamespacedKey equipmentModelKey(MiraItemDefinition definition) {
        if (!definition.ability(MiraAbility.DARK_RIDER)) return null;
        return switch (definition.id().toLowerCase(java.util.Locale.ROOT)) {
            case "dark_rider_chestplate" -> configuredMythicArmorKey("chestplate-equipment-model");
            case "dark_rider_leggings" -> configuredMythicArmorKey("leggings-equipment-model");
            case "dark_rider_boots" -> configuredMythicArmorKey("boots-equipment-model");
            default -> VANILLA_NETHERITE_EQUIPMENT_MODEL;
        };
    }

    private NamespacedKey darkRiderHelmetItemModel() {
        NamespacedKey configured = configuredMythicArmorKey("helmet-item-model");
        return configured.equals(VANILLA_NETHERITE_EQUIPMENT_MODEL) ? DARK_RIDER_HELMET_MODEL : configured;
    }

    private NamespacedKey configuredMythicArmorKey(String key) {
        boolean enabled = plugin.getConfig().getBoolean("mythic-armors.dark-rider.enabled", false)
                && plugin.getServer().getPluginManager().isPluginEnabled("MythicArmors");
        if (!enabled) return VANILLA_NETHERITE_EQUIPMENT_MODEL;

        String raw = plugin.getConfig().getString("mythic-armors.dark-rider." + key, "").trim().toLowerCase(java.util.Locale.ROOT);
        if (raw.isEmpty()) return VANILLA_NETHERITE_EQUIPMENT_MODEL;
        int separator = raw.indexOf(':');
        if (separator <= 0 || separator == raw.length() - 1) {
            plugin.getLogger().warning("Invalid MythicArmors key for " + key + ": " + raw + "; using vanilla Netherite.");
            return VANILLA_NETHERITE_EQUIPMENT_MODEL;
        }
        return new NamespacedKey(raw.substring(0, separator), raw.substring(separator + 1));
    }

    private void applyCanonicalVisuals(ItemMeta meta, MiraItemDefinition definition) {
        NamespacedKey model = modelKey(definition);
        if (model != null) meta.setItemModel(model);

        NamespacedKey equipment = equipmentModelKey(definition);
        EquipmentSlot slot = armorSlot(definition.id());
        if (equipment != null && slot != null) {
            EquippableComponent equippable = meta.getEquippable();
            configureDarkRiderEquippable(equippable, equipment, slot);
            meta.setEquippable(equippable);
        }
    }

    private static void configureDarkRiderEquippable(EquippableComponent equippable, NamespacedKey model, EquipmentSlot slot) {
        equippable.setModel(model);
        equippable.setSlot(slot);
        equippable.setSwappable(true);
        equippable.setDispensable(true);
        equippable.setDamageOnHurt(true);
    }

    private static EquipmentSlot armorSlot(String itemId) {
        if (itemId == null) return null;
        return switch (itemId.toLowerCase(java.util.Locale.ROOT)) {
            case "dark_rider_helmet" -> EquipmentSlot.HEAD;
            case "dark_rider_chestplate" -> EquipmentSlot.CHEST;
            case "dark_rider_leggings" -> EquipmentSlot.LEGS;
            case "dark_rider_boots" -> EquipmentSlot.FEET;
            default -> null;
        };
    }

    private List<Component> expectedLore(MiraItemDefinition definition, String ownerName, String date) {
        List<Component> lore = new ArrayList<>();
        for (String line : definition.lorePrefix()) lore.add(Text.component(resolve(line, definition, ownerName, date)));

        // Vouchers intentionally stay clean: their definition owns the complete lore.
        if (definition.abilityId().equalsIgnoreCase("VOUCHER")) return List.copyOf(lore);

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
