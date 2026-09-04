package com.mira.items.service;

import com.mira.items.api.MiraItemAbilityHandler;
import com.mira.items.model.MiraAbility;
import com.mira.items.model.MiraItemDefinition;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class AbilityRegistryService {
    private static final Set<String> RESERVED = java.util.Arrays.stream(MiraAbility.values())
            .map(Enum::name).collect(java.util.stream.Collectors.toUnmodifiableSet());

    private final Map<String, MiraItemAbilityHandler> handlers = new LinkedHashMap<>();

    public synchronized boolean register(MiraItemAbilityHandler handler) {
        if (handler == null || handler.id() == null) return false;
        String id = normalize(handler.id());
        if (id.isBlank() || RESERVED.contains(id) || handlers.containsKey(id)) return false;
        handlers.put(id, handler);
        return true;
    }

    public synchronized boolean unregister(String id) {
        return handlers.remove(normalize(id)) != null;
    }

    public synchronized boolean registered(String id) {
        return handlers.containsKey(normalize(id));
    }

    public synchronized Collection<String> ids() {
        return java.util.List.copyOf(handlers.keySet());
    }

    public boolean dispatchDamage(Player attacker, LivingEntity target, ItemStack item,
                                  MiraItemDefinition definition, EntityDamageByEntityEvent event) {
        MiraItemAbilityHandler handler;
        synchronized (this) { handler = handlers.get(normalize(definition.abilityId())); }
        return handler != null && handler.onDamage(attacker, target, item, definition, event);
    }

    public boolean dispatchInteract(Player player, ItemStack item,
                                    MiraItemDefinition definition, PlayerInteractEvent event) {
        MiraItemAbilityHandler handler;
        synchronized (this) { handler = handlers.get(normalize(definition.abilityId())); }
        return handler != null && handler.onInteract(player, item, definition, event);
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]+", "_");
    }
}
