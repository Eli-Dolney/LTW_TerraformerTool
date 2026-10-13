# Changelog

## 0.6.0

- Blueprints now live in one shared folder, `<game folder>/ltwterraformer/blueprints/`, so a build saved in one world can be pasted in any other. Older per-world presets still load.
- The library is now a Blueprint Book: the list on the left, and on the right the selected blueprint's size, block total and a material list with item icons.
- Vanilla structure-block `.nbt` files dropped into the blueprint folder can be loaded and pasted.
- Added paste rotation in 90° steps (`R`, or the Rotate button) with a matching preview box.
- Added an Air toggle: Clear makes an exact copy, Keep leaves existing blocks where the blueprint is empty.
- The Structure Editor now works in survival: pasting uses blocks from your inventory, only fills open space, never copies container contents, and lists what is missing if you are short.
- Raised the blueprint size limit from 65,536 to 524,288 blocks and switched saves to a compact palette format.
- Added a crafting recipe for the Structure Editor (diamond, book, stick on a diagonal).

## 0.5.0

- Raised the creative brush radius limit from 16 to 64, so a Cube brush can level up to 129×129 blocks in one click.
- Surface modes (Flatten, Smooth, Raise, Lower, Paint) now follow the brush shape: Cube works on a square patch, Sphere and Cylinder on a disc.
- Added the **Selection** shape: click two corners (up to 128×128), then apply any mode to the whole area. `[` and `]` add height to the box and `X` starts a new selection.
- Added sculpting modes **Hill**, **Valley**, and **Roughen**, plus selection-only **Grade** (even slope between the two corners) and **Walls** (hollow perimeter).
- Large edits use a 524,288-block budget per click and report when the budget is reached so the click can be repeated.
- Undo now keeps the last 10 operations per player instead of one.
- Added Builder Wand **Box Fill** and **Walls** two-corner patterns (up to 4,096 blocks) with adjustable extra height.
- Raised the Full Face / Row / Column limit from 64 to 256 blocks per click.
- Holding sneak makes `[` / `]` step by 5 and makes `V` cycle backwards.

## 0.3.0

- Added survival Crater Fill: a 17×17, 16-block-deep ground repair pattern using up to 2,304 blocks.
- Offhand-selected material now continues into matching inventory stacks.
- Increased Builder Wand durability to 4,096 and made placement respect remaining durability.
- Made top/bottom-face Stair Up and Stair Down follow the player's facing direction.
- Added the maximum normal Minecraft block reach of 64 blocks while holding the Ultimate Terraformer.
- Added remote creative sight-line edits up to 256 blocks for loaded terrain beyond normal targeting range.
- Expanded automated tests for reach, directional stairs, crater planning, inventory continuation, durability, and undo.

## 0.2.0

- Added original custom textures for both tools.
- Expanded the Survival Builder Wand from three to six patterns: Full Face, Row, Column, Line, Stair Up, and Stair Down.
- Expanded the Ultimate Creative Terraformer to eight modes: Flatten, Fill, Smooth, Clear, Raise, Lower, Paint, and Replace.
- Added Sphere, Cube, and Cylinder volume shapes.
- Increased the creative radius range to 1-16.
- Added rebindable mode, shape, radius, and undo keys.
- Added server-synchronized tool commands and clearer feedback when an operation makes no changes.
- Improved terrain surface detection around roofs and overhangs.
- Expanded automated tests for operations, shapes, patterns, consumption, protection hooks, and undo.

## 0.1.0

- Initial testing build with four creative modes and three survival patterns.
