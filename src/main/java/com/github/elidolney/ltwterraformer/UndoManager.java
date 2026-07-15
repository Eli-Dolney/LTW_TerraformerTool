package com.github.elidolney.ltwterraformer;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Clearable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class UndoManager {
    private static final Map<UUID, Operation> LAST_OPERATION = new ConcurrentHashMap<>();

    private UndoManager() {
    }

    public static void remember(ServerPlayer player, ServerLevel level, List<Change> changes) {
        LAST_OPERATION.put(player.getUUID(), new Operation(level.dimension(), List.copyOf(changes)));
    }

    public static int undo(ServerPlayer player) {
        Operation operation = LAST_OPERATION.remove(player.getUUID());
        if (operation == null || !operation.dimension().equals(player.level().dimension())) {
            return 0;
        }
        ServerLevel level = player.serverLevel();
        int restored = 0;
        List<Change> changes = operation.changes();
        for (int i = changes.size() - 1; i >= 0; i--) {
            Change change = changes.get(i);
            Clearable.tryClear(level.getBlockEntity(change.pos()));
            level.setBlock(change.pos(), change.oldState(), 2);
            if (change.oldBlockEntityData() != null) {
                BlockEntity blockEntity = level.getBlockEntity(change.pos());
                if (blockEntity != null) {
                    CompoundTag data = change.oldBlockEntityData().copy();
                    data.putInt("x", change.pos().getX());
                    data.putInt("y", change.pos().getY());
                    data.putInt("z", change.pos().getZ());
                    blockEntity.loadWithComponents(data, level.registryAccess());
                    blockEntity.setChanged();
                }
            }
            restored++;
        }
        for (int i = changes.size() - 1; i >= 0; i--) {
            Change change = changes.get(i);
            level.blockUpdated(change.pos(), change.oldState().getBlock());
        }
        return restored;
    }

    public record Change(BlockPos pos, BlockState oldState, CompoundTag oldBlockEntityData) {
        public Change(BlockPos pos, BlockState oldState) {
            this(pos, oldState, null);
        }
    }

    private record Operation(ResourceKey<Level> dimension, List<Change> changes) {
    }
}
