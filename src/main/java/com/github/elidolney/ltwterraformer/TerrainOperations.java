package com.github.elidolney.ltwterraformer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class TerrainOperations {
    public static final int MAX_RADIUS = 16;
    public static final int MAX_CHANGES = 65536;

    private TerrainOperations() {
    }

    public static int apply(UseOnContext context, TerraformMode mode, int radius) {
        if (!(context.getLevel() instanceof ServerLevel level) || !(context.getPlayer() instanceof ServerPlayer player)) {
            return 0;
        }

        BlockPos clicked = context.getClickedPos();
        BlockState target = level.getBlockState(clicked);
        BlockState brush = brushState(player, target);
        BrushShape shape = ToolData.shape(context.getItemInHand());
        List<UndoManager.Change> changes = new ArrayList<>();

        switch (mode) {
            case FLATTEN -> flatten(level, player, clicked, radius, brush, changes);
            case FILL -> fill(level, player, clicked.relative(context.getClickedFace()), radius, shape, brush, changes);
            case SMOOTH -> smooth(level, player, clicked, radius, brush, changes);
            case CLEAR -> clear(level, player, clicked.relative(context.getClickedFace()), radius, shape, changes);
            case RAISE -> raise(level, player, clicked, radius, brush, changes);
            case LOWER -> lower(level, player, clicked, radius, changes);
            case PAINT -> paint(level, player, clicked, radius, brush, changes);
            case REPLACE -> replace(level, player, clicked, radius, shape, target, brush, changes);
        }

        if (!changes.isEmpty()) {
            UndoManager.remember(player, level, changes);
        }
        return changes.size();
    }

    private static BlockState brushState(ServerPlayer player, BlockState fallback) {
        ItemStack offhand = player.getOffhandItem();
        if (offhand.getItem() instanceof BlockItem blockItem) {
            return blockItem.getBlock().defaultBlockState();
        }
        if (!fallback.isAir()) {
            return fallback;
        }
        return Blocks.DIRT.defaultBlockState();
    }

    private static void flatten(ServerLevel level, ServerPlayer player, BlockPos center, int radius,
                                BlockState brush, List<UndoManager.Change> changes) {
        int radiusSquared = radius * radius;
        for (int dx = -radius; dx <= radius && room(changes); dx++) {
            for (int dz = -radius; dz <= radius && room(changes); dz++) {
                if (dx * dx + dz * dz > radiusSquared) {
                    continue;
                }
                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                for (int y = center.getY() + radius; y > center.getY() && room(changes); y--) {
                    set(level, player, new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), changes, true, false);
                }
                for (int y = center.getY(); y >= center.getY() - radius && room(changes); y--) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (!state.isAir() && !state.canBeReplaced()) {
                        if (y == center.getY() && state.getBlock() != brush.getBlock()) {
                            set(level, player, pos, brush, changes, false, true);
                        }
                        break;
                    }
                    set(level, player, pos, brush, changes, false, false);
                }
            }
        }
    }

    private static void fill(ServerLevel level, ServerPlayer player, BlockPos center, int radius, BrushShape shape,
                             BlockState brush, List<UndoManager.Change> changes) {
        forEachShape(center, radius, shape, pos -> {
            if (room(changes)) {
                set(level, player, pos, brush, changes, false, false);
            }
        });
    }

    private static void clear(ServerLevel level, ServerPlayer player, BlockPos center, int radius, BrushShape shape,
                              List<UndoManager.Change> changes) {
        forEachShape(center, radius, shape, pos -> {
            if (room(changes)) {
                set(level, player, pos, Blocks.AIR.defaultBlockState(), changes, true, false);
            }
        });
    }

    private static void smooth(ServerLevel level, ServerPlayer player, BlockPos center, int radius,
                               BlockState brush, List<UndoManager.Change> changes) {
        Map<Long, Integer> heights = new HashMap<>();
        int scan = radius + 4;
        for (int dx = -radius - 1; dx <= radius + 1; dx++) {
            for (int dz = -radius - 1; dz <= radius + 1; dz++) {
                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                heights.put(BlockPos.asLong(x, 0, z), surface(level, x, z, center.getY(), scan));
            }
        }

        int radiusSquared = radius * radius;
        for (int dx = -radius; dx <= radius && room(changes); dx++) {
            for (int dz = -radius; dz <= radius && room(changes); dz++) {
                if (dx * dx + dz * dz > radiusSquared) {
                    continue;
                }
                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                int current = heights.get(BlockPos.asLong(x, 0, z));
                int sum = 0;
                int count = 0;
                for (int nx = -1; nx <= 1; nx++) {
                    for (int nz = -1; nz <= 1; nz++) {
                        sum += heights.get(BlockPos.asLong(x + nx, 0, z + nz));
                        count++;
                    }
                }
                int target = Math.clamp(Math.round((float) sum / count), current - 1, current + 1);
                if (target > current) {
                    for (int y = current + 1; y <= target && room(changes); y++) {
                        set(level, player, new BlockPos(x, y, z), brush, changes, false, false);
                    }
                } else if (target < current) {
                    for (int y = current; y > target && room(changes); y--) {
                        set(level, player, new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), changes, true, false);
                    }
                }
            }
        }
    }

    private static void raise(ServerLevel level, ServerPlayer player, BlockPos center, int radius,
                              BlockState brush, List<UndoManager.Change> changes) {
        forEachDisc(center, radius, column -> {
            if (!room(changes)) {
                return;
            }
            int top = surface(level, column.getX(), column.getZ(), center.getY(), radius + 8);
            set(level, player, new BlockPos(column.getX(), top + 1, column.getZ()), brush, changes, false, false);
        });
    }

    private static void lower(ServerLevel level, ServerPlayer player, BlockPos center, int radius,
                              List<UndoManager.Change> changes) {
        forEachDisc(center, radius, column -> {
            if (!room(changes)) {
                return;
            }
            int top = surface(level, column.getX(), column.getZ(), center.getY(), radius + 8);
            set(level, player, new BlockPos(column.getX(), top, column.getZ()),
                    Blocks.AIR.defaultBlockState(), changes, true, false);
        });
    }

    private static void paint(ServerLevel level, ServerPlayer player, BlockPos center, int radius,
                              BlockState brush, List<UndoManager.Change> changes) {
        forEachDisc(center, radius, column -> {
            if (!room(changes)) {
                return;
            }
            int top = surface(level, column.getX(), column.getZ(), center.getY(), radius + 8);
            set(level, player, new BlockPos(column.getX(), top, column.getZ()), brush, changes, false, true);
        });
    }

    private static void replace(ServerLevel level, ServerPlayer player, BlockPos center, int radius, BrushShape shape,
                                BlockState target, BlockState brush, List<UndoManager.Change> changes) {
        if (target.getBlock() == brush.getBlock()) {
            return;
        }
        forEachShape(center, radius, shape, pos -> {
            if (room(changes) && level.getBlockState(pos).is(target.getBlock())) {
                set(level, player, pos, brush, changes, false, true);
            }
        });
    }

    private static int surface(ServerLevel level, int x, int z, int centerY, int scan) {
        for (int distance = 0; distance <= scan; distance++) {
            int above = centerY + distance;
            if (isSurface(level, x, above, z)) {
                return above;
            }
            int below = centerY - distance;
            if (distance > 0 && isSurface(level, x, below, z)) {
                return below;
            }
        }
        return centerY;
    }

    private static boolean isSurface(ServerLevel level, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.canBeReplaced() || state.is(Blocks.BARRIER)) {
            return false;
        }

        BlockState above = level.getBlockState(pos.above());
        return above.isAir() || above.canBeReplaced();
    }

    private static void set(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState replacement,
                            List<UndoManager.Change> changes, boolean clearing, boolean replaceSolid) {
        if (!room(changes) || !level.getWorldBorder().isWithinBounds(pos) || !level.mayInteract(player, pos)
                || level.getBlockEntity(pos) != null) {
            return;
        }
        BlockState old = level.getBlockState(pos);
        if (old.equals(replacement) || old.getDestroySpeed(level, pos) < 0) {
            return;
        }
        if (clearing) {
            if (old.isAir()) {
                return;
            }
        } else if (!replaceSolid && !old.isAir() && !old.canBeReplaced()) {
            return;
        }

        boolean removesSolid = clearing || !old.isAir() && !old.canBeReplaced();
        if (removesSolid && CommonHooks.fireBlockBreak(
                level, player.gameMode.getGameModeForPlayer(), player, pos, old).isCanceled()) {
            return;
        }

        BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, pos, 3);
        if (!level.setBlock(pos, replacement, 3)) {
            return;
        }
        if (!clearing && EventHooks.onBlockPlace(player, snapshot, Direction.UP)) {
            snapshot.restore(3);
            return;
        }
        changes.add(new UndoManager.Change(pos.immutable(), old));
    }

    static boolean setCopiedBlock(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState replacement,
                                  List<UndoManager.Change> changes, boolean force) {
        if (!room(changes) || !level.getWorldBorder().isWithinBounds(pos) || !level.mayInteract(player, pos)) {
            return false;
        }
        BlockState old = level.getBlockState(pos);
        if (!force && old.equals(replacement) || old.getDestroySpeed(level, pos) < 0) {
            return false;
        }
        net.minecraft.world.level.block.entity.BlockEntity oldBlockEntity = level.getBlockEntity(pos);
        net.minecraft.nbt.CompoundTag oldBlockEntityData = oldBlockEntity == null
                ? null : oldBlockEntity.saveWithFullMetadata(level.registryAccess());
        boolean clearing = replacement.isAir();
        boolean removesSolid = clearing || !old.isAir() && !old.canBeReplaced();
        if (removesSolid && CommonHooks.fireBlockBreak(
                level, player.gameMode.getGameModeForPlayer(), player, pos, old).isCanceled()) {
            return false;
        }
        BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, pos, 3);
        net.minecraft.world.Clearable.tryClear(oldBlockEntity);
        boolean changedState = level.setBlock(pos, replacement, 2);
        if (!changedState && !force) {
            return false;
        }
        if (!clearing && EventHooks.onBlockPlace(player, snapshot, Direction.UP)) {
            snapshot.restore(3);
            return false;
        }
        changes.add(new UndoManager.Change(pos.immutable(), old, oldBlockEntityData));
        return true;
    }

    private static boolean room(List<UndoManager.Change> changes) {
        return changes.size() < MAX_CHANGES;
    }

    private static void forEachDisc(BlockPos center, int radius, Consumer<BlockPos> consumer) {
        int radiusSquared = radius * radius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz <= radiusSquared) {
                    consumer.accept(center.offset(dx, 0, dz));
                }
            }
        }
    }

    private static void forEachShape(BlockPos center, int radius, BrushShape shape, Consumer<BlockPos> consumer) {
        int radiusSquared = radius * radius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    boolean inside = switch (shape) {
                        case SPHERE -> dx * dx + dy * dy + dz * dz <= radiusSquared;
                        case CUBE -> true;
                        case CYLINDER -> dx * dx + dz * dz <= radiusSquared;
                    };
                    if (inside) {
                        consumer.accept(center.offset(dx, dy, dz));
                    }
                }
            }
        }
    }
}
