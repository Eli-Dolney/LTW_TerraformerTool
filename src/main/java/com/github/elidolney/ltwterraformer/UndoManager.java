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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class UndoManager {
    public static final int MAX_HISTORY = 10;
    private static final int MAX_STORED_CHANGES = 1_500_000;
    private static final Map<UUID, Deque<Operation>> HISTORY = new ConcurrentHashMap<>();

    private UndoManager() {
    }

    public static void remember(ServerPlayer player, ServerLevel level, List<Change> changes) {
        Deque<Operation> history = HISTORY.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>());
        history.addLast(new Operation(level.dimension(), List.copyOf(changes)));
        long stored = 0;
        for (Operation operation : history) {
            stored += operation.changes().size();
        }
        while (history.size() > 1 && (history.size() > MAX_HISTORY || stored > MAX_STORED_CHANGES)) {
            stored -= history.removeFirst().changes().size();
        }
    }

    /** Number of operations the player can still undo. */
    public static int depth(ServerPlayer player) {
        Deque<Operation> history = HISTORY.get(player.getUUID());
        return history == null ? 0 : history.size();
    }

    public static int undo(ServerPlayer player) {
        Deque<Operation> history = HISTORY.get(player.getUUID());
        Operation operation = history == null ? null : history.peekLast();
        if (operation == null || !operation.dimension().equals(player.level().dimension())) {
            return 0;
        }
        history.removeLast();
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
