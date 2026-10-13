package com.github.elidolney.ltwterraformer;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.component.DataComponents;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

@GameTestHolder(LTWTerraformerTool.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ModGameTests {
    private ModGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void toolsEditWorldConsumeItemsAndUndo(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = headlessPlayer(level);

        ItemStack terraformer = new ItemStack(ModItems.CREATIVE_TERRAFORMER.get());
        ToolData.setMode(terraformer, TerraformMode.FILL);
        ToolData.setRadius(terraformer, 1);
        ToolData.setShape(terraformer, BrushShape.SPHERE);
        helper.assertTrue(terraformer.is(ModItems.CREATIVE_TERRAFORMER.get()), "Creative Terraformer was not registered");
        helper.assertTrue(ToolData.mode(terraformer) == TerraformMode.FILL, "Terraform mode did not persist");
        helper.assertTrue(ToolData.radius(terraformer) == 1, "Brush radius did not persist");
        helper.assertTrue(ToolData.shape(terraformer) == BrushShape.SPHERE, "Brush shape did not persist");
        ItemAttributeModifiers reach = terraformer.getOrDefault(
                DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        helper.assertTrue(reach.compute(4.5, EquipmentSlot.MAINHAND) == 64.0,
                "Creative Terraformer should provide Minecraft's maximum normal reach");

        player.getAbilities().instabuild = true;
        player.getAbilities().mayBuild = true;
        player.setItemInHand(InteractionHand.MAIN_HAND, terraformer);
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Blocks.DIRT));

        BlockPos fillSupport = helper.absolutePos(new BlockPos(3, 2, 3));
        BlockPos fillCenter = fillSupport.above();
        level.setBlock(fillSupport, Blocks.STONE.defaultBlockState(), 3);
        InteractionResult fillResult = terraformer.useOn(context(player, fillSupport, Direction.UP));

        helper.assertTrue(fillResult.consumesAction(), "Creative Terraformer did not consume the use action");
        helper.assertTrue(level.getBlockState(fillSupport).is(Blocks.STONE), "Fill replaced a non-replaceable support block");
        helper.assertTrue(level.getBlockState(fillCenter).is(Blocks.DIRT), "Fill did not place its center block");
        helper.assertTrue(level.getBlockState(fillCenter.above()).is(Blocks.DIRT), "Fill did not place the upper sphere block");
        helper.assertTrue(level.getBlockState(fillCenter.east()).is(Blocks.DIRT), "Fill did not place a side sphere block");

        int terrainRestored = UndoManager.undo(player);
        helper.assertTrue(terrainRestored == 6, "Expected six fill blocks to be undoable, got " + terrainRestored);
        helper.assertTrue(level.getBlockState(fillCenter).isAir(), "Terrain undo did not restore air");
        helper.assertTrue(level.getBlockState(fillSupport).is(Blocks.STONE), "Terrain undo changed the support block");

        ToolData.setShape(terraformer, BrushShape.CUBE);
        terraformer.useOn(context(player, fillSupport, Direction.UP));
        int cubeRestored = UndoManager.undo(player);
        helper.assertTrue(cubeRestored == 26, "Radius-one cube should fill 26 air blocks, got " + cubeRestored);
        helper.assertTrue(level.getBlockState(fillSupport).is(Blocks.STONE), "Cube fill replaced its solid support");

        ToolData.setShape(terraformer, BrushShape.CYLINDER);
        terraformer.useOn(context(player, fillSupport, Direction.UP));
        int cylinderRestored = UndoManager.undo(player);
        helper.assertTrue(cylinderRestored == 14,
                "Radius-one cylinder should fill 14 air blocks, got " + cylinderRestored);
        ToolData.setShape(terraformer, BrushShape.SPHERE);

        ToolData.setMode(terraformer, TerraformMode.FLATTEN);
        BlockPos flattenCenter = helper.absolutePos(new BlockPos(3, 2, 8));
        level.setBlock(flattenCenter, Blocks.STONE.defaultBlockState(), 3);
        InteractionResult flattenResult = terraformer.useOn(context(player, flattenCenter, Direction.UP));

        helper.assertTrue(flattenResult.consumesAction(), "Flatten did not consume the use action");
        helper.assertTrue(level.getBlockState(flattenCenter).is(Blocks.DIRT),
                "Flatten did not replace the surface with the selected brush");
        helper.assertTrue(level.getBlockState(flattenCenter.east()).is(Blocks.DIRT),
                "Flatten did not fill a neighboring hole");
        int flattenRestored = UndoManager.undo(player);
        helper.assertTrue(flattenRestored == 9, "Expected nine flatten changes to be undoable, got " + flattenRestored);
        helper.assertTrue(level.getBlockState(flattenCenter).is(Blocks.STONE), "Flatten undo did not restore the surface");

        BlockPos paintCenter = helper.absolutePos(new BlockPos(9, 2, 2));
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx * dx + dz * dz <= 1) {
                    level.setBlock(paintCenter.offset(dx, 0, dz), Blocks.STONE.defaultBlockState(), 3);
                }
            }
        }
        helper.assertTrue(level.getBlockState(paintCenter).is(Blocks.STONE), "Paint test setup did not place stone");

        ToolData.setMode(terraformer, TerraformMode.PAINT);
        int painted = TerrainOperations.apply(context(player, paintCenter, Direction.UP), TerraformMode.PAINT, 1);
        helper.assertTrue(painted == 5, "Paint should recolor five blocks, changed " + painted);
        helper.assertTrue(level.getBlockState(paintCenter).is(Blocks.DIRT),
                "Paint did not recolor the surface; got " + level.getBlockState(paintCenter));
        int paintRestored = UndoManager.undo(player);
        helper.assertTrue(paintRestored == 5, "Paint should change five radius-one surface blocks");

        ToolData.setMode(terraformer, TerraformMode.RAISE);
        int raised = TerrainOperations.apply(context(player, paintCenter, Direction.UP), TerraformMode.RAISE, 1);
        helper.assertTrue(raised == 5, "Raise should add five blocks, changed " + raised);
        helper.assertTrue(level.getBlockState(paintCenter.above()).is(Blocks.DIRT), "Raise did not add terrain");
        int raiseRestored = UndoManager.undo(player);
        helper.assertTrue(raiseRestored == 5, "Raise should add five radius-one blocks");

        TerrainOperations.apply(context(player, paintCenter, Direction.UP), TerraformMode.RAISE, 1);
        ToolData.setMode(terraformer, TerraformMode.LOWER);
        int lowered = TerrainOperations.apply(context(player, paintCenter.above(), Direction.UP), TerraformMode.LOWER, 1);
        helper.assertTrue(lowered == 5, "Lower should remove five blocks, changed " + lowered);
        helper.assertTrue(level.getBlockState(paintCenter.above()).isAir(), "Lower did not remove the top terrain layer");
        int lowerRestored = UndoManager.undo(player);
        helper.assertTrue(lowerRestored == 5, "Lower should remove five radius-one blocks");
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx * dx + dz * dz <= 1) {
                    level.setBlock(paintCenter.offset(dx, 1, dz), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }

        BlockPos replaceCenter = helper.absolutePos(new BlockPos(14, 3, 3));
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    level.setBlock(replaceCenter.offset(dx, dy, dz), Blocks.STONE.defaultBlockState(), 3);
                }
            }
        }
        ToolData.setMode(terraformer, TerraformMode.REPLACE);
        ToolData.setShape(terraformer, BrushShape.CUBE);
        int replaced = TerrainOperations.apply(context(player, replaceCenter, Direction.UP), TerraformMode.REPLACE, 1);
        helper.assertTrue(replaced == 27, "Replace should change 27 blocks, changed " + replaced);
        helper.assertTrue(level.getBlockState(replaceCenter).is(Blocks.DIRT), "Replace did not change its target block");
        helper.assertTrue(level.getBlockState(replaceCenter.offset(1, 1, 1)).is(Blocks.DIRT),
                "Replace did not cover the selected cube");
        int replaceRestored = UndoManager.undo(player);
        helper.assertTrue(replaceRestored == 27, "Replace should change the full radius-one cube");

        ItemStack wand = new ItemStack(ModItems.SURVIVAL_BUILDER_WAND.get());
        ToolData.setRestriction(wand, WandRestriction.FACE);
        helper.assertTrue(wand.isDamageableItem(), "Survival Builder Wand must have durability");

        player.getAbilities().instabuild = false;
        player.setItemInHand(InteractionHand.MAIN_HAND, wand);
        ItemStack material = new ItemStack(Blocks.DIRT, 12);
        player.setItemInHand(InteractionHand.OFF_HAND, material);

        BlockPos wallCenter = helper.absolutePos(new BlockPos(7, 3, 6));
        for (int dy = -1; dy <= 1; dy++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.setBlock(wallCenter.offset(0, dy, dz), Blocks.COBBLESTONE.defaultBlockState(), 3);
            }
        }

        List<BlockPos> faceTargets = WandPlacementPlanner.plan(level, wallCenter, Direction.EAST,
                WandRestriction.FACE, Direction.NORTH);
        List<BlockPos> rowTargets = WandPlacementPlanner.plan(level, wallCenter, Direction.EAST,
                WandRestriction.ROW, Direction.NORTH);
        List<BlockPos> columnTargets = WandPlacementPlanner.plan(level, wallCenter, Direction.EAST,
                WandRestriction.COLUMN, Direction.NORTH);
        // Keep all 16 descending steps above neighboring GameTest barrier enclosures.
        BlockPos projectionOrigin = helper.absolutePos(new BlockPos(7, 80, 6));
        level.setBlock(projectionOrigin, Blocks.COBBLESTONE.defaultBlockState(), 3);
        List<BlockPos> lineTargets = WandPlacementPlanner.plan(level, projectionOrigin, Direction.EAST,
                WandRestriction.LINE, Direction.NORTH);
        List<BlockPos> stairUpTargets = WandPlacementPlanner.plan(level, projectionOrigin, Direction.EAST,
                WandRestriction.STAIR_UP, Direction.NORTH);
        List<BlockPos> stairDownTargets = WandPlacementPlanner.plan(level, projectionOrigin, Direction.EAST,
                WandRestriction.STAIR_DOWN, Direction.NORTH);
        List<BlockPos> topStairUpTargets = WandPlacementPlanner.plan(level, projectionOrigin, Direction.UP,
                WandRestriction.STAIR_UP, Direction.EAST);
        List<BlockPos> topStairDownTargets = WandPlacementPlanner.plan(level, projectionOrigin, Direction.UP,
                WandRestriction.STAIR_DOWN, Direction.SOUTH);
        helper.assertTrue(faceTargets.size() == 9, "Full Face should target nine blocks, got " + faceTargets.size());
        helper.assertTrue(rowTargets.size() == 3, "Row should target three blocks, got " + rowTargets.size());
        helper.assertTrue(columnTargets.size() == 3, "Column should target three blocks, got " + columnTargets.size());
        helper.assertTrue(lineTargets.size() == 16, "Bridge / Pillar should project sixteen blocks");
        helper.assertTrue(stairUpTargets.size() == 16 && stairUpTargets.get(15).getY() == projectionOrigin.getY() + 15,
                "Stair Up did not rise one block per step");
        helper.assertTrue(stairDownTargets.size() == 16 && stairDownTargets.get(15).getY() == projectionOrigin.getY() - 15,
                "Stair Down did not descend one block per step");
        helper.assertTrue(topStairUpTargets.size() == 16
                        && topStairUpTargets.getFirst().equals(projectionOrigin.east().above())
                        && topStairUpTargets.getLast().equals(projectionOrigin.east(16).above(16)),
                "Top-face Stair Up should follow the player's facing direction");
        helper.assertTrue(topStairDownTargets.size() == 16
                        && topStairDownTargets.getFirst().equals(projectionOrigin.south().below())
                        && topStairDownTargets.getLast().equals(projectionOrigin.south(16).below(16)),
                "Top-face Stair Down should follow the player's facing direction");

        InteractionResult wandResult = wand.useOn(context(player, wallCenter, Direction.EAST));
        helper.assertTrue(wandResult.consumesAction(), "Survival Builder Wand did not consume the use action");
        helper.assertTrue(material.getCount() == 3, "Wand should consume nine blocks, remaining count was " + material.getCount());
        helper.assertTrue(wand.getDamageValue() == 9, "Wand should take nine durability damage, got " + wand.getDamageValue());
        for (BlockPos target : faceTargets) {
            helper.assertTrue(level.getBlockState(target).is(Blocks.DIRT), "Wand did not place dirt at " + target);
        }

        int wandRestored = UndoManager.undo(player);
        helper.assertTrue(wandRestored == 9, "Expected nine wand blocks to be undoable, got " + wandRestored);
        helper.assertTrue(material.getCount() == 3, "Undo must not refund survival blocks");
        for (BlockPos target : faceTargets) {
            helper.assertTrue(level.getBlockState(target).isAir(), "Wand undo did not restore air at " + target);
        }

        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        ItemStack firstInventoryStack = new ItemStack(Blocks.COBBLESTONE, 2);
        ItemStack secondInventoryStack = new ItemStack(Blocks.COBBLESTONE, 2);
        player.getInventory().setItem(5, firstInventoryStack);
        player.getInventory().setItem(6, secondInventoryStack);
        ToolData.setRestriction(wand, WandRestriction.ROW);

        InteractionResult rowResult = wand.useOn(context(player, wallCenter, Direction.EAST));
        helper.assertTrue(rowResult.consumesAction(), "Row placement did not consume the use action");
        helper.assertTrue(firstInventoryStack.isEmpty(), "Inventory fallback did not exhaust the first matching stack");
        helper.assertTrue(secondInventoryStack.getCount() == 1, "Inventory fallback did not continue into the second matching stack");
        helper.assertTrue(wand.getDamageValue() == 12, "Three row blocks should add three durability damage");
        for (BlockPos target : rowTargets) {
            helper.assertTrue(level.getBlockState(target).is(Blocks.COBBLESTONE), "Row mode did not place cobblestone at " + target);
        }

        int rowRestored = UndoManager.undo(player);
        helper.assertTrue(rowRestored == 3, "Expected three row blocks to be undoable, got " + rowRestored);
        for (BlockPos target : rowTargets) {
            helper.assertTrue(level.getBlockState(target).isAir(), "Row undo did not restore air at " + target);
        }

        ItemStack lineMaterial = new ItemStack(Blocks.DIRT, 4);
        player.setItemInHand(InteractionHand.OFF_HAND, lineMaterial);
        ToolData.setRestriction(wand, WandRestriction.LINE);
        List<BlockPos> actualLineTargets = WandPlacementPlanner.plan(level, wallCenter, Direction.EAST,
                WandRestriction.LINE, Direction.NORTH);
        InteractionResult lineResult = wand.useOn(context(player, wallCenter, Direction.EAST));
        helper.assertTrue(lineResult.consumesAction(), "Bridge / Pillar placement did not consume the use action");
        helper.assertTrue(lineMaterial.isEmpty(), "Bridge / Pillar did not stop at available material");
        helper.assertTrue(wand.getDamageValue() == 16, "Four projected blocks should add four durability damage");
        for (int i = 0; i < 4; i++) {
            helper.assertTrue(level.getBlockState(actualLineTargets.get(i)).is(Blocks.DIRT),
                    "Bridge / Pillar missed target " + i);
        }
        int lineRestored = UndoManager.undo(player);
        helper.assertTrue(lineRestored == 4, "Expected four projected blocks to be undoable");
        helper.assertTrue(lineMaterial.isEmpty(), "Undo must not refund projected survival blocks");

        BlockPos fillGround = helper.absolutePos(new BlockPos(10, 40, 10));
        int fillRadiusSquared = WandPlacementPlanner.HOLE_FILL_RADIUS * WandPlacementPlanner.HOLE_FILL_RADIUS;
        for (int dx = -WandPlacementPlanner.HOLE_FILL_RADIUS; dx <= WandPlacementPlanner.HOLE_FILL_RADIUS; dx++) {
            for (int dz = -WandPlacementPlanner.HOLE_FILL_RADIUS; dz <= WandPlacementPlanner.HOLE_FILL_RADIUS; dz++) {
                if (dx * dx + dz * dz <= fillRadiusSquared) {
                    level.setBlock(fillGround.offset(dx, 0, dz), Blocks.STONE.defaultBlockState(), 3);
                }
            }
        }
        BlockPos craterCenter = fillGround.west(3);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos column = craterCenter.offset(dx, 0, dz);
                level.setBlock(column, Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(column.below(), Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(column.below(2), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        ItemStack fillOffhand = new ItemStack(Blocks.DIRT, 2);
        ItemStack fillInventory = new ItemStack(Blocks.DIRT, 16);
        player.setItemInHand(InteractionHand.OFF_HAND, fillOffhand);
        player.getInventory().setItem(7, fillInventory);
        ToolData.setRestriction(wand, WandRestriction.HOLE_FILL);
        List<BlockPos> holeTargets = WandPlacementPlanner.plan(
                level, fillGround, Direction.UP, WandRestriction.HOLE_FILL, Direction.NORTH);
        helper.assertTrue(holeTargets.size() == 18,
                "Crater Fill should find eighteen missing blocks, got " + holeTargets.size());

        int damageBeforeFill = wand.getDamageValue();
        InteractionResult holeFillResult = wand.useOn(context(player, fillGround, Direction.UP));
        helper.assertTrue(holeFillResult.consumesAction(), "Crater Fill did not consume the use action");
        helper.assertTrue(fillOffhand.isEmpty() && fillInventory.isEmpty(),
                "Crater Fill should continue from offhand material into matching inventory stacks");
        helper.assertTrue(wand.getDamageValue() == damageBeforeFill + 18,
                "Crater Fill should consume one durability per block");
        for (BlockPos target : holeTargets) {
            helper.assertTrue(level.getBlockState(target).is(Blocks.DIRT),
                    "Crater Fill missed " + target);
        }
        int holeFillRestored = UndoManager.undo(player);
        helper.assertTrue(holeFillRestored == 18, "Expected all crater blocks to be undoable");

        helper.succeed();
    }

    private static ServerPlayer headlessPlayer(ServerLevel level) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(
                new GameProfile(UUID.randomUUID(), "ltw-test-player"), false);
        return new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
            @Override
            public void displayClientMessage(Component message, boolean actionBar) {
                // GameTests exercise server behavior without a negotiated client connection.
            }
        };
    }

    private static UseOnContext context(ServerPlayer player, BlockPos target, Direction face) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(target), face, target, false);
        return new UseOnContext(player, InteractionHand.MAIN_HAND, hit);
    }
}
