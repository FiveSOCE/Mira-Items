package com.mira.items.service;

import com.mira.items.MiraItemsPlugin;
import com.mira.items.model.MiraItemDefinition;
import com.mira.items.model.MiraItemDefinitions;
import com.mira.items.store.ItemStateStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.query.QueryOptions;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.node.types.PermissionNode;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

public final class VoucherService implements Listener {
    private enum Type { RANK, JELLYLEGS, HOME, KIT, FLY, TAG, AIRDROP, PINATA }
    private record Spec(Type type, String value, int tier) {}

    private final MiraItemsPlugin plugin;
    private final MiraItemService items;
    private final ItemStateStore state;
    private final Map<String, Spec> specs = new LinkedHashMap<>();
    private final Set<String> generatedIds = new HashSet<>();
    private LuckPerms luckPerms;

    public VoucherService(MiraItemsPlugin plugin, MiraItemService items, ItemStateStore state) {
        this.plugin = plugin;
        this.items = items;
        this.state = state;
        if (Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            try { luckPerms = LuckPermsProvider.get(); }
            catch (IllegalStateException ignored) { }
        }
    }

    public void refreshDefinitions() {
        for (String id : generatedIds) MiraItemDefinitions.unregister(id);
        generatedIds.clear();
        specs.clear();

        registerStatic();
        registerRanks();
        registerKits();
        registerTags();
        ensureHomeTiers();
        plugin.getLogger().info("Prepared " + specs.size() + " MiraItems voucher definition(s).");
    }

    public Collection<String> voucherIds() {
        return List.copyOf(specs.keySet());
    }

    private void registerStatic() {
        register("voucher_jellylegs", "&a&lJelly Legs Voucher", Material.PAPER,
                voucherLore("/jellylegs"),
                new Spec(Type.JELLYLEGS, "", 0), List.of("jellylegs_voucher"));

        for (int tier = 1; tier <= 3; tier++) {
            register("voucher_home_" + tier, "&6&lHome Upgrade " + roman(tier), Material.PAPER,
                    voucherLore("Home Upgrade " + roman(tier)),
                    new Spec(Type.HOME, "mira" + tier, tier), List.of("home_voucher_" + tier));
        }

        register("voucher_fly", "&b&lPermanent Fly Voucher", Material.FEATHER,
                voucherLore("/fly"),
                new Spec(Type.FLY, "", 0), List.of("fly_voucher"));

        register("voucher_airdrop", "&d&lAirdrop Call Voucher", Material.REDSTONE_TORCH,
                voucherLore("an Airdrop"),
                new Spec(Type.AIRDROP, "", 0), List.of("airdrop_voucher"));

        register("voucher_pinata", "&6&lPinata Call Voucher", Material.SOUL_TORCH,
                voucherLore("a Pinata"),
                new Spec(Type.PINATA, "", 0), List.of("pinata_voucher"));
    }

    private void registerRanks() {
        if (luckPerms == null) return;
        try { luckPerms.getGroupManager().loadAllGroups().join(); }
        catch (RuntimeException ignored) { }

        List<String> blocked = plugin.getConfig().getStringList("vouchers.ranks.excluded-names").stream()
                .map(this::normalize).filter(s -> !s.isBlank()).toList();

        luckPerms.getGroupManager().getLoadedGroups().stream()
                .sorted(Comparator.comparingInt((Group g) -> g.getWeight().orElse(Integer.MIN_VALUE)).thenComparing(Group::getName))
                .forEach(group -> {
                    String name = group.getName();
                    String normalized = normalize(name);
                    if (normalized.isBlank() || blocked.stream().anyMatch(normalized::contains)) return;
                    String id = "voucher_rank_" + idPart(name);
                    register(id, "&d&l" + pretty(name) + " Rank Voucher", Material.BOOK,
                            voucherLore(pretty(name) + " Rank"),
                            new Spec(Type.RANK, name, 0), List.of(name + "_rank_voucher"));
                });
    }

    private void registerKits() {
        Plugin kitsPlugin = Bukkit.getPluginManager().getPlugin("MiraKits");
        if (kitsPlugin == null || !kitsPlugin.isEnabled()) return;
        try {
            Object kits = kitsPlugin.getClass().getMethod("kits").invoke(kitsPlugin);
            Object raw = kits.getClass().getMethod("kitIds").invoke(kits);
            if (!(raw instanceof Collection<?> collection)) return;
            for (Object value : collection) {
                String kit = Objects.toString(value, "").trim();
                if (kit.isBlank()) continue;
                register("voucher_kit_" + idPart(kit), "&e&l" + pretty(kit) + " Kit Voucher", Material.ENDER_CHEST,
                        voucherLore(pretty(kit) + " Kit"),
                        new Spec(Type.KIT, kit, 0), List.of(kit + "_kit_voucher"));
            }
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().warning("Could not discover MiraKits vouchers: " + ex.getMessage());
        }
    }

