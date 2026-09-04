package com.mira.items;

import com.mira.core.api.MiraCore;
import com.mira.core.api.MiraCoreProvider;
import com.mira.core.api.ModuleHealth;
import com.mira.items.api.MiraItemsApi;
import com.mira.items.command.MiraItemCommand;
import com.mira.items.listener.SpecialItemListener;
import com.mira.items.service.AbilityRegistryService;
import com.mira.items.service.CustomItemRegistryService;
import com.mira.items.service.MiraItemService;
import com.mira.items.store.ItemStateStore;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class MiraItemsPlugin extends JavaPlugin {
    private MiraCore core;
    private ItemStateStore state;
    private CustomItemRegistryService registry;
    private AbilityRegistryService abilities;
    private MiraItemService items;
    private MiraItemsApi api;
    private BukkitTask maintenanceTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        core = MiraCoreProvider.require();
        state = new ItemStateStore(this);
        registry = new CustomItemRegistryService(this);
        abilities = new AbilityRegistryService();
        items = new MiraItemService(this, state, registry);
        api = new MiraItemsApiImpl(items, state, registry, abilities);

        core.modules().register(this, "MiraItems");
        core.services().register(MiraItemsApi.class, api);

        SpecialItemListener listener = new SpecialItemListener(this, core, items, state, abilities);
        getServer().getPluginManager().registerEvents(listener, this);

        PluginCommand command = getCommand("mitem");
        if (command == null) {
            core.modules().setHealth(this, ModuleHealth.UNHEALTHY, "MiraItems command missing from plugin.yml");
            throw new IllegalStateException("MiraItems command missing from plugin.yml");
        }
        MiraItemCommand admin = new MiraItemCommand(this, core, items, state, registry, abilities);
        command.setExecutor(admin);
        command.setTabCompleter(admin);

        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new MiraItemsPlaceholderExpansion(items, registry, listener).register();
        }

        maintenanceTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            registry.cleanupExpired();
            listener.maintenance();
        }, 20L, 20L);

        core.modules().setHealth(this, ModuleHealth.HEALTHY,
                "Custom item/ability registries, event windows, Core cooldowns, verification tools and PAPI displays ready");
        getLogger().info("MiraItems v" + getPluginMeta().getVersion() + " enabled.");
    }

    public MiraCore core() { return core; }
    public MiraItemService items() { return items; }
    public CustomItemRegistryService registry() { return registry; }
    public AbilityRegistryService abilities() { return abilities; }

    @Override
    public void onDisable() {
        if (maintenanceTask != null) maintenanceTask.cancel();
        if (core != null) {
            if (api != null) core.services().unregister(MiraItemsApi.class, api);
            core.modules().unregister(this);
        }
    }
}
