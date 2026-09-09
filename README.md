## v0.1.21 model reliability + Lochaber fix

- Valid claimed MiraItems now have their canonical resource-pack model continuously re-enforced during inventory maintenance, including existing rank and Pinata vouchers.
- Lochaber Axe now hooks any living target rather than only players.

## v0.1.20 custom item visuals

Permanent resource-pack model keys are now assigned only to authenticated MiraItems:

- Excalibur → `mira:excalibur`
- Lochaber Axe → `mira:lochaber_axe`
- Empower! → `mira:empower`
- rank vouchers → `mira:voucher_rank`
- Pinata Call voucher → `mira:voucher_pinata`
- Airdrop Call voucher → `mira:voucher_airdrop`
- Home Upgrade vouchers → `mira:voucher_home_upgrade`
- Jelly Legs voucher → `mira:voucher_jellylegs`
- Permanent Fly voucher → `mira:voucher_fly`
- temporary kit vouchers → `mira:voucher_temp_kit`

Vanilla base items keep their normal textures.

## v0.1.19 short /mi reward names

The canonical grant command now hides internal `voucher_` IDs.

Examples:

- `/mi <player> jellylegs`
- `/mi <player> hermes`
- `/mi <player> starter`
- `/mi <player> airdrop`
- `/mi <player> pyro_axe`

If a short name is ambiguous, use the explicit dotted form such as `Rank.Hermes`, `Tag.Reaper`, or `Kit.Starter`.

# MiraItems

## Download

**Latest compatibility release: v0.1.21**

