package com.mira.items.service;

import com.mira.core.api.MiraCore;
import com.mira.items.MiraItemsPlugin;
import com.mira.items.model.MiraAbility;
import com.mira.items.model.MiraItemDefinition;
import com.mira.items.store.ItemStateStore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.time.Duration;
import java.util.UUID;

public final class DarkRiderSetService {
    private static final String HELMET_ID = "dark_rider_helmet";
    private static final String CHESTPLATE_ID = "dark_rider_chestplate";
    private static final String LEGGINGS_ID = "dark_rider_leggings";
    private static final String BOOTS_ID = "dark_rider_boots";
    private static final String REPAIR_COOLDOWN = "miraitems:dark_rider_repair";

    private final MiraItemsPlugin plugin;
    private final MiraCore core;
    private final MiraItemService items;
    private final ItemStateStore state;

    public DarkRiderSetService(MiraItemsPlugin plugin, MiraCore core, MiraItemService items, ItemStateStore state) {
        this.plugin = plugin;
        this.core = core;
        this.items = items;
        this.state = state;
    }

    public void maintenance() {
        int luckLevel = Math.max(1, plugin.getConfig().getInt("mechanics.dark-rider-luck-level", 10));
        int luckDuration = Math.max(40, plugin.getConfig().getInt("mechanics.dark-rider-luck-refresh-ticks", 60));
        long repairCooldownSeconds = Math.max(1L,
                plugin.getConfig().getLong("mechanics.dark-rider-repair-cooldown-seconds", 300L));

        for (Player player : Bukkit.getOnlinePlayers()) {
            ItemStack helmet = player.getInventory().getHelmet();
            ItemStack chestplate = player.getInventory().getChestplate();
            ItemStack leggings = player.getInventory().getLeggings();
            ItemStack boots = player.getInventory().getBoots();

            if (!isPiece(helmet, HELMET_ID)
                    || !isPiece(chestplate, CHESTPLATE_ID)
                    || !isPiece(leggings, LEGGINGS_ID)
                    || !isPiece(boots, BOOTS_ID)) {
                continue;
            }

            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.LUCK,
                    luckDuration,
                    luckLevel - 1,
                    false,
                    false,
                    true
            ));

            UUID playerId = player.getUniqueId();
            if (core.cooldowns().active(playerId, REPAIR_COOLDOWN)) continue;
            if (!needsRepair(helmet) && !needsRepair(chestplate) && !needsRepair(leggings) && !needsRepair(boots)) continue;

            repair(helmet);
            repair(chestplate);
            repair(leggings);
            repair(boots);
            core.cooldowns().start(playerId, REPAIR_COOLDOWN, Duration.ofSeconds(repairCooldownSeconds));
        }
    }

    private boolean isPiece(ItemStack item, String expectedId) {
        MiraItemDefinition definition = items.identify(item).orElse(null);
        return definition != null
                && definition.id().equals(expectedId)
                && definition.ability(MiraAbility.DARK_RIDER)
                && state.enabled(definition.id());
    }

    private static boolean needsRepair(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta instanceof Damageable damageable && damageable.getDamage() > 0;
    }

    private static void repair(ItemStack item) {
        if (item == null || item.getType().isAir()) return;
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable) || damageable.getDamage() <= 0) return;
        damageable.setDamage(0);
        item.setItemMeta(meta);
    }
}
