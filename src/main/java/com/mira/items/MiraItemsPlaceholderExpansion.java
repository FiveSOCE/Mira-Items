package com.mira.items;

import com.mira.items.listener.SpecialItemListener;
import com.mira.items.service.CustomItemRegistryService;
import com.mira.items.service.MiraItemService;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;

public final class MiraItemsPlaceholderExpansion extends PlaceholderExpansion {
    private final MiraItemService items;
    private final CustomItemRegistryService registry;
    private final SpecialItemListener abilities;

    public MiraItemsPlaceholderExpansion(MiraItemService items, CustomItemRegistryService registry,
                                         SpecialItemListener abilities) {
        this.items = items;
        this.registry = registry;
        this.abilities = abilities;
    }

    @Override public @NotNull String getIdentifier() { return "miraitems"; }
    @Override public @NotNull String getAuthor() { return "FiveS"; }
    @Override public @NotNull String getVersion() { return "0.1.5"; }
    @Override public boolean persist() { return true; }

    @Override
    public @Nullable String onRequest(OfflinePlayer offline, @NotNull String params) {
        if (!(offline instanceof Player player)) return null;
        ItemStack held = player.getInventory().getItemInMainHand();
        var definition = items.identify(held, false).orElse(null);

        return switch (params.toLowerCase(java.util.Locale.ROOT)) {
            case "held_id" -> definition == null ? "" : definition.id();
            case "held_ability" -> definition == null ? "NONE" : definition.abilityId();
            case "held_cooldown" -> Long.toString(abilities.heldCooldownSeconds(held));
            case "held_event" -> definition == null ? "" : registry.eventId(definition.id()).orElse("");
            case "held_event_remaining" -> definition == null ? "0" : Long.toString(remainingSeconds(registry.expiresAt(definition.id()).orElse(null)));
            default -> null;
        };
    }

    private static long remainingSeconds(Instant end) {
        if (end == null) return 0L;
        long seconds = Duration.between(Instant.now(), end).toSeconds();
        return Math.max(0L, seconds);
    }
}
