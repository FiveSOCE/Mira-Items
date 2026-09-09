# MythicArmors integration

MiraItems owns Dark Rider gameplay, authentication and set bonuses. MythicArmors owns the worn 3D geometry.

## Model workflow

1. Build `dark_rider.bbmodel` from the official MythicArmors armor template.
2. Preserve all required bone names and pivot points.
3. Place the model in `plugins/MythicArmors/models/`.
4. Run `/ma reload`.
5. Verify the loaded set with `/ma list` and test generated pieces with `/ma give <player> dark_rider <piece>`.
6. Copy the generated component keys into `plugins/MiraItems/config.yml`:
   - `helmet-item-model`
   - `chestplate-equipment-model`
   - `leggings-equipment-model`
   - `boots-equipment-model`
7. Set `mythic-armors.dark-rider.enabled: true` and restart/reload MiraItems.

Until enabled with valid keys, Dark Rider armor remains safely equippable as Netherite and uses Mira's normal inventory icons. No ItemDisplay entities are used.
