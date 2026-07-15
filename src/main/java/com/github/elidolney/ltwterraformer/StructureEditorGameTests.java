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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.BedPart;

import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(LTWTerraformerTool.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StructureEditorGameTests {
    private StructureEditorGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void copiesSavesLoadsPastesAndUndoes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = headlessPlayer(level);
        player.getAbilities().instabuild = true;
        player.getAbilities().mayBuild = true;

        ItemStack editor = new ItemStack(ModItems.STRUCTURE_EDITOR.get());
        BlockPos first = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos second = first.offset(1, 0, 1);
        level.setBlock(first, Blocks.OAK_LOG.defaultBlockState(), 3);
        level.setBlock(second, Blocks.OAK_LEAVES.defaultBlockState(), 3);
        ToolData.setCornerOne(editor, first, level.dimension().location().toString());
        ToolData.setCornerTwo(editor, second);
        helper.assertTrue(ToolData.adjustSelectionCeiling(editor, 3).orElseThrow() == first.getY() + 3,
                "Selection ceiling did not rise three blocks");
        helper.assertTrue(ToolData.cornerTwo(editor).orElseThrow().getY() == first.getY() + 3,
                "Upper selection corner did not move");
        ToolData.adjustSelectionCeiling(editor, -3);
        helper.assertTrue(ToolData.cornerTwo(editor).orElseThrow().getY() == first.getY(), "Selection ceiling did not lower");

        helper.assertTrue(StructureLibrary.captureClipboard(player, editor), "Selection was not copied");
        helper.assertTrue(ToolData.activeStructure(editor).equals(StructureLibrary.CLIPBOARD_NAME),
                "Clipboard did not become active");
        helper.assertTrue(StructureLibrary.savePreset(player, editor, "house_1"), "Named preset was not saved");
        helper.assertTrue(ToolData.structureNames(editor).contains("house_1"), "Preset was not listed on the tool");
        ToolData.clearActiveStructure(editor);
        helper.assertTrue(StructureLibrary.loadPreset(player, editor, "house_1"), "Named preset was not loaded");
        helper.assertTrue(ToolData.editorMode(editor) == StructureEditorMode.PASTE, "Load did not switch to paste mode");

        BlockPos destination = helper.absolutePos(new BlockPos(8, 2, 2));
        int changed = StructureLibrary.paste(player, editor, destination);
        helper.assertTrue(changed == 2, "Expected two non-air pasted blocks, got " + changed);
        helper.assertTrue(level.getBlockState(destination).is(Blocks.OAK_LOG), "Paste missed the log");
        helper.assertTrue(level.getBlockState(destination.offset(1, 0, 1)).is(Blocks.OAK_LEAVES), "Paste missed the leaves");
        helper.assertTrue(UndoManager.undo(player) == 2, "Paste was not fully undoable");
        helper.assertTrue(level.getBlockState(destination).isAir(), "Undo did not restore the paste origin");
        helper.assertTrue(StructureLibrary.deletePreset(player, editor, "house_1"), "Preset was not deleted");
        helper.assertTrue(!ToolData.structureNames(editor).contains("house_1"), "Deleted preset remained in the library");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void preservesAttachedBlocksBedsAndContainerData(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = headlessPlayer(level);
        player.getAbilities().instabuild = true;
        player.getAbilities().mayBuild = true;
        ItemStack editor = new ItemStack(ModItems.STRUCTURE_EDITOR.get());

        BlockPos source = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos torch = source.east();
        BlockPos bedFoot = source.south();
        BlockPos bedHead = bedFoot.east();
        BlockPos chestPos = bedHead.east();
        level.setBlock(source, Blocks.STONE.defaultBlockState(), 2);
        level.setBlock(torch, Blocks.WALL_TORCH.defaultBlockState()
                .setValue(WallTorchBlock.FACING, Direction.EAST), 2);
        level.setBlock(bedFoot, Blocks.RED_BED.defaultBlockState()
                .setValue(BedBlock.FACING, Direction.EAST).setValue(BedBlock.PART, BedPart.FOOT), 2);
        level.setBlock(bedHead, Blocks.RED_BED.defaultBlockState()
                .setValue(BedBlock.FACING, Direction.EAST).setValue(BedBlock.PART, BedPart.HEAD), 2);
        level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 2);
        ChestBlockEntity sourceChest = (ChestBlockEntity) level.getBlockEntity(chestPos);
        helper.assertTrue(sourceChest != null, "Source chest block entity was not created");
        sourceChest.setItem(0, new ItemStack(Items.DIAMOND, 3));
        sourceChest.setChanged();

        ToolData.setCornerOne(editor, source, level.dimension().location().toString());
        ToolData.setCornerTwo(editor, source.offset(2, 0, 1));
        helper.assertTrue(StructureLibrary.captureClipboard(player, editor), "Detailed house section was not copied");

        BlockPos destination = helper.absolutePos(new BlockPos(9, 2, 2));
        int changed = StructureLibrary.paste(player, editor, destination);
        helper.assertTrue(changed == 5, "Expected five detailed blocks to paste, got " + changed);
        helper.assertTrue(level.getBlockState(destination.east()).is(Blocks.WALL_TORCH), "Wall torch broke during paste");
        helper.assertTrue(level.getBlockState(destination.south()).is(Blocks.RED_BED)
                && level.getBlockState(destination.south().east()).is(Blocks.RED_BED), "Bed halves were not preserved");
        ChestBlockEntity pastedChest = (ChestBlockEntity) level.getBlockEntity(destination.offset(2, 0, 1));
        helper.assertTrue(pastedChest != null && pastedChest.getItem(0).is(Items.DIAMOND)
                && pastedChest.getItem(0).getCount() == 3, "Chest contents were not preserved");
        helper.assertTrue(UndoManager.undo(player) == 5, "Detailed structure paste was not fully undoable");
        helper.assertTrue(level.getBlockState(destination.east()).isAir(), "Undo did not remove the pasted torch");
        helper.succeed();
    }

    private static ServerPlayer headlessPlayer(ServerLevel level) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(
                new GameProfile(UUID.randomUUID(), "ltw-structure-test"), false);
        return new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
            @Override
            public void displayClientMessage(Component message, boolean actionBar) {
                // Server behavior is exercised without a negotiated client connection.
            }
        };
    }
}
