package com.github.elidolney.ltwterraformer;

import net.minecraft.network.chat.Component;

public enum BrushShape {
    SPHERE("sphere"),
    CUBE("cube"),
    CYLINDER("cylinder"),
    SELECTION("selection");

    private final String id;

    BrushShape(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public Component displayName() {
        return Component.translatable("shape.ltwterraformer." + id);
    }

    public BrushShape next() {
        BrushShape[] shapes = values();
        return shapes[(ordinal() + 1) % shapes.length];
    }

    public static BrushShape fromId(String id) {
        for (BrushShape shape : values()) {
            if (shape.id.equals(id)) {
                return shape;
            }
        }
        return SPHERE;
    }
}
