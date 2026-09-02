package com.mira.items.listener;

import com.mira.items.MiraItemsPlugin;
import com.mira.items.model.MiraAbility;
import com.mira.items.model.MiraItemDefinition;
import com.mira.items.service.MiraItemService;
import com.mira.items.store.ItemStateStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class SpecialItemListener implements Listener {
    private static final String PYRO_CHAIN_SOUND = "entity.wither.break_block";

    private final MiraItemsPlugin plugin;
    private final MiraItemService items;
    private final ItemStateStore state;
    private final Map<UUID, PyroChain> pyroChains = new HashMap<>();
    private final Map<UUID, Long> excaliburCooldowns = new HashMap<>();
    private final Map<UUID, Integer> lochaberHits = new HashMap<>();
    private final Map<UUID, Long> empowerCooldowns = new HashMap<>();
    private int maintenanceRuns;

    public SpecialItemListener(MiraItemsPlugin plugin, MiraItemService items, ItemStateStore state) {
        this.plugin = plugin;
        this.items = items;
        this.state = state;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        Optional<MiraItemDefinition> definitionOptional = items.identify(weapon);
        if (definitionOptional.isEmpty()) return;

        MiraItemDefinition definition = definitionOptional.get();
        if (!state.enabled(definition.id())) return;

        if (definition.ability() == MiraAbility.PYRO && event.getEntity() instanceof LivingEntity target) {
            applyPyro(event, attacker, target);
            return;
        }

        if (definition.ability() == MiraAbility.EXCALIBUR && event.getEntity() instanceof LivingEntity target) {
            applyExcalibur(weapon, attacker, target);
            return;
        }

        if (definition.ability() == MiraAbility.LOCHABER && event.getEntity() instanceof Player target) {
            applyLochaber(weapon, attacker, target);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        Optional<MiraItemDefinition> definitionOptional = items.identify(item);
        if (definitionOptional.isEmpty()) return;

        MiraItemDefinition definition = definitionOptional.get();
        if (definition.ability() != MiraAbility.EMPOWER || !state.enabled(definition.id())) return;

        UUID issueId = items.issueId(item).orElse(null);
        if (issueId == null) return;

        long now = System.currentTimeMillis();
        long until = empowerCooldowns.getOrDefault(issueId, 0L);
        if (until > now) {
            event.setCancelled(true);
            player.sendActionBar(Component.text("Empower! ready in " + secondsRemaining(until, now) + "s", NamedTextColor.RED));
            return;
        }

        int duration = plugin.getConfig().getInt("mechanics.empower-duration-ticks", 600);
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, duration, 1, false, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, 1, false, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, duration, 1, false, true, true));

        long cooldownMillis = plugin.getConfig().getLong("mechanics.empower-cooldown-ticks", 6000L) * 50L;
        empowerCooldowns.put(issueId, now + cooldownMillis);
        player.sendActionBar(Component.text("Empower! activated for 30 seconds.", NamedTextColor.AQUA));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pyroChains.remove(event.getPlayer().getUniqueId());
    }

    public void maintenance() {
        maintenanceRuns++;
        int scanEvery = Math.max(1, plugin.getConfig().getInt("integrity.scan-interval-ticks", 40) / 20);

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (maintenanceRuns % scanEvery == 0) {
                int invalidated = items.sanitizeInventory(player);
                if (invalidated > 0) {
                    player.sendMessage(Component.text(
                            invalidated + " altered MiraItem" + (invalidated == 1 ? " has" : "s have")
                                    + " lost special-item backing.",
                            NamedTextColor.RED));
                }
            }

            ItemStack held = player.getInventory().getItemInMainHand();
            Optional<MiraItemDefinition> heldDefinition = items.identify(held);
            if (heldDefinition.isPresent()
                    && heldDefinition.get().ability() == MiraAbility.LOCHABER
                    && state.enabled(heldDefinition.get().id())) {
                int duration = plugin.getConfig().getInt("mechanics.lochaber-fatigue-duration-ticks", 60);
                player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, duration, 0, false, false, true));
            }
        }
    }

    private void applyPyro(EntityDamageByEntityEvent event, Player attacker, LivingEntity target) {
        long now = System.currentTimeMillis();
        long resetMillis = plugin.getConfig().getLong("mechanics.pyro-reset-seconds", 5L) * 1000L;
        PyroChain previous = pyroChains.get(attacker.getUniqueId());

        int hit = 1;
        if (previous != null
                && previous.targetId().equals(target.getUniqueId())
                && now - previous.lastHitMillis() <= resetMillis) {
            hit = previous.hitCount() + 1;
        }

        pyroChains.put(attacker.getUniqueId(), new PyroChain(target.getUniqueId(), now, hit));
        double multiplier = Math.pow(2.0D, hit - 1);
        event.setDamage(event.getDamage() * multiplier);

        if (hit >= 2) {
            attacker.playSound(attacker.getLocation(), PYRO_CHAIN_SOUND, 1.0F, 1.0F);
        }
    }

    private void applyExcalibur(ItemStack weapon, Player attacker, LivingEntity target) {
        UUID issueId = items.issueId(weapon).orElse(null);
        if (issueId == null) return;

        long now = System.currentTimeMillis();
        long until = excaliburCooldowns.getOrDefault(issueId, 0L);
        if (until > now) return;

        int duration = plugin.getConfig().getInt("mechanics.excalibur-duration-ticks", 60);
        target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, duration, 0, false, true, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, 0, false, true, true));

        long cooldownMillis = plugin.getConfig().getLong("mechanics.excalibur-cooldown-ticks", 600L) * 50L;
        excaliburCooldowns.put(issueId, now + cooldownMillis);
        attacker.sendActionBar(Component.text("Excalibur struck with royal force.", NamedTextColor.GOLD));
    }

    private void applyLochaber(ItemStack weapon, Player attacker, Player target) {
        UUID issueId = items.issueId(weapon).orElse(null);
        if (issueId == null) return;

        int hit = lochaberHits.merge(issueId, 1, Integer::sum);
        if (hit % 5 != 0) return;

        Vector pull = attacker.getLocation().toVector().subtract(target.getLocation().toVector());
        if (pull.lengthSquared() < 0.0001D) return;

        double strength = plugin.getConfig().getDouble("mechanics.lochaber-pull-strength", 1.35D);
        pull.normalize().multiply(strength);
        pull.setY(Math.max(0.25D, pull.getY() + 0.15D));
        target.setVelocity(pull);
        attacker.sendActionBar(Component.text("Lochaber hooked " + target.getName() + "!", NamedTextColor.GREEN));
    }

    private long secondsRemaining(long until, long now) {
        return Math.max(1L, (long) Math.ceil((until - now) / 1000.0D));
    }

    private record PyroChain(UUID targetId, long lastHitMillis, int hitCount) {
    }
}
