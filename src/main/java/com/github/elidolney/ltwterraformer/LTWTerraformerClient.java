package com.github.elidolney.ltwterraformer;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import java.util.List;

@Mod(value = LTWTerraformerTool.MOD_ID, dist = Dist.CLIENT)
public final class LTWTerraformerClient {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String KEY_CATEGORY = "key.categories.ltwterraformer";
    private static final KeyMapping CYCLE_MODE = key("key.ltwterraformer.cycle_mode", GLFW.GLFW_KEY_V);
    private static final KeyMapping CYCLE_SHAPE = key("key.ltwterraformer.cycle_shape", GLFW.GLFW_KEY_B);
    private static final KeyMapping RADIUS_DOWN = key("key.ltwterraformer.radius_down", GLFW.GLFW_KEY_LEFT_BRACKET);
    private static final KeyMapping RADIUS_UP = key("key.ltwterraformer.radius_up", GLFW.GLFW_KEY_RIGHT_BRACKET);
    private static final KeyMapping UNDO = key("key.ltwterraformer.undo", GLFW.GLFW_KEY_Z);
    private static final KeyMapping CLEAR_AREA = key("key.ltwterraformer.clear_area", GLFW.GLFW_KEY_X);
    /** Radius or height change per key press while sneaking. */
    private static final int FAST_STEP = 5;

    private static final int HUD_BACKGROUND = 0xB0181C20;
    private static final int HUD_PRIMARY = 0xFFF4F8FA;
    private static final int HUD_SECONDARY = 0xFF9EABB3;

    public LTWTerraformerClient(IEventBus modBus) {
        modBus.addListener(this::registerKeyMappings);
        NeoForge.EVENT_BUS.addListener(this::clientTick);
        NeoForge.EVENT_BUS.addListener(this::renderHighlight);
        NeoForge.EVENT_BUS.addListener(this::renderHud);
        LOGGER.info("Registered LTW Terraformer client controls, previews, and HUD");
    }

