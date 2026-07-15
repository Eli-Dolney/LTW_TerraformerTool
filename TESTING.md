# MineColonies World Test Checklist

Use a copy of your world for the first test. Automated tests cover operations and material rules, but terrain brush feel still needs tuning in a real colony.

## Install

1. Exit Minecraft and CurseForge.
2. Back up the target world.
3. Remove every older `ltwterraformer-*.jar` from the profile's `mods` folder.
4. Copy `build/libs/ltwterraformer-0.3.0.jar` into that folder.
5. Launch the **Minecolonies Custom** profile (Minecraft 1.21.1, NeoForge 21.1.235).
6. Confirm **LTW Terraformer Tool 0.3.0** appears on the Mods screen.

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
10. Test inside an allowed and denied MineColonies claim and report unexpected protection behavior.

## Report useful results

Include:

- tool mode/pattern, shape, and radius;
- target and offhand block types;
- expected and actual behavior;
- before/after screenshots;
- `logs/latest.log` if an error occurs;
- whether the area was in a MineColonies claim or contained a Structurize/MineColonies block.
