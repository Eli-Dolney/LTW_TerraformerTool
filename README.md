# LTW Terraformer Tool

LTW Terraformer Tool is an original Minecraft 1.21.1 NeoForge mod for large-scale landscaping, survival construction, and reusable build editing. It combines an Axiom-inspired creative terrain workflow, a survival-friendly builder wand, and a creative Structure Editor.

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
| `V` | Cycle the Terraformer mode or Builder Wand pattern |
| `B` | Cycle the Creative Terraformer's shape |
| `[` / `]` | Decrease/increase creative radius (1-16) |
| `G` | Open the Structure Editor's named preset library |
| `Z` | Undo the most recent operation |
| Right-click | Apply the held tool's operation |

Sneak-right-click cycles Terraformer modes, Builder Wand patterns, or the Structure Editor's Select/Paste mode as a fallback.

## Structure Editor

The creative-only **LTW Structure Editor** makes builds reusable without leaving the world:

1. In **Select** mode, right-click two opposite corners. The cyan 3D outline previews the selected cube and the second click copies it.
2. Sneak-right-click to enter **Paste** mode. Aim at a block face to preview the destination, then right-click to paste.
3. Press `G` to save the current selection with names such as `tree`, `path`, `house_1`, or `house_2`.
4. Use the library's Load and Delete buttons to manage presets. Loading automatically selects Paste mode.
5. Press `Z` after a paste to undo it.

Presets are saved per player inside the world at `ltwterraformer/structures/<player-uuid>/`. A selection can contain at most 65,536 blocks. Air is preserved so copied builds keep their exact shape. Block entities such as chests are rejected for now so paste and undo remain reliable.

## Ultimate Creative Terraformer

The Ultimate Terraformer only edits blocks while its holder is in creative mode. Put a block in the offhand to select the brush material; otherwise it uses the clicked block.

- **Flatten** creates a level circular platform, fills shallow holes, and clears above it.
- **Fill** fills the selected sphere, cube, or cylinder.
- **Smooth** averages neighboring terrain heights.
- **Clear** removes blocks in the selected volume.
- **Raise** and **Lower** add or remove one terrain layer.
- **Paint** recolors the nearest terrain surface without changing its shape.
- **Replace** replaces the clicked block type throughout the selected volume.

A colored wireframe previews the operation, and the top-left HUD shows the current mode, shape, radius, and controls. The tool provides Minecraft's maximum normal targeting range of 64 blocks and a server-authoritative sight-line trace up to 256 blocks, limited to loaded chunks.

## Survival Builder Wand

Right-click a block face to place matching material using Full Face, Row, Column, Line, Stair Up, Stair Down, or Crater Fill patterns. An offhand block selects the material, then matching inventory stacks are used. Each placement consumes one item and one durability point.

Survival undo restores the world but deliberately does not refund consumed items, preventing duplication. Craft the wand with one diamond and two sticks in a diagonal line.

## Commands

Normal play does not require commands, but these are useful for testing and accessibility:

```mcfunction
/ltwterraformer undo
/ltwterraformer mode <flatten|fill|smooth|clear|raise|lower|paint|replace>
/ltwterraformer shape <sphere|cube|cylinder|next>
/ltwterraformer radius <1..16>
/ltwterraformer restriction <face|row|column|line|stair_up|stair_down|hole_fill>
/ltwterraformer structure save <name>
/ltwterraformer structure load <name>
/ltwterraformer structure delete <name>
/ltwterraformer structure list
/ltwterraformer structure clear
```

## Building and testing

```text
gradlew.bat build
gradlew.bat runGameTestServer
```

The mod JAR is written to `build/libs/ltwterraformer-0.4.0.jar`. Install the same JAR in the client and server `mods` folders.

## Safety

Back up important worlds before testing any world-editing mod. Creative operations are capped at 65,536 changed blocks, require loaded chunks, skip block entities and unbreakable blocks, respect the world border and interaction checks, and emit NeoForge break/place events so protection mods can cancel changes. One undo operation per player is kept in server memory.

## Inspiration and license

The interaction design is inspired by [Axiom](https://axiom.moulberry.com/) and [Construction Wand](https://www.curseforge.com/minecraft/mc-mods/construction-wand). This repository is an original implementation and contains none of their code or assets.

LTW Terraformer Tool is available under the [MIT License](LICENSE).
