package com.mira.items.service;

import com.mira.items.MiraItemsPlugin;
import com.mira.items.model.MiraItemDefinition;
import com.mira.items.store.ItemStateStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.PermissionNode;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class FixVoucherService implements Listener {
    private final MiraItemsPlugin plugin;
    private final MiraItemService items;
    private final ItemStateStore state;
    private LuckPerms luckPerms;

    public FixVoucherService(MiraItemsPlugin plugin, MiraItemService items, ItemStateStore state) {
        this.plugin = plugin;
        this.items = items;
        this.state = state;
        if (Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            try { luckPerms = LuckPermsProvider.get(); }
            catch (IllegalStateException ignored) { }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onRedeem(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() == null) return;

        ItemStack held = event.getItem();
        MiraItemDefinition definition = items.identify(held).orElse(null);
        if (definition == null) return;

        String permission;
        String success;
        if (definition.id().equals("voucher_fix_hand")) {
            permission = plugin.getConfig().getString("vouchers.permissions.fix-hand", "miracore.fix.hand");
            success = "/fix hand unlocked permanently.";
        } else if (definition.id().equals("voucher_fix_all")) {
            permission = plugin.getConfig().getString("vouchers.permissions.fix-all", "miracore.fix.all");
            success = "/fix all unlocked permanently.";
        } else {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!state.enabled(definition.id())) {
            fail(player, "That voucher is currently disabled.");
            return;
        }

        if (permission == null || permission.isBlank()) {
            fail(player, "That voucher has no configured permission node.");
            return;
        }
        if (player.hasPermission(permission)) {
            fail(player, "You already have that permission.");
            return;
        }
        if (luckPerms == null) {
            fail(player, "LuckPerms is not available.");
            return;
        }

        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) user = luckPerms.getUserManager().loadUser(player.getUniqueId()).join();
        user.data().add(PermissionNode.builder(permission).value(true).build());
        try {
            luckPerms.getUserManager().saveUser(user).join();
        } catch (RuntimeException ex) {
            plugin.getLogger().severe("Failed to save fix voucher permission '" + permission + "' for "
                    + player.getName() + ": " + ex.getMessage());
            fail(player, "That permission could not be saved. Your voucher was not consumed.");
            return;
        }

        boolean stored = user.getNodes(NodeType.PERMISSION).stream()
                .anyMatch(node -> node.getKey().equalsIgnoreCase(permission) && node.getValue());
        if (!stored) {
            fail(player, "LuckPerms did not apply that permission. Your voucher was not consumed.");
            return;
        }

        consume(player, held, event.getHand());
        player.sendMessage(Component.text(success, NamedTextColor.GREEN));
    }

    private void consume(Player player, ItemStack held, EquipmentSlot hand) {
        if (held.getAmount() <= 1) {
            if (hand == EquipmentSlot.OFF_HAND) player.getInventory().setItemInOffHand(null);
            else player.getInventory().setItemInMainHand(null);
        } else {
            held.setAmount(held.getAmount() - 1);
            if (hand == EquipmentSlot.OFF_HAND) player.getInventory().setItemInOffHand(held);
            else player.getInventory().setItemInMainHand(held);
        }
        player.updateInventory();
    }

    private void fail(Player player, String message) {
        player.sendMessage(Component.text(message, NamedTextColor.RED));
    }
}
