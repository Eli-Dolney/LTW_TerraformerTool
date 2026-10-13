package com.github.elidolney.ltwterraformer;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The blueprint book: saved builds on the left, the selected build's size, paste options and material list on
 * the right. Everything shown comes from the held Structure Editor's synced data, so it works on servers too.
 */
public final class StructurePresetScreen extends Screen {
    private static final int PANEL_WIDTH = 430;
    private static final int PANEL_HEIGHT = 246;
    private static final int ROWS_PER_PAGE = 6;
    private static final int MATERIAL_ROWS = 6;
    private static final int RIGHT_COLUMN = 224;
    private static final int RIGHT_WIDTH = 192;

    private EditBox nameBox;
    private String typedName = "";
    private int page;
    private int materialPage;
    private String shownState = "";

    public StructurePresetScreen() {
        super(Component.translatable("screen.ltwterraformer.structure_library"));
    }

    @Override
    protected void init() {
        int left = (width - PANEL_WIDTH) / 2;
        int top = Math.max(4, (height - PANEL_HEIGHT) / 2);
        ItemStack editor = heldEditor();
        shownState = stateKey(editor);

        nameBox = new EditBox(font, left + 14, top + 38, 130, 20,
                Component.translatable("screen.ltwterraformer.structure_name"));
        nameBox.setMaxLength(48);
        nameBox.setHint(Component.translatable("screen.ltwterraformer.structure_name_hint"));
        nameBox.setValue(typedName);
        nameBox.setResponder(value -> typedName = value);
        addRenderableWidget(nameBox);
        addRenderableWidget(Button.builder(Component.translatable("screen.ltwterraformer.save"), button -> save())
                .bounds(left + 148, top + 38, 62, 20).build());

        List<String> names = ToolData.structureNames(editor);
        int maxPage = names.isEmpty() ? 0 : (names.size() - 1) / ROWS_PER_PAGE;
        page = Math.clamp(page, 0, maxPage);
        int start = page * ROWS_PER_PAGE;
        String active = ToolData.activeStructure(editor);
        for (int i = start; i < Math.min(names.size(), start + ROWS_PER_PAGE); i++) {
            String name = names.get(i);
            int rowY = top + 66 + (i - start) * 23;
            Component label = Component.literal((name.equals(active) ? "> " : "") + name);
            addRenderableWidget(Button.builder(label, button -> send("ltwterraformer structure load " + name))
                    .bounds(left + 14, rowY, 172, 20).build());
            addRenderableWidget(Button.builder(Component.literal("x"), button -> send("ltwterraformer structure delete " + name))
                    .bounds(left + 190, rowY, 20, 20).build());
        }
        Button previous = Button.builder(Component.literal("<"), button -> changePage(-1))
                .bounds(left + 14, top + 212, 20, 20).build();
        previous.active = page > 0;
        addRenderableWidget(previous);
        addRenderableWidget(Button.builder(Component.translatable("screen.ltwterraformer.open_folder"), button -> openFolder())
                .bounds(left + 38, top + 212, 148, 20).build());
        Button next = Button.builder(Component.literal(">"), button -> changePage(1))
                .bounds(left + 190, top + 212, 20, 20).build();
        next.active = page < maxPage;
        addRenderableWidget(next);

        int right = left + RIGHT_COLUMN;
        addRenderableWidget(Button.builder(Component.translatable("screen.ltwterraformer.rotate",
                        ToolData.pasteRotation(editor) * 90), button -> send("ltwterraformer structure rotate"))
                .bounds(right, top + 38, 94, 20).build());
        addRenderableWidget(Button.builder(Component.translatable(ToolData.pasteSkipAir(editor)
                        ? "screen.ltwterraformer.air_keep" : "screen.ltwterraformer.air_replace"),
                        button -> send("ltwterraformer structure air"))
                .bounds(right + 98, top + 38, 94, 20).build());

        int materialCount = ToolData.activeMaterials(editor).size();
        int maxMaterialPage = materialCount == 0 ? 0 : (materialCount - 1) / MATERIAL_ROWS;
        materialPage = Math.clamp(materialPage, 0, maxMaterialPage);
        Button materialsBack = Button.builder(Component.literal("<"), button -> changeMaterialPage(-1))
                .bounds(right, top + 212, 20, 20).build();
        materialsBack.active = materialPage > 0;
        addRenderableWidget(materialsBack);
        addRenderableWidget(Button.builder(Component.translatable("screen.ltwterraformer.done"), button -> onClose())
                .bounds(right + 24, top + 212, 144, 20).build());
        Button materialsNext = Button.builder(Component.literal(">"), button -> changeMaterialPage(1))
                .bounds(right + 172, top + 212, 20, 20).build();
        materialsNext.active = materialPage < maxMaterialPage;
        addRenderableWidget(materialsNext);
    }

