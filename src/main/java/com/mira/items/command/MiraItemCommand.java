package com.mira.items.command;

import com.mira.core.api.MiraCore;
import com.mira.items.MiraItemsPlugin;
import com.mira.items.model.MiraItemDefinition;
import com.mira.items.model.MiraItemDefinitions;
import com.mira.items.service.AbilityRegistryService;
import com.mira.items.service.CustomItemRegistryService;
import com.mira.items.service.MiraItemService;
import com.mira.items.service.UtilityTokenService;
import com.mira.items.store.ItemStateStore;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class MiraItemCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBCOMMANDS = List.of(
            "give", "token", "disable", "enable", "check", "inspect", "verify", "migrate", "reset", "addlimit", "removelimit", "status", "test", "help"
    );

    private final MiraItemsPlugin plugin;
    private final MiraCore core;
    private final MiraItemService items;
    private final ItemStateStore state;
    private final CustomItemRegistryService registry;
    private final AbilityRegistryService abilities;
    private final UtilityTokenService utilityTokens;

    public MiraItemCommand(MiraItemsPlugin plugin, MiraCore core, MiraItemService items, ItemStateStore state,
                           CustomItemRegistryService registry, AbilityRegistryService abilities,
                           UtilityTokenService utilityTokens) {
        this.plugin = plugin;
        this.core = core;
        this.items = items;
        this.state = state;
        this.registry = registry;
        this.abilities = abilities;
        this.utilityTokens = utilityTokens;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) { help(sender); return true; }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "give" -> give(sender, args);
            case "token" -> token(sender, args);
            case "disable" -> setEnabled(sender, args, false);
            case "enable" -> setEnabled(sender, args, true);
            case "check" -> check(sender, args);
            case "inspect" -> inspect(sender);
            case "verify" -> verify(sender);
            case "migrate" -> migrate(sender);
            case "reset" -> reset(sender, args);
            case "addlimit" -> addLimit(sender, args);
            case "removelimit" -> removeLimit(sender, args);
            case "status" -> status(sender);
            case "test" -> test(sender);
            default -> help(sender);
        }
        return true;
    }

    private void token(CommandSender sender, String[] args) {
        if (args.length < 3) {
            error(sender, "Usage: /mitem token <repair|rename> <player> [amount]");
            return;
        }
        UtilityTokenService.TokenType type;
        try {
            type = UtilityTokenService.TokenType.valueOf(args[1].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            error(sender, "Token type must be repair or rename.");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            error(sender, "Player '" + args[2] + "' is not online.");
            return;
        }
        int amount = 1;
        if (args.length >= 4) {
            try { amount = Integer.parseInt(args[3]); }
            catch (NumberFormatException exception) {
                error(sender, "Amount must be a positive number.");
                return;
            }
        }
        if (amount <= 0 || amount > 2304) {
            error(sender, "Amount must be between 1 and 2304.");
            return;
        }
        utilityTokens.give(target, type, amount);
        success(sender, "Gave " + amount + " " + type.name().toLowerCase(Locale.ROOT) + " token"
                + (amount == 1 ? "" : "s") + " to " + target.getName() + ".");
        core.audit().record("MiraItems", "UTILITY_TOKEN_GRANTED",
                sender instanceof Player player ? player.getUniqueId() : null, sender.getName(),
                target.getUniqueId().toString(), "Utility token granted",
                java.util.Map.of("type", type.name(), "amount", Integer.toString(amount),
                        "targetName", target.getName()));
    }

    private void give(CommandSender sender, String[] args) {
        if (args.length < 2) { error(sender, "Usage: /mitem give <item> OR /mitem give <player> <item>"); return; }
        if (sender instanceof Player self) {
            Optional<MiraItemDefinition> selfDefinition = resolve(join(args, 1));
            if (selfDefinition.isPresent()) { issueTo(sender, self, selfDefinition.get()); return; }
        }
        if (args.length < 3) { error(sender, "Console must use /mitem give <player> <item>."); return; }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) { error(sender, "Player '" + args[1] + "' is not online."); return; }
        MiraItemDefinition definition = resolve(join(args, 2)).orElse(null);
        if (definition == null) { error(sender, "Unknown MiraItem. Try pyro_axe, excalibur, lochaber_axe or empower."); return; }
        issueTo(sender, target, definition);
    }

    private void issueTo(CommandSender sender, Player target, MiraItemDefinition definition) {
        if (!state.enabled(definition.id())) { error(sender, definitionName(definition) + " is disabled."); return; }
        if (!state.canIssue(definition.id())) {
            error(sender, definitionName(definition) + " is at its issuance limit (" + state.issuedCount(definition.id()) + "/" + state.limit(definition.id()) + ")."); return;
        }
        if (!items.give(target, definition)) { error(sender, "Could not issue " + definitionName(definition) + "."); return; }
        success(sender, "Issued " + definitionName(definition) + " to " + target.getName() + ".");
        if (sender != target) success(target, "You received " + definitionName(definition) + ".");
    }

    private void setEnabled(CommandSender sender, String[] args, boolean enabled) {
        MiraItemDefinition definition = requiredDefinition(sender, args);
        if (definition == null) return;
        state.setEnabled(definition.id(), enabled);
        success(sender, definitionName(definition) + " is now " + (enabled ? "enabled" : "disabled") + ".");
    }

    private void check(CommandSender sender, String[] args) {
        MiraItemDefinition definition = requiredDefinition(sender, args);
        if (definition == null) return;
        int limit = state.limit(definition.id());
        int issued = state.issuedCount(definition.id());
        send(sender, "&dMiraItems: &f" + definitionName(definition));
        send(sender, "&7Enabled: &f" + state.enabled(definition.id()));
        send(sender, "&7Issued: &f" + issued + "/" + (limit < 0 ? "Unlimited" : limit));
        List<ItemStateStore.IssuedRecord> records = state.records(definition.id());
        if (records.isEmpty()) { send(sender, "&8Owners: none"); return; }
        send(sender, "&7Recorded owners:");
        for (ItemStateStore.IssuedRecord record : records) {
            send(sender, "&6- " + record.ownerName() + " &7| " + record.date() + " | #" + record.issueId().toString().substring(0, 8));
        }
    }

    private void inspect(CommandSender sender) {
        if (!(sender instanceof Player player)) { error(sender, "/mitem inspect must be run by a player holding an item."); return; }
        ItemStack held = player.getInventory().getItemInMainHand();
        var inspection = items.inspect(held);
        if (!inspection.claimed()) { error(sender, "The held item is not a MiraItem."); return; }
        send(sender, "&dMiraItem Inspection");
        send(sender, "&7ID: &f" + inspection.itemId() + " &7| Ability: &f" + inspection.abilityId());
        send(sender, "&7Issue: &f" + (inspection.issueId() == null ? "Missing" : inspection.issueId()));
        send(sender, "&7Owner: &f" + inspection.ownerName() + " &8(" + inspection.ownerUuid() + ")");
        send(sender, "&7Issued: &f" + inspection.issuedDate());
        send(sender, "&7Valid signature/canonical metadata: " + (inspection.valid() ? "&aYES" : "&cNO"));
        send(sender, "&7Issuance backing: " + (inspection.backed() ? "&aACTIVE" : "&cMISSING"));
        send(sender, "&7Definition active: " + (inspection.activeDefinition() ? "&aYES" : "&cNO"));
        registry.eventId(inspection.itemId()).ifPresent(id -> send(sender, "&7Event: &f" + id));
        registry.startsAt(inspection.itemId()).ifPresent(time -> send(sender, "&7Starts: &f" + time));
        if (inspection.eventExpiry() != null) send(sender, "&7Expires: &f" + inspection.eventExpiry());
    }

    private void verify(CommandSender sender) {
        if (!(sender instanceof Player player)) { error(sender, "/mitem verify must be run by a player holding an item."); return; }
        var inspection = items.inspect(player.getInventory().getItemInMainHand());
        if (!inspection.claimed()) { error(sender, "The held item is not a MiraItem."); return; }
        if (inspection.valid() && inspection.backed() && inspection.activeDefinition()) {
            success(sender, "Held MiraItem is valid, backed and active.");
        } else {
            error(sender, "Held MiraItem failed verification. No item data was modified.");
        }
    }

    private void migrate(CommandSender sender) {
        if (!(sender instanceof Player player)) { error(sender, "/mitem migrate must be run by a player holding an item."); return; }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!items.migrateCanonical(held)) {
            error(sender, "Migration refused. Only already-valid, issuance-backed MiraItems can be re-signed/canonicalized.");
            return;
        }
        player.getInventory().setItemInMainHand(held);
        success(sender, "Held MiraItem was safely refreshed to the current canonical metadata/signature.");
    }

    private void reset(CommandSender sender, String[] args) {
        MiraItemDefinition definition = requiredDefinition(sender, args);
        if (definition == null) return;
        int cleared = state.reset(definition.id());
        success(sender, "Reset " + definitionName(definition) + " issuance ledger. Cleared " + cleared + " record" + (cleared == 1 ? "" : "s") + ". Existing copies will lose backing when scanned.");
    }

    private void addLimit(CommandSender sender, String[] args) {
        MiraItemDefinition definition = requiredDefinition(sender, args);
        if (definition == null) return;
        int current = state.limit(definition.id());
        if (current < 0) { error(sender, definitionName(definition) + " has no maximum limit."); return; }
        state.setLimit(definition.id(), current + 1);
        success(sender, definitionName(definition) + " limit increased to " + (current + 1) + ".");
    }

    private void removeLimit(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { error(sender, "/mitem removelimit must be run by a player holding the MiraItem being removed."); return; }
        MiraItemDefinition definition = requiredDefinition(sender, args);
        if (definition == null) return;
        int currentLimit = state.limit(definition.id());
        if (currentLimit < 0) { error(sender, definitionName(definition) + " has no maximum limit."); return; }
        if (currentLimit == 0) { error(sender, definitionName(definition) + " limit is already 0."); return; }
        ItemStack held = player.getInventory().getItemInMainHand();
        MiraItemDefinition heldDefinition = items.identify(held).orElse(null);
        if (heldDefinition == null || !heldDefinition.id().equals(definition.id())) { error(sender, "You must hold a valid " + definitionName(definition) + " in your main hand."); return; }
        UUID issueId = items.issueId(held).orElse(null);
        if (issueId == null || !state.removeIssue(definition.id(), issueId)) { error(sender, "That item has no active issuance record."); return; }
        player.getInventory().setItemInMainHand(null);
        state.setLimit(definition.id(), currentLimit - 1);
        success(sender, "Consumed the held " + definitionName(definition) + " and reduced its limit to " + (currentLimit - 1) + ".");
    }

    private void status(CommandSender sender) {
        send(sender, "&dMiraItems v" + plugin.getPluginMeta().getVersion());
        for (MiraItemDefinition definition : MiraItemDefinitions.all()) {
            int limit = state.limit(definition.id());
            send(sender, "&7- " + definition.id() + ": " + (state.enabled(definition.id()) ? "&aENABLED" : "&cDISABLED")
                    + " &7| " + state.issuedCount(definition.id()) + "/" + (limit < 0 ? "Unlimited" : limit)
                    + " &7| ability &f" + definition.abilityId());
        }
        send(sender, "&7External ability handlers: &f" + (abilities.ids().isEmpty() ? "None" : abilities.ids()));
    }

    private void test(CommandSender sender) {
        int passed = 0;
        if (MiraItemDefinitions.all().size() == 4) passed++;
        if (MiraItemDefinitions.find("Pyro Axe").map(MiraItemDefinition::id).orElse("").equals("pyro_axe")) passed++;
        if (state.limit("pyro_axe") < 0) passed++;
        if (state.limit("excalibur") >= 0) passed++;
        if (state.limit("lochaber_axe") >= 0) passed++;
        if (state.limit("empower") >= 0) passed++;
        if (plugin.getConfig().getString("security.secret", "").length() >= 16) passed++;
        if (plugin.getServer().getPluginManager().isPluginEnabled("MiraCore")) passed++;
        send(sender, (passed == 8 ? "&a" : "&c") + "MiraItems Self-Test: " + passed + "/8 passed.");
    }

    private MiraItemDefinition requiredDefinition(CommandSender sender, String[] args) {
        if (args.length < 2) { error(sender, "You must specify an item."); return null; }
        MiraItemDefinition definition = resolve(join(args, 1)).orElse(null);
        if (definition == null) error(sender, "Unknown MiraItem. Try pyro_axe, excalibur, lochaber_axe or empower.");
        return definition;
    }

    private Optional<MiraItemDefinition> resolve(String input) { return MiraItemDefinitions.find(input); }
    private String join(String[] args, int start) { return String.join(" ", Arrays.copyOfRange(args, start, args.length)); }
    private String definitionName(MiraItemDefinition definition) {
        return switch (definition.id()) { case "pyro_axe" -> "Pyro Axe"; case "lochaber_axe" -> "Lochaber Axe"; case "empower" -> "Empower!"; default -> "Excalibur"; };
    }

    private void help(CommandSender sender) {
        send(sender, "&dMiraItems commands");
        send(sender, "&7/mitem give <item>");
        send(sender, "&7/mitem give <player> <item>");
        send(sender, "&7/mitem token <repair|rename> <player> [amount]");
        send(sender, "&7/mitem disable <item> | /mitem enable <item>");
        send(sender, "&7/mitem check <item> | /mitem reset <item>");
        send(sender, "&7/mitem inspect | /mitem verify | /mitem migrate");
        send(sender, "&7/mitem addlimit <item> | /mitem removelimit <item>");
        send(sender, "&7/mitem status | /mitem test");
    }

    private void send(CommandSender sender, String message) { core.messages().send(sender, message); }
    private void success(CommandSender sender, String message) { send(sender, "&a" + message); }
    private void error(CommandSender sender, String message) { send(sender, "&c" + message); }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return matching(SUBCOMMANDS, args[0]);
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && sub.equals("token")) return matching(List.of("repair", "rename"), args[1]);
        if (args.length == 3 && sub.equals("token")) {
            return matching(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[2]);
        }
        if (args.length == 4 && sub.equals("token")) return matching(List.of("1", "5", "10", "32", "64"), args[3]);
        if (args.length == 2 && sub.equals("give")) {
            List<String> values = new ArrayList<>(itemIds());
            Bukkit.getOnlinePlayers().forEach(player -> values.add(player.getName()));
            return matching(values, args[1]);
        }
        if (args.length >= 2 && List.of("disable", "enable", "check", "reset", "addlimit", "removelimit").contains(sub)) return args.length == 2 ? matching(itemIds(), args[1]) : List.of();
        if (args.length == 3 && sub.equals("give") && Bukkit.getPlayerExact(args[1]) != null) return matching(itemIds(), args[2]);
        return List.of();
    }

    private List<String> itemIds() { return MiraItemDefinitions.all().stream().map(MiraItemDefinition::id).toList(); }
    private List<String> matching(List<String> values, String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower)).sorted().toList();
    }
}
