package com.mira.items.api;

import com.mira.items.model.MiraItemDefinition;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Optional runtime ability hook for custom MiraItem ability IDs.
 * Return true when the handler consumed/handled the event for this item.
 */
public interface MiraItemAbilityHandler {
    String id();

    default boolean onDamage(Player attacker, LivingEntity target, ItemStack item,
                             MiraItemDefinition definition, EntityDamageByEntityEvent event) {
        return false;
    }

    default boolean onInteract(Player player, ItemStack item,
                               MiraItemDefinition definition, PlayerInteractEvent event) {
        return false;
    }
}
