package com.github.elidolney.ltwterraformer;

import net.minecraft.network.chat.Component;

public enum StructureEditorMode {
    SELECT("select"),
    PASTE("paste");

    private final String id;

    StructureEditorMode(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public Component displayName() {
        return Component.translatable("structure_mode.ltwterraformer." + id);
    }

    public StructureEditorMode next() {
        return this == SELECT ? PASTE : SELECT;
    }

    public static StructureEditorMode fromId(String id) {
        for (StructureEditorMode mode : values()) {
            if (mode.id.equals(id)) {
                return mode;
            }
        }
        return SELECT;
    }
}
