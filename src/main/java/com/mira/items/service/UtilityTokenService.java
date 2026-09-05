package com.mira.items.service;

import com.mira.core.api.MiraCore;
import com.mira.items.MiraItemsPlugin;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public final class UtilityTokenService implements Listener {
    public enum TokenType { REPAIR, RENAME }

    private final MiraItemsPlugin plugin;
    private final MiraCore core;
    private final MiraItemService items;
    private final NamespacedKey tokenKey;
    private final NamespacedKey tokenTypeKey;
    private final Map<UUID, PendingRename> pendingRenames = new HashMap<>();

    public UtilityTokenService(MiraItemsPlugin plugin, MiraCore core, MiraItemService items) {
        this.plugin = plugin;
        this.core = core;
        this.items = items;
        this.tokenKey = new NamespacedKey(plugin, "utility_token");
        this.tokenTypeKey = new NamespacedKey(plugin, "utility_token_type");
    }

    public ItemStack create(TokenType type, int amount) {
        Objects.requireNonNull(type, "type");
        ItemStack item = new ItemStack(Material.PAPER, Math.max(1, Math.min(64, amount)));
        ItemMeta meta = item.getItemMeta();
        meta.customName(Component.text(type == TokenType.REPAIR ? "Mira Repair Token" : "Mira Rename Token"));
        meta.lore(type == TokenType.REPAIR
                ? List.of(Component.text("Hold the item to repair in your offhand."),
                          Component.text("Right-click this token to fully repair it."))
                : List.of(Component.text("Hold the item to rename in your offhand."),
                          Component.text("Right-click this token, then type the new name in chat.")));
        meta.getPersistentDataContainer().set(tokenKey, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(tokenTypeKey, PersistentDataType.STRING, type.name());
        item.setItemMeta(meta);
        return item;
    }

    public boolean give(Player target, TokenType type, int amount) {
        if (target == null || amount <= 0) return false;
        int remaining = amount;
        while (remaining > 0) {
            int stack = Math.min(64, remaining);
            Map<Integer, ItemStack> leftovers = target.getInventory().addItem(create(type, stack));
            leftovers.values().forEach(leftover -> target.getWorld().dropItemNaturally(target.getLocation(), leftover));
            remaining -= stack;
        }
        return true;
    }

    public boolean isToken(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        Byte marker = item.getItemMeta().getPersistentDataContainer().get(tokenKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    public Optional<TokenType> type(ItemStack item) {
        if (!isToken(item)) return Optional.empty();
        String raw = item.getItemMeta().getPersistentDataContainer().get(tokenTypeKey, PersistentDataType.STRING);
        if (raw == null) return Optional.empty();
        try { return Optional.of(TokenType.valueOf(raw)); }
        catch (IllegalArgumentException ignored) { return Optional.empty(); }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack token = player.getInventory().getItemInMainHand();
        TokenType type = type(token).orElse(null);
        if (type == null) return;
        event.setCancelled(true);

        ItemStack target = player.getInventory().getItemInOffHand();
        if (target == null || target.getType().isAir() || isToken(target)) {
            core.messages().send(player, "&cHold the item you want to use this token on in your offhand.");
            return;
        }

        if (type == TokenType.REPAIR) {
            repair(player, target);
            return;
        }

        long timeout = Math.max(15L, plugin.getConfig().getLong("utility-tokens.rename.prompt-seconds", 60L));
        pendingRenames.put(player.getUniqueId(),
                new PendingRename(target.clone(), System.currentTimeMillis() + timeout * 1000L));
        core.messages().send(player, "&dType the new item name in chat. &7Type &fcancel &7to stop. "
                + "&7The rename token is only consumed after a valid rename.");
    }

    private void repair(Player player, ItemStack target) {
        ItemMeta meta = target.getItemMeta();
        if (!(meta instanceof Damageable damageable) || target.getType().getMaxDurability() <= 0) {
            core.messages().send(player, "&cThat item cannot be repaired.");
            return;
        }
        if (damageable.getDamage() <= 0) {
            core.messages().send(player, "&eThat item is already fully repaired.");
            return;
        }

        int previousDamage = damageable.getDamage();
        double fraction = Math.max(0.01D, Math.min(1D,
                plugin.getConfig().getDouble("utility-tokens.repair.fraction", 1D)));
        int repair = Math.max(1, (int) Math.ceil(target.getType().getMaxDurability() * fraction));
        damageable.setDamage(Math.max(0, previousDamage - repair));
        target.setItemMeta((ItemMeta) damageable);
        player.getInventory().setItemInOffHand(target);
        consumeMainHandToken(player);

        core.audit().record("MiraItems", "REPAIR_TOKEN_USED", player.getUniqueId(), player.getName(),
                target.getType().name(), "Repair token used",
                Map.of("previousDamage", Integer.toString(previousDamage),
                        "newDamage", Integer.toString(damageable.getDamage())));
        core.messages().send(player, "&aYour offhand item was repaired.");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRenameChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        PendingRename pending = pendingRenames.remove(player.getUniqueId());
        if (pending == null) return;
        event.setCancelled(true);

        String raw = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        plugin.getServer().getScheduler().runTask(plugin, () -> completeRename(player, pending, raw));
    }

    private void completeRename(Player player, PendingRename pending, String raw) {
        if (!player.isOnline()) return;
        if (System.currentTimeMillis() >= pending.expiresAt()) {
            core.messages().send(player, "&cRename prompt expired. Your token was not consumed.");
            return;
        }
        if (raw.equalsIgnoreCase("cancel")) {
            core.messages().send(player, "&eRename cancelled. Your token was not consumed.");
            return;
        }
        if (type(player.getInventory().getItemInMainHand()).orElse(null) != TokenType.RENAME) {
            core.messages().send(player, "&cKeep the Rename Token in your main hand. Nothing was consumed.");
            return;
        }

        ItemStack target = player.getInventory().getItemInOffHand();
        if (target == null || target.getType().isAir() || !target.isSimilar(pending.targetSnapshot())) {
            core.messages().send(player, "&cYour offhand item changed. Rename cancelled and the token was not consumed.");
            return;
        }

        String safe = sanitizeName(player, raw);
        if (safe == null) return;

        if (!items.renamePreservingIdentity(target, safe)) {
            core.messages().send(player, "&cThat item could not be renamed safely. Your token was not consumed.");
            return;
        }

        player.getInventory().setItemInOffHand(target);
        consumeMainHandToken(player);
        core.audit().record("MiraItems", "RENAME_TOKEN_USED", player.getUniqueId(), player.getName(),
                target.getType().name(), "Rename token used",
                Map.of("name", safe));
        core.messages().send(player, "&aYour offhand item was renamed.");
    }

    private String sanitizeName(Player player, String input) {
        String safe = input == null ? "" : input.trim().replace('§', '&');
        if (!plugin.getConfig().getBoolean("utility-tokens.rename.allow-colors", true)) {
            safe = safe.replaceAll("(?i)&[0-9A-FK-OR]", "");
        } else if (!plugin.getConfig().getBoolean("utility-tokens.rename.allow-formatting", false)) {
            safe = safe.replaceAll("(?i)&[K-O]", "");
        }

        String plain = safe.replaceAll("(?i)&[0-9A-FK-OR]", "").trim();
        int max = Math.max(1, plugin.getConfig().getInt("utility-tokens.rename.max-length", 32));
        if (plain.isBlank() || plain.length() > max) {
            return failName(player, "Name must be between 1 and " + max + " visible characters.");
        }

        String lower = plain.toLowerCase(Locale.ROOT);
        for (String blocked : plugin.getConfig().getStringList("utility-tokens.rename.blocked-terms")) {
            if (blocked != null && !blocked.isBlank() && lower.contains(blocked.toLowerCase(Locale.ROOT))) {
                return failName(player, "That item name contains blocked text.");
            }
        }
        return safe;
    }

    private String failName(Player player, String message) {
        core.messages().send(player, "&c" + message + " &7Your token was not consumed.");
        return null;
    }

    private void consumeMainHandToken(Player player) {
        ItemStack token = player.getInventory().getItemInMainHand();
        if (!isToken(token)) return;
        if (token.getAmount() <= 1) player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        else token.setAmount(token.getAmount() - 1);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pendingRenames.remove(event.getPlayer().getUniqueId());
    }

    private record PendingRename(ItemStack targetSnapshot, long expiresAt) { }
}
