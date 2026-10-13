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
import java.util.Optional;
import java.util.function.Consumer;

public final class TerrainOperations {
    /** Largest brush radius; a radius-64 cube footprint covers 129 x 129 columns. */
    public static final int MAX_RADIUS = 64;
    /** Change budget for ordinary brushes and structure pastes. */
    public static final int MAX_CHANGES = 65536;
    /** Change budget for selections and brushes wider than {@link #FINE_RADIUS}. */
    public static final int MAX_AREA_CHANGES = 524288;
    /** Longest horizontal side of a two-corner selection. */
    public static final int MAX_SELECTION_SPAN = 128;
    /** Brushes up to this radius keep full neighbour updates; larger edits skip them for speed. */
    public static final int FINE_RADIUS = 16;
    /** How far above and below the reference height selection-wide operations look. */
    public static final int AREA_REACH = 48;

    private static final int NO_SURFACE = Integer.MIN_VALUE;

    private TerrainOperations() {
    }

    /** The change budget that applies to the tool's current shape and radius. */
    public static int changeLimit(ItemStack stack) {
        return ToolData.shape(stack) == BrushShape.SELECTION || ToolData.radius(stack) > FINE_RADIUS
                ? MAX_AREA_CHANGES : MAX_CHANGES;
    }

    public static int apply(UseOnContext context, TerraformMode mode, int radius) {
        if (!(context.getLevel() instanceof ServerLevel level) || !(context.getPlayer() instanceof ServerPlayer player)) {
            return 0;
        }

        ItemStack stack = context.getItemInHand();
        BlockPos clicked = context.getClickedPos();
        BlockState target = level.getBlockState(clicked);
        BlockState brush = brushState(player, target);
        BrushShape shape = ToolData.shape(stack);

        Selection selection = null;
        if (shape == BrushShape.SELECTION) {
            selection = Selection.of(stack, level);
            if (selection == null) {
                return 0;
            }
        } else if (mode.needsSelection()) {
            return 0;
        }

        boolean large = selection != null || radius > FINE_RADIUS;
        Edit edit = new Edit(level, player, new ArrayList<>(), large ? MAX_AREA_CHANGES : MAX_CHANGES, large ? 2 : 3);
        Footprint footprint = selection != null
                ? selection.footprint()
                : Footprint.brush(clicked, radius, shape != BrushShape.CUBE);
        BlockPos volumeCenter = clicked.relative(context.getClickedFace());
        int reach = selection != null ? AREA_REACH : radius;
        int scan = selection != null ? AREA_REACH : radius + 8;
        boolean skipMissing = selection != null;

        switch (mode) {
            case FLATTEN -> flatten(edit, footprint, clicked.getY(), reach, brush);
            case FILL -> fill(edit, selection, volumeCenter, radius, shape, brush);
            case SMOOTH -> smooth(edit, footprint, clicked.getY(), selection != null ? AREA_REACH : radius + 4,
                    skipMissing, brush);
            case CLEAR -> clear(edit, selection, volumeCenter, radius, shape);
            case RAISE -> raise(edit, footprint, clicked.getY(), scan, skipMissing, brush);
            case LOWER -> lower(edit, footprint, clicked.getY(), scan, skipMissing);
            case PAINT -> paint(edit, footprint, clicked.getY(), scan, skipMissing, brush);
            case REPLACE -> replace(edit, selection, clicked, radius, shape, target, brush);
            case HILL -> sculpt(edit, footprint, clicked.getY(), scan, brush, true);
            case VALLEY -> sculpt(edit, footprint, clicked.getY(), scan, brush, false);
            case ROUGHEN -> roughen(edit, footprint, clicked.getY(), scan, brush);
            case GRADE -> grade(edit, selection, brush);
            case WALLS -> walls(edit, selection, brush);
        }

        if (!edit.changes.isEmpty()) {
            UndoManager.remember(player, level, edit.changes);
        }
        return edit.changes.size();
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

    private static void flatten(Edit edit, Footprint footprint, int targetY, int reach, BlockState brush) {
        footprint.forEach((x, z, t) -> levelColumn(edit, x, z, targetY, reach, reach, brush));
    }

    /** Clears everything above {@code targetY}, then fills downward until solid ground is reached. */
    private static void levelColumn(Edit edit, int x, int z, int targetY, int up, int down, BlockState brush) {
        for (int y = targetY + up; y > targetY && edit.room(); y--) {
            edit.set(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), true, false);
        }
        for (int y = targetY; y >= targetY - down && edit.room(); y--) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = edit.level.getBlockState(pos);
            if (!state.isAir() && !state.canBeReplaced()) {
                if (y == targetY && state.getBlock() != brush.getBlock()) {
                    edit.set(pos, brush, false, true);
                }
                break;
            }
            edit.set(pos, brush, false, false);
        }
    }

    private static void fill(Edit edit, Selection selection, BlockPos center, int radius, BrushShape shape,
                             BlockState brush) {
        forEachVolume(selection, center, radius, shape, pos -> {
            if (edit.room()) {
                edit.set(pos, brush, false, false);
            }
        });
    }

    private static void clear(Edit edit, Selection selection, BlockPos center, int radius, BrushShape shape) {
        forEachVolume(selection, center, radius, shape, pos -> {
            if (edit.room()) {
                edit.set(pos, Blocks.AIR.defaultBlockState(), true, false);
            }
        });
    }

    private static void smooth(Edit edit, Footprint footprint, int referenceY, int scan, boolean skipMissing,
                               BlockState brush) {
        Map<Long, Integer> heights = new HashMap<>();
        for (int x = footprint.minX() - 1; x <= footprint.maxX() + 1; x++) {
            for (int z = footprint.minZ() - 1; z <= footprint.maxZ() + 1; z++) {
                int height = surface(edit.level, x, z, referenceY, scan);
                if (height == NO_SURFACE && !skipMissing) {
                    height = referenceY;
                }
                heights.put(BlockPos.asLong(x, 0, z), height);
            }
        }

        footprint.forEach((x, z, t) -> {
            int current = heights.get(BlockPos.asLong(x, 0, z));
            if (current == NO_SURFACE || !edit.room()) {
                return;
            }
            int sum = 0;
            int count = 0;
            for (int nx = -1; nx <= 1; nx++) {
                for (int nz = -1; nz <= 1; nz++) {
                    int neighbour = heights.get(BlockPos.asLong(x + nx, 0, z + nz));
                    sum += neighbour == NO_SURFACE ? current : neighbour;
                    count++;
                }
            }
            int target = TerrainMath.smoothTarget(current, sum, count);
            if (target > current) {
                for (int y = current + 1; y <= target && edit.room(); y++) {
                    edit.set(new BlockPos(x, y, z), brush, false, false);
                }
            } else if (target < current) {
                for (int y = current; y > target && edit.room(); y--) {
                    edit.set(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), true, false);
                }
            }
        });
    }

    private static void raise(Edit edit, Footprint footprint, int referenceY, int scan, boolean skipMissing,
                              BlockState brush) {
        footprint.forEach((x, z, t) -> {
            int top = columnTop(edit, x, z, referenceY, scan, skipMissing);
            if (top != NO_SURFACE && edit.room()) {
                edit.set(new BlockPos(x, top + 1, z), brush, false, false);
            }
        });
    }

    private static void lower(Edit edit, Footprint footprint, int referenceY, int scan, boolean skipMissing) {
        footprint.forEach((x, z, t) -> {
            int top = columnTop(edit, x, z, referenceY, scan, skipMissing);
            if (top != NO_SURFACE && edit.room()) {
                edit.set(new BlockPos(x, top, z), Blocks.AIR.defaultBlockState(), true, false);
            }
        });
    }

    private static void paint(Edit edit, Footprint footprint, int referenceY, int scan, boolean skipMissing,
                              BlockState brush) {
        footprint.forEach((x, z, t) -> {
            int top = columnTop(edit, x, z, referenceY, scan, skipMissing);
            if (top != NO_SURFACE && edit.room()) {
                edit.set(new BlockPos(x, top, z), brush, false, true);
            }
        });
    }

    private static void replace(Edit edit, Selection selection, BlockPos center, int radius, BrushShape shape,
                                BlockState target, BlockState brush) {
        if (target.getBlock() == brush.getBlock()) {
            return;
        }
        forEachVolume(selection, center, radius, shape, pos -> {
            if (edit.room() && edit.level.getBlockState(pos).is(target.getBlock())) {
                edit.set(pos, brush, false, true);
            }
        });
    }

    /** Hill and Valley: move each column by a height that eases from full strength at the centre to zero at the rim. */
    private static void sculpt(Edit edit, Footprint footprint, int referenceY, int scan, BlockState brush,
                               boolean upward) {
        int strength = TerrainMath.sculptStrength(footprint.halfWidth());
        footprint.forEach((x, z, t) -> {
            int amount = TerrainMath.falloffHeight(t, strength);
            if (amount <= 0 || !edit.room()) {
                return;
            }
            int top = surface(edit.level, x, z, referenceY, scan);
            if (top == NO_SURFACE) {
                return;
            }
            if (upward) {
                for (int y = top + 1; y <= top + amount && edit.room(); y++) {
                    edit.set(new BlockPos(x, y, z), brush, false, false);
                }
            } else {
                for (int y = top; y > top - amount && edit.room(); y--) {
                    edit.set(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), true, false);
                }
            }
        });
    }

    /** Adds natural one-block variation so large flat or graded areas stop looking machine-made. */
    private static void roughen(Edit edit, Footprint footprint, int referenceY, int scan, BlockState brush) {
        long salt = edit.level.getGameTime();
        footprint.forEach((x, z, t) -> {
            int offset = TerrainMath.roughenOffset(x, z, salt);
            if (offset == 0 || !edit.room()) {
                return;
            }
            int top = surface(edit.level, x, z, referenceY, scan);
            if (top == NO_SURFACE) {
                return;
            }
            if (offset > 0) {
                edit.set(new BlockPos(x, top + 1, z), brush, false, false);
            } else {
                edit.set(new BlockPos(x, top, z), Blocks.AIR.defaultBlockState(), true, false);
            }
        });
    }

    /** Builds an even slope from the first selection corner's height to the second corner's height. */
    private static void grade(Edit edit, Selection selection, BlockState brush) {
        if (selection == null) {
            return;
        }
        BlockPos one = selection.one();
        BlockPos two = selection.two();
        boolean alongX = Math.abs(two.getX() - one.getX()) >= Math.abs(two.getZ() - one.getZ());
        selection.footprint().forEach((x, z, t) -> {
            int targetY = alongX
                    ? TerrainMath.gradeHeight(one.getY(), two.getY(), one.getX(), two.getX(), x)
                    : TerrainMath.gradeHeight(one.getY(), two.getY(), one.getZ(), two.getZ(), z);
            levelColumn(edit, x, z, targetY, AREA_REACH, AREA_REACH, brush);
        });
    }

    /** Places the four outer walls of the selection box, leaving the inside untouched. */
    private static void walls(Edit edit, Selection selection, BlockState brush) {
        if (selection == null) {
            return;
        }
        for (int y = selection.minY(); y <= selection.maxY() && edit.room(); y++) {
            for (int x = selection.minX(); x <= selection.maxX() && edit.room(); x++) {
                for (int z = selection.minZ(); z <= selection.maxZ() && edit.room(); z++) {
                    if (x == selection.minX() || x == selection.maxX()
                            || z == selection.minZ() || z == selection.maxZ()) {
                        edit.set(new BlockPos(x, y, z), brush, false, false);
                    }
                }
            }
        }
    }

    private static int columnTop(Edit edit, int x, int z, int referenceY, int scan, boolean skipMissing) {
        int top = surface(edit.level, x, z, referenceY, scan);
        return top == NO_SURFACE && !skipMissing ? referenceY : top;
    }

    /** Nearest exposed solid block to {@code centerY} in this column, or {@link #NO_SURFACE}. */
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
        return NO_SURFACE;
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

    static boolean setCopiedBlock(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState replacement,
                                  List<UndoManager.Change> changes, boolean force) {
        if (changes.size() >= MAX_AREA_CHANGES || !level.getWorldBorder().isWithinBounds(pos) || !level.mayInteract(player, pos)) {
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

    private static void forEachVolume(Selection selection, BlockPos center, int radius, BrushShape shape,
                                      Consumer<BlockPos> consumer) {
        if (selection == null) {
            forEachShape(center, radius, shape, consumer);
            return;
        }
        for (int y = selection.minY(); y <= selection.maxY(); y++) {
            for (int x = selection.minX(); x <= selection.maxX(); x++) {
                for (int z = selection.minZ(); z <= selection.maxZ(); z++) {
                    consumer.accept(new BlockPos(x, y, z));
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
                        case CUBE, SELECTION -> true;
                        case CYLINDER -> dx * dx + dz * dz <= radiusSquared;
                    };
                    if (inside) {
                        consumer.accept(center.offset(dx, dy, dz));
                    }
                }
            }
        }
    }

    @FunctionalInterface
    private interface ColumnConsumer {
        /** @param t normalised distance from the footprint centre: 0 at the centre, 1 at the rim */
        void accept(int x, int z, double t);
    }

    /** The set of columns a surface operation covers: a disc, a square brush, or a selection rectangle. */
    private record Footprint(int minX, int minZ, int maxX, int maxZ, boolean round) {
        static Footprint brush(BlockPos center, int radius, boolean round) {
            return new Footprint(center.getX() - radius, center.getZ() - radius,
                    center.getX() + radius, center.getZ() + radius, round);
        }

        int halfWidth() {
            return Math.max(1, Math.min(maxX - minX, maxZ - minZ) / 2);
        }

        void forEach(ColumnConsumer consumer) {
            double centerX = (minX + maxX) / 2.0;
            double centerZ = (minZ + maxZ) / 2.0;
            double halfX = (maxX - minX) / 2.0;
            double halfZ = (maxZ - minZ) / 2.0;
            double radiusSquared = halfX * halfX;
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    double dx = x - centerX;
                    double dz = z - centerZ;
                    double t;
                    if (round) {
                        double distanceSquared = dx * dx + dz * dz;
                        if (distanceSquared > radiusSquared) {
                            continue;
                        }
                        t = Math.sqrt(distanceSquared) / (halfX + 0.5);
                    } else {
                        t = Math.max(Math.abs(dx) / (halfX + 0.5), Math.abs(dz) / (halfZ + 0.5));
                    }
                    consumer.accept(x, z, t);
                }
            }
        }
    }

    /** A completed two-corner selection, including any extra height added with the radius keys. */
    private record Selection(BlockPos one, BlockPos two, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        static Selection of(ItemStack stack, ServerLevel level) {
            Optional<BlockPos> first = ToolData.areaOne(stack);
            Optional<BlockPos> second = ToolData.areaTwo(stack);
            if (first.isEmpty() || second.isEmpty()
                    || !ToolData.areaDimension(stack).equals(level.dimension().location().toString())) {
                return null;
            }
            BlockPos one = first.get();
            BlockPos two = second.get();
            return new Selection(one, two,
                    Math.min(one.getX(), two.getX()), Math.min(one.getY(), two.getY()), Math.min(one.getZ(), two.getZ()),
                    Math.max(one.getX(), two.getX()),
                    Math.max(one.getY(), two.getY()) + ToolData.areaExtraHeight(stack),
                    Math.max(one.getZ(), two.getZ()));
        }

        Footprint footprint() {
            return new Footprint(minX, minZ, maxX, maxZ, false);
        }
    }

    /** One in-progress operation: where it runs, who runs it, what it has changed and how much it may change. */
    private static final class Edit {
        private final ServerLevel level;
        private final ServerPlayer player;
        private final List<UndoManager.Change> changes;
        private final int limit;
        private final int flags;

        private Edit(ServerLevel level, ServerPlayer player, List<UndoManager.Change> changes, int limit, int flags) {
            this.level = level;
            this.player = player;
            this.changes = changes;
            this.limit = limit;
            this.flags = flags;
        }

        private boolean room() {
            return changes.size() < limit;
        }

        private void set(BlockPos pos, BlockState replacement, boolean clearing, boolean replaceSolid) {
            if (!room() || !level.getWorldBorder().isWithinBounds(pos) || !level.mayInteract(player, pos)
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
            if (!level.setBlock(pos, replacement, flags)) {
                return;
            }
            if (!clearing && EventHooks.onBlockPlace(player, snapshot, Direction.UP)) {
                snapshot.restore(3);
                return;
            }
            changes.add(new UndoManager.Change(pos.immutable(), old));
        }
    }
}
