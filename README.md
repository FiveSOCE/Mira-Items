# MiraItems

Scarce tracked-special-item and voucher system for the Mira Paper server suite.

MiraItems issues uniquely signed custom weapons/items, enforces scarcity limits and integrity checks, provides permanent resource-pack model identities, and attaches custom combat/utility mechanics to authenticated issued copies.

## Versioned downloads

| Paper server | Java | JAR filename | Download |
| --- | --- | --- | --- |
| **1.18.2** | 17 | `MiraItems_1.18.2.jar` | [Legacy release](https://github.com/FiveSOCE/Mira-Items/releases/tag/dev-1.18.2) |
| **1.21.11–26.2** | 21 baseline; newer Paper may require Java 25 | `MiraItems_1.21.11-26.2.jar` | [Modern release](https://github.com/FiveSOCE/Mira-Items/releases/tag/compat-modern-1.21.11-26.2) |

Only use download links once the relevant CI build passes and the JAR appears in GitHub releases. **1.19–1.21.10 are not yet certified.** Item models and some equipment functionality depend on the Minecraft client's resource-pack format; no identical client-side visuals are promised across the full version range.

[Legacy source](https://github.com/FiveSOCE/Mira-Items/tree/compat/1.18.2) · [Modern source](https://github.com/FiveSOCE/Mira-Items) · [All releases](https://github.com/FiveSOCE/Mira-Items/releases)

## Dependencies and integrations

- **Required:** Matching Paper and Java runtime, plus a matching **MiraCore** JAR.
- **Optional:** MiraEnchantments to prevent protected items from receiving runes; PlaceholderAPI, LuckPerms for rank vouchers, EssentialsX, MiraKits, MiraTags, MiraAirdrops, MiraPinata and MythicArmors, where installed and version-compatible.
- **Resource pack:** Model files are required for custom visual appearance. The legacy client needs `CustomModelData` overrides, whereas modern item-model components need modern pack definitions.
- Back up `plugins/MiraItems/` and remove the older JAR before installing a matching build.

## Commands and permissions

| Command | Permission | Purpose |
| --- | --- | --- |
| `/mi <player> <item-or-reward>` | `miraitems.admin` | Give custom signed weapons or vouchers |
| `/mi applyitem <item>` | `miraitems.admin` | Apply authenticated mechanics to held item without replacing its appearance |
| `/mitem`, `/miraitem`, `/miraitems` | `miraitems.admin` | Command aliases |

| Permission | Default | Purpose |
| --- | --- | --- |
| `miraitems.admin` | OP | Signed item issuance, ability administration and diagnostics |

## Signed Item Identity

Every issued MiraItem receives hidden persistent identity including:

- unique issuance UUID
- MiraItem ID
- owner identity
- issue date
- signed backing metadata

Visible lore can remain clean while MiraItems validates the hidden identity. Materially invalid backing strips the special-item identity/ability. Scarcity is issuance-backed: destroying or losing a limited item does not automatically free another issuance slot.

## Permanent Special Items

### Pyro Axe

- Netherite Axe
- Sharpness V / Fire Aspect II
- consecutive hits on the same target scale through 1x → 2x → 4x damage
- chain remains capped at 4x until reset
- hit two onward plays the configured Wither block-break and anvil-land sounds from the target location

### Excalibur

- Golden Sword
- Sharpness X / Infinity X
- maximum issuance: 2
- successful hits blind and slow the target for 3 seconds
- 30-second ability cooldown per issued sword

### Lochaber Axe

- Diamond Axe
- Sharpness X
- maximum issuance: 5
- applies Mining Fatigue I while held
- pulls any living target toward the attacker every fifth successful hit

### Empower!

- Yearn Goat Horn
- Unbreaking X
- maximum issuance: 10
- grants Resistance II, Speed II and Regeneration II for 30 seconds
- 5-minute cooldown per issued horn

## Resource-Pack Models

Canonical models are applied only to authenticated MiraItems; vanilla base items keep their normal appearance.

Current stable model keys include:

```text
mira:excalibur
mira:lochaber_axe
mira:empower
mira:voucher_rank
mira:voucher_pinata
mira:voucher_airdrop
mira:voucher_home_upgrade
mira:voucher_jellylegs
mira:voucher_fly
mira:voucher_temp_kit
```

Valid claimed MiraItems continuously re-enforce their canonical model during inventory maintenance.

## Dynamic Vouchers

MiraItems can generate signed vouchers from live server configuration instead of requiring every reward to be hard-coded.

Supported families include:

- LuckPerms rank vouchers
- MiraTags ownership/permission vouchers
- Jelly Legs
- Home Upgrade I/II/III
- one-use/temporary kit vouchers
- Permanent Fly
- Airdrop Call
- Pinata Call
- Fix Hand permission
- Fix All permission

Rank progression prefers LuckPerms track order, then explicit group weights, then exact-rank ownership when no reliable ladder exists. Staff-style and `miratag_*` backing groups are excluded from rank-voucher generation.

Voucher grants are persisted and verified before consumption where the integration supports it.

## Canonical `/mi` Issuance

`/mi` is the primary staff/console/Tebex issuance command.

Examples:

```text
/mi <player> pyro_axe
/mi <player> Excalibur
/mi <player> Rank.Hermes
/mi <player> Tag.Reaper
/mi <player> Kit.Starter
/mi <player> airdrop
```

Short friendly names hide internal `voucher_` IDs. Dotted store forms remain available when explicit disambiguation is useful.

Player senders require `miraitems.admin`; console remains supported for automation/Tebex.

Legacy `/mitem`, `/miraitem` and `/miraitems` aliases remain available.

## Apply Mechanics to an Existing Item — v0.1.28

Administrators can apply a MiraItem mechanics/ability layer to the single item currently held in the main hand:

```text
/mi applyitem <item>
```

This preserves the held item's:

- material
- display name
- lore
- enchantments
- custom model
- unrelated metadata

The mechanics overlay remains authenticated, signed and issuance-backed, participates in normal MiraItems cooldown/ability logic, and deliberately does **not** force the canonical Mira resource-pack visual onto the customized carrier item.

## Kit Voucher Delivery

Modern MiraKits integrations use the direct voucher-delivery path so redeemed kit contents appear immediately in the player's inventory. Older MiraKits builds retain the existing console-command fallback.

## Commands

All administration commands require `miraitems.admin`.

| Command | Purpose |
| --- | --- |
| `/mi <player> <item/reward>` | Issues a signed MiraItem or voucher. |
| `/mi applyitem <item>` | Applies authenticated MiraItem mechanics to the held item while preserving its presentation. |
| `/mitem give <item>` | Gives the executing player a newly issued item. |
| `/mitem give <player> <item>` | Issues to another player. |
| `/mitem token <repair|rename> <player> [amount]` | Gives supported signed utility tokens. |
| `/mitem inspect` | Inspects held-item identity/backing without modifying it. |
| `/mitem verify` | Performs a non-destructive integrity check. |
| `/mitem migrate` | Refreshes canonical metadata/signature on an already-valid backed item. |
| `/mitem enable <item>` / `/mitem disable <item>` | Controls active availability. |
| `/mitem check <item>` | Shows scarcity/issuance information. |
| `/mitem addlimit <item>` / `/mitem removelimit <item>` | Adjusts issuance capacity. |
| `/mitem reset <item>` | Deliberately resets that item's issuance ledger. |
| `/mitem status` | Shows registry/runtime state. |
| `/mitem test` | Runs diagnostics. |

## API / Integration

`MiraItemsApi` is registered through MiraCore. Other Mira modules can register normal/event items and attach named runtime ability handlers. Built-in ability IDs are reserved so third-party handlers cannot silently replace first-party mechanics.

Event item definitions can include an event ID plus absolute start/end timestamps. Expired definitions are removed from active use without turning admin verification into a destructive operation.

## PlaceholderAPI

Player-context placeholders include:

```text
%miraitems_held_id%
%miraitems_held_ability%
%miraitems_held_cooldown%
%miraitems_held_event%
%miraitems_held_event_remaining%
```

## Building

```bash
gradle clean build
```

The output JAR is created in `build/libs/`.
