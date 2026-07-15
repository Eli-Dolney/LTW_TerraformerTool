# LTW Terraformer Tool

LTW Terraformer Tool is an original Minecraft 1.21.1 NeoForge mod for large-scale landscaping and survival construction in MineColonies worlds. It provides an Axiom-inspired creative terrain workflow and a separate survival-friendly builder wand.

## Compatibility

- Minecraft 1.21.1
- NeoForge 21.1.235 or newer 21.1.x
- Java 21
- No required mod dependencies
- Designed to coexist with MineColonies, Structurize, Domum Ornamentum, Iris, and Sodium

## Default controls

All keys can be changed under Minecraft's **Controls** menu.

| Key | Action |
| --- | --- |
| `V` | Cycle the held tool's mode or pattern |
| `B` | Cycle the Creative Terraformer's shape |
| `[` / `]` | Decrease/increase creative radius (1-16) |
| `Z` | Undo the most recent operation |
| Right-click | Apply the selected operation |

Sneak-right-click still cycles modes as a fallback. Sneak-right-clicking air with the Creative Terraformer increases its radius.

## Ultimate Creative Terraformer

The Ultimate Terraformer only edits blocks while its holder is in creative mode. Put a block in the offhand to select the brush material; otherwise it uses the clicked block.

- **Flatten**: creates a level circular platform, fills shallow holes, and clears above it.
- **Fill**: fills the selected sphere, cube, or cylinder.
- **Smooth**: averages neighboring terrain heights.
- **Clear**: removes blocks in the selected volume.
- **Raise**: adds one terrain layer.
- **Lower**: removes one terrain layer.
- **Paint**: recolors the nearest terrain surface without changing its shape.
- **Replace**: replaces the clicked block type throughout the selected volume.

The available volume shapes are **Sphere**, **Cube**, and **Cylinder**. A colored wireframe previews the operation, and the top-left HUD shows the current mode, shape, radius, and controls.

While held, the Ultimate Terraformer provides Minecraft's maximum normal targeting range of 64 blocks, including the standard crosshair and preview. Right-clicking beyond that range performs a server-authoritative sight-line trace up to 256 blocks, limited to chunks the game has loaded. This lets large brushes be placed from outside their edit volume.

## Survival Builder Wand

Right-click a block face to place matching material using one of seven patterns:

- **Full Face** extends all connected matching blocks on that face.
- **Row** extends a horizontal run.
- **Column** extends a vertical run.
- **Line** projects a straight line up to 16 blocks from the clicked face.
- **Stair Up** and **Stair Down** project diagonal runs. Top/bottom clicks follow the direction the player faces; side clicks follow the clicked side.
- **Crater Fill** fills holes to the clicked block's height in a 17×17 area, searching as deep as 16 blocks.

An offhand block selects the material. After that stack runs out, the wand continues through matching inventory stacks. Otherwise, it consumes inventory blocks matching the clicked block. Each placement uses one item and one durability point. Normal patterns place at most 64 blocks; Crater Fill can use up to 2,304 blocks. The cyan preview shows positions for which material is available, capped at 512 outlines for client performance.

Survival undo restores the world but deliberately does not refund consumed items, preventing duplication.

Craft the wand with one diamond and two sticks in a diagonal line.

## Commands

Normal play does not require commands, but these are useful for testing and accessibility:

```mcfunction
/ltwterraformer undo
/ltwterraformer mode <flatten|fill|smooth|clear|raise|lower|paint|replace>
/ltwterraformer shape <sphere|cube|cylinder|next>
/ltwterraformer radius <1..16>
/ltwterraformer restriction <face|row|column|line|stair_up|stair_down|hole_fill>
```

## Building

```text
gradlew.bat build
```

The mod JAR is written to `build/libs/ltwterraformer-0.3.0.jar`. Install the same JAR in the client and server `mods` folders.

## Safety

Back up important worlds before testing any world-editing mod. Creative operations are capped at 65,536 changed blocks, skip block entities and unbreakable blocks, respect the world border and interaction checks, and emit NeoForge break/place events so protection mods can cancel changes. One undo operation per player is kept in server memory.

## Inspiration and license

The interaction design is inspired by [Axiom](https://axiom.moulberry.com/) and [Construction Wand](https://www.curseforge.com/minecraft/mc-mods/construction-wand). This repository is an original implementation and contains none of their code or assets.

LTW Terraformer Tool is available under the [MIT License](LICENSE).
