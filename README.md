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
| `V` | Cycle the Terraformer mode or Builder Wand pattern (hold sneak to cycle backwards) |
| `B` | Cycle the Creative Terraformer's shape |
| `[` / `]` | Decrease/increase creative radius, add height to a Selection or two-corner wand build, or lower/raise the Structure Editor ceiling (hold sneak to step by 5) |
| `X` | Clear the Terraformer selection or cancel the wand's first corner |
| `G` | Open the Blueprint Book |
| `R` | Rotate the blueprint being pasted by 90° |
| `Z` | Undo; press again to step back through the last 10 operations |
| Right-click | Apply the held tool's operation |

Sneak-right-click cycles Terraformer modes, Builder Wand patterns, or the Structure Editor's Select/Paste mode as a fallback.

## Structure Editor and blueprints

The **LTW Structure Editor** copies builds and pastes them anywhere, in any world.

### Copy

1. In **Select** mode, right-click the first starting corner.
2. Right-click the opposite corner of the footprint. This sets only the selection's length and width.
3. Right-click any block at the height you want. Only this third point's Y level is used, so clicking a roof peak will not move the footprint. The structure is copied after this click.
4. The HUD always shows which of the three points comes next. Afterward, `[` and `]` remain available for one-block ceiling adjustments.

A selection can contain at most 524,288 blocks including air (for example 80×80×80). Exact block states are preserved, and block-entity data is copied for chests, furnaces, signs, banners, beds, and modded block entities, including container inventories.

### Blueprint Book

Press `G` to open the Blueprint Book.

- Type a name and press **Save Copy** to store the current selection as a blueprint.
- Click a blueprint to load it. The right side shows its size, total block count, and every block it needs with counts. In survival each line shows how many you carry against how many are needed, in green or red.
- **Rotate** turns the paste in 90° steps (also `R` while holding the tool). The preview box follows.
- **Air: Clear** pastes an exact copy, removing whatever is in the blueprint's empty spaces. **Air: Keep** leaves existing blocks there, which is what you want when dropping a house onto terrain or building over something.
- `x` deletes a blueprint.

### Blueprint folder

Blueprints are plain `.nbt` files in `ltwterraformer/blueprints/` inside your Minecraft game folder (the profile folder that contains `mods` and `saves`). **Open Blueprint Folder** in the book opens it. Every world on the installation shares this folder, so you can:

- copy a castle in one world and paste it in another;
- back up or share blueprints by copying the files;
- drop in `.nbt` files saved by vanilla structure blocks.

File names are tidied to lowercase letters, numbers, `_` and `-` the next time the book opens. On a dedicated server the folder is on the server, and the Open button shows the folder on your own computer instead.

### Paste

Press `V` or sneak-right-click to enter **Paste** mode, aim at a block face to preview the destination, then right-click. Press `Z` to undo. Solid supports are placed first so torches, doors, beds, and other attached or multi-block pieces remain intact.

### Survival

In survival the editor can copy freely, but pasting is paid for:

- every block comes out of your inventory, and nothing is placed unless you carry everything needed;
- it only fills open space and never removes or replaces existing blocks;
- container contents and other block-entity data are not copied;
- blocks with no item form, such as water, are skipped;
- undo removes the blocks without refunding them, matching the Builder Wand.

Craft the editor with a diamond, a book, and a stick in a diagonal line.

## Ultimate Creative Terraformer

The Ultimate Terraformer only edits blocks while its holder is in creative mode. Put a block in the offhand to select the brush material; otherwise it uses the clicked block.

### Modes

- **Flatten** levels the footprint to the clicked block's height: it clears what is above and fills holes below.
- **Fill** fills the selected sphere, cube, cylinder, or selection box.
- **Smooth** averages neighboring terrain heights, one block per click.
- **Clear** removes blocks in the selected volume.
- **Raise** and **Lower** add or remove one terrain layer.
- **Paint** recolors the nearest terrain surface without changing its shape.
- **Replace** replaces the clicked block type throughout the selected volume.
- **Hill** and **Valley** sculpt: each click pushes the ground up or down, strongest in the middle and fading to nothing at the edge. Bigger brushes move more (1 block at radius 1–4, up to 8 blocks at radius 23+). Click repeatedly to build up.
- **Roughen** nudges about half of the columns up or down one block so flat ground stops looking machine-made.
- **Grade** (Selection only) builds an even slope from the first corner's height to the second corner's height — ramps, roads, and terraces.
- **Walls** (Selection only) builds the four outer walls of the selection box and leaves the inside open.

