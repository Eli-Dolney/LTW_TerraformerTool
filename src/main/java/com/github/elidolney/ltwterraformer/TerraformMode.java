package com.github.elidolney.ltwterraformer;

import net.minecraft.network.chat.Component;

public enum TerraformMode {
    FLATTEN("flatten"),
    FILL("fill"),
    SMOOTH("smooth"),
    CLEAR("clear"),
    RAISE("raise"),
    LOWER("lower"),
    PAINT("paint"),
    REPLACE("replace"),
    HILL("hill"),
    VALLEY("valley"),
    ROUGHEN("roughen"),
    GRADE("grade"),
    WALLS("walls");

    private final String id;

    TerraformMode(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public Component displayName() {
        return Component.translatable("mode.ltwterraformer." + id);
    }

    public TerraformMode next() {
        TerraformMode[] modes = values();
        return modes[(ordinal() + 1) % modes.length];
    }

    public TerraformMode previous() {
        TerraformMode[] modes = values();
        return modes[(ordinal() + modes.length - 1) % modes.length];
    }

    /** Modes that only make sense on a two-corner selection. */
    public boolean needsSelection() {
        return this == GRADE || this == WALLS;
    }

    /** Modes that edit a 3D volume rather than the terrain surface. */
    public boolean editsVolume() {
        return this == FILL || this == CLEAR || this == REPLACE || this == WALLS;
    }

    public static TerraformMode fromId(String id) {
        for (TerraformMode mode : values()) {
            if (mode.id.equals(id)) {
                return mode;
            }
        }
        return FLATTEN;
    }
}
