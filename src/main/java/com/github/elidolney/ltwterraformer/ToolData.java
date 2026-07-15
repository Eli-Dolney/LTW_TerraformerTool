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

public final class ToolData {
    private static final String MODE = "TerraformMode";
    private static final String RADIUS = "BrushRadius";
    private static final String SHAPE = "BrushShape";
    private static final String RESTRICTION = "WandRestriction";
    private static final String EDITOR_MODE = "StructureEditorMode";
    private static final String CORNER_ONE = "StructureCornerOne";
    private static final String CORNER_TWO = "StructureCornerTwo";
    private static final String SELECTION_DIMENSION = "StructureSelectionDimension";
    private static final String ACTIVE_STRUCTURE = "ActiveStructure";
    private static final String ACTIVE_SIZE_X = "ActiveStructureSizeX";
    private static final String ACTIVE_SIZE_Y = "ActiveStructureSizeY";
    private static final String ACTIVE_SIZE_Z = "ActiveStructureSizeZ";
    private static final String STRUCTURE_NAMES = "StructureNames";

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
        write(stack, tag);
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

    public static void setCornerOne(ItemStack stack, BlockPos pos, String dimension) {
        CompoundTag tag = tag(stack);
        tag.putLong(CORNER_ONE, pos.asLong());
        tag.remove(CORNER_TWO);
        tag.putString(SELECTION_DIMENSION, dimension);
        write(stack, tag);
    }

    public static void setCornerTwo(ItemStack stack, BlockPos pos) {
        CompoundTag tag = tag(stack);
        tag.putLong(CORNER_TWO, pos.asLong());
        write(stack, tag);
    }

    public static String selectionDimension(ItemStack stack) {
        return tag(stack).getString(SELECTION_DIMENSION);
    }

    public static void clearSelection(ItemStack stack) {
        CompoundTag tag = tag(stack);
        tag.remove(CORNER_ONE);
        tag.remove(CORNER_TWO);
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
        write(stack, tag);
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

    private static void setStructureNames(ItemStack stack, List<String> names) {
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
