# MineColonies World Test Checklist

Use a copy of your world for the first test. Automated tests cover operations and material rules, but terrain brush feel still needs tuning in a real colony.

## Install

1. Exit Minecraft and CurseForge.
2. Back up the target world.
3. Remove every older `ltwterraformer-*.jar` from the profile's `mods` folder.
4. Copy `build/libs/ltwterraformer-0.6.0.jar` into that folder.
5. Launch the **Minecolonies Custom** profile (Minecraft 1.21.1, NeoForge 21.1.235).
6. Confirm **LTW Terraformer Tool 0.6.0** appears on the Mods screen.

For a dedicated server, install the same JAR on the server and every connecting client.

## Get fresh tools

```mcfunction
/give @s ltwterraformer:creative_terraformer
/give @s ltwterraformer:survival_builder_wand
```

## Confirm controls and models

1. Hold each tool and confirm its custom texture appears.
2. Confirm the top-left HUD appears.
3. Press `V` to cycle modes, `B` to cycle creative shapes, `[` and `]` to change radius, and `Z` to undo.
4. Resolve conflicts under **Options → Controls → Key Binds → LTW Terraformer**.

## Creative Terraformer scenarios

Use a disposable area, put dirt in the offhand, and start at radius 1 or 2.

1. Test **Flatten** across a shallow hole and mound.
2. Test **Fill** and **Clear** with Sphere, Cube, and Cylinder.
3. Test **Smooth**, **Raise**, **Lower**, **Paint**, and **Replace**.
4. Press `Z` after each operation and confirm the edit is restored.
5. Confirm chests and MineColonies/Structurize block entities are skipped.
6. Move at least 30 blocks away and confirm normal targeting and preview work.
7. Aim at a loaded block more than 64 blocks away and right-click; confirm the operation executes without entering the brush volume.

If right-click does nothing, verify creative mode and read the yellow action-bar explanation.

## Large-area and sculpting scenarios (0.5.0)

1. Press `B` until the shape is **Selection**. Click two corners roughly 100 blocks apart; confirm the HUD shows the size and the box preview appears.
2. In **Flatten**, click a block at the height you want and confirm the whole area levels. If the action bar says the limit was reached, click again.
3. Press `Z` twice in a row after two edits and confirm both are undone in order.
4. Try **Hill**, **Valley**, and **Roughen** with a radius of 8–12, then **Smooth** over the result.
5. Select two corners at different heights and use **Grade**; confirm an even slope.
6. Press `]` a few times and use **Walls**, then **Fill**; confirm `X` clears the selection.
7. Watch server tick time during a full 128×128 Flatten and report long freezes.

## Blueprint scenarios (0.6.0)

1. Copy a small build, press `G`, save it, and confirm it appears in the Blueprint Book with a material list.
2. Press **Open Blueprint Folder** and confirm the `.nbt` file is there. Open a different world and confirm the blueprint is listed and pastes.
3. Press `R` four times in Paste mode; confirm the preview turns and stairs, doors, and chests face the right way after each paste.
4. Paste over uneven terrain with **Air: Keep**, then with **Air: Clear**, and compare.
5. Copy and paste the largest build you have and report any long freeze.
6. Load a preset saved by an older version and confirm it still pastes.
7. In survival, load a blueprint without the materials and confirm nothing is placed and the missing blocks are listed. Then carry the materials, paste, and confirm the right counts are consumed.
8. Save a structure with a vanilla structure block, copy its file into the blueprint folder, and confirm it loads.

## Survival Builder Wand scenarios

1. Enter survival mode and give yourself the wand plus building material.
2. Cycle through **Full Face**, **Row**, **Column**, **Line**, **Stair Up**, **Stair Down**, and **Crater Fill** with `V`.
3. Confirm the cyan preview does not show more blocks than available material.
4. Confirm each placement consumes the same number of items and durability points.
5. For stairs, click the top of a block and confirm the preview travels in the direction you face. Side clicks should follow that side.
6. Make a disposable two- or three-block-deep crater. Select **Crater Fill**, put dirt in the offhand and inventory, then click intact ground beside the hole.
7. Confirm the crater fills to the clicked block's height and consumes offhand dirt before matching inventory stacks.
8. Test a crater larger than the available dirt and confirm placement stops cleanly when material runs out.
9. Press `Z` and confirm blocks are removed but items are not refunded.
10. Select **Box Fill**, click two corners, and confirm the box fills and consumes one item per block. Repeat with **Walls** after pressing `]` three times.
11. Test inside an allowed and denied MineColonies claim and report unexpected protection behavior.

## Report useful results

Include:

- tool mode/pattern, shape, and radius;
- target and offhand block types;
- expected and actual behavior;
- before/after screenshots;
- `logs/latest.log` if an error occurs;
- whether the area was in a MineColonies claim or contained a Structurize/MineColonies block.
