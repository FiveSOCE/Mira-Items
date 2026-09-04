package com.mira.items.api;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

public interface MiraItemsApi {
    Collection<MiraItemInfo> items();
    Optional<MiraItemInfo> item(String idOrAlias);
    Optional<String> identify(ItemStack item);
    boolean isSpecial(ItemStack item);
    boolean give(Player player, String idOrAlias);
    boolean register(MiraItemRegistration registration);
    boolean registerEvent(MiraItemRegistration registration, Instant expiresAt);
    boolean registerEvent(MiraItemRegistration registration, String eventId, Instant startsAt, Instant expiresAt);
    Optional<String> eventId(String id);
    Optional<Instant> eventStart(String id);
    boolean unregister(String id);
    Optional<Instant> eventExpiry(String id);
    boolean registerAbilityHandler(MiraItemAbilityHandler handler);
    boolean unregisterAbilityHandler(String abilityId);
    Collection<String> registeredAbilityIds();
}
