package com.github.elidolney.ltwterraformer;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(LTWTerraformerTool.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AreaGameTests {
    private AreaGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void selectionLevelsSculptsAndWandBuildsBoxes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = headlessPlayer(level);
        player.getAbilities().instabuild = true;
        player.getAbilities().mayBuild = true;

        // A 5x5 stone floor with one bump and one hole.
        BlockPos corner = helper.absolutePos(new BlockPos(2, 50, 2));
        BlockPos opposite = corner.offset(4, 0, 4);
        for (int dx = 0; dx <= 4; dx++) {
            for (int dz = 0; dz <= 4; dz++) {
                level.setBlock(corner.offset(dx, 0, dz), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        BlockPos bump = corner.offset(2, 1, 2);
        BlockPos hole = corner.offset(1, 0, 1);
        level.setBlock(bump, Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(hole, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(hole.below(), Blocks.STONE.defaultBlockState(), 3);

        ItemStack terraformer = new ItemStack(ModItems.CREATIVE_TERRAFORMER.get());
        ToolData.setMode(terraformer, TerraformMode.FLATTEN);
        ToolData.setShape(terraformer, BrushShape.SELECTION);
        player.setItemInHand(InteractionHand.MAIN_HAND, terraformer);
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Blocks.DIRT));

        terraformer.useOn(context(player, corner, Direction.UP));
        helper.assertTrue(ToolData.areaOne(terraformer).isPresent() && ToolData.areaTwo(terraformer).isEmpty(),
                "First Selection click should only mark corner one");
        helper.assertTrue(level.getBlockState(corner).is(Blocks.STONE), "Marking a corner must not edit the world");
        terraformer.useOn(context(player, opposite, Direction.UP));
        helper.assertTrue(ToolData.areaTwo(terraformer).isPresent(), "Second Selection click should mark corner two");

        int depthBefore = UndoManager.depth(player);
        terraformer.useOn(context(player, corner, Direction.UP));
        helper.assertTrue(level.getBlockState(bump).isAir(), "Selection Flatten did not remove the bump");
        helper.assertTrue(level.getBlockState(hole).is(Blocks.DIRT), "Selection Flatten did not fill the hole");
        helper.assertTrue(level.getBlockState(opposite).is(Blocks.DIRT), "Selection Flatten did not reach the far corner");
        helper.assertTrue(UndoManager.depth(player) == depthBefore + 1, "Flatten should add one undo step");

        // Walls: raise the box two blocks and build only its perimeter.
        ToolData.setMode(terraformer, TerraformMode.WALLS);
        ToolData.setAreaExtraHeight(terraformer, 2);
        terraformer.useOn(context(player, corner, Direction.UP));
        helper.assertTrue(level.getBlockState(corner.above(2)).is(Blocks.DIRT), "Walls did not build the corner column");
        helper.assertTrue(level.getBlockState(corner.offset(2, 2, 2)).isAir(), "Walls must leave the inside open");
        helper.assertTrue(UndoManager.depth(player) == depthBefore + 2, "Undo history should keep both operations");

        int wallsRestored = UndoManager.undo(player);
        helper.assertTrue(wallsRestored == 32, "Two wall layers of a 5x5 box are 32 blocks, got " + wallsRestored);
        int flattenRestored = UndoManager.undo(player);
        helper.assertTrue(flattenRestored == 26, "Flatten should undo 26 changes, got " + flattenRestored);
        helper.assertTrue(level.getBlockState(bump).is(Blocks.STONE) && level.getBlockState(hole).isAir(),
                "Second undo did not restore the original terrain");

        // Grade refuses to run without a selection.
        ToolData.setShape(terraformer, BrushShape.SPHERE);
        int graded = TerrainOperations.apply(context(player, corner, Direction.UP), TerraformMode.GRADE, 2);
        helper.assertTrue(graded == 0, "Grade must not edit anything without a selection");

        // Builder Wand Box Fill between two clicked corners.
        ItemStack wand = new ItemStack(ModItems.SURVIVAL_BUILDER_WAND.get());
        ToolData.setRestriction(wand, WandRestriction.BOX);
        player.setItemInHand(InteractionHand.MAIN_HAND, wand);
        wand.useOn(context(player, corner, Direction.UP));
        helper.assertTrue(ToolData.wandCorner(wand).isPresent(), "Box Fill should remember its first corner");
        wand.useOn(context(player, corner.offset(2, 0, 2), Direction.UP));
        helper.assertTrue(ToolData.wandCorner(wand).isEmpty(), "Box Fill should reset after building");
        helper.assertTrue(level.getBlockState(corner.above()).is(Blocks.DIRT), "Box Fill did not place its first corner");
        helper.assertTrue(level.getBlockState(corner.offset(1, 1, 1)).is(Blocks.DIRT), "Box Fill did not fill the middle");
        int boxRestored = UndoManager.undo(player);
        helper.assertTrue(boxRestored == 8, "A 3x1x3 box around one existing block is 8 blocks, got " + boxRestored);

        helper.succeed();
    }

    private static ServerPlayer headlessPlayer(ServerLevel level) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(
                new GameProfile(UUID.randomUUID(), "ltw-area-test"), false);
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
