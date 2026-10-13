package com.github.elidolney.ltwterraformer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

public final class ToolData {
    private static final String MODE = "TerraformMode";
    private static final String RADIUS = "BrushRadius";
    private static final String SHAPE = "BrushShape";
    private static final String RESTRICTION = "WandRestriction";
    private static final String EDITOR_MODE = "StructureEditorMode";
    private static final String CORNER_ONE = "StructureCornerOne";
    private static final String CORNER_TWO = "StructureCornerTwo";
    private static final String FOOTPRINT_CORNER = "StructureFootprintCorner";
    private static final String SELECTION_DIMENSION = "StructureSelectionDimension";
    private static final String ACTIVE_STRUCTURE = "ActiveStructure";
    private static final String ACTIVE_SIZE_X = "ActiveStructureSizeX";
    private static final String ACTIVE_SIZE_Y = "ActiveStructureSizeY";
    private static final String ACTIVE_SIZE_Z = "ActiveStructureSizeZ";
    private static final String STRUCTURE_NAMES = "StructureNames";
    private static final String ACTIVE_MATERIALS = "ActiveStructureMaterials";
    private static final String ACTIVE_BLOCK_COUNT = "ActiveStructureBlocks";
    private static final String PASTE_ROTATION = "PasteRotation";
    private static final String PASTE_SKIP_AIR = "PasteSkipAir";
    private static final String AREA_ONE = "AreaCornerOne";
    private static final String AREA_TWO = "AreaCornerTwo";
    private static final String AREA_DIMENSION = "AreaDimension";
    private static final String AREA_EXTRA_HEIGHT = "AreaExtraHeight";
    private static final String WAND_CORNER = "WandBoxCorner";
    private static final String WAND_DIMENSION = "WandBoxDimension";
    private static final String WAND_EXTRA_HEIGHT = "WandBoxExtraHeight";

    public static final int MAX_AREA_EXTRA_HEIGHT = 255;
    public static final int MAX_WAND_EXTRA_HEIGHT = 63;

    private ToolData() {
    }

    public static TerraformMode mode(ItemStack stack) {
        return TerraformMode.fromId(tag(stack).getString(MODE));
    }

    public static void setMode(ItemStack stack, TerraformMode mode) {
        CompoundTag tag = tag(stack);
        tag.putString(MODE, mode.id());
        write(stack, tag);
    }

    public static int radius(ItemStack stack) {
        int value = tag(stack).getInt(RADIUS);
        return value == 0 ? 3 : Math.clamp(value, 1, TerrainOperations.MAX_RADIUS);
    }

    public static void setRadius(ItemStack stack, int radius) {
        CompoundTag tag = tag(stack);
        tag.putInt(RADIUS, Math.clamp(radius, 1, TerrainOperations.MAX_RADIUS));
        write(stack, tag);
    }

    public static BrushShape shape(ItemStack stack) {
        return BrushShape.fromId(tag(stack).getString(SHAPE));
    }

    public static void setShape(ItemStack stack, BrushShape shape) {
        CompoundTag tag = tag(stack);
        tag.putString(SHAPE, shape.id());
        write(stack, tag);
    }

    public static WandRestriction restriction(ItemStack stack) {
        return WandRestriction.fromId(tag(stack).getString(RESTRICTION));
    }

    public static void setRestriction(ItemStack stack, WandRestriction restriction) {
        CompoundTag tag = tag(stack);
        tag.putString(RESTRICTION, restriction.id());
        tag.remove(WAND_CORNER);
        tag.remove(WAND_DIMENSION);
        write(stack, tag);
    }

    public static Optional<BlockPos> areaOne(ItemStack stack) {
        return position(stack, AREA_ONE);
    }

    public static Optional<BlockPos> areaTwo(ItemStack stack) {
        return position(stack, AREA_TWO);
    }

    public static String areaDimension(ItemStack stack) {
        return tag(stack).getString(AREA_DIMENSION);
    }

    public static void setAreaOne(ItemStack stack, BlockPos pos, String dimension) {
        CompoundTag tag = tag(stack);
        tag.putLong(AREA_ONE, pos.asLong());
        tag.remove(AREA_TWO);
        tag.remove(AREA_EXTRA_HEIGHT);
        tag.putString(AREA_DIMENSION, dimension);
        write(stack, tag);
    }