### Shapes and large areas

`B` cycles Sphere, Cube, Cylinder, and Selection. Radius goes from 1 to 64.

- For surface modes, Sphere and Cylinder cover a disc and **Cube covers a square**. Flatten with Cube at radius 50 levels 101×101 blocks around the clicked block in one click.
- **Selection** works on an exact area instead of a brush:
  1. Right-click the first corner.
  2. Right-click the opposite corner. Each side can be up to 128 blocks, so 100×100 fits.
  3. Right-click again to apply the current mode to the whole area. For Flatten, the block you click sets the target height.
  4. `[` and `]` add height to the box for Fill, Clear, Replace, and Walls. `X` starts a new selection.

Selection Flatten and Grade clear up to 48 blocks above the target height and fill up to 48 blocks below it. Selections and brushes wider than radius 16 may change up to 524,288 blocks per click; if a click reaches that budget the tool says so, and clicking again continues where it stopped. These large edits skip neighbor block updates for speed, so water or sand at the edge may not move until something touches it.

A colored wireframe previews the operation, and the top-left HUD shows the current mode, shape, radius or selection size, and controls. The tool provides Minecraft's maximum normal targeting range of 64 blocks and a server-authoritative sight-line trace up to 256 blocks, limited to loaded chunks.

## Survival Builder Wand

Right-click a block face to place matching material using Full Face, Row, Column, Line, Stair Up, Stair Down, or Crater Fill patterns. Full Face, Row, and Column place up to 256 blocks per click.

**Box Fill** and **Walls** build between two corners: right-click the first corner, then the opposite corner (up to 64 blocks per side, 4,096 blocks per build). The corners are the open spaces against the faces you click. `[` and `]` add extra height first, so two clicks on the ground can raise a full room's walls; `X` cancels the first corner. An offhand block selects the material, then matching inventory stacks are used. Each placement consumes one item and one durability point.

Survival undo restores the world but deliberately does not refund consumed items, preventing duplication. Craft the wand with one diamond and two sticks in a diagonal line.

## Commands

Normal play does not require commands, but these are useful for testing and accessibility:

```mcfunction
/ltwterraformer undo
/ltwterraformer mode <flatten|fill|smooth|clear|raise|lower|paint|replace|hill|valley|roughen|grade|walls>
/ltwterraformer shape <sphere|cube|cylinder|selection|next>
/ltwterraformer radius <1..64>
/ltwterraformer area clear
/ltwterraformer area height <0..255>
/ltwterraformer restriction <face|row|column|line|stair_up|stair_down|hole_fill|box|walls>
/ltwterraformer structure save <name>
/ltwterraformer structure load <name>
/ltwterraformer structure delete <name>
/ltwterraformer structure list
/ltwterraformer structure adjust_height <-1|1>
/ltwterraformer structure refresh
/ltwterraformer structure rotate
/ltwterraformer structure air
/ltwterraformer structure clear
```

## Building and testing

```text
gradlew.bat build
gradlew.bat runGameTestServer
```

The mod JAR is written to `build/libs/ltwterraformer-0.6.0.jar`. Install the same JAR in the client and server `mods` folders.

## Safety

Back up important worlds before testing any world-editing mod. Creative brush operations are capped at 65,536 changed blocks (524,288 for selections and brushes wider than radius 16), require loaded chunks, skip block entities and unbreakable blocks, respect the world border and interaction checks, and emit NeoForge break/place events so protection mods can cancel changes. The last 10 operations per player are kept in server memory until the server restarts.

## Inspiration and license

The interaction design is inspired by [Axiom](https://axiom.moulberry.com/) and [Construction Wand](https://www.curseforge.com/minecraft/mc-mods/construction-wand). This repository is an original implementation and contains none of their code or assets.

LTW Terraformer Tool is available under the [MIT License](LICENSE).
