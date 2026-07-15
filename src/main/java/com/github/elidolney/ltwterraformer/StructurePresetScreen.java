package com.github.elidolney.ltwterraformer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class StructurePresetScreen extends Screen {
    private static final int PANEL_WIDTH = 360;
    private static final int ROWS_PER_PAGE = 6;
    private EditBox nameBox;
    private int page;

    public StructurePresetScreen() {
        super(Component.translatable("screen.ltwterraformer.structure_library"));
    }

    @Override
    protected void init() {
        int left = (width - PANEL_WIDTH) / 2;
        int top = Math.max(20, (height - 220) / 2);
        nameBox = new EditBox(font, left + 14, top + 38, 230, 20,
                Component.translatable("screen.ltwterraformer.structure_name"));
        nameBox.setMaxLength(48);
        nameBox.setHint(Component.translatable("screen.ltwterraformer.structure_name_hint"));
        addRenderableWidget(nameBox);
        addRenderableWidget(Button.builder(Component.translatable("screen.ltwterraformer.save"), button -> save())
                .bounds(left + 250, top + 38, 96, 20).build());

        List<String> names = names();
        int maxPage = names.isEmpty() ? 0 : (names.size() - 1) / ROWS_PER_PAGE;
        page = Math.clamp(page, 0, maxPage);
        int start = page * ROWS_PER_PAGE;
        for (int i = start; i < Math.min(names.size(), start + ROWS_PER_PAGE); i++) {
            String name = names.get(i);
            int rowY = top + 70 + (i - start) * 23;
            addRenderableWidget(Button.builder(Component.literal(StructureLibrary.displayName(name)), button -> load(name))
                    .bounds(left + 14, rowY, 238, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("screen.ltwterraformer.delete"), button -> delete(name))
                    .bounds(left + 258, rowY, 88, 20).build());
        }
        Button previous = Button.builder(Component.literal("<"), button -> changePage(-1))
                .bounds(left + 126, top + 204, 36, 20).build();
        previous.active = page > 0;
        addRenderableWidget(previous);
        Button next = Button.builder(Component.literal(">"), button -> changePage(1))
                .bounds(left + 198, top + 204, 36, 20).build();
        next.active = page < maxPage;
        addRenderableWidget(next);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int left = (width - PANEL_WIDTH) / 2;
        int top = Math.max(20, (height - 220) / 2);
        graphics.fill(left, top, left + PANEL_WIDTH, top + 232, 0xDC10161D);
        graphics.renderOutline(left, top, PANEL_WIDTH, 232, 0xFF3CCFEA);
        graphics.drawCenteredString(font, title, width / 2, top + 12, 0xFFF4F8FA);
        String active = ToolData.activeStructure(heldEditor());
        graphics.drawString(font, Component.translatable("screen.ltwterraformer.active", active.isEmpty()
                ? Component.translatable("screen.ltwterraformer.none") : StructureLibrary.displayName(active)),
                left + 14, top + 26, 0xFFA8BAC4, false);
        if (names().isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("screen.ltwterraformer.empty"),
                    width / 2, top + 92, 0xFF9EABB3);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void save() {
        String name = StructureLibrary.normalizeName(nameBox.getValue());
        if (!name.isEmpty() && send("ltwterraformer structure save " + name)) {
            onClose();
        }
    }

    private void load(String name) {
        if (send("ltwterraformer structure load " + name)) {
            onClose();
        }
    }

    private void delete(String name) {
        if (send("ltwterraformer structure delete " + name)) {
            onClose();
        }
    }

    private void changePage(int amount) {
        page += amount;
        rebuildWidgets();
    }

    private boolean send(String command) {
        if (minecraft == null || minecraft.player == null || minecraft.player.connection == null) {
            return false;
        }
        minecraft.player.connection.sendCommand(command);
        return true;
    }

    private List<String> names() {
        return ToolData.structureNames(heldEditor());
    }

    private ItemStack heldEditor() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return ItemStack.EMPTY;
        }
        ItemStack main = client.player.getMainHandItem();
        if (main.is(ModItems.STRUCTURE_EDITOR.get())) {
            return main;
        }
        ItemStack offhand = client.player.getOffhandItem();
        return offhand.is(ModItems.STRUCTURE_EDITOR.get()) ? offhand : ItemStack.EMPTY;
    }
}