    public static void setAreaTwo(ItemStack stack, BlockPos pos) {
        CompoundTag tag = tag(stack);
        tag.putLong(AREA_TWO, pos.asLong());
        write(stack, tag);
    }

    public static int areaExtraHeight(ItemStack stack) {
        return Math.clamp(tag(stack).getInt(AREA_EXTRA_HEIGHT), 0, MAX_AREA_EXTRA_HEIGHT);
    }

    public static void setAreaExtraHeight(ItemStack stack, int extraHeight) {
        CompoundTag tag = tag(stack);
        tag.putInt(AREA_EXTRA_HEIGHT, Math.clamp(extraHeight, 0, MAX_AREA_EXTRA_HEIGHT));
        write(stack, tag);
    }

    public static void clearArea(ItemStack stack) {
        CompoundTag tag = tag(stack);
        tag.remove(AREA_ONE);
        tag.remove(AREA_TWO);
        tag.remove(AREA_DIMENSION);
        tag.remove(AREA_EXTRA_HEIGHT);
        write(stack, tag);
    }

    public static Optional<BlockPos> wandCorner(ItemStack stack) {
        return position(stack, WAND_CORNER);
    }

    public static String wandCornerDimension(ItemStack stack) {
        return tag(stack).getString(WAND_DIMENSION);
    }

    public static void setWandCorner(ItemStack stack, BlockPos pos, String dimension) {
        CompoundTag tag = tag(stack);
        tag.putLong(WAND_CORNER, pos.asLong());
        tag.putString(WAND_DIMENSION, dimension);
        write(stack, tag);
    }

    public static void clearWandCorner(ItemStack stack) {
        CompoundTag tag = tag(stack);
        tag.remove(WAND_CORNER);
        tag.remove(WAND_DIMENSION);
        write(stack, tag);
    }

    public static int wandExtraHeight(ItemStack stack) {
        return Math.clamp(tag(stack).getInt(WAND_EXTRA_HEIGHT), 0, MAX_WAND_EXTRA_HEIGHT);
    }

    public static void setWandExtraHeight(ItemStack stack, int extraHeight) {
        CompoundTag tag = tag(stack);
        tag.putInt(WAND_EXTRA_HEIGHT, Math.clamp(extraHeight, 0, MAX_WAND_EXTRA_HEIGHT));
        write(stack, tag);
    }

    private static Optional<BlockPos> position(ItemStack stack, String key) {
        CompoundTag tag = tag(stack);
        return tag.contains(key, Tag.TAG_LONG) ? Optional.of(BlockPos.of(tag.getLong(key))) : Optional.empty();
    }

    public static StructureEditorMode editorMode(ItemStack stack) {
        return StructureEditorMode.fromId(tag(stack).getString(EDITOR_MODE));
    }

    public static void setEditorMode(ItemStack stack, StructureEditorMode mode) {
        CompoundTag tag = tag(stack);
        tag.putString(EDITOR_MODE, mode.id());
        write(stack, tag);
    }

    public static Optional<BlockPos> cornerOne(ItemStack stack) {
        CompoundTag tag = tag(stack);
        return tag.contains(CORNER_ONE, Tag.TAG_LONG) ? Optional.of(BlockPos.of(tag.getLong(CORNER_ONE))) : Optional.empty();
    }

    public static Optional<BlockPos> cornerTwo(ItemStack stack) {
        CompoundTag tag = tag(stack);
        return tag.contains(CORNER_TWO, Tag.TAG_LONG) ? Optional.of(BlockPos.of(tag.getLong(CORNER_TWO))) : Optional.empty();
    }

    public static Optional<BlockPos> footprintCorner(ItemStack stack) {
        CompoundTag tag = tag(stack);
        return tag.contains(FOOTPRINT_CORNER, Tag.TAG_LONG)
                ? Optional.of(BlockPos.of(tag.getLong(FOOTPRINT_CORNER))) : Optional.empty();
    }

