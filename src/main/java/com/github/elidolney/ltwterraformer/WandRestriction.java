package com.github.elidolney.ltwterraformer;

import net.minecraft.network.chat.Component;

public enum WandRestriction {
    FACE("face"),
    ROW("row"),
    COLUMN("column"),
    LINE("line"),
    STAIR_UP("stair_up"),
    STAIR_DOWN("stair_down"),
    HOLE_FILL("hole_fill");

    private final String id;

    WandRestriction(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public Component displayName() {
        return Component.translatable("restriction.ltwterraformer." + id);
    }

    public WandRestriction next() {
        WandRestriction[] restrictions = values();
        return restrictions[(ordinal() + 1) % restrictions.length];
    }

    public static WandRestriction fromId(String id) {
        for (WandRestriction restriction : values()) {
            if (restriction.id.equals(id)) {
                return restriction;
            }
        }
        return FACE;
    }
}
