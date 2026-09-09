package com.mira.items.service;

import com.mira.items.MiraItemsPlugin;
import com.mira.items.model.MiraItemDefinition;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class DarkRiderVisualService {
    private static final String VISUAL_TAG = "mira_dark_rider_visual";

    private final MiraItemsPlugin plugin;
    private final MiraItemService items;
    private final Map<UUID, EnumMap<Attachment, ItemDisplay>> active = new java.util.HashMap<>();

    public DarkRiderVisualService(MiraItemsPlugin plugin, MiraItemService items) {
        this.plugin = plugin;
        this.items = items;
        cleanupStale();
    }

    public void tick() {
        Set<UUID> online = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            online.add(player.getUniqueId());
            sync(player);
        }

        Iterator<Map.Entry<UUID, EnumMap<Attachment, ItemDisplay>>> iterator = active.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, EnumMap<Attachment, ItemDisplay>> entry = iterator.next();
            if (online.contains(entry.getKey())) continue;
            entry.getValue().values().forEach(this::remove);
            iterator.remove();
        }
    }

    public void shutdown() {
        active.values().forEach(map -> map.values().forEach(this::remove));
        active.clear();
        cleanupStale();
    }

    private void sync(Player player) {
        EnumMap<Attachment, ItemDisplay> visuals =
                active.computeIfAbsent(player.getUniqueId(), ignored -> new EnumMap<>(Attachment.class));

        for (Attachment attachment : Attachment.values()) {
            boolean shouldShow = wearing(player, attachment.itemId);
            ItemDisplay display = visuals.get(attachment);

            if (!shouldShow) {
                if (display != null) remove(display);
                visuals.remove(attachment);
                continue;
            }

            Location anchor = anchor(player, attachment);
            if (display == null || !display.isValid() || display.getWorld() != anchor.getWorld()) {
                if (display != null) remove(display);
                display = spawn(anchor, attachment);
                visuals.put(attachment, display);
            } else {
                display.teleport(anchor);
            }
        }

        if (visuals.isEmpty()) active.remove(player.getUniqueId());
    }

    private boolean wearing(Player player, String expectedId) {
        ItemStack stack = switch (expectedId) {
            case "dark_rider_helmet" -> player.getInventory().getHelmet();
            case "dark_rider_chestplate" -> player.getInventory().getChestplate();
            case "dark_rider_boots" -> player.getInventory().getBoots();
            default -> null;
        };
        MiraItemDefinition definition = items.identify(stack, false).orElse(null);
        return definition != null && definition.id().equals(expectedId);
    }

    private ItemDisplay spawn(Location location, Attachment attachment) {
        World world = location.getWorld();
        if (world == null) throw new IllegalStateException("Cannot spawn Dark Rider visual without a world");

        ItemStack visualItem = new ItemStack(Material.PAPER);
        ItemMeta meta = visualItem.getItemMeta();
        meta.setItemModel(attachment.modelKey);
        visualItem.setItemMeta(meta);

        return world.spawn(location, ItemDisplay.class, display -> {
            display.addScoreboardTag(VISUAL_TAG);
            display.setPersistent(false);
            display.setInvulnerable(true);
            display.setGravity(false);
            display.setNoPhysics(true);
            display.setSilent(true);
            display.setViewRange(1.5f);
            display.setShadowRadius(0.0f);
            display.setShadowStrength(0.0f);
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(2);
            display.setTeleportDuration(2);
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            display.setItemStack(visualItem);
        });
    }

    private Location anchor(Player player, Attachment attachment) {
        Location body = player.getLocation();
        float yaw = attachment == Attachment.HELMET ? body.getYaw() : player.getBodyYaw();
        float pitch = attachment == Attachment.HELMET ? body.getPitch() : 0.0f;

        double radians = Math.toRadians(yaw);
        Vector forward = new Vector(-Math.sin(radians), 0.0, Math.cos(radians));
        Vector right = new Vector(Math.cos(radians), 0.0, Math.sin(radians));

        Location anchor = body.clone();
        switch (attachment) {
            case HELMET -> {
                anchor.add(0.0, player.getEyeHeight() - 0.08, 0.0);
            }
            case CHESTPLATE -> {
                anchor.add(0.0, player.isSneaking() ? 1.12 : 1.25, 0.0);
                anchor.add(forward.multiply(-0.20));
            }
            case BOOTS -> {
                anchor.add(0.0, 0.20, 0.0);
                anchor.add(forward.multiply(-0.10));
            }
        }

        // Small backward offset keeps attachments outside the vanilla armor surface.
        if (attachment == Attachment.HELMET) anchor.add(forward.multiply(-0.02));
        anchor.add(right.multiply(0.0));
        anchor.setYaw(yaw);
        anchor.setPitch(pitch);
        return anchor;
    }

    private void cleanupStale() {
        for (World world : Bukkit.getWorlds()) {
            for (ItemDisplay display : world.getEntitiesByClass(ItemDisplay.class)) {
                if (display.getScoreboardTags().contains(VISUAL_TAG)) display.remove();
            }
        }
    }

    private void remove(ItemDisplay display) {
        if (display != null && display.isValid()) display.remove();
    }

    private enum Attachment {
        HELMET("dark_rider_helmet", new NamespacedKey("mira", "dark_rider_helmet_attachment")),
        CHESTPLATE("dark_rider_chestplate", new NamespacedKey("mira", "dark_rider_wings_attachment")),
        BOOTS("dark_rider_boots", new NamespacedKey("mira", "dark_rider_boots_attachment"));

        private final String itemId;
        private final NamespacedKey modelKey;

        Attachment(String itemId, NamespacedKey modelKey) {
            this.itemId = itemId;
            this.modelKey = modelKey;
        }
    }
}