    private void registerTags() {
        Plugin tags = Bukkit.getPluginManager().getPlugin("MiraTags");
        if (tags == null || !tags.isEnabled()) return;
        File file = new File(tags.getDataFolder(), "tags.yml");
        if (!file.isFile()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("tags");
        if (root == null) return;

        for (String rawId : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(rawId);
            if (section == null || !section.getBoolean("enabled", true)) continue;
            boolean defaultUnlocked = section.getBoolean("default-unlocked", false);
            if (defaultUnlocked) continue;
            String display = strip(section.getString("display-name", rawId));
            register("voucher_tag_" + idPart(rawId), "&d&l" + display + " Tag Voucher", Material.FLOWER_BANNER_PATTERN,
                    voucherLore(display + " Tag"),
                    new Spec(Type.TAG, rawId, 0), List.of(rawId + "_tag_voucher"));
        }
    }

    private void register(String id, String name, Material material, List<String> lore, Spec spec, List<String> aliases) {
        MiraItemDefinition definition = new MiraItemDefinition(id, name, aliases, lore, material, Map.of(), -1, "VOUCHER");
        if (MiraItemDefinitions.register(definition)) {
            specs.put(id, spec);
            generatedIds.add(id);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onRedeem(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() == null) return;

        Player player = event.getPlayer();
        ItemStack held = event.getItem();
        MiraItemDefinition definition = items.identify(held).orElse(null);
        if (definition == null) return;
        Spec spec = specs.get(definition.id());
        if (spec == null) return;

        event.setCancelled(true);
        if (!state.enabled(definition.id())) {
            fail(player, "That voucher is currently disabled.");
            return;
        }

        Redemption result = redeem(player, spec);
        if (!result.success()) {
            fail(player, result.message());
            return;
        }

        consume(player, held, event.getHand());
        player.sendMessage(Component.text(result.message(), NamedTextColor.GREEN));
    }

    private Redemption redeem(Player player, Spec spec) {
        return switch (spec.type()) {
            case RANK -> redeemRank(player, spec.value());
            case JELLYLEGS -> grantPermission(player, commandPermission("jellylegs",
                    plugin.getConfig().getString("vouchers.permissions.jellylegs", "jellylegs.use")),
                    "Jelly Legs unlocked permanently.");
            case HOME -> redeemHome(player, spec.tier(), spec.value());
            case KIT -> redeemKit(player, spec.value());
            case FLY -> grantPermission(player, commandPermission("fly",
                    plugin.getConfig().getString("vouchers.permissions.fly", "essentials.fly")),
                    "Permanent /fly unlocked.");
            case TAG -> redeemTag(player, spec.value());
            case AIRDROP -> redeemAirdrop(player);
            case PINATA -> redeemPinata(player);
        };
    }

    private Redemption redeemRank(Player player, String groupName) {
        if (luckPerms == null) return Redemption.fail("LuckPerms is not available.");
        Group target = luckPerms.getGroupManager().getGroup(groupName);
        if (target == null) return Redemption.fail("That rank no longer exists.");

        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) user = luckPerms.getUserManager().loadUser(player.getUniqueId()).join();

        int targetWeight = target.getWeight().orElse(0);
        int currentWeight = user.getInheritedGroups(QueryOptions.nonContextual()).stream()
                .filter(g -> !isStaffGroup(g.getName()))
                .mapToInt(g -> g.getWeight().orElse(0))
                .max().orElse(Integer.MIN_VALUE);

        boolean already = user.getInheritedGroups(QueryOptions.nonContextual()).stream()
                .anyMatch(g -> g.getName().equalsIgnoreCase(groupName));
        if (already || currentWeight >= targetWeight) {
            return Redemption.fail("You already have this rank or a higher rank.");
        }

        user.data().add(InheritanceNode.builder(groupName).value(true).build());
        luckPerms.getUserManager().saveUser(user);
        return Redemption.ok("Redeemed rank: " + pretty(groupName) + ".");
    }

    private Redemption redeemHome(Player player, int tier, String setName) {
        if (luckPerms == null) return Redemption.fail("LuckPerms is not available.");
        int current = 0;
        for (int i = 1; i <= 3; i++) if (player.hasPermission("essentials.sethome.multiple.mira" + i)) current = i;
        if (current >= tier) return Redemption.fail("You already have this home upgrade or better.");
        if (tier > 1 && current != tier - 1) return Redemption.fail("Redeem Home Upgrade " + roman(tier - 1) + " first.");

        User user = requireUser(player);
        if (user == null) return Redemption.fail("Could not load your LuckPerms user.");
        user.data().add(PermissionNode.builder("essentials.sethome.multiple").value(true).build());
        user.data().add(PermissionNode.builder("essentials.sethome.multiple." + setName).value(true).build());
        luckPerms.getUserManager().saveUser(user);
        return Redemption.ok("Home Upgrade " + roman(tier) + " unlocked permanently.");
    }

    private Redemption redeemKit(Player player, String kit) {
        Plugin kitsPlugin = Bukkit.getPluginManager().getPlugin("MiraKits");
        if (kitsPlugin == null || !kitsPlugin.isEnabled()) return Redemption.fail("MiraKits is not available.");

        try {
            Object kits = kitsPlugin.getClass().getMethod("kits").invoke(kitsPlugin);
            Object exists = kits.getClass().getMethod("exists", String.class).invoke(kits, kit);
            if (!(exists instanceof Boolean b) || !b) return Redemption.fail("That kit no longer exists.");
        } catch (ReflectiveOperationException ex) {
            return Redemption.fail("Could not validate that kit.");
        }

        boolean dispatched = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "kit " + kit + " " + player.getName());
        return dispatched ? Redemption.ok("Redeemed one " + pretty(kit) + " kit.")
                : Redemption.fail("That kit could not be redeemed.");
    }

