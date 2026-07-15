# Changelog

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
