package com.github.elidolney.ltwterraformer;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

final class WandMaterialSource {
    private final BlockItem blockItem;
    private final boolean offhandSelected;

    private WandMaterialSource(BlockItem blockItem, boolean offhandSelected) {
        this.blockItem = blockItem;
        this.offhandSelected = offhandSelected;
    }

    @Nullable
    static WandMaterialSource select(Player player, BlockState target) {
        ItemStack offhand = player.getOffhandItem();
        if (offhand.getItem() instanceof BlockItem blockItem) {
            return new WandMaterialSource(blockItem, true);
        }
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() == target.getBlock()) {
                return new WandMaterialSource(blockItem, false);
            }
        }
        return null;
    }

    BlockItem blockItem() {
        return blockItem;
    }

    boolean available(Player player) {
        return player.getAbilities().instabuild || !currentStack(player).isEmpty();
    }

    int availableCount(Player player) {
        if (player.getAbilities().instabuild) {
            return WandPlacementPlanner.MAX_HOLE_FILL_BLOCKS;
        }
        int count = 0;
        if (offhandSelected) {
            ItemStack offhand = player.getOffhandItem();
            if (offhand.getItem() == blockItem) {
                count += offhand.getCount();
            }
        }
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() == blockItem) {
                count += stack.getCount();
            }
        }
        return Math.min(count, WandPlacementPlanner.MAX_HOLE_FILL_BLOCKS);
    }

    ItemStack currentStack(Player player) {
        if (offhandSelected) {
            ItemStack offhand = player.getOffhandItem();
            if (offhand.getItem() == blockItem && !offhand.isEmpty()) {
                return offhand;
            }
        }
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() == blockItem && !stack.isEmpty()) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    void consume(Player player) {
        if (!player.getAbilities().instabuild) {
            currentStack(player).shrink(1);
        }
    }
}