[**Download MiraItems-0.1.21.jar**](https://github.com/FiveSOCE/Mira-Items/releases/download/v0.1.21/MiraItems-0.1.21.jar)

[View all releases](https://github.com/FiveSOCE/Mira-Items/releases)

## v0.1.18 /mi permission hardening

- `/mi` is fully locked behind `miraitems.admin` for player senders.
- Console remains allowed so Tebex and server automation can execute grants.
- A runtime permission check backs up Bukkit's command permission metadata.

## v0.1.17 canonical /mi issuance

`/mi` is now the primary MiraItems command.

Standard issuance syntax for console, Tebex and staff:

- `/mi <player> Rank.Hermes`
- `/mi <player> Tag.Reaper`
- `/mi <player> Kit.Starter`
- `/mi <player> pyro_axe`
- `/mi <player> Excalibur`

The parser first resolves an exact MiraItem ID/friendly name, then maps dotted store rewards such as `Rank.Hermes` to `voucher_rank_hermes`.

Legacy command names `/mitem`, `/miraitem` and `/miraitems` remain aliases so existing automation does not break, but new integrations should use `/mi <player> <reward>`.

## v0.1.16 Tebex-friendly grants

MiraItems now supports a short console-safe store syntax:

- `/mi <player> Rank.Hermes` → resolves to `voucher_rank_hermes`
- `/mi <player> Tag.Reaper` → resolves to `voucher_tag_reaper`
- `/mi <player> Kit.Starter` → resolves to `voucher_kit_starter`

The command targets an online player directly and issues the existing signed MiraItem/voucher through the normal issuance pipeline. Existing `/mitem give ...` commands remain unchanged.

# MiraItems

## v0.1.13 LuckPerms grant persistence

LuckPerms-backed vouchers now wait for the save to complete and verify the node/group before the voucher is consumed.

Rank vouchers now:

- add the target LuckPerms group
- set that group as the player's primary group
- remove lower direct groups from the same LuckPerms track when applicable
- wait for LuckPerms to save successfully
- verify the target rank is actually inherited before reporting success

Direct permission vouchers now:

- add the permission node
- wait for LuckPerms to save successfully
- verify the permission node exists on the user's data before reporting success

If LuckPerms fails to persist the grant, the voucher is not consumed.


## v0.1.13 rank voucher persistence

Rank vouchers now generate for all eligible non-staff LuckPerms groups again, including groups without explicit weights.

Progression checks use this order:

1. LuckPerms **Track order** when the target rank belongs to a track.
2. Explicit LuckPerms **group weights** when no track applies.
3. Exact-rank ownership only when neither track order nor weight can establish a reliable ladder.

This prevents rank vouchers disappearing after restart while still blocking redemption of the same or a higher rank whenever the LuckPerms configuration provides a real ordering.


## v0.1.13 rank ladder fix

Rank vouchers now use only **explicitly weighted LuckPerms rank groups** for both voucher generation and progression checks.

- unweighted/default/utility groups do not generate rank vouchers
- `miratag_*` backing groups remain excluded
- staff-style groups remain excluded
- the player's current ladder position is the highest inherited eligible weighted rank
- an unranked/default player no longer compares as weight `0` against every target rank

This fixes the false `You already have this rank or a higher rank` rejection at low/default ranks.


## v0.1.13 tag/rank separation

MiraTags creates LuckPerms backing groups named `miratag_<tagid>` for permission-backed tags. MiraItems now explicitly excludes every `miratag_*` group from rank-voucher generation.

Tag vouchers are treated as permissions, not ranks:

- the voucher reads the tag's configured `permission` from `MiraTags/tags.yml`
- if that field is blank, the fallback is `miratags.tag.<tagid>`
- redeeming the voucher permanently adds that permission node to the player's LuckPerms user
- the player can then use/equip the tag through MiraTags normally


## v0.1.13 voucher presentation and tag grants

Voucher presentation is standardized:

- default voucher material: `PAPER`
- kits: `ENDER_CHEST`
- fly: `FEATHER`
- airdrop: `REDSTONE_TORCH`
- pinata: `SOUL_TORCH`
- ranks: `BOOK`
- tags: `FLOWER_BANNER_PATTERN`

Voucher lore is exactly two lines:

- `This voucher grants <reward>`
- `Right Click to receive`

Voucher items no longer receive MiraItems' normal Owner/Date provenance lore.

Tag vouchers now grant the tag's configured permission node directly to the player. MiraTags LuckPerms backing groups (`miratag_*`) are explicitly excluded from rank voucher discovery, so tags are never presented as ranks.


## v0.1.13 voucher interaction reliability

Every generated MiraItems voucher now redeems through the same reliable interaction path used by MiraRename:

- right-clicking **in air** redeems the voucher
- right-clicking a block also redeems it
- another plugin cancelling the underlying interaction first no longer prevents voucher redemption
- the actual interacted hand/item is used instead of re-reading the main hand
- main-hand and off-hand vouchers are both supported and the correct hand is consumed


## v0.1.13 dynamic vouchers

MiraItems can now generate voucher MiraItems from the live server configuration instead of requiring every voucher to be hard-coded.

Generated voucher families include:

- **LuckPerms rank vouchers** for non-staff groups. Staff-style names such as owner/admin/moderator/helper/staff/developer/manager/builder/support are excluded through the configurable deny list.
- Rank redemption uses LuckPerms group weights as the progression ladder. A player cannot redeem a rank they already have or one at/below their current non-staff weighted rank.
- **Jelly Legs voucher** granting the configured permission for `/jellylegs`.
- **Home Upgrade I/II/III**. The vouchers are sequential and create three EssentialsX multihome tiers above the normal home baseline.
- **One-use kit vouchers** generated from every kit discovered through MiraKits/Essentials.
- **Permanent Fly voucher** granting the command's live Bukkit permission when available, with `essentials.fly` as the configured fallback.
- **Tag vouchers** generated from enabled, non-default MiraTags entries and redeemed through MiraTags' own ownership/grant API.
- **Airdrop Call voucher**, rejected while an airdrop is inbound/active.
- **Pinata Call voucher**, rejected while a Pinata is active/counting down.

The generated vouchers are ordinary registered MiraItem definitions, so administrators issue them through the same `/mitem give` flow and tab completion used by other MiraItems.


MiraItems is the scarce tracked-special-item system for the Mira Paper server suite. It issues uniquely signed custom weapons/items, enforces scarcity limits and integrity checks, and attaches custom combat or utility abilities to those issued copies.

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
