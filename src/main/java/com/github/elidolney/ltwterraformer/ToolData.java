package com.github.elidolney.ltwterraformer;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class ToolData {
    private static final String MODE = "TerraformMode";
    private static final String RADIUS = "BrushRadius";
    private static final String SHAPE = "BrushShape";
    private static final String RESTRICTION = "WandRestriction";

    private ToolData() {
    }

    public static TerraformMode mode(ItemStack stack) {
        return TerraformMode.fromId(tag(stack).getString(MODE));
    }

    public static void setMode(ItemStack stack, TerraformMode mode) {
        CompoundTag tag = tag(stack);
        tag.putString(MODE, mode.id());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static int radius(ItemStack stack) {
        int value = tag(stack).getInt(RADIUS);
        return value == 0 ? 3 : Math.clamp(value, 1, TerrainOperations.MAX_RADIUS);
    }

    public static void setRadius(ItemStack stack, int radius) {
        CompoundTag tag = tag(stack);
        tag.putInt(RADIUS, Math.clamp(radius, 1, TerrainOperations.MAX_RADIUS));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static BrushShape shape(ItemStack stack) {
        return BrushShape.fromId(tag(stack).getString(SHAPE));
    }

    public static void setShape(ItemStack stack, BrushShape shape) {
        CompoundTag tag = tag(stack);
        tag.putString(SHAPE, shape.id());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static WandRestriction restriction(ItemStack stack) {
        return WandRestriction.fromId(tag(stack).getString(RESTRICTION));
    }

    public static void setRestriction(ItemStack stack, WandRestriction restriction) {
        CompoundTag tag = tag(stack);
        tag.putString(RESTRICTION, restriction.id());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private static CompoundTag tag(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }
}
