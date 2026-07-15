package com.github.elidolney.ltwterraformer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.List;

public final class CreativeTerraformerItem extends Item {
    public static final double REMOTE_REACH = 256.0;

    public CreativeTerraformerItem(Properties properties) {
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
            TerraformMode next = ToolData.mode(stack).next();
            ToolData.setMode(stack, next);
            player.displayClientMessage(Component.translatable("message.ltwterraformer.mode", next.displayName()), true);
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
        }

        if (!player.getAbilities().instabuild) {
            player.displayClientMessage(Component.translatable("message.ltwterraformer.creative_only")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }

        if (!context.getLevel().isClientSide()) {
            int changed = TerrainOperations.apply(context, ToolData.mode(stack), ToolData.radius(stack));
            Component message = changed == 0
                    ? Component.translatable("message.ltwterraformer.no_changes").withStyle(ChatFormatting.YELLOW)
                    : Component.translatable("message.ltwterraformer.changed", changed);
            player.displayClientMessage(message, true);
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            int next = ToolData.radius(stack) >= TerrainOperations.MAX_RADIUS ? 1 : ToolData.radius(stack) + 1;
            ToolData.setRadius(stack, next);
            player.displayClientMessage(Component.translatable("message.ltwterraformer.radius", next), true);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        if (!player.getAbilities().instabuild) {
            player.displayClientMessage(Component.translatable("message.ltwterraformer.creative_only")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }

        HitResult hit = player.pick(REMOTE_REACH, 1.0F, false);
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            useOn(new UseOnContext(player, hand, blockHit));
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        if (!level.isClientSide()) {
            player.displayClientMessage(Component.translatable("message.ltwterraformer.no_remote_target")
                    .withStyle(ChatFormatting.YELLOW), true);
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.ltwterraformer.creative_terraformer.1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.ltwterraformer.mode", ToolData.mode(stack).displayName()).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.ltwterraformer.shape", ToolData.shape(stack).displayName()).withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("tooltip.ltwterraformer.radius", ToolData.radius(stack)).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.ltwterraformer.creative_terraformer.2").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.ltwterraformer.creative_terraformer.3").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.ltwterraformer.creative_terraformer.4").withStyle(ChatFormatting.DARK_AQUA));
    }
}
