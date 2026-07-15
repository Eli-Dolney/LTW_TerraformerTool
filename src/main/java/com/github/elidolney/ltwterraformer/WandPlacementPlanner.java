package com.github.elidolney.ltwterraformer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class WandPlacementPlanner {
    static final int MAX_BLOCKS = 64;
    static final int MAX_SCANNED_BLOCKS = 4096;
    static final int MAX_PROJECTED_BLOCKS = 16;
    static final int HOLE_FILL_RADIUS = 8;
    static final int HOLE_FILL_DEPTH = 16;
    static final int MAX_HOLE_FILL_BLOCKS = 2304;
    static final int MAX_PREVIEW_BLOCKS = 512;

    private WandPlacementPlanner() {
    }

    static List<BlockPos> plan(Level level, BlockPos origin, Direction face, WandRestriction restriction,
                               Direction playerDirection) {
        if (restriction == WandRestriction.HOLE_FILL) {
            return planHoleFill(level, origin);
        }
        if (restriction == WandRestriction.LINE || restriction == WandRestriction.STAIR_UP
                || restriction == WandRestriction.STAIR_DOWN) {
            return planProjection(level, origin, face, restriction, playerDirection);
        }

        BlockState matching = level.getBlockState(origin);
        Direction[] traversal = traversalDirections(face, restriction, playerDirection);
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        List<BlockPos> targets = new ArrayList<>();
        queue.add(origin);

        while (!queue.isEmpty() && visited.size() < MAX_SCANNED_BLOCKS && targets.size() < MAX_BLOCKS) {
            BlockPos support = queue.removeFirst();
            if (!visited.add(support) || !level.getBlockState(support).is(matching.getBlock())) {
                continue;
            }

            BlockPos target = support.relative(face);
            BlockState old = level.getBlockState(target);
            if (level.getBlockEntity(target) == null && (old.isAir() || old.canBeReplaced())) {
                targets.add(target.immutable());
            }

            for (Direction direction : traversal) {
                BlockPos next = support.relative(direction);
                if (!visited.contains(next)) {
                    queue.addLast(next);
                }
            }
        }
        return List.copyOf(targets);
    }

    private static List<BlockPos> planProjection(Level level, BlockPos origin, Direction face,
                                                  WandRestriction restriction, Direction playerDirection) {
        List<BlockPos> targets = new ArrayList<>();
        boolean stairs = restriction != WandRestriction.LINE;
        boolean sideClick = face.getAxis().isHorizontal();
        Direction travel = stairs && !sideClick ? playerDirection : face;
        int verticalStep = restriction == WandRestriction.STAIR_UP ? 1 : -1;

        for (int distance = 1; distance <= MAX_PROJECTED_BLOCKS; distance++) {
            BlockPos target = origin.relative(travel, distance);
            if (stairs) {
                int verticalDistance = sideClick ? distance - 1 : distance;
                target = target.offset(0, verticalStep * verticalDistance, 0);
            }
            BlockState old = level.getBlockState(target);
            if (level.getBlockEntity(target) != null || !old.isAir() && !old.canBeReplaced()) {
                break;
            }
            targets.add(target.immutable());
        }
        return List.copyOf(targets);
    }

    private static List<BlockPos> planHoleFill(Level level, BlockPos origin) {
        List<Column> columns = new ArrayList<>();
        int radiusSquared = HOLE_FILL_RADIUS * HOLE_FILL_RADIUS;
        for (int dx = -HOLE_FILL_RADIUS; dx <= HOLE_FILL_RADIUS; dx++) {
            for (int dz = -HOLE_FILL_RADIUS; dz <= HOLE_FILL_RADIUS; dz++) {
                if (dx * dx + dz * dz > radiusSquared) {
                    continue;
                }
                BlockPos surface = new BlockPos(origin.getX() + dx, origin.getY(), origin.getZ() + dz);
                BlockState surfaceState = level.getBlockState(surface);
                if (!surfaceState.isAir() && !surfaceState.canBeReplaced()) {
                    continue;
                }

                int supportY = Integer.MIN_VALUE;
                for (int depth = 1; depth <= HOLE_FILL_DEPTH; depth++) {
                    BlockPos candidate = surface.below(depth);
                    BlockState state = level.getBlockState(candidate);
                    if (!state.isAir() && !state.canBeReplaced()) {
                        supportY = candidate.getY();
                        break;
                    }
                }
                if (supportY != Integer.MIN_VALUE) {
                    columns.add(new Column(surface, supportY, dx * dx + dz * dz));
                }
            }
        }

        columns.sort(Comparator.comparingInt(Column::distanceSquared));
        List<BlockPos> targets = new ArrayList<>();
        for (Column column : columns) {
            for (int y = column.supportY() + 1; y <= origin.getY(); y++) {
                BlockPos target = new BlockPos(column.surface().getX(), y, column.surface().getZ());
                BlockState old = level.getBlockState(target);
                if (level.getBlockEntity(target) == null && (old.isAir() || old.canBeReplaced())) {
                    targets.add(target.immutable());
                    if (targets.size() >= MAX_HOLE_FILL_BLOCKS) {
                        return List.copyOf(targets);
                    }
                }
            }
        }
        return List.copyOf(targets);
    }

    private static Direction[] traversalDirections(Direction face, WandRestriction restriction, Direction playerDirection) {
        if (restriction == WandRestriction.FACE) {
            return switch (face.getAxis()) {
                case X -> new Direction[]{Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH};
                case Y -> new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
                case Z -> new Direction[]{Direction.UP, Direction.DOWN, Direction.EAST, Direction.WEST};
            };
        }
        if (restriction == WandRestriction.COLUMN) {
            return face.getAxis().isHorizontal()
                    ? new Direction[]{Direction.UP, Direction.DOWN}
                    : new Direction[]{Direction.NORTH, Direction.SOUTH};
        }
        if (face.getAxis() == Direction.Axis.X) {
            return new Direction[]{Direction.NORTH, Direction.SOUTH};
        }
        if (face.getAxis() == Direction.Axis.Z) {
            return new Direction[]{Direction.EAST, Direction.WEST};
        }
        return playerDirection.getAxis() == Direction.Axis.X
                ? new Direction[]{Direction.NORTH, Direction.SOUTH}
                : new Direction[]{Direction.EAST, Direction.WEST};
    }

    private record Column(BlockPos surface, int supportY, int distanceSquared) {
    }
}
