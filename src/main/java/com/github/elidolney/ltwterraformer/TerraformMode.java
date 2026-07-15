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
    REPLACE("replace");

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

    public static TerraformMode fromId(String id) {
        for (TerraformMode mode : values()) {
            if (mode.id.equals(id)) {
                return mode;
            }
        }
        return FLATTEN;
    }
}