    private Redemption redeemTag(Player player, String tagId) {
        Plugin target = Bukkit.getPluginManager().getPlugin("MiraTags");
        if (target == null || !target.isEnabled()) return Redemption.fail("MiraTags is not available.");

        try {
            ClassLoader loader = target.getClass().getClassLoader();
            Class<?> apiType = Class.forName("com.mira.tags.api.MiraTagsApi", true, loader);
            var apiField = target.getClass().getDeclaredField("api");
            apiField.setAccessible(true);
            Object api = apiField.get(target);
            if (api == null) return Redemption.fail("MiraTags API is not available.");

            Method owns = apiType.getMethod("owns", Player.class, String.class);
            if ((Boolean) owns.invoke(api, player, tagId)) {
                return Redemption.fail("You already own that tag.");
            }

            Method grant = apiType.getMethod("grant", UUID.class, String.class);
            boolean granted = (Boolean) grant.invoke(api, player.getUniqueId(), tagId);
            return granted ? Redemption.ok("Tag unlocked permanently.")
                    : Redemption.fail("That tag could not be granted.");
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().warning("Could not redeem MiraTags voucher '" + tagId + "': " + ex.getMessage());
            return Redemption.fail("MiraTags integration is unavailable.");
        }
    }

    private List<String> voucherLore(String grant) {
        return List.of(
                "&7This voucher grants &f" + grant,
                "&eRight Click to receive"
        );
    }

    private Redemption redeemAirdrop(Player player) {
        Plugin target = Bukkit.getPluginManager().getPlugin("MiraAirdrops");
        if (target == null || !target.isEnabled()) return Redemption.fail("MiraAirdrops is not available.");
        try {
            Object service = target.getClass().getMethod("service").invoke(target);
            boolean active = (Boolean) service.getClass().getMethod("active").invoke(service);
            boolean inbound = (Boolean) service.getClass().getMethod("inbound").invoke(service);
            if (active || inbound) return Redemption.fail("An airdrop is already inbound or active.");
            boolean started = (Boolean) service.getClass().getMethod("start", org.bukkit.command.CommandSender.class)
                    .invoke(service, player);
            return started ? Redemption.ok("Airdrop called.") : Redemption.fail("The airdrop could not start.");
        } catch (ReflectiveOperationException ex) {
            return Redemption.fail("MiraAirdrops does not expose voucher integration yet.");
        }
    }

    private Redemption redeemPinata(Player player) {
        Plugin target = Bukkit.getPluginManager().getPlugin("MiraPinata");
        if (target == null || !target.isEnabled()) return Redemption.fail("MiraPinata is not available.");
        try {
            Object manager = target.getClass().getMethod("manager").invoke(target);
            boolean active = (Boolean) manager.getClass().getMethod("active").invoke(manager);
            boolean countdown = (Boolean) manager.getClass().getMethod("countingDown").invoke(manager);
            if (active || countdown) return Redemption.fail("A Pinata is already active or counting down.");
            boolean started = (Boolean) manager.getClass().getMethod("startCountdown").invoke(manager);
            return started ? Redemption.ok("Pinata event called.") : Redemption.fail("The Pinata could not start.");
        } catch (ReflectiveOperationException ex) {
            return Redemption.fail("MiraPinata does not expose voucher integration yet.");
        }
    }

