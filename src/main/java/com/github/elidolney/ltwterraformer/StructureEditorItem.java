package com.github.elidolney.ltwterraformer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

import java.util.List;

public final class StructureEditorItem extends Item {
    public StructureEditorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        ItemStack stack = context.getItemInHand();
        if (player.isShiftKeyDown()) {
            if (!context.getLevel().isClientSide()) {
                StructureEditorMode next = ToolData.editorMode(stack).next();
                ToolData.setEditorMode(stack, next);
                player.displayClientMessage(Component.translatable(
                        "message.ltwterraformer.structure_mode", next.displayName()), true);
            }
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
        }

        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }

        if (ToolData.editorMode(stack) == StructureEditorMode.SELECT) {
            select(serverPlayer, stack, context.getClickedPos());
        } else {
            StructureLibrary.paste(serverPlayer, stack, context.getClickedPos().relative(context.getClickedFace()));
        }
        return InteractionResult.SUCCESS;
    }

    private static void select(ServerPlayer player, ItemStack stack, BlockPos clicked) {
        if (ToolData.cornerOne(stack).isEmpty() || ToolData.cornerTwo(stack).isPresent()) {
            ToolData.setCornerOne(stack, clicked, player.level().dimension().location().toString());
            player.displayClientMessage(Component.translatable(
                    "message.ltwterraformer.structure_corner_one", clicked.getX(), clicked.getY(), clicked.getZ()), true);
            return;
        }

        if (ToolData.footprintCorner(stack).isEmpty()) {
            ToolData.setFootprintCorner(stack, clicked);
            player.displayClientMessage(Component.translatable(
                    "message.ltwterraformer.structure_footprint", clicked.getX(), clicked.getZ()), true);
            return;
        }

        ToolData.finishThreePointSelection(stack, clicked.getY());
        player.displayClientMessage(Component.translatable(
                "message.ltwterraformer.structure_height", clicked.getY()), true);
        StructureLibrary.captureClipboard(player, stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.ltwterraformer.structure_editor.1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.ltwterraformer.structure_mode", ToolData.editorMode(stack).displayName())
                .withStyle(ChatFormatting.AQUA));
        String active = ToolData.activeStructure(stack);
        if (!active.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.ltwterraformer.structure_active", StructureLibrary.displayName(active))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        tooltip.add(Component.translatable("tooltip.ltwterraformer.structure_editor.2").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.ltwterraformer.structure_editor.3").withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable("tooltip.ltwterraformer.structure_editor.4").withStyle(ChatFormatting.DARK_GRAY));
    }
}