    public static void setCornerOne(ItemStack stack, BlockPos pos, String dimension) {
        CompoundTag tag = tag(stack);
        tag.putLong(CORNER_ONE, pos.asLong());
        tag.remove(CORNER_TWO);
        tag.remove(FOOTPRINT_CORNER);
        tag.putString(SELECTION_DIMENSION, dimension);
        write(stack, tag);
    }

    public static void setFootprintCorner(ItemStack stack, BlockPos pos) {
        CompoundTag tag = tag(stack);
        tag.putLong(FOOTPRINT_CORNER, pos.asLong());
        tag.remove(CORNER_TWO);
        write(stack, tag);
    }

    public static boolean finishThreePointSelection(ItemStack stack, int heightY) {
        Optional<BlockPos> first = cornerOne(stack);
        Optional<BlockPos> footprint = footprintCorner(stack);
        if (first.isEmpty() || footprint.isEmpty()) {
            return false;
        }
        BlockPos one = first.get();
        BlockPos two = footprint.get();
        int floorY = Math.min(heightY, Math.min(one.getY(), two.getY()));
        int ceilingY = Math.max(heightY, Math.max(one.getY(), two.getY()));
        CompoundTag tag = tag(stack);
        tag.putLong(CORNER_ONE, new BlockPos(one.getX(), floorY, one.getZ()).asLong());
        tag.putLong(CORNER_TWO, new BlockPos(two.getX(), ceilingY, two.getZ()).asLong());
        tag.remove(FOOTPRINT_CORNER);
        write(stack, tag);
        return true;
    }

    public static void setCornerTwo(ItemStack stack, BlockPos pos) {
        CompoundTag tag = tag(stack);
        tag.putLong(CORNER_TWO, pos.asLong());
        tag.remove(FOOTPRINT_CORNER);
        write(stack, tag);
    }

    public static OptionalInt adjustSelectionCeiling(ItemStack stack, int amount) {
        Optional<BlockPos> first = cornerOne(stack);
        Optional<BlockPos> second = cornerTwo(stack);
        if (first.isEmpty() || second.isEmpty()) {
            return OptionalInt.empty();
        }
        BlockPos one = first.get();
        BlockPos two = second.get();
        int floorY = Math.min(one.getY(), two.getY());
        boolean adjustFirst = one.getY() > two.getY();
        BlockPos ceiling = adjustFirst ? one : two;
        int nextY = Math.max(floorY, ceiling.getY() + amount);
        CompoundTag tag = tag(stack);
        if (adjustFirst) {
            tag.putLong(CORNER_ONE, new BlockPos(one.getX(), nextY, one.getZ()).asLong());
        } else {
            tag.putLong(CORNER_TWO, new BlockPos(two.getX(), nextY, two.getZ()).asLong());
        }
        write(stack, tag);
        return OptionalInt.of(nextY);
    }

    public static String selectionDimension(ItemStack stack) {
        return tag(stack).getString(SELECTION_DIMENSION);
    }

    public static void clearSelection(ItemStack stack) {
        CompoundTag tag = tag(stack);
        tag.remove(CORNER_ONE);
        tag.remove(CORNER_TWO);
        tag.remove(FOOTPRINT_CORNER);
        tag.remove(SELECTION_DIMENSION);
        write(stack, tag);
    }

    public static String activeStructure(ItemStack stack) {
        return tag(stack).getString(ACTIVE_STRUCTURE);
    }

    public static Vec3i activeStructureSize(ItemStack stack) {
        CompoundTag tag = tag(stack);
        return new Vec3i(
                Math.max(0, tag.getInt(ACTIVE_SIZE_X)),
                Math.max(0, tag.getInt(ACTIVE_SIZE_Y)),
                Math.max(0, tag.getInt(ACTIVE_SIZE_Z)));
    }

    public static void setActiveStructure(ItemStack stack, String name, Vec3i size) {
        CompoundTag tag = tag(stack);
        tag.putString(ACTIVE_STRUCTURE, name);
        tag.putInt(ACTIVE_SIZE_X, size.getX());
        tag.putInt(ACTIVE_SIZE_Y, size.getY());
        tag.putInt(ACTIVE_SIZE_Z, size.getZ());
        write(stack, tag);
    }

