# MiraItems

MiraItems is the scarce tracked-special-item system for the Mira Paper server suite. It issues uniquely signed custom weapons/items, enforces scarcity limits and integrity checks, and attaches custom combat or utility abilities to those issued copies.

## Download

[**Download MiraItems v0.1.3**](https://github.com/FiveSOCE/Mira-Items/releases/download/v0.1.3/MiraItems-0.1.3.jar)

## Requirements / Dependencies

- Paper 1.21.11
- Java 21
- MiraCore 0.1.0 or newer
- MiraEnchantments optional; MiraEnchantments v0.4.6+ is recommended so Runes are rejected before they can modify MiraItems

## How MiraItems Works

Every issued MiraItem receives hidden persistent identity including a unique issuance UUID, item ID, owner identity, issue date and signed backing metadata. Visible lore stays clean while the hidden data is used to validate the item. If a claimed MiraItem is materially altered or receives invalid backing, MiraItems strips its special-item identity and its ability stops functioning. Scarcity is based on issuance records, so destroying or losing a limited item does not automatically free another slot.

Current special items include:

- **Pyro Axe**: Netherite Axe with Sharpness V/Fire Aspect II. Consecutive hits against the same target scale through 1x, 2x and 4x damage, staying capped at 4x until the chain resets. From hit two onward it plays the Wither block-break and anvil-land sounds from the target location for nearby players.
- **Excalibur**: Golden Sword with Sharpness X/Infinity X, maximum issuance 2. Successful hits blind and slow the target for 3 seconds with a 30-second ability cooldown per issued sword.
- **Lochaber Axe**: Diamond Axe with Sharpness X, maximum issuance 5. Applies Mining Fatigue I while held and pulls the target toward the attacker every fifth successful player hit.
- **Empower!**: Yearn Goat Horn with Unbreaking X, maximum issuance 10. On use grants Resistance II, Speed II and Regeneration II for 30 seconds, with a 5-minute cooldown per issued horn.

MiraEnchantments Runes are not valid on MiraItems. Administrative limit commands deliberately manage issuance capacity rather than automatically replacing lost rare items.

## Commands

All commands require `miraitems.admin`.

| Command | Permission | What it does |
| --- | --- | --- |
| `/mitem give <item>` | `miraitems.admin` | Gives the executing player a newly issued copy of the selected MiraItem. |
| `/mitem give <player> <item>` | `miraitems.admin` | Issues the selected MiraItem to another player. |
| `/mitem disable <item>` | `miraitems.admin` | Disables issuance/active availability of the selected item definition. |
| `/mitem enable <item>` | `miraitems.admin` | Re-enables the selected item definition. |
| `/mitem check <item>` | `miraitems.admin` | Shows issuance/scarcity information for an item. |
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
