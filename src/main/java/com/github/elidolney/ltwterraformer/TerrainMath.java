package com.github.elidolney.ltwterraformer;

/**
 * Pure height-map math shared by the sculpting operations. Kept free of Minecraft types so it can be
 * verified without launching the game.
 */
final class TerrainMath {
    static final int MAX_SCULPT_STRENGTH = 8;

    private TerrainMath() {
    }

    /** Peak height, in blocks, that one Hill or Valley click adds for a brush of the given half-width. */
    static int sculptStrength(int halfWidth) {
        return Math.max(1, Math.min(MAX_SCULPT_STRENGTH, Math.round(halfWidth / 3.0F)));
    }

    /**
     * Smooth cosine falloff: full strength at the centre ({@code t == 0}) easing to nothing at the edge
     * ({@code t >= 1}).
     */
    static int falloffHeight(double t, int strength) {
        if (t >= 1.0 || strength <= 0) {
            return 0;
        }
        double clamped = Math.max(0.0, t);
        return (int) Math.round(strength * 0.5 * (1.0 + Math.cos(Math.PI * clamped)));
    }

    /** Linear height between two anchor points along one horizontal axis. */
    static int gradeHeight(int startY, int endY, int startCoordinate, int endCoordinate, int coordinate) {
        if (startCoordinate == endCoordinate) {
            return startY;
        }
        double t = (double) (coordinate - startCoordinate) / (endCoordinate - startCoordinate);
        t = Math.max(0.0, Math.min(1.0, t));
        return (int) Math.round(startY + t * (endY - startY));
    }

    /** Neighbourhood average, limited to one block of change per pass so repeated clicks stay controllable. */
    static int smoothTarget(int current, int neighbourSum, int neighbourCount) {
        if (neighbourCount <= 0) {
            return current;
        }
        int average = Math.round((float) neighbourSum / neighbourCount);
        return Math.max(current - 1, Math.min(current + 1, average));
    }

    /** Deterministic -1/0/+1 offset per column; half of all columns are left untouched. */
    static int roughenOffset(int x, int z, long salt) {
        long hash = x * 341873128712L + z * 132897987541L + salt * 6364136223846793005L;
        hash ^= hash >>> 29;
        hash *= 0x9E3779B97F4A7C15L;
        hash ^= hash >>> 32;
        int bucket = (int) (hash & 3L);
        return bucket == 0 ? -1 : bucket == 1 ? 1 : 0;
    }

    /** X coordinate of a blueprint cell after {@code quarterTurns} clockwise turns inside a sizeX-by-sizeZ footprint. */
    static int rotatedX(int x, int z, int sizeX, int sizeZ, int quarterTurns) {
        return switch (quarterTurns & 3) {
            case 1 -> sizeZ - 1 - z;
            case 2 -> sizeX - 1 - x;
            case 3 -> z;
            default -> x;
        };
    }

    /** Z coordinate of a blueprint cell after {@code quarterTurns} clockwise turns inside a sizeX-by-sizeZ footprint. */
    static int rotatedZ(int x, int z, int sizeX, int sizeZ, int quarterTurns) {
        return switch (quarterTurns & 3) {
            case 1 -> x;
            case 2 -> sizeZ - 1 - z;
            case 3 -> sizeX - 1 - x;
            default -> z;
        };
    }
}
