package com.mira.items;

import com.mira.items.api.MiraItemInfo;
import com.mira.items.api.MiraItemRegistration;
import com.mira.items.api.MiraItemsApi;
import com.mira.items.model.MiraItemDefinition;
import com.mira.items.model.MiraItemDefinitions;
import com.mira.items.service.CustomItemRegistryService;
import com.mira.items.service.MiraItemService;
import com.mira.items.store.ItemStateStore;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

public final class MiraItemsApiImpl implements MiraItemsApi {
    private final MiraItemService items;
    private final ItemStateStore state;
    private final CustomItemRegistryService registry;

    public MiraItemsApiImpl(MiraItemService items, ItemStateStore state, CustomItemRegistryService registry) {
        this.items = items;
        this.state = state;
        this.registry = registry;
    }

    @Override public Collection<MiraItemInfo> items() { return MiraItemDefinitions.all().stream().map(this::info).toList(); }
    @Override public Optional<MiraItemInfo> item(String idOrAlias) { return MiraItemDefinitions.find(idOrAlias).map(this::info); }
    @Override public Optional<String> identify(ItemStack item) { return items.identify(item).map(MiraItemDefinition::id); }
    @Override public boolean isSpecial(ItemStack item) { return identify(item).isPresent(); }

    @Override
    public boolean give(Player player, String idOrAlias) {
        Optional<MiraItemDefinition> definition = MiraItemDefinitions.find(idOrAlias);
        return definition.isPresent() && registry.active(definition.get().id()) && state.enabled(definition.get().id())
                && state.canIssue(definition.get().id()) && items.give(player, definition.get());
    }

    @Override public boolean register(MiraItemRegistration registration) { return registry.register(registration); }
    @Override public boolean registerEvent(MiraItemRegistration registration, Instant expiresAt) { return registry.register(registration, expiresAt); }
    @Override public boolean unregister(String id) { return registry.unregister(id); }
    @Override public Optional<Instant> eventExpiry(String id) { return registry.expiresAt(id); }

    private MiraItemInfo info(MiraItemDefinition definition) {
        return new MiraItemInfo(definition.id(), definition.displayName(), definition.material().name(),
                state.enabled(definition.id()) && registry.active(definition.id()), state.issuedCount(definition.id()), state.limit(definition.id()));
    }
}
