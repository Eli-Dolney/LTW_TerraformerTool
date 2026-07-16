package com.github.elidolney.ltwterraformer;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = LTWTerraformerTool.MOD_ID, value = Dist.CLIENT)
public final class StructureEditorClientEvents {
    private static final String KEY_CATEGORY = "key.categories.ltwterraformer";
    private static final KeyMapping LIBRARY = new KeyMapping("key.ltwterraformer.structure_library",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, KEY_CATEGORY);
    private static final int HUD_BACKGROUND = 0xB0181C20;

    private StructureEditorClientEvents() {
    }

    @EventBusSubscriber(modid = LTWTerraformerTool.MOD_ID, value = Dist.CLIENT,
            bus = EventBusSubscriber.Bus.MOD)
    public static final class ModBusEvents {
        private ModBusEvents() {
        }

        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(LIBRARY);
        }
    }

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null) {
            return;
        }
        ItemStack editor = heldEditor(minecraft);
        if (editor.isEmpty() || !LIBRARY.consumeClick()) {
            return;
        }
        minecraft.setScreen(new StructurePresetScreen());
    }

    @SubscribeEvent
    public static void renderHighlight(RenderHighlightEvent.Block event) {
        Minecraft minecraft = Minecraft.getInstance();
        ItemStack stack = heldEditor(minecraft);
        if (stack.isEmpty() || minecraft.level == null) {
            return;
        }

        BlockHitResult hit = event.getTarget();
        BlockPos min;
        BlockPos max;
        if (ToolData.editorMode(stack) == StructureEditorMode.PASTE) {
            BlockPos origin = hit.getBlockPos().relative(hit.getDirection());
            var size = ToolData.activeStructureSize(stack);
            if (size.getX() == 0 || size.getY() == 0 || size.getZ() == 0) {
                return;
            }
            min = origin;
            max = origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1);
        } else {
            var first = ToolData.cornerOne(stack);
            if (first.isEmpty() || !ToolData.selectionDimension(stack).equals(
                    minecraft.level.dimension().location().toString())) {
                min = hit.getBlockPos();
                max = min;
            } else {
                var second = ToolData.cornerTwo(stack);
                var footprint = ToolData.footprintCorner(stack);
                if (second.isPresent()) {
                    min = min(first.get(), second.get());
                    max = max(first.get(), second.get());
                } else if (footprint.isEmpty()) {
                    min = min(first.get(), hit.getBlockPos());
                    max = max(first.get(), hit.getBlockPos());
                } else {
                    BlockPos footprintPos = footprint.get();
                    int floorY = Math.min(hit.getBlockPos().getY(),
                            Math.min(first.get().getY(), footprintPos.getY()));
                    int ceilingY = Math.max(hit.getBlockPos().getY(),
                            Math.max(first.get().getY(), footprintPos.getY()));
                    min = new BlockPos(Math.min(first.get().getX(), footprintPos.getX()), floorY,
                            Math.min(first.get().getZ(), footprintPos.getZ()));
                    max = new BlockPos(Math.max(first.get().getX(), footprintPos.getX()), ceilingY,
                            Math.max(first.get().getZ(), footprintPos.getZ()));
                }
            }
        }

        PoseStack poses = event.getPoseStack();
        VertexConsumer lines = event.getMultiBufferSource().getBuffer(RenderType.lines());
        Vec3 camera = event.getCamera().getPosition();
        AABB box = new AABB(min.getX(), min.getY(), min.getZ(), max.getX() + 1, max.getY() + 1, max.getZ() + 1)
                .inflate(0.003).move(-camera.x, -camera.y, -camera.z);
        LevelRenderer.renderLineBox(poses, lines, box, 0.25F, 0.95F, 0.85F, 0.95F);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void renderHud(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ItemStack stack = heldEditor(minecraft);
        if (stack.isEmpty() || minecraft.options.hideGui) {
            return;
        }

        String active = ToolData.activeStructure(stack);
        Component title = Component.translatable("hud.ltwterraformer.structure_editor",
                ToolData.editorMode(stack).displayName(), active.isEmpty()
                        ? Component.translatable("screen.ltwterraformer.none")
                        : StructureLibrary.displayName(active));
        Component hint;
        if (ToolData.editorMode(stack) == StructureEditorMode.PASTE) {
            hint = Component.translatable("hud.ltwterraformer.structure_editor_paste",
                    LIBRARY.getTranslatedKeyMessage());
        } else if (ToolData.cornerTwo(stack).isPresent()) {
            hint = Component.translatable("hud.ltwterraformer.structure_editor_ready",
                    LIBRARY.getTranslatedKeyMessage());
        } else if (ToolData.cornerOne(stack).isEmpty()) {
            hint = Component.translatable("hud.ltwterraformer.structure_editor_point_one");
        } else if (ToolData.footprintCorner(stack).isEmpty()) {
            hint = Component.translatable("hud.ltwterraformer.structure_editor_point_two");
        } else {
            hint = Component.translatable("hud.ltwterraformer.structure_editor_point_three");
        }
        GuiGraphics graphics = event.getGuiGraphics();
        Font font = minecraft.font;
        int width = Math.max(font.width(title), font.width(hint)) + 12;
        graphics.fill(6, 6, 6 + width, 34, HUD_BACKGROUND);
        graphics.drawString(font, title, 12, 10, 0xFFF4F8FA, true);
        graphics.drawString(font, hint, 12, 22, 0xFF9EABB3, false);
    }

    private static ItemStack heldEditor(Minecraft minecraft) {
        if (minecraft.player == null) {
            return ItemStack.EMPTY;
        }
        ItemStack main = minecraft.player.getMainHandItem();
        if (main.is(ModItems.STRUCTURE_EDITOR.get())) {
            return main;
        }
        ItemStack offhand = minecraft.player.getOffhandItem();
        return offhand.is(ModItems.STRUCTURE_EDITOR.get()) ? offhand : ItemStack.EMPTY;
    }

    private static BlockPos min(BlockPos first, BlockPos second) {
        return new BlockPos(Math.min(first.getX(), second.getX()), Math.min(first.getY(), second.getY()),
                Math.min(first.getZ(), second.getZ()));
    }

    private static BlockPos max(BlockPos first, BlockPos second) {
        return new BlockPos(Math.max(first.getX(), second.getX()), Math.max(first.getY(), second.getY()),
                Math.max(first.getZ(), second.getZ()));
    }
}
