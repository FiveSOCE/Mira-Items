# MiraItems

MiraItems is the scarce tracked-special-item system for the Mira Paper server suite. It issues uniquely signed custom weapons/items, enforces scarcity limits and integrity checks, and attaches custom combat or utility abilities to those issued copies.

## Download

[**Download MiraItems v0.1.6**](https://github.com/FiveSOCE/Mira-Items/releases/download/v0.1.6/MiraItems-0.1.6.jar)

Adds signed custom rename overlays plus repair/rename utility tokens and admin token issuance.

## Requirements / Dependencies

- Paper 1.21.11
- Java 21
- MiraCore 0.2.0 or newer
- MiraEnchantments optional; MiraEnchantments v0.4.6+ is recommended so Runes are rejected before they can modify MiraItems
- PlaceholderAPI optional for held-item ability/cooldown/event placeholders

## How MiraItems Works

Every issued MiraItem receives hidden persistent identity including a unique issuance UUID, item ID, owner identity, issue date and signed backing metadata. Visible lore stays clean while the hidden data is used to validate the item. If a claimed MiraItem is materially altered or receives invalid backing, MiraItems strips its special-item identity and its ability stops functioning. Scarcity is based on issuance records, so destroying or losing a limited item does not automatically free another slot.

Current special items include:

- **Pyro Axe**: Netherite Axe with Sharpness V/Fire Aspect II. Consecutive hits against the same target scale through 1x, 2x and 4x damage, staying capped at 4x until the chain resets. From hit two onward it plays the Wither block-break and anvil-land sounds from the target location for nearby players.
- **Excalibur**: Golden Sword with Sharpness X/Infinity X, maximum issuance 2. Successful hits blind and slow the target for 3 seconds with a 30-second ability cooldown per issued sword.
- **Lochaber Axe**: Diamond Axe with Sharpness X, maximum issuance 5. Applies Mining Fatigue I while held and pulls the target toward the attacker every fifth successful player hit.
- **Empower!**: Yearn Goat Horn with Unbreaking X, maximum issuance 10. On use grants Resistance II, Speed II and Regeneration II for 30 seconds, with a 5-minute cooldown per issued horn.

MiraEnchantments Runes are not valid on MiraItems. Administrative limit commands deliberately manage issuance capacity rather than automatically replacing lost rare items.

v0.1.6 makes the custom-item layer extensible rather than enum-locked. External Mira modules can register named ability handlers through the MiraItems API, while the built-in Pyro, Excalibur, Lochaber and Empower mechanics remain first-party handlers. Excalibur and Empower cooldowns use MiraCore's shared cooldown service with the issued-item UUID as the cooldown subject, preserving cooldowns even if an item changes hands.

Event item registrations can carry an event ID plus absolute start/end timestamps. Definitions remain persisted and inactive before their start window; expired event definitions are removed from active use. Admin inspection/verification is deliberately non-destructive, and `/mitem migrate` only refreshes canonical metadata/signatures for an item that is already valid and backed by a real issuance record.

## Commands

All commands require `miraitems.admin`.

| Command | Permission | What it does |
| --- | --- | --- |
| `/mitem give <item>` | `miraitems.admin` | Gives the executing player a newly issued copy of the selected MiraItem. |
| `/mitem token <repair|rename> <player> [amount]` | `miraitems.admin` | Gives signed utility tokens for repairing or renaming supported MiraItems. |
| `/mitem give <player> <item>` | `miraitems.admin` | Issues the selected MiraItem to another player. |
| `/mitem disable <item>` | `miraitems.admin` | Disables issuance/active availability of the selected item definition. |
| `/mitem enable <item>` | `miraitems.admin` | Re-enables the selected item definition. |
| `/mitem check <item>` | `miraitems.admin` | Shows issuance/scarcity information for an item. |
| `/mitem inspect` | `miraitems.admin` | Inspects the held MiraItem's ID, ability, issue/owner backing, event window and verification state without modifying it. |
| `/mitem verify` | `miraitems.admin` | Performs a non-destructive integrity/backing check on the held MiraItem. |
| `/mitem migrate` | `miraitems.admin` | Safely refreshes canonical name/lore/signature metadata only for an already-valid backed MiraItem. |
| `/mitem reset <item>` | `miraitems.admin` | Performs the deliberate wipe/reset flow for that item's issuance ledger. |
| `/mitem addlimit <item>` | `miraitems.admin` | Increases the issuance limit for a limited MiraItem. |
| `/mitem removelimit <item>` | `miraitems.admin` | Decreases/removes issuance capacity for a limited MiraItem. |
| `/mitem status` | `miraitems.admin` | Shows MiraItems runtime/registry state. |
| `/mitem test` | `miraitems.admin` | Runs MiraItems diagnostics/self-tests. |
| `/mitem help` | `miraitems.admin` | Shows MiraItems command help. |

Aliases: `/miraitem`, `/miraitems`, `/mi`.

Canonical item IDs include `pyro_axe`, `excalibur`, `lochaber_axe` and `empower`; friendly names such as `Pyro Axe` are also accepted by command parsing.

## Permissions

| Permission | Default | What it does |
| --- | --- | --- |
| `miraitems.admin` | OP | Allows all MiraItems administration, issuance, limit and diagnostic commands. |


## API / Integration

MiraItems registers `MiraItemsApi` through MiraCore. Other plugins can register normal or event items and can attach custom runtime ability handlers by ability ID. Built-in ability IDs are reserved so external handlers cannot silently replace first-party mechanics.

Event registration supports an event ID, optional start timestamp and optional expiry timestamp. All timestamps are absolute `Instant` values so restart does not reset availability.

## PlaceholderAPI

Player-context placeholders:

- `%miraitems_held_id%`
- `%miraitems_held_ability%`
- `%miraitems_held_cooldown%`
- `%miraitems_held_event%`
- `%miraitems_held_event_remaining%`
