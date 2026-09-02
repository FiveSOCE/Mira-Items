# MiraItems

Scarce, tracked special items for the Mira Minecraft plugin ecosystem. Targets **Paper 1.21.11** and **Java 21** and requires **MiraCore 0.1.0+**.

## v0.1.0 items

### Pyro Axe
- Netherite Axe
- Name: `&4Pyro Axe`
- Lore: `&4Run For The Hills`, blank white line, owner and issue date
- Sharpness V, Fire Aspect II
- Unlimited issuance
- Damage chain: 1x, 2x, 4x, 8x and onward
- Chain resets after more than 5 seconds or when switching targets

### Excalibur
- Golden Sword
- Name: `Excalibur`
- Lore: `&6Thy Might Of King Arthur`, blank white line, owner and issue date
- Sharpness X, Infinity X
- Maximum issuance: 2
- Successful hit blinds and slows the target for 3 seconds
- Ability cooldown: 30 seconds per issued Excalibur

### Lochaber Axe
- Diamond Axe
- Name: `&aLochaber Axe`
- Lore: `&aCome Closer!`, blank white line, owner and issue date
- Sharpness X
- Maximum issuance: 5
- Applies Mining Fatigue I while held
- Every 5th successful player hit pulls the target back toward the attacker

### Empower!
- Yearn Goat Horn specifically
- Name: `&1Empower!`
- Lore: `&9Empower Thy Ally!`, blank white line, owner and issue date
- Unbreaking X
- Maximum issuance: 10
- On use: Resistance II, Speed II and Regeneration II for 30 seconds
- Cooldown: 5 minutes per issued horn

## Integrity and scarcity

Every issued copy receives an internal MiraItems item id, unique issuance UUID, owner UUID/name, issue date and signed backing metadata. The visible lore remains exactly the requested four lines and does not expose the serial.

Name, lore, material, owner/date backing and special variant are validated. If a claimed MiraItem is altered, its MiraItems backing is stripped and its special ability stops working. The issuance record remains in the scarcity ledger, so intentionally damaging a rare item does not mint a replacement slot.

MiraEnchantments runes are not valid on MiraItems. MiraItems also rejects any claimed special item that somehow contains MiraEnchantments backing.

Limits are issuance limits. Losing a limited item does not automatically replenish the supply. Use `addlimit`, `removelimit`, or the wipe-only `reset` command deliberately.

## Commands

```text
/mitem give <item>
/mitem give <player> <item>
/mitem disable <item>
/mitem enable <item>
/mitem check <item>
/mitem reset <item>
/mitem addlimit <item>
/mitem removelimit <item>
/mitem status
/mitem test
/mitem help
```

Canonical ids are `pyro_axe`, `excalibur`, `lochaber_axe`, and `empower`. Friendly aliases such as `Pyro Axe` and `Lochaber Axe` are accepted by command parsing.

`/mitem removelimit <item>` must be executed by a player holding a valid copy of that exact item. The held copy is consumed, its issuance record is removed, then the maximum is reduced by one.

`/mitem reset <item>` clears the full issuance ledger for that item. Existing copies become invalid when next scanned. This is intended for server wipes after item storage has been deleted.

Permission: `miraitems.admin` (OP by default).

## MiraCore API

MiraItems registers `MiraItemsApi` in MiraCore. Future MiraCrates, MiraKits and MiraShop integrations can issue tracked items through the API without dispatching commands.

## Data

Runtime scarcity state is stored at:

```text
plugins/MiraItems/state.yml
```

The signing secret is created automatically in `config.yml` on first boot.

## Building

```bash
gradle clean test build
```

Output:

```text
build/libs/MiraItems-0.1.0.jar
```
