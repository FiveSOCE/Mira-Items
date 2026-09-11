package com.mira.items.listener;

import com.mira.items.MiraItemsPlugin;
import com.mira.items.model.MiraItemDefinition;
import com.mira.items.service.MiraItemService;
import com.mira.items.store.ItemStateStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.Locale;
import java.util.Objects;

/**
 * Handles MiraItems kit vouchers before the generic voucher listener so the kit is
 * expanded directly into the player's inventory by MiraKits instead of routing the
 * player through the normal /kit claim flow.
 */
public final class InstantKitVoucherListener implements Listener {
    private final MiraItemsPlugin plugin;
    private final MiraItemService items;
    private final ItemStateStore state;

    public InstantKitVoucherListener(MiraItemsPlugin plugin, MiraItemService items, ItemStateStore state) {
        this.plugin = plugin;
        this.items = items;
        this.state = state;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onRedeem(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() == null) return;

        ItemStack held = event.getItem();
        MiraItemDefinition definition = items.identify(held).orElse(null);
        if (definition == null || !definition.id().startsWith("voucher_kit_")) return;

        // Keep the generic voucher service as the compatibility fallback for older
        // MiraKits builds that do not expose the direct voucher delivery method.
        Plugin kitsPlugin = Bukkit.getPluginManager().getPlugin("MiraKits");
        if (kitsPlugin == null || !kitsPlugin.isEnabled()) return;
        if (!state.enabled(definition.id())) return;

        try {
            Object kits = kitsPlugin.getClass().getMethod("kits").invoke(kitsPlugin);
            String kit = resolveKit(kits, definition.id());
            if (kit == null) return;

            Object granted;
            try {
                granted = kits.getClass().getMethod("grantVoucher", Player.class, String.class)
                        .invoke(kits, event.getPlayer(), kit);
            } catch (NoSuchMethodException oldMiraKits) {
                return;
            }

            event.setCancelled(true);
            if (!(granted instanceof Boolean success) || !success) {
                event.getPlayer().sendMessage(Component.text(
                        "That kit could not be delivered. Make sure you have enough inventory space.",
                        NamedTextColor.RED));
                return;
            }

            consume(event.getPlayer(), held, event.getHand());
            event.getPlayer().sendMessage(Component.text(
                    "Redeemed one " + pretty(kit) + " kit.", NamedTextColor.GREEN));
        } catch (ReflectiveOperationException ex) {
            Throwable cause = ex instanceof InvocationTargetException invocation && invocation.getCause() != null
                    ? invocation.getCause() : ex;
            plugin.getLogger().warning("Could not instantly redeem kit voucher: " + cause.getMessage());
        }
    }

    private String resolveKit(Object kits, String voucherId) throws ReflectiveOperationException {
        Object raw = kits.getClass().getMethod("kitIds").invoke(kits);
        if (!(raw instanceof Collection<?> collection)) return null;
        for (Object value : collection) {
            String kit = Objects.toString(value, "").trim();
            if (kit.isBlank()) continue;
            if (("voucher_kit_" + idPart(kit)).equalsIgnoreCase(voucherId)) return kit;
        }
        return null;
    }

    private void consume(Player player, ItemStack held, EquipmentSlot hand) {
        ItemStack remaining = held.clone();
        int remainingAmount = held.getAmount() - 1;

        // Invalidate the exact stack referenced by PlayerInteractEvent so the generic
        // HIGHEST-priority voucher listener cannot redeem the same click a second time.
        held.setType(Material.AIR);
        held.setAmount(0);

        if (remainingAmount > 0) {
            remaining.setAmount(remainingAmount);
            if (hand == EquipmentSlot.OFF_HAND) player.getInventory().setItemInOffHand(remaining);
            else player.getInventory().setItemInMainHand(remaining);
        } else if (hand == EquipmentSlot.OFF_HAND) {
            player.getInventory().setItemInOffHand(null);
        } else {
            player.getInventory().setItemInMainHand(null);
        }
        player.updateInventory();
    }

    private String idPart(String value) {
        String out = value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        return out.isBlank() ? "unknown" : out;
    }

    private String pretty(String value) {
        String[] parts = value.replace('_', ' ').replace('-', ' ').trim().split("\\s+");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }
}