    private void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(CYCLE_MODE);
        event.register(CYCLE_SHAPE);
        event.register(RADIUS_DOWN);
        event.register(RADIUS_UP);
        event.register(UNDO);
        event.register(CLEAR_AREA);
    }

    private void clientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.player.connection == null || minecraft.screen != null) {
            return;
        }
        ItemStack held = heldTool(minecraft);
        boolean structureEditorHeld = minecraft.player.getMainHandItem().is(ModItems.STRUCTURE_EDITOR.get())
                || minecraft.player.getOffhandItem().is(ModItems.STRUCTURE_EDITOR.get());
        if (structureEditorHeld) {
            if (CYCLE_MODE.consumeClick()) {
                minecraft.player.connection.sendCommand("ltwterraformer cycle");
            }
            if (RADIUS_DOWN.consumeClick()) {
                minecraft.player.connection.sendCommand("ltwterraformer structure adjust_height -1");
            }
            if (RADIUS_UP.consumeClick()) {
                minecraft.player.connection.sendCommand("ltwterraformer structure adjust_height 1");
            }
            if (UNDO.consumeClick()) {
                minecraft.player.connection.sendCommand("ltwterraformer undo");
            }
            return;
        }
        if (held.isEmpty()) {
            return;
        }
        boolean sneaking = minecraft.player.isShiftKeyDown();
        int step = sneaking ? FAST_STEP : 1;
        if (CYCLE_MODE.consumeClick()) {
            minecraft.player.connection.sendCommand(sneaking ? "ltwterraformer cycle_back" : "ltwterraformer cycle");
        }
        boolean terraformer = held.is(ModItems.CREATIVE_TERRAFORMER.get());
        if (terraformer && CYCLE_SHAPE.consumeClick()) {
            minecraft.player.connection.sendCommand("ltwterraformer shape next");
        }
        if (terraformer || ToolData.restriction(held).twoPoint()) {
            if (RADIUS_DOWN.consumeClick()) {
                minecraft.player.connection.sendCommand("ltwterraformer adjust_radius -" + step);
            }
            if (RADIUS_UP.consumeClick()) {
                minecraft.player.connection.sendCommand("ltwterraformer adjust_radius " + step);
            }
        }
        if (CLEAR_AREA.consumeClick()) {
            minecraft.player.connection.sendCommand("ltwterraformer area clear");
        }
        if (UNDO.consumeClick()) {
            minecraft.player.connection.sendCommand("ltwterraformer undo");
        }
    }

    private void renderHighlight(RenderHighlightEvent.Block event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }

        ItemStack held = heldTool(minecraft);
        if (held.isEmpty()) {
            return;
        }

        BlockHitResult hit = event.getTarget();
        PoseStack poseStack = event.getPoseStack();
        VertexConsumer lines = event.getMultiBufferSource().getBuffer(RenderType.lines());
        Vec3 camera = event.getCamera().getPosition();

        if (held.is(ModItems.CREATIVE_TERRAFORMER.get())) {
            if (ToolData.shape(held) == BrushShape.SELECTION) {
                renderSelectionPreview(poseStack, lines, camera, hit, held, minecraft.level.dimension().location().toString());
            } else {
                renderBrushPreview(poseStack, lines, camera, hit, ToolData.mode(held), ToolData.radius(held), ToolData.shape(held));
            }
        } else if (held.is(ModItems.SURVIVAL_BUILDER_WAND.get()) && ToolData.restriction(held).twoPoint()) {
            BlockPos point = hit.getBlockPos().relative(hit.getDirection());
            BlockPos first = ToolData.wandCorner(held)
                    .filter(corner -> minecraft.level.dimension().location().toString().equals(ToolData.wandCornerDimension(held)))
                    .orElse(point);
            drawBox(poseStack, lines, camera, first, point, ToolData.wandExtraHeight(held),
                    new float[]{0.25F, 0.85F, 1.0F, 0.95F});
        } else if (held.is(ModItems.SURVIVAL_BUILDER_WAND.get())) {
            List<BlockPos> targets = WandPlacementPlanner.plan(
                    minecraft.level,
                    hit.getBlockPos(),
                    hit.getDirection(),
                    ToolData.restriction(held),
                    minecraft.player.getDirection()
            );
            WandMaterialSource material = WandMaterialSource.select(minecraft.player, minecraft.level.getBlockState(hit.getBlockPos()));
            int visibleTargets = material == null ? 0 : Math.min(WandPlacementPlanner.MAX_PREVIEW_BLOCKS,
                    Math.min(targets.size(), material.availableCount(minecraft.player)));
            for (int i = 0; i < visibleTargets; i++) {
                BlockPos target = targets.get(i);
                AABB box = new AABB(target).inflate(0.002).move(-camera.x, -camera.y, -camera.z);
                LevelRenderer.renderLineBox(poseStack, lines, box, 0.25F, 0.85F, 1.0F, 0.85F);
            }
        }
        event.setCanceled(true);
    }

    private void renderBrushPreview(PoseStack poseStack, VertexConsumer lines, Vec3 camera, BlockHitResult hit,
                                    TerraformMode mode, int radius, BrushShape shape) {
        BlockPos clicked = hit.getBlockPos();
        boolean volumeMode = mode == TerraformMode.FILL || mode == TerraformMode.CLEAR;
        BlockPos centerBlock = volumeMode ? clicked.relative(hit.getDirection()) : clicked;
        double cx = centerBlock.getX() + 0.5;
        double cy = centerBlock.getY() + 0.5;
        double cz = centerBlock.getZ() + 0.5;
        double visualRadius = radius + 0.5;
        float[] color = modeColor(mode);

        if (!volumeMode && mode != TerraformMode.REPLACE && shape == BrushShape.CUBE) {
            // Surface modes follow the brush footprint: Cube means a square patch of ground.
            AABB square = new AABB(
                    centerBlock.getX() - radius, centerBlock.getY() + 1.01, centerBlock.getZ() - radius,
                    centerBlock.getX() + radius + 1, centerBlock.getY() + 1.02, centerBlock.getZ() + radius + 1
            ).move(-camera.x, -camera.y, -camera.z);
            LevelRenderer.renderLineBox(poseStack, lines, square, color[0], color[1], color[2], color[3]);
            return;
        }
        if (!volumeMode && mode != TerraformMode.REPLACE) {
            drawCircle(poseStack, lines, camera, cx, centerBlock.getY() + 1.01, cz, visualRadius,
                    Direction.Axis.Y, color);
            drawLine(poseStack, lines, camera, cx - visualRadius, centerBlock.getY() + 1.01, cz,
                    cx + visualRadius, centerBlock.getY() + 1.01, cz, color);
            drawLine(poseStack, lines, camera, cx, centerBlock.getY() + 1.01, cz - visualRadius,
                    cx, centerBlock.getY() + 1.01, cz + visualRadius, color);
            return;
        }

        switch (shape) {
            case SPHERE -> {
                drawCircle(poseStack, lines, camera, cx, cy, cz, visualRadius, Direction.Axis.X, color);
                drawCircle(poseStack, lines, camera, cx, cy, cz, visualRadius, Direction.Axis.Y, color);
                drawCircle(poseStack, lines, camera, cx, cy, cz, visualRadius, Direction.Axis.Z, color);
            }
            case CUBE -> {
                AABB box = new AABB(
                        centerBlock.getX() - radius, centerBlock.getY() - radius, centerBlock.getZ() - radius,
                        centerBlock.getX() + radius + 1, centerBlock.getY() + radius + 1, centerBlock.getZ() + radius + 1
                ).inflate(0.002).move(-camera.x, -camera.y, -camera.z);
                LevelRenderer.renderLineBox(poseStack, lines, box, color[0], color[1], color[2], color[3]);
            }
            case CYLINDER -> drawCylinder(poseStack, lines, camera, cx, cy, cz, visualRadius, color);
            case SELECTION -> {
            }
        }
    }

    private void renderSelectionPreview(PoseStack poseStack, VertexConsumer lines, Vec3 camera, BlockHitResult hit,
                                        ItemStack held, String dimension) {
        float[] color = modeColor(ToolData.mode(held));
        BlockPos target = hit.getBlockPos();
        java.util.Optional<BlockPos> first = ToolData.areaOne(held);
        if (first.isEmpty() || !dimension.equals(ToolData.areaDimension(held))) {
            drawBox(poseStack, lines, camera, target, target, 0, color);
            return;
        }
        BlockPos second = ToolData.areaTwo(held).orElse(target);
        drawBox(poseStack, lines, camera, first.get(), second, ToolData.areaExtraHeight(held), color);
    }

    private static void drawBox(PoseStack poseStack, VertexConsumer lines, Vec3 camera, BlockPos first, BlockPos second,
                                int extraHeight, float[] color) {
        AABB box = new AABB(
                Math.min(first.getX(), second.getX()), Math.min(first.getY(), second.getY()),
                Math.min(first.getZ(), second.getZ()),
                Math.max(first.getX(), second.getX()) + 1, Math.max(first.getY(), second.getY()) + 1 + extraHeight,
                Math.max(first.getZ(), second.getZ()) + 1
        ).inflate(0.003).move(-camera.x, -camera.y, -camera.z);
        LevelRenderer.renderLineBox(poseStack, lines, box, color[0], color[1], color[2], color[3]);
    }

    private void renderHud(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) {
            return;
        }

        ItemStack held = heldTool(minecraft);
        Component title;
        Component hint;
        if (held.is(ModItems.CREATIVE_TERRAFORMER.get()) && ToolData.shape(held) == BrushShape.SELECTION) {
            java.util.Optional<BlockPos> first = ToolData.areaOne(held);
            java.util.Optional<BlockPos> second = ToolData.areaTwo(held);
            if (first.isPresent() && second.isPresent()) {
                title = Component.translatable("hud.ltwterraformer.terraformer_area", ToolData.mode(held).displayName(),
                        Math.abs(first.get().getX() - second.get().getX()) + 1,
                        Math.abs(first.get().getY() - second.get().getY()) + 1 + ToolData.areaExtraHeight(held),
                        Math.abs(first.get().getZ() - second.get().getZ()) + 1);
                hint = Component.translatable("hud.ltwterraformer.terraformer_area_hint",
                        CYCLE_MODE.getTranslatedKeyMessage(), RADIUS_DOWN.getTranslatedKeyMessage(),
                        RADIUS_UP.getTranslatedKeyMessage(), CLEAR_AREA.getTranslatedKeyMessage(),
                        UNDO.getTranslatedKeyMessage());
            } else {
                title = Component.translatable("hud.ltwterraformer.terraformer_selecting",
                        ToolData.mode(held).displayName());
                hint = Component.translatable(first.isEmpty()
                        ? "hud.ltwterraformer.terraformer_area_point_one"
                        : "hud.ltwterraformer.terraformer_area_point_two");
            }
        } else if (held.is(ModItems.CREATIVE_TERRAFORMER.get())) {
            title = Component.translatable("hud.ltwterraformer.terraformer",
                    ToolData.mode(held).displayName(), ToolData.shape(held).displayName(), ToolData.radius(held));
            hint = Component.translatable("hud.ltwterraformer.terraformer_hint",
                    CYCLE_MODE.getTranslatedKeyMessage(), CYCLE_SHAPE.getTranslatedKeyMessage(),
                    RADIUS_DOWN.getTranslatedKeyMessage(), RADIUS_UP.getTranslatedKeyMessage(), UNDO.getTranslatedKeyMessage());
        } else if (held.is(ModItems.SURVIVAL_BUILDER_WAND.get()) && ToolData.restriction(held).twoPoint()) {
            title = Component.translatable("hud.ltwterraformer.wand_box", ToolData.restriction(held).displayName(),
                    ToolData.wandExtraHeight(held));
            hint = Component.translatable(ToolData.wandCorner(held).isEmpty()
                            ? "hud.ltwterraformer.wand_box_point_one" : "hud.ltwterraformer.wand_box_point_two",
                    RADIUS_DOWN.getTranslatedKeyMessage(), RADIUS_UP.getTranslatedKeyMessage(),
                    CLEAR_AREA.getTranslatedKeyMessage());
        } else if (held.is(ModItems.SURVIVAL_BUILDER_WAND.get())) {
            title = Component.translatable("hud.ltwterraformer.wand", ToolData.restriction(held).displayName());
            hint = Component.translatable("hud.ltwterraformer.wand_hint",
                    CYCLE_MODE.getTranslatedKeyMessage(), UNDO.getTranslatedKeyMessage());
        } else {
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        Font font = minecraft.font;
        int width = Math.max(font.width(title), font.width(hint)) + 12;
        graphics.fill(6, 6, 6 + width, 34, HUD_BACKGROUND);
        graphics.drawString(font, title, 12, 10, HUD_PRIMARY, true);
        graphics.drawString(font, hint, 12, 22, HUD_SECONDARY, false);
    }

    private static ItemStack heldTool(Minecraft minecraft) {
        ItemStack main = minecraft.player.getMainHandItem();
        if (isTool(main)) {
            return main;
        }
        ItemStack offhand = minecraft.player.getOffhandItem();
        return isTool(offhand) ? offhand : ItemStack.EMPTY;
    }

    private static boolean isTool(ItemStack stack) {
        return stack.is(ModItems.CREATIVE_TERRAFORMER.get()) || stack.is(ModItems.SURVIVAL_BUILDER_WAND.get());
    }

    private static KeyMapping key(String translation, int keyCode) {
        return new KeyMapping(translation, InputConstants.Type.KEYSYM, keyCode, KEY_CATEGORY);
    }

    private static float[] modeColor(TerraformMode mode) {
        return switch (mode) {
            case FLATTEN -> new float[]{0.2F, 0.85F, 1.0F, 0.95F};
            case FILL -> new float[]{0.25F, 1.0F, 0.45F, 0.95F};
            case SMOOTH -> new float[]{1.0F, 0.72F, 0.2F, 0.95F};
            case CLEAR -> new float[]{1.0F, 0.25F, 0.25F, 0.95F};
            case RAISE -> new float[]{0.55F, 1.0F, 0.2F, 0.95F};
            case LOWER -> new float[]{1.0F, 0.45F, 0.15F, 0.95F};
            case PAINT -> new float[]{0.7F, 0.35F, 1.0F, 0.95F};
            case REPLACE -> new float[]{1.0F, 0.25F, 0.8F, 0.95F};
            case HILL -> new float[]{0.4F, 0.9F, 0.35F, 0.95F};
            case VALLEY -> new float[]{0.95F, 0.6F, 0.3F, 0.95F};
            case ROUGHEN -> new float[]{0.85F, 0.85F, 0.45F, 0.95F};
            case GRADE -> new float[]{0.3F, 0.7F, 1.0F, 0.95F};
            case WALLS -> new float[]{0.9F, 0.9F, 0.95F, 0.95F};
        };
    }

    private static void drawCylinder(PoseStack poseStack, VertexConsumer consumer, Vec3 camera,
                                     double cx, double cy, double cz, double radius, float[] color) {
        double bottom = cy - radius;
        double top = cy + radius;
        drawCircle(poseStack, consumer, camera, cx, bottom, cz, radius, Direction.Axis.Y, color);
        drawCircle(poseStack, consumer, camera, cx, top, cz, radius, Direction.Axis.Y, color);
        drawLine(poseStack, consumer, camera, cx - radius, bottom, cz, cx - radius, top, cz, color);
        drawLine(poseStack, consumer, camera, cx + radius, bottom, cz, cx + radius, top, cz, color);
        drawLine(poseStack, consumer, camera, cx, bottom, cz - radius, cx, top, cz - radius, color);
        drawLine(poseStack, consumer, camera, cx, bottom, cz + radius, cx, top, cz + radius, color);
    }

    private static void drawCircle(PoseStack poseStack, VertexConsumer consumer, Vec3 camera,
                                   double cx, double cy, double cz, double radius, Direction.Axis axis, float[] color) {
        int segments = 48;
        for (int i = 0; i < segments; i++) {
            double a = Math.PI * 2.0 * i / segments;
            double b = Math.PI * 2.0 * (i + 1) / segments;
            double aCos = Math.cos(a) * radius;
            double aSin = Math.sin(a) * radius;
            double bCos = Math.cos(b) * radius;
            double bSin = Math.sin(b) * radius;
            switch (axis) {
                case X -> drawLine(poseStack, consumer, camera, cx, cy + aCos, cz + aSin,
                        cx, cy + bCos, cz + bSin, color);
                case Y -> drawLine(poseStack, consumer, camera, cx + aCos, cy, cz + aSin,
                        cx + bCos, cy, cz + bSin, color);
                case Z -> drawLine(poseStack, consumer, camera, cx + aCos, cy + aSin, cz,
                        cx + bCos, cy + bSin, cz, color);
            }
        }
    }

    private static void drawLine(PoseStack poseStack, VertexConsumer consumer, Vec3 camera,
                                 double x1, double y1, double z1, double x2, double y2, double z2, float[] color) {
        PoseStack.Pose pose = poseStack.last();
        consumer.addVertex(pose, (float) (x1 - camera.x), (float) (y1 - camera.y), (float) (z1 - camera.z))
                .setColor(color[0], color[1], color[2], color[3])
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
        consumer.addVertex(pose, (float) (x2 - camera.x), (float) (y2 - camera.y), (float) (z2 - camera.z))
                .setColor(color[0], color[1], color[2], color[3])
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }
}