    public static void clearActiveStructure(ItemStack stack) {
        CompoundTag tag = tag(stack);
        tag.remove(ACTIVE_STRUCTURE);
        tag.remove(ACTIVE_SIZE_X);
        tag.remove(ACTIVE_SIZE_Y);
        tag.remove(ACTIVE_SIZE_Z);
        tag.remove(ACTIVE_MATERIALS);
        tag.remove(ACTIVE_BLOCK_COUNT);
        write(stack, tag);
    }

    /** Material lines for the active blueprint, each formatted as {@code item_id=count}, most-used first. */
    public static List<String> activeMaterials(ItemStack stack) {
        ListTag list = tag(stack).getList(ACTIVE_MATERIALS, Tag.TAG_STRING);
        List<String> lines = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            lines.add(list.getString(i));
        }
        return List.copyOf(lines);
    }

    public static int activeBlockCount(ItemStack stack) {
        return Math.max(0, tag(stack).getInt(ACTIVE_BLOCK_COUNT));
    }

    public static void setActiveMaterials(ItemStack stack, List<String> lines, int blockCount) {
        CompoundTag tag = tag(stack);
        ListTag list = new ListTag();
        for (String line : lines) {
            list.add(StringTag.valueOf(line));
        }
        tag.put(ACTIVE_MATERIALS, list);
        tag.putInt(ACTIVE_BLOCK_COUNT, blockCount);
        write(stack, tag);
    }

    /** Clockwise quarter turns (0-3) applied when pasting. */
    public static int pasteRotation(ItemStack stack) {
        return tag(stack).getInt(PASTE_ROTATION) & 3;
    }

    public static void setPasteRotation(ItemStack stack, int quarterTurns) {
        CompoundTag tag = tag(stack);
        tag.putInt(PASTE_ROTATION, quarterTurns & 3);
        write(stack, tag);
    }

    /** When true, air in the blueprint leaves the existing world blocks alone. */
    public static boolean pasteSkipAir(ItemStack stack) {
        return tag(stack).getBoolean(PASTE_SKIP_AIR);
    }

    public static void setPasteSkipAir(ItemStack stack, boolean skipAir) {
        CompoundTag tag = tag(stack);
        tag.putBoolean(PASTE_SKIP_AIR, skipAir);
        write(stack, tag);
    }

    /** Footprint of the active blueprint after the paste rotation is applied. */
    public static Vec3i rotatedActiveStructureSize(ItemStack stack) {
        Vec3i size = activeStructureSize(stack);
        return (pasteRotation(stack) & 1) == 0 ? size : new Vec3i(size.getZ(), size.getY(), size.getX());
    }

    public static List<String> structureNames(ItemStack stack) {
        ListTag list = tag(stack).getList(STRUCTURE_NAMES, Tag.TAG_STRING);
        List<String> names = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            String name = StructureLibrary.normalizeName(list.getString(i));
            if (!name.isEmpty() && !names.contains(name)) {
                names.add(name);
            }
        }
        names.sort(Comparator.naturalOrder());
        return List.copyOf(names);
    }

    public static void addStructureName(ItemStack stack, String requestedName) {
        String name = StructureLibrary.normalizeName(requestedName);
        if (name.isEmpty()) {
            return;
        }
        List<String> names = new ArrayList<>(structureNames(stack));
        if (!names.contains(name)) {
            names.add(name);
            names.sort(Comparator.naturalOrder());
            setStructureNames(stack, names);
        }
    }

    public static void removeStructureName(ItemStack stack, String requestedName) {
        String name = StructureLibrary.normalizeName(requestedName);
        List<String> names = new ArrayList<>(structureNames(stack));
        if (names.remove(name)) {
            setStructureNames(stack, names);
        }
    }

    public static void setStructureNames(ItemStack stack, List<String> names) {
        CompoundTag tag = tag(stack);
        ListTag list = new ListTag();
        for (String name : names) {
            list.add(StringTag.valueOf(name));
        }
        tag.put(STRUCTURE_NAMES, list);
        write(stack, tag);
    }

    private static CompoundTag tag(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    private static void write(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
}