    @Override
    public void tick() {
        super.tick();
        // The server answers Load, Delete, Rotate and Save by updating the held tool; redraw when that arrives.
        if (!stateKey(heldEditor()).equals(shownState)) {
            rebuildWidgets();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int left = (width - PANEL_WIDTH) / 2;
        int top = Math.max(4, (height - PANEL_HEIGHT) / 2);
        int right = left + RIGHT_COLUMN;
        graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xDC10161D);
        graphics.renderOutline(left, top, PANEL_WIDTH, PANEL_HEIGHT, 0xFF3CCFEA);
        graphics.fill(left + 217, top + 24, left + 218, top + PANEL_HEIGHT - 10, 0x663CCFEA);
        graphics.drawCenteredString(font, title, width / 2, top + 10, 0xFFF4F8FA);

        ItemStack editor = heldEditor();
        List<String> names = ToolData.structureNames(editor);
        graphics.drawString(font, Component.translatable("screen.ltwterraformer.blueprints", names.size()),
                left + 14, top + 26, 0xFFA8BAC4, false);
        if (names.isEmpty()) {
            graphics.drawWordWrap(font, Component.translatable("screen.ltwterraformer.empty"),
                    left + 14, top + 72, 196, 0xFF9EABB3);
        }

        String active = ToolData.activeStructure(editor);
        if (active.isEmpty()) {
            graphics.drawString(font, Component.translatable("screen.ltwterraformer.active",
                    Component.translatable("screen.ltwterraformer.none")), right, top + 26, 0xFFA8BAC4, false);
            graphics.drawWordWrap(font, Component.translatable("screen.ltwterraformer.pick_one"),
                    right, top + 72, RIGHT_WIDTH, 0xFF9EABB3);
        } else {
            Vec3i size = ToolData.rotatedActiveStructureSize(editor);
            String heading = font.plainSubstrByWidth(StructureLibrary.displayName(active), 92);
            graphics.drawString(font, heading, right, top + 26, 0xFFF4F8FA, false);
            String dimensions = size.getX() + " x " + size.getY() + " x " + size.getZ();
            graphics.drawString(font, dimensions, right + RIGHT_WIDTH - font.width(dimensions), top + 26, 0xFFA8BAC4, false);
            renderMaterials(graphics, editor, right, top + 64);
            graphics.drawString(font, Component.translatable("screen.ltwterraformer.block_count",
                    ToolData.activeBlockCount(editor)), right, top + 199, 0xFFA8BAC4, false);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderMaterials(GuiGraphics graphics, ItemStack editor, int x, int y) {
        Minecraft client = Minecraft.getInstance();
        boolean survival = client.player != null && !client.player.getAbilities().instabuild;
        List<String> lines = ToolData.activeMaterials(editor);
        int start = materialPage * MATERIAL_ROWS;
        for (int i = start; i < Math.min(lines.size(), start + MATERIAL_ROWS); i++) {
            String line = lines.get(i);
            int split = line.lastIndexOf('=');
            if (split <= 0) {
                continue;
            }
            ResourceLocation id = ResourceLocation.tryParse(line.substring(0, split));
            int needed;
            try {
                needed = Integer.parseInt(line.substring(split + 1));
            } catch (NumberFormatException exception) {
                continue;
            }
            Item item = id == null ? Items.AIR : BuiltInRegistries.ITEM.get(id);
            ItemStack icon = new ItemStack(item);
            int rowY = y + (i - start) * 22;
            graphics.renderItem(icon, x, rowY);
            String amount;
            int color;
            if (survival) {
                int have = carried(client, item);
                amount = have + " / " + needed;
                color = have >= needed ? 0xFF7CE08A : 0xFFFF7A7A;
            } else {
                amount = "x " + needed;
                color = 0xFFF4F8FA;
            }
            int amountWidth = font.width(amount);
            String label = font.plainSubstrByWidth(icon.getHoverName().getString(), RIGHT_WIDTH - 26 - amountWidth);
            graphics.drawString(font, label, x + 20, rowY + 4, 0xFFD6DEE3, false);
            graphics.drawString(font, amount, x + RIGHT_WIDTH - amountWidth, rowY + 4, color, false);
        }
    }

    private static int carried(Minecraft client, Item item) {
        if (client.player == null) {
            return 0;
        }
        int count = 0;
        for (ItemStack stack : client.player.getInventory().items) {
            if (stack.getItem() == item) {
                count += stack.getCount();
            }
        }
        return count;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void save() {
        String name = StructureLibrary.normalizeName(nameBox.getValue());
        if (!name.isEmpty() && send("ltwterraformer structure save " + name)) {
            typedName = "";
            nameBox.setValue("");
        }
    }

    private void openFolder() {
        Path folder = StructureLibrary.blueprintFolder();
        try {
            Files.createDirectories(folder);
            Util.getPlatform().openFile(folder.toFile());
        } catch (IOException | RuntimeException exception) {
            LTWTerraformerTool.LOGGER.error("Could not open the blueprint folder {}", folder, exception);
        }
    }

    private void changePage(int amount) {
        page += amount;
        rebuildWidgets();
    }

    private void changeMaterialPage(int amount) {
        materialPage += amount;
        rebuildWidgets();
    }

    private boolean send(String command) {
        if (minecraft == null || minecraft.player == null || minecraft.player.connection == null) {
            return false;
        }
        minecraft.player.connection.sendCommand(command);
        return true;
    }

    /** Everything that changes which buttons exist or what they say. */
    private static String stateKey(ItemStack editor) {
        List<String> parts = new ArrayList<>(ToolData.structureNames(editor));
        parts.add(ToolData.activeStructure(editor));
        parts.add(String.valueOf(ToolData.pasteRotation(editor)));
        parts.add(String.valueOf(ToolData.pasteSkipAir(editor)));
        parts.add(String.valueOf(ToolData.activeMaterials(editor).size()));
        return String.join("|", parts);
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