    private Redemption grantPermission(Player player, String permission, String success) {
        if (permission == null || permission.isBlank()) return Redemption.fail("That command has no configured permission node.");
        if (player.hasPermission(permission)) return Redemption.fail("You already have that permission.");
        User user = requireUser(player);
        if (user == null) return Redemption.fail("Could not load your LuckPerms user.");
        user.data().add(PermissionNode.builder(permission).value(true).build());
        luckPerms.getUserManager().saveUser(user);
        return Redemption.ok(success);
    }

    private User requireUser(Player player) {
        if (luckPerms == null) return null;
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        return user != null ? user : luckPerms.getUserManager().loadUser(player.getUniqueId()).join();
    }

    private String commandPermission(String commandName, String fallback) {
        PluginCommand command = Bukkit.getPluginCommand(commandName);
        if (command != null && command.getPermission() != null && !command.getPermission().isBlank()) return command.getPermission();
        return fallback == null ? "" : fallback.trim();
    }

    private boolean isStaffGroup(String name) {
        String normalized = normalize(name);
        return plugin.getConfig().getStringList("vouchers.ranks.excluded-names").stream()
                .map(this::normalize).anyMatch(normalized::contains);
    }

    private void ensureHomeTiers() {
        Plugin essentials = Bukkit.getPluginManager().getPlugin("Essentials");
        if (essentials == null || !essentials.isEnabled()) return;
        File file = new File(essentials.getDataFolder(), "config.yml");
        if (!file.isFile()) return;

        try {
            List<String> lines = new ArrayList<>(Files.readAllLines(file.toPath(), StandardCharsets.UTF_8));
            boolean has1 = lines.stream().anyMatch(line -> line.trim().startsWith("mira1:"));
            boolean has2 = lines.stream().anyMatch(line -> line.trim().startsWith("mira2:"));
            boolean has3 = lines.stream().anyMatch(line -> line.trim().startsWith("mira3:"));
            if (has1 && has2 && has3) return;

            int baseHomes = 1;
            try {
                Object settings = essentials.getClass().getMethod("getSettings").invoke(essentials);
                Object value = settings.getClass().getMethod("getHomeLimit", String.class).invoke(settings, "default");
                if (value instanceof Number n) baseHomes = Math.max(1, n.intValue());
            } catch (ReflectiveOperationException ignored) { }

            int section = -1;
            int insert = lines.size();
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).startsWith("sethome-multiple:")) {
                    section = i;
                    insert = i + 1;
                    for (int j = i + 1; j < lines.size(); j++) {
                        String line = lines.get(j);
                        if (!line.isBlank() && !Character.isWhitespace(line.charAt(0)) && !line.trim().startsWith("#")) {
                            insert = j;
                            break;
                        }
                        insert = j + 1;
                    }
                    break;
                }
            }

            List<String> additions = new ArrayList<>();
            if (!has1) additions.add("  mira1: " + (baseHomes + 1));
            if (!has2) additions.add("  mira2: " + (baseHomes + 2));
            if (!has3) additions.add("  mira3: " + (baseHomes + 3));

            if (section < 0) {
                lines.add("");
                lines.add("sethome-multiple:");
                lines.addAll(additions);
            } else {
                lines.addAll(insert, additions);
            }
            Files.write(file.toPath(), lines, StandardCharsets.UTF_8);

            try {
                Object settings = essentials.getClass().getMethod("getSettings").invoke(essentials);
                settings.getClass().getMethod("reloadConfig").invoke(settings);
            } catch (ReflectiveOperationException ignored) { }

            plugin.getLogger().info("Ensured Essentials home voucher tiers mira1/mira2/mira3 at "
                    + (baseHomes + 1) + "/" + (baseHomes + 2) + "/" + (baseHomes + 3) + " homes.");
        } catch (Exception ex) {
            plugin.getLogger().warning("Could not prepare Essentials home voucher tiers: " + ex.getMessage());
        }
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

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "");
    }

    private String idPart(String value) {
        String out = value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
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

    private String strip(String value) {
        return value == null ? "" : value.replaceAll("(?i)&[0-9A-FK-ORX]", "");
    }

    private String roman(int value) {
        return switch (value) { case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; default -> Integer.toString(value); };
    }

    private record Redemption(boolean success, String message) {
        static Redemption ok(String message) { return new Redemption(true, message); }
        static Redemption fail(String message) { return new Redemption(false, message); }
    }
}
